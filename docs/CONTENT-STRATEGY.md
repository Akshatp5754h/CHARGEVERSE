# CHARGERVERSE v2 — Content Strategy (S7/S8 foundation)

## Ground rules (directive §7/§8)
- No franchise/copyrighted media ever bundled. Starter catalogue = 100% original,
  self-generated assets; every asset gets a licence row in the manifest and
  `delivery/ASSET-LICENCES.md`.
- Big video files never enter git history. They are hosted as versioned release
  assets / a static pack bucket; the APK stays small and works fully offline
  without any pack.

## Realism check (honest numbers, no fake catalogue)
- Target "100 assets/category" is the *infrastructure* design ceiling, NOT a
  v2 claim. v2 ships a verified starter pack and the machinery to grow it.
- Starter pack v2: **24 distinct original videos** (6 categories × 4) +
  **24 original sounds** (procedural) — every one unique, no duplicates.

## Asset pipeline (ranked, all local/offline, zero paid APIs)
1. **Procedural MP4 synthesis** (Python + numpy + ffmpeg, PC-side): abstract
   energy/AMOLED visuals — plasma flows, particle storms, neon wireframes,
   liquid gradients, waveform loops. 720×1280@30fps, 8–15 s loops, H.264
   CRF 26 (~1.5–4 MB each). Fully original, deterministic scripts in
   `tools/content-gen/`.
2. **ComfyUI txt2img → Ken Burns pan/zoom** (local ComfyUI at 127.0.0.1:8188):
   original anime/fantasy/sci-fi character art (generic, non-franchise
   prompts), converted to 10 s cinematic pans. Output credited
   "AI-assisted original".
3. **Lottie JSON originals** (already supported by the app since D8):
   tiny vector animations (energy rings, boot sequences) — generate
   programmatically; trivially rights-cleared.

## Manifest architecture (S7)
`pack.json` (versioned, per pack, hosted next to the videos):
```json
{
  "schemaVersion": 1,
  "packId": "starter.anime.v1",
  "packVersion": 1,
  "category": "anime",
  "title": "Anime Origins — Starter",
  "licence": "CC0-1.0 | ChargeverseOriginal",
  "sha256": "<manifest checksum>",
  "items": [{
    "id": "cv-anime-0001",
    "title": "Kinetic Surge",
    "description": "...",
    "tags": ["energy","blue","fast"],
    "media": {"url": ".../kinetic_surge.mp4", "sha256": "...",
              "sizeBytes": 2400000, "durationMs": 12000,
              "width": 720, "height": 1280, "fps": 30,
              "format": "video/mp4"},
    "thumb": {"url": ".../kinetic_surge.webp", "sha256": "...", "sizeBytes": 40000},
    "sound": {"url": ".../kinetic_surge.m4a", "sha256": "..."} | null,
    "attribution": "Chargeverse Original",
    "featured": true
  }]
}
```
In-app: `ContentPackRepository` — parse, verify manifest sha256, verify each
asset sha256 after download (WorkManager worker, resumable via ranges,
pause/cancel queue in Room `download_state` table), store under
`filesDir/packs/<packId>/<id>`. Offline-first: gallery merges Room media +
pack media; delete removes files only.

## Hosting (no paid APIs)
- GitHub Releases on Akshatp5754h/CHARGERVERSE: one release tag per pack
  (`pack-starter-anime-v1`), assets = pack.json + zips per category.
- Throttling awareness: pack files are downloads (not streams) — users
  download once, play offline. Posters/thumbnails bundled in APK or packed.
- Broken-link recovery: manifest lists mirror URLs (primary + same file in
  the bundle zip); app falls back to zip extraction if direct 404.

## Sound pack (S5/S8)
Same manifest schema, `category`: sound packs; procedural synthesis
(ffmpeg/sox-style recipes) for impacts, lightning, plasma, startup — all
original WAV/OGG, ≤200 KB each.
