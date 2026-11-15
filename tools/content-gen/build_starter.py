#!/usr/bin/env python3
"""Build the CHARGERVERSE v2 starter content pack (all original).

Renders 24 videos (6 styles × 4 palettes/seeds) + 24 sounds (12 recipes × 2
seeds), writes posters, and assembles pack.json with sha256 checksums per the
docs/CONTENT-STRATEGY.md manifest schema.

Output: tools/content-gen/out/starter/  (gitignored; hosted as release assets)
Usage:  python build_starter.py [--fast]   (--fast = 6 s clips instead of 10)
"""
import argparse
import hashlib
import json
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "out", "starter")

VIDEOS = [
    # (style, palette, seed, title, description, tags)
    ("plasma", "electric", 11, "Kinetic Surge", "Flowing plasma field in electric blue.", ["energy", "blue", "flow"]),
    ("plasma", "violet", 23, "Violet Nebula", "Deep violet plasma clouds drifting.", ["energy", "purple", "ambient"]),
    ("plasma", "emerald", 37, "Bio Flow", "Emerald energy streams.", ["energy", "green", "nature"]),
    ("plasma", "magma", 41, "Core Meltdown", "Charged magma plasma churn.", ["energy", "red", "intense"]),
    ("particles", "electric", 5, "Particle Storm", "Radial particle storm, electric blue.", ["particles", "blue", "dynamic"]),
    ("particles", "violet", 17, "Stardust Drive", "Violet stardust racing outward.", ["particles", "purple", "space"]),
    ("particles", "ice", 29, "Cryo Burst", "Ice-blue particle explosion.", ["particles", "cyan", "cool"]),
    ("particles", "gold", 53, "Golden Orbit", "Warm golden particle orbit.", ["particles", "gold", "luxury"]),
    ("grid", "electric", 3, "Hyperlane", "Neon wireframe hyperlane scroll.", ["scifi", "grid", "retro"]),
    ("grid", "violet", 19, "Night Circuit", "Violet circuit horizon.", ["scifi", "grid", "night"]),
    ("grid", "emerald", 31, "Data Field", "Green data-grid horizon.", ["scifi", "grid", "data"]),
    ("grid", "gold", 47, "Sunset Run", "Golden grid sunset drive.", ["scifi", "sunset", "drive"]),
    ("rings", "electric", 7, "Energy Cores", "Concentric energy cores pulsing.", ["energy", "blue", "pulse"]),
    ("rings", "violet", 13, "Portal Wake", "Violet portal rings awakening.", ["magic", "purple", "portal"]),
    ("rings", "ice", 43, "Frost Ring", "Cold energy ring pulse.", ["energy", "cyan", "calm"]),
    ("rings", "magma", 59, "Ember Core", "Fiery core shockwaves.", ["energy", "red", "fire"]),
    ("liquid", "electric", 2, "Liquid Charge", "Rotating liquid gradient wash.", ["abstract", "blue", "smooth"]),
    ("liquid", "violet", 61, "Aurora Drift", "Aurora-like colour drift.", ["abstract", "purple", "aurora"]),
    ("liquid", "emerald", 67, "Mint Swirl", "Emerald liquid swirl.", ["abstract", "green", "smooth"]),
    ("liquid", "magma", 71, "Lava Lamp", "Molten colour rotation.", ["abstract", "red", "warm"]),
    ("lightning", "electric", 8, "Thunderstrike", "Electric bolts over dark sky.", ["lightning", "blue", "storm"]),
    ("lightning", "violet", 79, "Arc Storm", "Violet arc discharge.", ["lightning", "purple", "storm"]),
    ("lightning", "ice", 83, "Static Field", "Cold static discharges.", ["lightning", "cyan", "storm"]),
    ("lightning", "gold", 89, "Zeus Bolt", "Golden god-bolts.", ["lightning", "gold", "storm"]),
]

SOUNDS = [
    ("impact", 3, "Cinematic Hit", "impact", "Deep cinematic impact hit."),
    ("impact", 12, "Impact Heavy", "impact", "Heavier cinematic impact."),
    ("lightning", 5, "Lightning Crack", "lightning", "Sharp lightning crack."),
    ("lightning", 21, "Thunder Roll", "lightning", "Rolling thunder."),
    ("plasma", 7, "Plasma Hum", "plasma", "Detuned plasma hum."),
    ("plasma", 33, "Plasma Deep", "plasma", "Deep plasma drone."),
    ("startup", 2, "System Boot", "startup", "Futuristic system boot sweep."),
    ("startup", 44, "Ignition", "startup", "Engine-style ignition rise."),
    ("magic", 9, "Arcane Shimmer", "magic", "Bell-like arcane shimmer."),
    ("magic", 27, "Spell Cast", "magic", "Inharmonic spell cast."),
    ("pulse", 4, "Charge Pulse", "pulse", "Heartbeat-style charge pulse."),
    ("pulse", 55, "Deep Pulse", "pulse", "Slower deep pulse."),
    ("chime_up", 1, "Connect Rise", "connect", "Two-note rising connect chime."),
    ("chime_up", 18, "Bright Connect", "connect", "Brighter rising chime."),
    ("chime_down", 2, "Disconnect Fall", "disconnect", "Falling disconnect chime."),
    ("chime_down", 26, "Soft Unplug", "disconnect", "Soft falling unplug chime."),
    ("full", 6, "Full Charge", "full", "Major-triad full-charge swell."),
    ("full", 31, "Complete", "full", "Warm completion swell."),
    ("low", 8, "Low Warning", "low", "Minor warning pulse."),
    ("low", 39, "Critical", "low", "Urgent low-battery pulse."),
    ("mechanical", 10, "Servo Whirr", "mechanical", "Mechanical servo slide."),
    ("mechanical", 48, "Machinery", "mechanical", "Heavier machine cycle."),
    ("minimal", 11, "Soft Tick", "minimal", "Minimal tick + blip."),
    ("minimal", 57, "Clean Blip", "minimal", "Cleaner minimal blip."),
]


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def run(cmd):
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode != 0:
        print("FAIL", " ".join(cmd), "\n", r.stdout[-800:], r.stderr[-800:], file=sys.stderr)
        raise SystemExit(1)
    return r.stdout.strip()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--fast", action="store_true", help="6 s clips instead of 10 s")
    args = ap.parse_args()
    dur = 6.0 if args.fast else 10.0
    os.makedirs(OUT, exist_ok=True)
    items = []

    for style, pal, seed, title, desc, tags in VIDEOS:
        name = f"{style}_{pal}_{seed}"
        mp4 = os.path.join(OUT, "video", f"{name}.mp4")
        poster = os.path.join(OUT, "thumb", f"{name}.webp")
        if not (os.path.exists(mp4) and os.path.exists(poster)):
            run([sys.executable, os.path.join(HERE, "gen_videos.py"),
                 "--style", style, "--palette", pal, "--seed", str(seed),
                 "--duration", str(dur), "--out", mp4])
            # gen_videos writes the poster next to the mp4 as <name>_poster.webp;
            # move it into thumb/ under the manifest's expected name.
            src_poster = mp4.rsplit(".", 1)[0] + "_poster.webp"
            if os.path.exists(src_poster):
                os.makedirs(os.path.dirname(poster), exist_ok=True)
                os.replace(src_poster, poster)
        if not os.path.exists(poster):
            raise SystemExit(f"poster missing for {name}")
        size = os.path.getsize(mp4)
        items.append({
            "id": f"cv-{style}-{pal}-{seed}",
            "title": title,
            "description": desc,
            "category": style if style not in ("grid", "liquid") else ("scifi" if style == "grid" else "abstract"),
            "tags": tags,
            "media": {"file": f"video/{name}.mp4", "sha256": sha256(mp4), "sizeBytes": size,
                      "durationMs": int(dur * 1000), "width": 720, "height": 1280,
                      "fps": 30, "format": "video/mp4"},
            "thumb": {"file": f"thumb/{name}.webp", "sha256": sha256(poster), "sizeBytes": os.path.getsize(poster)},
            "sound": None,
            "attribution": "Chargeverse Original — procedural, CC0",
            "licence": "CC0-1.0",
            "featured": seed in (5, 7, 11, 13),
        })
        print(f"video ok: {title}")

    for recipe, seed, title, cat, desc in SOUNDS:
        name = f"{recipe}_{seed}"
        wav = os.path.join(OUT, "sound", f"{name}.wav")
        if not os.path.exists(wav):
            run([sys.executable, os.path.join(HERE, "gen_sounds.py"),
                 "--recipe", recipe, "--seed", str(seed),
                 "--duration", "1.8", "--out", wav])
        items.append({
            "id": f"cv-snd-{recipe}-{seed}",
            "title": title,
            "description": desc,
            "category": cat,
            "tags": [recipe],
            "media": {"file": f"sound/{name}.wav", "sha256": sha256(wav),
                      "sizeBytes": os.path.getsize(wav), "format": "audio/wav"},
            "thumb": None, "sound": None,
            "attribution": "Chargeverse Original — synthesised, CC0",
            "licence": "CC0-1.0",
            "featured": False,
        })
        print(f"sound ok: {title}")

    manifest = {
        "schemaVersion": 1,
        "packId": "starter.original.v1",
        "packVersion": 1,
        "title": "CHARGERVERSE Starter — Original Pack",
        "licence": "CC0-1.0",
        "items": items,
    }
    mpath = os.path.join(OUT, "pack.json")
    with open(mpath, "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=1)
    print(f"manifest: {mpath} items={len(items)}")


if __name__ == "__main__":
    main()
