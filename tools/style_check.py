#!/usr/bin/env python3
"""Checks new writing against the house style before a commit.

What it looks at: the game's strings, the README, and comments in the code and
tools, as far as they're new in the commit (or everything with --all), and a
commit message with --message FILE. What it catches: dashes in sentences, US
spellings, "X, not Y", bold in the README, words the game doesn't use any more,
and the names of the games Infill draws on, which it reads from docs/lineage.md
so they're never written here.

It can't tell whether something is plain and short. That's still read by eye.
"""

import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
STRINGS = "shared/src/commonMain/composeResources/values/strings.xml"

# Canadian English, word by word: these are the US spellings that keep turning up.
US = {
    "color": "colour", "colors": "colours", "colored": "coloured", "colorful": "colourful",
    "center": "centre", "centers": "centres", "centered": "centred",
    "gray": "grey", "harbor": "harbour", "harbors": "harbours",
    "behavior": "behaviour", "behaviors": "behaviours",
    "neighbor": "neighbour", "neighbors": "neighbours", "neighborhood": "neighbourhood",
    "favorite": "favourite", "traveled": "travelled", "traveling": "travelling",
    "labeled": "labelled", "labeling": "labelling", "modeled": "modelled",
    "catalog": "catalogue", "defense": "defence", "theater": "theatre",
    "liter": "litre", "liters": "litres", "fiber": "fibre", "analyze": "analyse",
}

DISCORD = "**[on my discord](https://discord.gg/9Wun47jGC6)**"

# Words the game's text doesn't use, and what it says instead.
RETIRED = {
    r"\bshut\b": "closed",
    r"\bTake .* off\b": "a short verb and noun, like Remove toll",
}

# "X, not Y", except where "not" just goes with what follows.
NOT_Y = re.compile(r",\s+not\s+(?!before\b|yet\b|if\b|when\b|to\b|that\b|only\b|all\b|at all\b|even\b|just\b)\w")

DASH = re.compile(r"[—–]")
# A dash on its own, as a mark for nothing.
LONE_DASH = re.compile(r"""^["'>]?\s*[—–]\s*["'<]?$""")


def lineage_names():
    """The games Infill draws on, from the untracked lineage notes."""
    path = ROOT / "docs" / "lineage.md"
    if not path.exists():
        return []
    names = set()
    for line in path.read_text().splitlines():
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if len(cells) < 2 or cells[0].startswith("---") or cells[0] == "Idea in Infill":
            continue
        source = re.sub(r"\(.*?\)", "", cells[1]).replace("The original", "")
        for part in re.split(r",| and |;", source):
            part = part.strip()
            if len(part) >= 4:
                names.add(part)
                names.add(part.replace(":", ""))
                # The series without its number, and what follows a colon.
                names.add(re.sub(r"\s+(\d+|I+)$", "", part))
                if ":" in part:
                    names.add(part.split(":", 1)[1].strip())
    return sorted(names, key=len, reverse=True)


def prose(path, line):
    """The part of a line that's writing, or None: a string's text, a comment, the README."""
    if path == STRINGS:
        m = re.search(r"<(?:string|item)[^>]*>(.*?)</", line)
        return m.group(1) if m else None
    if path.endswith(".md"):
        return line
    if path.endswith((".kt", ".kts")):
        m = re.search(r"(//|/\*\*?|^\s*\*)(.*)", line)
        return m.group(2) if m else None
    if path.endswith(".py"):
        m = re.search(r"#(.*)", line)
        if m:
            return m.group(1)
        m = re.search(r'^\s*("""|\'\'\')?([A-Z].*)', line)
        return m.group(2) if m and line.strip().endswith(('."""', ".", '"""')) else None
    return None


def check(path, number, text, names, out):
    if text is None:
        return
    # Code inside the writing isn't writing: `names`, [links] and quoted strings.
    words = re.sub(r"`[^`]*`|\[[^\]]*\]|\"[^\"]*\"", "", text)
    if DASH.search(words) and not LONE_DASH.match(text.strip()):
        out.append((path, number, "a dash in a sentence: use a comma, colon or full stop", text))
    for us, ca in US.items():
        if re.search(rf"\b{us}\b", words, re.IGNORECASE):
            out.append((path, number, f"US spelling {us}: write {ca}", text))
    if NOT_Y.search(words):
        out.append((path, number, "\"X, not Y\": say what it is", text))
    # The one bold the README keeps: the link to the discord, as Dan's other projects have it.
    if path.endswith("README.md") and "**" in text.replace(DISCORD, ""):
        out.append((path, number, "bold in the README", text))
    if path == STRINGS:
        for pattern, instead in RETIRED.items():
            if re.search(pattern, words, re.IGNORECASE):
                out.append((path, number, f"the game says {instead}", text))
    for name in names:
        if re.search(rf"\b{re.escape(name)}\b", text, re.IGNORECASE):
            out.append((path, number, "names a game Infill draws on", text.replace(name, "▒" * len(name))))
            break


def changed_lines(everything):
    """(path, line number, line) for each line to look at: added in the staged change, or every line with [everything]."""
    if everything:
        files = subprocess.run(["git", "ls-files"], cwd=ROOT, capture_output=True, text=True).stdout.split()
        for f in files:
            if prose(f, "") is None and not f.endswith((".kt", ".kts", ".py", ".md", ".xml")):
                continue
            try:
                for n, line in enumerate((ROOT / f).read_text().splitlines(), 1):
                    yield f, n, line
            except (UnicodeDecodeError, FileNotFoundError):
                pass
        return
    diff = subprocess.run(["git", "diff", "--cached", "-U0", "--no-color"], cwd=ROOT, capture_output=True, text=True).stdout
    path = None
    number = 0
    for line in diff.splitlines():
        if line.startswith("+++ "):
            path = line[6:] if line.startswith("+++ b/") else None
        elif line.startswith("@@"):
            number = int(re.search(r"\+(\d+)", line).group(1))
        elif line.startswith("+") and path:
            yield path, number, line[1:]
            number += 1


def main():
    args = sys.argv[1:]
    names = lineage_names()
    out = []
    if args[:1] == ["--message"]:
        # A commit message is all writing; the lines git adds and the trailers aren't.
        for n, line in enumerate(Path(args[1]).read_text().splitlines(), 1):
            if line.startswith("#") or re.match(r"^[A-Z][\w-]+: ", line):
                continue
            check("commit message", n, line, names, out)
    else:
        for path, n, line in changed_lines("--all" in args):
            if path.startswith("docs/") or path == "tools/style_check.py":
                continue
            check(path, n, prose(path, line), names, out)
    for path, n, why, text in out:
        print(f"{path}:{n}: {why}\n    {text.strip()}")
    if out:
        print(f"\n{len(out)} to fix. Run tools/style_check.py --all to look at everything.")
        sys.exit(1)


if __name__ == "__main__":
    main()
