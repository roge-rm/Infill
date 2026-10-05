#!/usr/bin/env python3
"""Turns manual/*.md into the manual the app carries.

The manual is Markdown in `manual/`, so it can be read on the git host and
reviewed in a diff, and the app's Help window shows the same words. This
turns the Markdown into `ui/Manual.kt` so there's no second copy to keep
in step.

It isn't a full Markdown renderer. The manual uses this subset:

    # Title              the section, one per file (and one per sub-page)
    > summary            the line under it in the contents (optional)
    ## Heading           a heading inside the section
    ### Subheading       a heading inside that
    paragraph            run of lines, joined
    - bullet             a list item
    1. step              a numbered item
    `code` and **bold**  left in the text, drawn by the reader

Two files are written from it and neither is edited by hand: the app's
`ui/Manual.kt`, and the contents list in `manual/README.md`, so titles and
summaries aren't typed out twice.

Re-run after editing the manual:  python3 tools/gen_manual.py
Check both are in step:          python3 tools/gen_manual.py --check
"""
import pathlib
import re
import sys

SRC = pathlib.Path("manual")
OUT = pathlib.Path("shared/src/commonMain/kotlin/com/rm/infill/ui/Manual.kt")
INDEX = SRC / "README.md"
# The project's README carries the same list, its links reaching into manual/.
TOP = SRC.parent / "README.md"
OPEN, CLOSE = "<!-- contents -->", "<!-- /contents -->"

HEADING, PARA, BULLET, STEP, SUBHEADING = 0, 1, 2, 3, 4

# A Markdown link. The app's reader can't draw links, so they're resolved
# here: the file keeps the link so section pages can point at their
# sub-pages, and the app just gets the text. The app reaches sub-pages through
# the rows under "in detail".
LINK = re.compile(r"\[([^\]]+)\]\([^)]+\)")


def unlink(text):
    """`[Delay](delay.md)` -> `Delay`, for a reader that cannot draw one."""
    return LINK.sub(r"\1", text)


# --- The manual on a computer ----------------------------------------------
#
# The manual is written for the phone ("tap", "pinch"). The browser build
# shows it in a computer's words: a second text for a block, only where it
# differs. The Markdown stays one text, the phone's.
#
# - DESKTOP_WORDS, applied to every block: tap becomes click. A phone in the
#   manual is nearly always a telephone, so the few lines about the device
#   itself are whole sentences below.
# - KEEP, phrases the word rules leave alone.
# - TOUCH_ONLY, headings whose blocks are left as they are.
# - PHONE_ONLY, headings the computer leaves out with everything under them.
# - DESKTOP_SENTENCES, whole sentences a word swap can't fix, such as two
#   finger gestures, which are the mouse on a computer. An empty one leaves
#   the sentence out. Each must still be found in the manual, or --check
#   fails, so rewording one can't quietly drop it.
#
# Lines only a computer needs are written in the Markdown as a comment,
# `<!-- desktop: text -->` or `<!-- desktop: - a bullet -->`, which git hosts
# don't show and the phone's manual doesn't include.
DESKTOP_WORDS = [
    (re.compile(r"\b([Dd])ouble tap\b"), lambda m: m.group(1) + "ouble-click"),
    (re.compile(r"\b([Tt])ap(s|ped|ping)?\b"),
     lambda m: ("C" if m.group(1) == "T" else "c") + "lick" + {None: "", "s": "s", "ped": "ed", "ping": "ing"}[m.group(2)]),
]

KEEP = [
]

TOUCH_ONLY = {
}

PHONE_ONLY = {
}

DESKTOP_SENTENCES = {
    "On a phone held upright the strip is two lines, round the camera, and undo and redo sit in it.":
        "In a narrow window the strip is two lines, and undo and redo sit in it.",
    "With a tool picked, one finger builds and two fingers move and zoom the map.":
        "With a tool picked, the left mouse button builds.",
    "Buildings go where you lift your finger.":
        "Buildings go where you let go of the button.",
    "With **Inspect**, one finger moves the map and a tap opens a card on what's there:":
        "With **Inspect**, dragging moves the map and a click opens a card on what's there:",
    "Settings are kept on this phone and change as you go.":
        "Settings are kept in this browser and change as you go.",
}


def for_desktop(text, headings, used):
    """Returns [text] as the desktop says it, None if it's the same, or "" to leave it out.

    [headings] are the section heading and subheading it's under.
    """
    if any(h in PHONE_ONLY for h in headings if h):
        return ""
    if any(h in TOUCH_ONLY for h in headings if h):
        return None
    out = text
    kept = {}
    # A replaced sentence is already in desktop words, so keep the word rules off it.
    for n, (phone, desktop) in enumerate(DESKTOP_SENTENCES.items()):
        if phone in out:
            kept[f"\x01{n}\x01"] = desktop
            out = out.replace(phone, f"\x01{n}\x01")
            used.add(phone)
    for i, phrase in enumerate(KEEP):
        if phrase in out:
            kept[f"\x00{i}\x00"] = phrase
            out = out.replace(phrase, f"\x00{i}\x00")
    for pattern, swap in DESKTOP_WORDS:
        out = pattern.sub(swap, out)
    for mark, phrase in kept.items():
        out = out.replace(mark, phrase)
    out = out.strip()
    return None if out == text else out


DESKTOP_LINE = re.compile(r"^<!-- desktop: (.+) -->$")


def parse(path):
    """Parses one file into (title, summary, blocks)."""
    title, summary, blocks = None, "", []
    para = []

    def flush():
        if para:
            blocks.append((PARA, " ".join(para), False))
            para.clear()

    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.rstrip()
        if not line.strip():
            flush()
            continue
        only = DESKTOP_LINE.match(line.strip())
        if only:
            flush()
            inner = only.group(1).strip()
            if inner.startswith("- "):
                blocks.append((BULLET, inner[2:].strip(), True))
            else:
                blocks.append((PARA, inner, True))
        elif line.startswith("# "):
            flush()
            title = line[2:].strip()
        elif line.startswith("> "):
            flush()
            summary = line[2:].strip()
        elif line.startswith("## "):
            flush()
            blocks.append((HEADING, line[3:].strip(), False))
        elif line.startswith("### "):
            # A subheading.
            flush()
            blocks.append((SUBHEADING, line[4:].strip(), False))
        elif line.startswith("- "):
            flush()
            blocks.append((BULLET, line[2:].strip(), False))
        elif re.match(r"^\d+\. ", line):
            flush()
            blocks.append((STEP, line.split(". ", 1)[1].strip(), False))
        elif line.startswith("  ") and blocks and blocks[-1][0] in (BULLET, STEP) and not para and not blocks[-1][2]:
            # An indented continuation of the item above.
            kind, text, only = blocks[-1]
            blocks[-1] = (kind, text + " " + line.strip(), only)
        else:
            para.append(line.strip())
    flush()
    if title is None:
        sys.exit(f"gen_manual: {path} has no '# Title' line")
    return title, summary, blocks


def children_of(path):
    """Returns a section's sub-pages: a folder `05-power/` next to `05-power.md`.

    Sub-pages are the same Markdown, parsed the same way, and listed in the
    same contents.
    """
    folder = path.with_suffix("")
    if not folder.is_dir():
        return []
    return [(p, parse(p)) for p in sorted(folder.glob("*.md"))]


def section_id(path):
    """`12-money.md` -> `money`: a section's name that doesn't change with its title or number."""
    stem = path.stem
    head, _, rest = stem.partition("-")
    return rest if head.isdigit() and rest else stem

def kotlin(files, sections, used):
    q = lambda s: '"' + s.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$") + '"'
    out = [
        "package com.rm.infill.ui",
        "",
        "// Generated by tools/gen_manual.py from manual/. Do not edit by hand:",
        "// the Markdown in manual/ is the manual, and this is the same words in",
        "// the form the app can draw. Re-run the script after editing it.",
        "",
        "/** What a line of the manual is. Inline `code` and **bold** stay in the text. */",
        "enum class ManualKind { Heading, Para, Bullet, Step, Subheading }",
        "",
        "/**",
        " * [desktop] is the same words on a computer, \"click\" for \"tap\", or null",
        " * where they're the same. Empty text is a line the one platform leaves",
        " * out: a computer's own lines are empty on the phone.",
        " */",
        "class ManualBlock(val kind: ManualKind, val text: String, val desktop: String? = null) {",
        "    fun text(desktop: Boolean): String = if (desktop) this.desktop ?: text else text",
        "}",
        "",
        "class ManualSection(",
        "    /** Where it is in manual/, without the number: the same in every language, for opening it at. */",
        "    val id: String,",
        "    val title: String,",
        "    val summary: String,",
        "    val blocks: List<ManualBlock>,",
        "    /** Pages of this one's own, for a part that's more than a section. */",
        "    val children: List<ManualSection> = emptyList(),",
        "    /** [summary] on a computer, where it differs. */",
        "    val desktopSummary: String? = null,",
        ") {",
        "    fun summary(desktop: Boolean): String = if (desktop) desktopSummary ?: summary else summary",
        "}",
        "",
        "object Manual {",
        "    val sections: List<ManualSection> = listOf(",
    ]
    kinds = {HEADING: "Heading", PARA: "Para", BULLET: "Bullet", STEP: "Step", SUBHEADING: "Subheading"}

    def emit(path, title, summary, blocks, kids, pad):
        out.append(f"{pad}ManualSection({q(section_id(path))}, {q(unlink(title))}, {q(unlink(summary))}, listOf(")
        heading, sub = title, None
        for kind, text, only in blocks:
            if kind == HEADING:
                heading, sub = text, None
            elif kind == SUBHEADING:
                sub = text
            if only:
                shown, desktop = "", unlink(text)
            else:
                shown = unlink(text)
                # A heading is judged on its own, since PHONE_ONLY names the heading.
                under = (heading, None) if kind == HEADING else (heading, sub)
                desktop = for_desktop(shown, under, used)
            extra = f", {q(desktop)}" if desktop is not None else ""
            out.append(f"{pad}    ManualBlock(ManualKind.{kinds[kind]}, {q(shown)}{extra}),")
        desk = for_desktop(unlink(summary), (title,), used)
        tail = f", desktopSummary = {q(desk)}" if desk is not None else ""
        if not kids:
            out.append(f"{pad}){tail}),")
            return
        out.append(f"{pad}), listOf(")
        for kid, (t, s2, b2) in kids:
            emit(kid, t, s2, b2, [], pad + "    ")
        out.append(f"{pad}){tail}),")

    for path, ((title, summary, blocks), kids) in zip(files, sections):
        emit(path, title, summary, blocks, kids, "        ")
    out += ["    )", "}", ""]
    return "\n".join(out)


def lowered(summary):
    """Returns " - " and the summary starting in lower case, or nothing if the page has none."""
    return f" - {summary[0].lower() + summary[1:]}" if summary else ""


def contents(files, sections, prefix=""):
    """Builds the numbered list that goes between the markers, its links starting with [prefix]."""
    rows = []
    for i, (path, ((title, summary, _), kids)) in enumerate(zip(files, sections), 1):
        rows.append(f"{i}. [{title}]({prefix}{path.name})" + lowered(summary))
        for kp, (kt, ks, _) in kids:
            here = f"{prefix}{path.stem}/{kp.name}"
            rows.append(f"    - [{kt}]({here})" + lowered(ks))
    return "\n".join([OPEN, ""] + rows + ["", CLOSE])


def indexed(files, sections, path=INDEX, prefix=""):
    """Returns [path] with its contents list brought up to date."""
    text = path.read_text(encoding="utf-8")
    a, b = text.find(OPEN), text.find(CLOSE)
    if a < 0 or b < 0:
        sys.exit(f"gen_manual: {path} has no {OPEN} ... {CLOSE} to write into")
    return text[:a] + contents(files, sections, prefix) + text[b + len(CLOSE):]


def main():
    files = sorted(p for p in SRC.glob("*.md") if p.name != "README.md")
    if not files:
        sys.exit("gen_manual: manual/ has no sections")
    sections = [(parse(p), children_of(p)) for p in files]
    used = set()
    text = kotlin(files, sections, used)
    lost = [phone for phone in DESKTOP_SENTENCES if phone not in used]
    if lost:
        print("  FAIL manual: a sentence the desktop wording replaces is no longer in manual/:")
        for phone in lost:
            print(f"       {phone}")
        print("       update DESKTOP_SENTENCES in tools/gen_manual.py to match")
        sys.exit(1)
    index = indexed(files, sections)
    top = indexed(files, sections, TOP, "manual/")
    words = sum(len(t.split()) for (_, _, bs), _ in sections for _, t, only in bs if not only)
    pages = len(sections)
    for _, kids in sections:
        pages += len(kids)
        words += sum(len(t.split()) for _, (_, _, bs) in kids for _, t, only in bs if not only)
    if "--check" in sys.argv:
        for path, want in ((OUT, text), (INDEX, index), (TOP, top)):
            have = path.read_text(encoding="utf-8") if path.exists() else ""
            if have != want:
                print(f"  FAIL manual: {path} is not what manual/ would produce")
                print("       run: python3 tools/gen_manual.py")
                sys.exit(1)
        print(f"  ok   manual: {pages} pages, {words} words, in step")
        return
    OUT.write_text(text, encoding="utf-8")
    INDEX.write_text(index, encoding="utf-8")
    TOP.write_text(top, encoding="utf-8")
    print(f"gen_manual: {pages} pages, {words} words -> {OUT}, {INDEX} and {TOP}")


main()
