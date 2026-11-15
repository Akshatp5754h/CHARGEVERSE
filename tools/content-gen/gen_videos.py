#!/usr/bin/env python3
"""CHARGERVERSE original content generator — procedural MP4 loops.

Every clip is 100% original, generated mathematically (no source media), so
the bundled/downloadable catalogue is rights-clean by construction.

Styles (all seamless loops, period = duration):
  plasma      domain-warped colour field
  particles   radial particle storm with additive glow
  grid        perspective neon wireframe scroll
  liquid      rotating conic gradient wash
  rings       concentric energy cores with pulse
  lightning   jagged bolts with additive glow

Usage:
  python gen_videos.py --style plasma --palette electric --seed 7 \
      --duration 10 --out out/plasma_electric_7.mp4 [--fps 30] [--hw 720 1280]

Requires: numpy, Pillow, ffmpeg on PATH.
"""
import argparse
import math
import os
import subprocess
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

PALETTES = {
    "electric": [(0, 212, 255), (0, 128, 255), (140, 82, 255), (10, 10, 24)],
    "violet":   [(124, 92, 255), (225, 78, 255), (60, 40, 160), (8, 6, 20)],
    "magma":    [(255, 74, 106), (255, 183, 74), (120, 20, 60), (12, 4, 8)],
    "emerald":  [(0, 255, 178), (74, 255, 158), (0, 120, 140), (4, 14, 12)],
    "ice":      [(120, 220, 255), (200, 240, 255), (40, 90, 200), (6, 10, 22)],
    "gold":     [(255, 200, 80), (255, 240, 180), (140, 90, 20), (10, 8, 4)],
}


def palette(name):
    return [np.array(c, dtype=np.float64) for c in PALETTES[name]]


def smooth_noise(shape, freq, rng):
    """Tileable value noise via low-res random grid + bicubic upscale."""
    h, w = shape
    gh, gw = max(2, int(freq)), max(2, int(freq * 1.5))
    grid = rng.random((gh, gw))
    img = Image.fromarray((grid * 255).astype(np.uint8)).resize(
        (w, h), Image.BICUBIC)
    return np.asarray(img, dtype=np.float64) / 255.0


def warp(u, v, t, strength, rng_seed):
    """Domain warp with time-periodic offsets (seamless loop)."""
    ph1, ph2 = rng_seed % 628 / 100.0, (rng_seed * 7) % 628 / 100.0
    wx = np.sin(2 * np.pi * (v + ph1) + 2 * np.pi * t) * strength
    wy = np.cos(2 * np.pi * (u + ph2) + 2 * np.pi * t) * strength
    return np.clip(u + wx, 0, 1), np.clip(v + wy, 0, 1)


def colourize(field, pal, mix):
    """Map normalised field(s) to RGB via palette interpolation."""
    a, b, c, base = pal
    f1 = np.clip(field, 0, 1)[..., None]
    f2 = np.clip(mix, 0, 1)[..., None]
    col = base[None, None, :] \
        + (a - base)[None, None, :] * f1 \
        + (b - base)[None, None, :] * f2 * f1
    peak = np.clip((field - 0.75) * 4, 0, 1)[..., None]
    col = col + (c - col) * peak * 0.6
    return np.clip(col, 0, 255)


def frame_plasma(t, w, h, pal, rng_seed):
    n1 = smooth_noise((h // 4, w // 4), 3, np.random.default_rng(rng_seed))
    n1 = np.asarray(Image.fromarray((n1 * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC), dtype=np.float64) / 255
    n2 = smooth_noise((h // 4, w // 4), 5, np.random.default_rng(rng_seed + 1))
    n2 = np.asarray(Image.fromarray((n2 * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC), dtype=np.float64) / 255
    yy, xx = np.mgrid[0:h, 0:w]
    u, v = xx / w, yy / h
    u, v = warp(u, v, t, 0.08, rng_seed)
    idx_u = (u * (w - 1)).astype(int)
    idx_v = (v * (h - 1)).astype(int)
    f1 = n1[idx_v, idx_u]
    f2 = n2[idx_v, idx_u]
    blend = 0.5 + 0.5 * np.sin(2 * np.pi * t)
    return colourize(f1 * 0.7 + f2 * 0.5, pal, f2 * blend)


def frame_rings(t, w, h, pal, rng_seed):
    a, b, c, base = pal
    yy, xx = np.mgrid[0:h, 0:w]
    cx, cy = w / 2, h * 0.42
    r = np.sqrt((xx - cx) ** 2 + ((yy - cy) * 1.18) ** 2) / (min(w, h) * 0.75)
    out = np.zeros((h, w, 3), dtype=np.float64) + base
    for k in range(5):
        ph = (t + k / 5) % 1.0
        rad = 0.15 + ph * 0.85
        band = np.exp(-((r - rad) ** 2) / (2 * (0.035 + 0.02 * ph) ** 2))
        fade = (1 - ph) ** 1.6
        col = (a if k % 2 == 0 else b) * fade
        out += col[None, None, :] * band[..., None]
    core = np.exp(-(r ** 2) / (2 * 0.10 ** 2))
    out += c[None, None, :] * core[..., None] * (0.55 + 0.45 * math.sin(2 * np.pi * t))
    return np.clip(out, 0, 255)


def frame_liquid(t, w, h, pal, rng_seed):
    a, b, c, base = pal
    yy, xx = np.mgrid[0:h, 0:w]
    u, v = xx / w - 0.5, yy / h - 0.5
    ang = np.arctan2(v, u) + 2 * np.pi * t * 0.5
    rad = np.sqrt(u ** 2 + (v * 1.7) ** 2)
    f1 = (ang / (2 * np.pi)) % 1.0
    f2 = np.clip(rad * 1.4 + 0.2 * math.sin(2 * np.pi * t), 0, 1)
    col = base + (a - base) * (1 - f2)[..., None] + (b - base) * f2[..., None] * 0.8
    streak = np.exp(-((np.abs(((f1 * 6) % 1.0) - 0.5)) ** 2) / 0.004) * np.exp(-f2 * 2.2)
    col += c[None, None, :] * streak[..., None] * 0.8
    return np.clip(col, 0, 255)


def frame_grid(t, w, h, pal, rng_seed):
    a, b, _, base = pal
    img = Image.new("RGB", (w, h), tuple(int(x) for x in base))
    dr = ImageDraw.Draw(img, "RGBA")
    horizon = h * 0.36
    ph = t % 1.0
    for i in range(26):
        k = i + ph
        z = 1.0 / (1.0 + k * 0.55)
        y = horizon + (h - horizon) * z
        alpha = int(180 * z)
        dr.line([(0, y), (w, y)], fill=tuple(a) + (alpha,), width=2)
    for i in range(-14, 15):
        x0 = w / 2 + i * w * 0.09
        x1 = w / 2 + i * w * 0.55
        dr.line([(w / 2 + i * w * 0.012, horizon), (x1, h + 40)],
                fill=tuple(b) + (150,), width=2)
    glow = Image.new("RGB", (w, h), tuple(int(x) for x in a))
    img = Image.blend(img, glow, 0.10 + 0.06 * math.sin(2 * np.pi * t))
    sun = np.zeros((h, w, 3), dtype=np.float64)
    yy, xx = np.mgrid[0:h, 0:w]
    r = np.sqrt((xx - w / 2) ** 2 + (yy - horizon) ** 2) / (min(w, h) * 0.5)
    sun += np.array(a)[None, None, :] * np.exp(-(r ** 2) / 0.02)[..., None] * (0.5 + 0.3 * math.sin(2 * np.pi * t))
    return np.clip(np.asarray(img, dtype=np.float64) + sun, 0, 255)


def frame_particles(t, w, h, pal, rng_seed):
    a, b, c, base = pal
    rng = np.random.default_rng(rng_seed)
    n = 140
    ang = rng.random(n) * 2 * np.pi
    speed = 0.25 + rng.random(n) * 0.75
    size = rng.random(n) * 2.4 + 0.8
    hue = rng.random(n)
    yy, xx = np.mgrid[0:h, 0:w]
    img = np.zeros((h, w, 3), dtype=np.float64)
    cx, cy = w / 2, h * 0.44
    maxr = min(w, h) * 0.75
    for i in range(n):
        rr = ((speed[i] * t + speed[i]) % 1.0) * maxr
        x = cx + math.cos(ang[i] + 0.6 * t) * rr * 1.35
        y = cy + math.sin(ang[i] + 0.6 * t) * rr
        col = (a if hue[i] < 0.45 else b if hue[i] < 0.85 else c) * (1 - rr / maxr) ** 1.4
        r2 = size[i]
        m = np.exp(-(((xx - x) ** 2 + (yy - y) ** 2) / (2 * r2 ** 2)))
        img += col[None, None, :] * m[..., None]
    vign = np.exp(-(((xx - cx) / w) ** 2 + ((yy - cy) / h) ** 2) * 2.2)
    return np.clip(img + np.array(base)[None, None, :] * (1 - vign[..., None]) * 0.9, 0, 255)


def _bolt(draw, x, y, angle, length, depth, width, col, rng):
    if depth == 0 or length < 6:
        return
    steps = max(2, int(length / 14))
    pts = [(x, y)]
    px, py = x, y
    for _ in range(steps):
        ang = angle + rng.uniform(-0.5, 0.5)
        seg = length / steps * rng.uniform(0.7, 1.3)
        px += math.cos(ang) * seg
        py += math.sin(ang) * seg
        pts.append((px, py))
    draw.line(pts, fill=tuple(int(c) for c in col) + (235,), width=max(1, width))
    if rng.random() < 0.5:
        bx, by = pts[len(pts) // 2]
        _bolt(draw, bx, by, angle + rng.choice([-1, 1]) * 0.9, length * 0.45, depth - 1, max(1, width - 1), col, rng)
    _bolt(draw, px, py, angle + rng.uniform(-0.25, 0.25), length * 0.92, depth - 1, width, col, rng)


def frame_lightning(t, w, h, pal, rng_seed):
    a, b, base = pal
    yy, xx = np.mgrid[0:h, 0:w]
    amb = np.array(base)[None, None, :] + np.array(a)[None, None, :] * 0.06
    flicker = 0.5 + 0.5 * math.sin(2 * np.pi * t * 3) ** 4
    amb += np.array(a)[None, None, :] * 0.10 * flicker
    img = Image.fromarray(np.clip(amb, 0, 255).astype(np.uint8))
    dr = ImageDraw.Draw(img, "RGBA")
    rng = np.random.default_rng(int(t * 30) * 977 + rng_seed)
    for k in range(2):
        x = rng.uniform(w * 0.2, w * 0.8)
        col = a if k == 0 else b
        _bolt(dr, x, 0, math.pi / 2, h * 0.95, 4, 3, col, rng)
    arr = np.asarray(img, dtype=np.float64)
    glow = np.asarray(img.filter(ImageFilter.GaussianBlur(6)), dtype=np.float64)
    return np.clip(arr * 0.65 + glow * 0.6, 0, 255)


STYLES = {
    "plasma": frame_plasma,
    "rings": frame_rings,
    "liquid": frame_liquid,
    "grid": frame_grid,
    "particles": frame_particles,
    "lightning": frame_lightning,
}


def render(style, pal_name, seed, duration, fps, w, h, out):
    fn = STYLES[style]
    pal = palette(pal_name)
    if not os.path.exists(out):
        os.makedirs(os.path.dirname(out) or ".", exist_ok=True)
    n_frames = int(duration * fps)
    proc = subprocess.Popen(
        ["ffmpeg", "-y", "-f", "rawvideo", "-pix_fmt", "rgb24",
         "-s", f"{w}x{h}", "-r", str(fps), "-i", "-",
         "-an", "-vcodec", "libx264", "-pix_fmt", "yuv420p",
         "-crf", "26", "-preset", "medium", "-movflags", "+faststart", out],
        stdin=subprocess.PIPE, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    for i in range(n_frames):
        t = i / n_frames  # 0..1 → all periodic terms loop seamlessly
        frame = fn(t, w, h, pal, seed)
        proc.stdin.write(np.clip(frame, 0, 255).astype(np.uint8).tobytes())
    proc.stdin.close()
    proc.wait()
    # Poster: mid-loop frame as webp
    mid = fn(0.5, w, h, pal, seed)
    poster = Image.fromarray(np.clip(mid, 0, 255).astype(np.uint8))
    poster_path = out.rsplit(".", 1)[0] + "_poster.webp"
    poster.resize((360, int(360 * h / w))).save(poster_path, "WEBP", quality=78)
    return out, poster_path


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--style", required=True, choices=STYLES)
    p.add_argument("--palette", default="electric", choices=PALETTES)
    p.add_argument("--seed", type=int, default=1)
    p.add_argument("--duration", type=float, default=10.0)
    p.add_argument("--fps", type=int, default=30)
    p.add_argument("--hw", nargs=2, type=int, default=[720, 1280])
    p.add_argument("--out", required=True)
    a = p.parse_args()
    w, h = a.hw[0], a.hw[1]
    mp4, poster = render(a.style, a.palette, a.seed, a.duration, a.fps, w, h, a.out)
    print(f"OK {mp4} + {poster}")


if __name__ == "__main__":
    main()
