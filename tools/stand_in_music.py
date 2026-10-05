"""Stand-in music, until Dan's own pieces are written: four for each era, in
music/ as FLAC, named the way his will be (township-1.flac and so on).

Each is a short, simple tune in the manner of its years: parlour piano and a
music-hall waltz, hot jazz and a slow blues, a lounge and a bossa, funk and
synth pop, chill electronic, and slow pads for the future. They're only there
so the game has something to play in every era.

    cd tools && uv run --with numpy stand_in_music.py [names...]

With names (township-3 and so on), only those are made.
"""

import os
import subprocess
import sys
import wave

import numpy as np

SR = 44100
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "music")


def hz(midi):
    return 440.0 * 2 ** ((midi - 69) / 12)


def env(n, attack, decay):
    """An attack then an exponential fall over n samples."""
    t = np.arange(n) / SR
    a = np.minimum(1.0, t / max(attack, 1e-4))
    return a * np.exp(-t / max(decay, 1e-4))


def fade(n, out=0.03):
    """A short fade at the end of a note so it doesn't click."""
    e = np.ones(n)
    k = min(n, int(out * SR))
    if k > 0:
        e[-k:] *= np.linspace(1, 0, k)
    return e


# ---- instruments: each returns a note's samples ----------------------------

def piano(f, dur, vel=1.0):
    n = int((dur + 1.2) * SR)
    t = np.arange(n) / SR
    s = np.zeros(n)
    for k, a in enumerate([1, 0.5, 0.3, 0.18, 0.1, 0.06], 1):
        fk = f * k * (1 + 0.0004 * k * k)
        s += a * np.sin(2 * np.pi * fk * t) * np.exp(-t * (1.2 + 0.9 * k) * (f / 400) ** 0.3)
    return 0.3 * vel * s * env(n, 0.003, 1.6) * fade(n, 0.2)


def clarinet(f, dur, vel=1.0):
    n = int((dur + 0.08) * SR)
    t = np.arange(n) / SR
    vib = 1 + 0.004 * np.sin(2 * np.pi * 5.2 * t) * np.minimum(1, t / 0.3)
    ph = 2 * np.pi * f * np.cumsum(vib) / SR
    s = sum(a * np.sin(k * ph) for k, a in [(1, 1), (3, 0.45), (5, 0.25), (7, 0.12), (9, 0.06)])
    a = np.minimum(1, t / 0.04) * np.minimum(1, (dur + 0.08 - t) / 0.08).clip(0, 1)
    return 0.22 * vel * s * a


def bass(f, dur, vel=1.0):
    n = int((dur + 0.15) * SR)
    t = np.arange(n) / SR
    s = np.sin(2 * np.pi * f * t) + 0.35 * np.sin(4 * np.pi * f * t) + 0.12 * np.sin(6 * np.pi * f * t)
    return 0.4 * vel * s * env(n, 0.006, 0.6 + dur) * fade(n, 0.06)


def vibes(f, dur, vel=1.0):
    n = int((dur + 1.5) * SR)
    t = np.arange(n) / SR
    s = np.sin(2 * np.pi * f * t) + 0.25 * np.sin(2 * np.pi * f * 4 * t) * np.exp(-t * 6)
    trem = 1 - 0.25 * (0.5 + 0.5 * np.sin(2 * np.pi * 5.5 * t))
    return 0.25 * vel * s * trem * env(n, 0.002, 1.3) * fade(n, 0.3)


def epiano(f, dur, vel=1.0):
    n = int((dur + 0.9) * SR)
    t = np.arange(n) / SR
    index = 2.2 * np.exp(-t * 4) + 0.3
    s = np.sin(2 * np.pi * f * t + index * np.sin(2 * np.pi * f * t))
    return 0.22 * vel * s * env(n, 0.002, 1.1) * fade(n, 0.25)


def organ(f, dur, vel=1.0):
    n = int((dur + 0.05) * SR)
    t = np.arange(n) / SR
    s = sum(a * np.sin(2 * np.pi * f * k * t) for k, a in [(0.5, 0.6), (1, 1), (2, 0.5), (3, 0.3), (4, 0.2)])
    a = np.minimum(1, t / 0.01) * np.minimum(1, (dur + 0.05 - t) / 0.05).clip(0, 1)
    return 0.1 * vel * s * a * (1 + 0.1 * np.sin(2 * np.pi * 6.5 * t))


def pad(f, dur, vel=1.0):
    n = int((dur + 1.0) * SR)
    t = np.arange(n) / SR
    s = np.zeros(n)
    for d in (-0.006, 0.0, 0.007):
        for k in range(1, 7):
            s += np.sin(2 * np.pi * f * (1 + d) * k * t + k * d * 40) / (k * 1.4)
    a = np.minimum(1, t / 0.5) * np.minimum(1, (dur + 1.0 - t) / 1.0).clip(0, 1)
    return 0.045 * vel * s * a


def bell(f, dur, vel=1.0):
    n = int((dur + 2.5) * SR)
    t = np.arange(n) / SR
    s = sum(a * np.sin(2 * np.pi * f * k * t) * np.exp(-t * d) for k, a, d in [(1, 1, 1.0), (2.76, 0.4, 2.2), (5.4, 0.2, 4.0), (8.93, 0.1, 6.0)])
    return 0.2 * vel * s * np.minimum(1, t / 0.002) * fade(n, 0.4)


def pluck(f, dur, vel=1.0):
    n = int((dur + 0.6) * SR)
    t = np.arange(n) / SR
    s = sum(np.sin(2 * np.pi * f * k * t) * np.exp(-t * (3 + 4 * k)) / k for k in range(1, 8))
    return 0.3 * vel * s * np.minimum(1, t / 0.002) * fade(n, 0.1)


def saw_lead(f, dur, vel=1.0):
    n = int((dur + 0.1) * SR)
    t = np.arange(n) / SR
    s = sum(np.sin(2 * np.pi * f * k * t) / k for k in range(1, 10)) * np.exp(-np.arange(1, 2) * 0)  # a soft saw
    a = np.minimum(1, t / 0.01) * np.minimum(1, (dur + 0.1 - t) / 0.1).clip(0, 1)
    return 0.12 * vel * s * a


def kick(vel=1.0):
    n = int(0.35 * SR)
    t = np.arange(n) / SR
    f = 50 + 90 * np.exp(-t * 30)
    return 0.8 * vel * np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 9)


def snare(vel=1.0, rng=np.random.default_rng(1)):
    n = int(0.25 * SR)
    t = np.arange(n) / SR
    noise = rng.standard_normal(n)
    noise = noise - np.convolve(noise, np.ones(6) / 6, "same")
    return 0.3 * vel * (noise * np.exp(-t * 18) + 0.6 * np.sin(2 * np.pi * 190 * t) * np.exp(-t * 25))


def hat(vel=1.0, rng=np.random.default_rng(2)):
    n = int(0.06 * SR)
    t = np.arange(n) / SR
    noise = np.diff(rng.standard_normal(n + 1))
    return 0.08 * vel * noise * np.exp(-t * 70)


def brush(vel=1.0, rng=np.random.default_rng(3)):
    n = int(0.18 * SR)
    t = np.arange(n) / SR
    noise = rng.standard_normal(n)
    noise = np.convolve(noise, np.ones(4) / 4, "same")
    return 0.07 * vel * noise * np.minimum(1, t / 0.02) * np.exp(-t * 14)


# ---- the score -----------------------------------------------------------------

class Track:
    def __init__(self, seconds):
        self.l = np.zeros(int((seconds + 4) * SR))
        self.r = np.zeros_like(self.l)

    def add(self, at, samples, pan=0.0):
        i = int(at * SR)
        j = min(len(self.l), i + len(samples))
        if j <= i:
            return
        gl = np.cos((pan + 1) * np.pi / 4)
        gr = np.sin((pan + 1) * np.pi / 4)
        self.l[i:j] += samples[: j - i] * gl
        self.r[i:j] += samples[: j - i] * gr


SCALES = {
    "major": [0, 2, 4, 5, 7, 9, 11],
    "minor": [0, 2, 3, 5, 7, 8, 10],
    "dorian": [0, 2, 3, 5, 7, 9, 10],
    "lydian": [0, 2, 4, 6, 7, 9, 11],
    "blues": [0, 3, 5, 6, 7, 10],
}

# Chords as offsets from the key's root.
CHORDS = {
    "I": [0, 4, 7], "I6": [0, 4, 7, 9], "I7": [0, 4, 7, 10], "III7": [4, 8, 11, 14], "bIII": [3, 7, 10], "Imaj7": [0, 4, 7, 11], "ii": [2, 5, 9], "ii7": [2, 5, 9, 12],
    "iii7": [4, 7, 11, 14], "IV": [5, 9, 12], "IV7": [5, 9, 12, 15], "IVmaj7": [5, 9, 12, 16], "iv": [5, 8, 12],
    "V": [7, 11, 14], "V7": [7, 11, 14, 17], "vi": [9, 12, 16], "vi7": [9, 12, 16, 19], "VI7": [9, 13, 16, 19],
    "II7": [2, 6, 9, 12], "i": [0, 3, 7], "i7": [0, 3, 7, 10], "iv7": [5, 8, 12, 15], "bVI": [8, 12, 15],
    "bVII": [10, 14, 17], "II": [2, 6, 9], "v7": [7, 10, 14, 17], "bIImaj7": [1, 5, 8, 12],
}


def melody(rng, key, scale, chords, bars, beats, rhythms, low, high):
    """A tune over the chords: four-bar phrases A, A, B, A, chord tones on the beat, steps between."""
    steps = SCALES[scale]
    pitches = sorted({key + 12 * o + s for o in range(-2, 4) for s in steps if low <= key + 12 * o + s <= high})

    def phrase(start_bar):
        notes = []
        p = rng.choice(pitches[len(pitches) // 3: 2 * len(pitches) // 3])
        for b in range(4):
            chord = chords[(start_bar + b) % len(chords)]
            tones = [key + c for c in CHORDS[chord]]
            rhythm = rhythms[rng.integers(len(rhythms))]
            at = 0.0
            for d in rhythm:
                if d < 0:
                    at += -d
                    continue
                strong = abs(at - round(at)) < 1e-6 and int(round(at)) % 2 == 0
                if strong:
                    near = [q for q in pitches if (q - key) % 12 in [(t - key) % 12 for t in tones]]
                    p = min(near, key=lambda q: abs(q - p) + rng.random())
                else:
                    i = pitches.index(min(pitches, key=lambda q: abs(q - p)))
                    i = int(np.clip(i + rng.choice([-2, -1, -1, 1, 1, 2]), 0, len(pitches) - 1))
                    p = pitches[i]
                notes.append((b * beats + at, d, p))
                at += d
        return notes

    a = phrase(0)
    b = phrase(8)
    out = []
    for k, ph in enumerate([a, a, b, a][: bars // 4]):
        for at, d, p in ph:
            out.append((k * 4 * beats + at, d, p))
    return out


def render(name, *args, **kwargs):
    """Renders [name] unless names were given on the command line and it isn't one of them."""
    if ONLY and name not in ONLY:
        return
    render_piece(name, *args, **kwargs)


ONLY = set(sys.argv[1:])


def render_piece(name, tempo, key, scale, chords, beats=4, swing=0.0, lead=piano, comp=piano, comp_style="stride",
           bass_style="roots", drums=None, lead_range=(64, 84), rhythms=None, pad_under=False, seed=1, repeats=2,
           lead_gain=1.0, comp_gain=0.6, reverb=0.25):
    rng = np.random.default_rng(seed)
    beat = 60.0 / tempo
    bars = 16
    rhythms = rhythms or [[1, 1, 1, 1], [1, 0.5, 0.5, 1, 1], [2, 1, 1], [0.5, 0.5, 1, 2], [1.5, 0.5, 2], [1, -1, 1, 1]]
    if beats == 3:
        rhythms = [[1, 1, 1], [2, 1], [1, 0.5, 0.5, 1], [3]]
    tune = melody(rng, key, scale, chords, bars, beats, rhythms, *lead_range)
    total = repeats * bars * beats * beat + 2 * beats * beat
    tr = Track(total)

    def when(b):
        # Swung eighths: the off-beat late.
        whole = np.floor(b)
        frac = b - whole
        if swing and abs(frac - 0.5) < 1e-6:
            frac = 0.5 + swing * 0.5
        return (whole + frac) * beat

    for rep in range(repeats):
        base = rep * bars * beats
        for bar in range(bars):
            chord = chords[bar % len(chords)]
            tones = [key + c for c in CHORDS[chord]]
            root = key + CHORDS[chord][0]
            b0 = base + bar * beats
            # The bass.
            if bass_style == "roots":
                for k in range(0, beats, 2 if beats == 4 else 3):
                    p = root - 24 + (7 if k == 2 else 0)
                    tr.add(when(b0 + k), bass(hz(p), beat * 1.6), -0.1)
            elif bass_style == "walk":
                walk = [root, root + 4, root + 7, root + 5 if rng.random() < 0.5 else root + 9]
                for k in range(beats):
                    tr.add(when(b0 + k), bass(hz(walk[k % 4] - 24), beat * 0.9), -0.1)
            elif bass_style == "funk":
                for at, off in [(0, 0), (1.5, 12), (2.5, 0), (3, 10)]:
                    tr.add(when(b0 + at), bass(hz(root - 24 + off), beat * 0.4, 1.1), -0.1)
            elif bass_style == "eighths":
                for k in range(beats * 2):
                    tr.add(when(b0 + k / 2), bass(hz(root - 24), beat * 0.45, 0.8), -0.1)
            elif bass_style == "bossa":
                for at, off in [(0, 0), (1.5, 7), (2, 7), (3.5, 0)]:
                    tr.add(when(b0 + at), bass(hz(root - 24 + off), beat * 0.5), -0.1)
            # The chords.
            voicing = [t - 12 if t > key + 7 else t for t in tones]
            if comp_style == "stride":
                for k in (1, 3):
                    for t in voicing:
                        tr.add(when(b0 + k), comp(hz(t), beat * 0.5, comp_gain), 0.15)
            elif comp_style == "waltz":
                for k in (1, 2):
                    for t in voicing:
                        tr.add(when(b0 + k), comp(hz(t), beat * 0.5, comp_gain), 0.15)
            elif comp_style == "sustain":
                for t in voicing:
                    tr.add(when(b0), comp(hz(t), beat * beats * 0.95, comp_gain), 0.2)
            elif comp_style == "offbeats":
                for k in range(beats):
                    for t in voicing:
                        tr.add(when(b0 + k + 0.5), comp(hz(t), beat * 0.3, comp_gain), 0.2)
            elif comp_style == "arp":
                order = voicing + [voicing[0] + 12]
                for k in range(beats * 2):
                    tr.add(when(b0 + k / 2), comp(hz(order[k % len(order)] + 12), beat * 0.45, comp_gain), 0.25 if k % 2 else -0.25)
            if pad_under:
                for t in voicing:
                    tr.add(when(b0), pad(hz(t), beat * beats), -0.2)
            # The drums.
            if drums == "brushes":
                for k in range(beats):
                    tr.add(when(b0 + k), brush(1.0 if k % 2 else 0.6), 0.3)
                    tr.add(when(b0 + k + 0.5), brush(0.4), 0.3)
            elif drums == "machine":
                for k in range(beats):
                    if k % 2 == 0:
                        tr.add(when(b0 + k), kick())
                    else:
                        tr.add(when(b0 + k), snare())
                    tr.add(when(b0 + k), hat(0.8), 0.3)
                    tr.add(when(b0 + k + 0.5), hat(0.5), 0.3)
            elif drums == "soft":
                tr.add(when(b0), kick(0.7))
                tr.add(when(b0 + 2.5), kick(0.5))
                for k in range(beats * 2):
                    tr.add(when(b0 + k / 2), hat(0.4 if k % 2 else 0.6), 0.3)
                tr.add(when(b0 + 2), snare(0.5))
        # The tune, a little freer the second time.
        for at, d, p in tune:
            jitter = 0 if rep == 0 else rng.choice([0, 0, 0, 12 if p < lead_range[1] - 12 else 0])
            tr.add(when(base + at), lead(hz(p + jitter), d * beat * 0.95, lead_gain), -0.15)
    # An ending on the home chord.
    end = repeats * bars * beats
    for t in [key + c for c in CHORDS[chords[0]]]:
        tr.add(when(end), comp(hz(t), beat * beats * 1.5, comp_gain), 0.15)
    tr.add(when(end), bass(hz(key + CHORDS[chords[0]][0] - 24), beat * beats * 1.5), -0.1)
    tr.add(when(end), lead(hz(key + 12 + CHORDS[chords[0]][0] if key + 12 <= lead_range[1] else key), beat * beats * 1.2, lead_gain), -0.15)

    # A room round it.
    out = np.stack([tr.l, tr.r])
    if reverb:
        n = int(1.6 * SR)
        ir_rng = np.random.default_rng(seed + 100)
        t = np.arange(n) / SR
        for c in range(2):
            ir = ir_rng.standard_normal(n) * np.exp(-t * 3.2)
            ir[0] = 0
            wet = np.fft.irfft(np.fft.rfft(out[c], len(out[c]) + n) * np.fft.rfft(ir, len(out[c]) + n))[: len(out[c])]
            out[c] = out[c] + reverb * wet / np.max(np.abs(ir)) * 0.02
    # Trim the silence off the end, fade out, and leave room at the top.
    last = np.max(np.nonzero(np.max(np.abs(out), axis=0) > 1e-4)) + 1
    out = out[:, :last]
    k = int(2.0 * SR)
    out[:, -k:] *= np.linspace(1, 0, k)
    out *= 0.89 / np.max(np.abs(out))
    write(name, out)


def write(name, stereo):
    os.makedirs(OUT, exist_ok=True)
    wav_path = os.path.join(OUT, name + ".wav")
    pcm = (np.clip(stereo.T, -1, 1) * 32767).astype("<i2")
    with wave.open(wav_path, "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    flac = os.path.join(OUT, name + ".flac")
    subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", wav_path, flac], check=True)
    os.remove(wav_path)
    print(name, f"{stereo.shape[1] / SR:.0f} s")


def main():
    # 1900s: parlour piano, and a music-hall waltz.
    render("township-1", 96, 60, "major", ["I", "I", "IV", "IV", "I", "V7", "V7", "I"], seed=11)
    render("township-2", 138, 65, "major", ["I", "I", "V7", "V7", "V7", "V7", "I", "I"], beats=3, comp_style="waltz", seed=12)
    # 1910s to the 30s: hot jazz on a clarinet, and a slow blues.
    render("streetcar-1", 168, 58, "major", ["I", "VI7", "II7", "V7"], swing=0.33, lead=clarinet, comp_style="stride", bass_style="roots",
           drums="brushes", lead_range=(62, 82), seed=21)
    render("streetcar-2", 76, 65, "blues", ["I7", "IV7", "I7", "I7", "IV7", "IV7", "I7", "I7", "V7", "IV7", "I7", "V7"], swing=0.33, lead=clarinet,
           bass_style="walk", drums="brushes", lead_range=(60, 80), seed=22)
    # The 40s to the 60s: a lounge on the vibes, and a bossa.
    render("motor-1", 120, 67, "major", ["ii7", "V7", "Imaj7", "Imaj7", "vi7", "II7", "ii7", "V7"], swing=0.33, lead=vibes, comp=organ, comp_style="sustain",
           bass_style="walk", drums="brushes", lead_range=(67, 88), seed=31, comp_gain=0.4)
    render("motor-2", 132, 57, "dorian", ["i7", "IV7", "i7", "IV7", "iv7", "bVII", "i7", "v7"], lead=vibes, comp=epiano, comp_style="offbeats",
           bass_style="bossa", drums="brushes", lead_range=(64, 84), seed=32, comp_gain=0.35)
    # The 70s to the 90s: funk on an electric piano, and synth pop.
    render("renewal-1", 100, 64, "dorian", ["i7", "IV7"], lead=saw_lead, comp=epiano, comp_style="offbeats", bass_style="funk", drums="machine",
           lead_range=(64, 84), seed=41, comp_gain=0.5, lead_gain=0.8)
    render("renewal-2", 116, 57, "minor", ["i", "bVI", "I", "bVII"], lead=saw_lead, comp=pluck, comp_style="arp", bass_style="eighths", drums="machine",
           pad_under=True, lead_range=(64, 81), seed=42, comp_gain=0.4, lead_gain=0.7)
    # 2000 to 2030: chill electronic, and soft plucked arpeggios.
    render("infill-1", 88, 62, "major", ["Imaj7", "vi7", "IVmaj7", "V"], lead=epiano, comp=pad, comp_style="sustain", bass_style="roots", drums="soft",
           lead_range=(66, 86), seed=51, comp_gain=0.9)
    render("infill-2", 104, 60, "major", ["I", "vi", "IV", "V"], lead=bell, comp=pluck, comp_style="arp", bass_style="roots", drums="soft",
           pad_under=True, lead_range=(67, 88), seed=52, comp_gain=0.35, lead_gain=0.7)
    # 2030 on: slow pads and bells, and a gentle pulse.
    render("future-1", 66, 62, "lydian", ["Imaj7", "II", "Imaj7", "vi7"], lead=bell, comp=pad, comp_style="sustain", bass_style="roots",
           lead_range=(69, 93), rhythms=[[2, 2], [4], [1, 1, 2], [3, 1]], seed=61, comp_gain=1.2, lead_gain=0.8, reverb=0.6)
    render("future-2", 96, 64, "minor", ["i7", "bVI", "bIImaj7", "i7"], lead=bell, comp=pluck, comp_style="arp", bass_style="eighths", drums="soft",
           pad_under=True, lead_range=(64, 88), seed=62, comp_gain=0.3, lead_gain=0.6, reverb=0.5)

    # More for each era, so a long game hears more than two.
    # 1900s: a parlour ballad on the piano, and a slow hymn on the organ.
    render("township-3", 84, 62, "major", ["I", "vi", "ii7", "V7", "I", "IV", "V7", "I"], comp_style="stride", seed=13, repeats=2)
    render("township-4", 72, 60, "major", ["I", "IV", "I", "V", "vi", "IV", "V7", "I"], lead=organ, comp=organ, comp_style="sustain",
           lead_range=(60, 76), rhythms=[[2, 2], [1, 1, 2], [4], [3, 1]], seed=14, comp_gain=0.4, reverb=0.4)
    # 1910s to the 30s: a ragtime two-step, and a sweet dance-band waltz.
    render("streetcar-3", 112, 63, "major", ["I", "I", "III7", "III7", "VI7", "II7", "V7", "I"], lead=piano, comp_style="stride", bass_style="roots",
           lead_range=(67, 87), seed=23)
    render("streetcar-4", 126, 58, "major", ["I", "I6", "ii7", "V7", "V7", "ii7", "V7", "I"], beats=3, swing=0.0, lead=clarinet, comp_style="waltz",
           drums="brushes", lead_range=(62, 80), seed=24)
    # The 40s to the 60s: big-band swing, and a slow organ-trio blues.
    render("motor-3", 152, 65, "major", ["Imaj7", "vi7", "ii7", "V7", "iii7", "VI7", "ii7", "V7"], swing=0.33, lead=clarinet, comp=piano,
           comp_style="offbeats", bass_style="walk", drums="brushes", lead_range=(62, 84), seed=33)
    render("motor-4", 84, 60, "blues", ["I7", "IV7", "I7", "I7", "IV7", "IV7", "I7", "I7", "V7", "IV7", "I7", "V7"], swing=0.33, lead=organ,
           comp=organ, comp_style="sustain", bass_style="walk", drums="brushes", lead_range=(60, 79), seed=34, comp_gain=0.35)
    # The 70s to the 90s: disco strings, and a slow synth ballad.
    render("renewal-3", 120, 62, "minor", ["i7", "iv7", "bVII", "bIII"], lead=saw_lead, comp=epiano, comp_style="offbeats", bass_style="eighths",
           drums="machine", lead_range=(62, 82), seed=43, comp_gain=0.4, lead_gain=0.7)
    render("renewal-4", 80, 64, "major", ["I", "V", "vi", "IV"], lead=epiano, comp=pad, comp_style="sustain", bass_style="roots", drums="soft",
           lead_range=(64, 84), seed=44, comp_gain=0.8)
    # 2000 to 2030: downtempo with a walking bass, and an acoustic-feeling pluck.
    render("infill-3", 92, 57, "dorian", ["i7", "IV7", "i7", "v7"], lead=vibes, comp=epiano, comp_style="offbeats", bass_style="walk", drums="soft",
           lead_range=(64, 84), seed=53, comp_gain=0.35)
    render("infill-4", 112, 67, "major", ["I", "V", "vi", "IV", "I", "V", "IV", "IV"], lead=pluck, comp=piano, comp_style="arp", bass_style="roots",
           drums="soft", lead_range=(67, 88), seed=54, comp_gain=0.3, lead_gain=0.8)
    # 2030 on: drifting bells over long chords, and a bright pulse.
    render("future-3", 60, 65, "lydian", ["Imaj7", "IVmaj7", "Imaj7", "II"], lead=pad, comp=bell, comp_style="arp", bass_style="roots",
           lead_range=(65, 86), rhythms=[[4], [2, 2], [3, 1]], seed=63, comp_gain=0.25, lead_gain=0.9, reverb=0.7)
    render("future-4", 108, 62, "major", ["Imaj7", "iii7", "vi7", "IVmaj7"], lead=bell, comp=pluck, comp_style="arp", bass_style="eighths",
           drums="machine", pad_under=True, lead_range=(69, 90), seed=64, comp_gain=0.3, lead_gain=0.6, reverb=0.45)


if __name__ == "__main__":
    main()
