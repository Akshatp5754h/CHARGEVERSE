#!/usr/bin/env python3
"""CHARGERVERSE original sound generator — procedural WAV synthesis.

Every sound is synthesised from oscillators/noise/shaped envelopes — original
by construction. Outputs 16-bit 44.1 kHz mono WAV.

Recipes:
  impact      cinematic sub-hit (noise burst + pitched thump + tail)
  lightning   crackle burst (filtered noise, jagged envelope)
  plasma      detuned saw hum with slow filter sweep
  startup     rising FM sweep + shimmer
  magic       bell partial cluster (inharmonic), long decay
  pulse       soft techno pulse (charging heartbeat)
  chime_up    two-note rising chime (connect)
  chime_down  falling counterpart (disconnect)
  full        resolved major triad swell
  low         minor warning pulse
  mechanical  servo whirr (bandpassed noise + sawtooth slide)
  minimal     tick + soft sine blip

Usage: python gen_sounds.py --recipe impact --seed 3 --duration 1.6 --out out/impact_3.wav
"""
import argparse
import math
import os
import struct
import wave

import numpy as np

SR = 44100


def env(n, a, d, curve=3.0):
    """attack/decay envelope, n samples."""
    an = max(1, int(a * SR))
    at = np.linspace(0, 1, an) ** 0.5
    dn = n - an
    dt = np.linspace(1, 0, max(1, dn)) ** (1 / curve)
    return np.concatenate([at, dt])[:n]


def noise_band(n, lo, hi, rng):
    x = rng.standard_normal(n)
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(n, 1 / SR)
    mask = (f >= lo) & (f <= hi)
    X[~mask] = 0
    y = np.fft.irfft(X, n)
    return y / (np.max(np.abs(y)) + 1e-9)


def tone(freq, n, detune_cents=0.0):
    f = freq * 2 ** (detune_cents / 1200)
    return np.sin(2 * np.pi * f * np.arange(n) / SR)


def adsorb(sig, peak=0.92):
    m = np.max(np.abs(sig))
    return sig / (m + 1e-9) * peak


def save(path, sig):
    sig = adsorb(sig)
    data = (sig * 32767).astype(np.int16)
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(struct.pack(f"<{len(data)}h", *data))
    print(f"OK {path} ({os.path.getsize(path)} bytes)")


def recipe_impact(n, rng, seed):
    boom = noise_band(n, 30, 220, rng) * env(n, 0.004, 1.0, 2.0)
    thump_f = 55 * (1 + 0.4 * np.exp(-np.arange(n) / (0.12 * SR)))
    thump = np.sin(2 * np.pi * np.cumsum(thump_f) / SR) * env(n, 0.002, 1.0, 2.2) * 0.8
    crack = noise_band(n, 900, 6000, rng) * env(n, 0.001, 0.12, 4.0) * 0.5
    return boom * 0.9 + thump + crack


def recipe_lightning(n, rng, seed):
    crack = noise_band(n, 1200, 9000, rng)
    jag = np.abs(rng.standard_normal(n))
    jag = (jag > np.percentile(jag, 92)).astype(np.float64)
    jag = np.convolve(jag, np.ones(220) / 220, "same")
    rumble = noise_band(n, 40, 300, rng) * env(n, 0.01, 1.0, 2.5) * 0.7
    return (crack * jag + rumble) * env(n, 0.001, 1.0, 2.5)


def recipe_plasma(n, rng, seed):
    f0 = 110 + (seed % 7) * 6
    sig = tone(f0, n) + tone(f0, n, 14) + tone(f0 * 2, n, -9) * 0.5
    sweep = 0.5 + 0.5 * np.sin(2 * np.pi * np.arange(n) / n * 2 + seed)
    lfo = 0.7 + 0.3 * np.sin(2 * np.pi * 0.7 * np.arange(n) / SR)
    return (sig * sweep * lfo) * env(n, 0.25, 1.0, 1.2)


def recipe_startup(n, rng, seed):
    t = np.arange(n) / SR
    f = 180 * 2 ** (2.2 * t / (n / SR))
    fm = np.sin(2 * np.pi * f * t + 3 * np.sin(2 * np.pi * 0.5 * t))
    shimmer = tone(1760, n) * env(n, 0.6 * n / SR, 1.0, 1.5) * 0.12
    return fm * env(n, 0.05, 1.0, 1.6) + shimmer


def recipe_magic(n, rng, seed):
    sig = np.zeros(n)
    for k, (f, amp) in enumerate([(880, 1.0), (1320, 0.6), (1760, 0.45), (2637, 0.3), (3520, 0.2)]):
        off = int(rng.uniform(0, 0.05) * SR)
        seg = n - off
        sig[off:] += tone(f, seg) * env(seg, 0.003, 1.0, 3.5) * amp
    sparkle = noise_band(n, 3000, 10000, rng) * env(n, 0.002, 0.35, 5.0) * 0.25
    return sig + sparkle


def recipe_pulse(n, rng, seed):
    beat = int(0.5 * SR)
    sig = np.zeros(n)
    i = 0
    while i < n:
        seg = min(beat, n - i)
        thump = tone(70, seg) * env(seg, 0.002, 1.0, 2.0) * 0.9
        sig[i:i + seg] += thump
        i += beat
    return sig


def recipe_chime_up(n, rng, seed):
    sig = np.zeros(n)
    for off, f in [(0, 523.25), (0.09, 783.99)]:
        s = int(off * SR)
        seg = n - s
        sig[s:] += tone(f, seg) * env(seg, 0.004, 1.0, 3.0) * 0.8
    return sig


def recipe_chime_down(n, rng, seed):
    sig = np.zeros(n)
    for off, f in [(0, 783.99), (0.09, 523.25)]:
        s = int(off * SR)
        seg = n - s
        sig[s:] += tone(f, seg) * env(seg, 0.004, 1.0, 3.0) * 0.8
    return sig


def recipe_full(n, rng, seed):
    sig = np.zeros(n)
    for f in (523.25, 659.25, 783.99):
        sig += tone(f, n) * env(n, 0.15, 1.0, 1.8) * 0.5
    return sig + noise_band(n, 2000, 8000, rng) * env(n, 0.3, 1.0, 2.0) * 0.06


def recipe_low(n, rng, seed):
    beat = int(0.55 * SR)
    sig = np.zeros(n)
    i = 0
    while i < n:
        seg = min(beat, n - i)
        sig[i:i + seg] += tone(196, seg) * env(seg, 0.005, 1.0, 2.5) * 0.8
        i += beat
    warn = tone(311.13, n) * env(n, 0.02, 1.0, 2.0) * 0.35
    return sig + warn


def recipe_mechanical(n, rng, seed):
    t = np.arange(n) / SR
    f = 90 + 160 * (t / (n / SR))
    saw = 2 * ((np.cumsum(f) / SR) % 1) - 1
    band = noise_band(n, 400, 2400, rng) * 0.4
    wob = 0.6 + 0.4 * np.sin(2 * np.pi * 11 * t)
    return (saw * 0.5 + band) * wob * env(n, 0.05, 1.0, 1.6)


def recipe_minimal(n, rng, seed):
    sig = np.zeros(n)
    tick = noise_band(int(0.02 * SR), 2000, 9000, rng) * env(int(0.02 * SR), 0.0005, 1.0, 3.0) * 0.7
    sig[:len(tick)] += tick
    blip = tone(1046.5, n - int(0.06 * SR)) * env(n - int(0.06 * SR), 0.002, 1.0, 3.0) * 0.5
    sig[int(0.06 * SR):] += blip
    return sig


RECIPES = {
    "impact": recipe_impact, "lightning": recipe_lightning, "plasma": recipe_plasma,
    "startup": recipe_startup, "magic": recipe_magic, "pulse": recipe_pulse,
    "chime_up": recipe_chime_up, "chime_down": recipe_chime_down,
    "full": recipe_full, "low": recipe_low, "mechanical": recipe_mechanical,
    "minimal": recipe_minimal,
}


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--recipe", required=True, choices=RECIPES)
    p.add_argument("--seed", type=int, default=1)
    p.add_argument("--duration", type=float, default=1.5)
    p.add_argument("--out", required=True)
    a = p.parse_args()
    n = int(a.duration * SR)
    rng = np.random.default_rng(a.seed)
    sig = RECIPES[a.recipe](n, rng, a.seed)
    save(a.out, sig)


if __name__ == "__main__":
    main()
