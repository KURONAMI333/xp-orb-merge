# XP Orb Merge

> Experience orbs lying next to each other join into one, and you collect every orb you touch at once.

[![License: All Rights Reserved](https://img.shields.io/badge/License-All_Rights_Reserved-red.svg)](LICENSE)
Homepage (intended CurseForge page, not yet published): https://www.curseforge.com/minecraft/mc-mods/xp-orb-merge
Bugs and questions: comment on the CurseForge page, or DM @kuronami333 on X (https://x.com/kuronami333).

---

## Supported Loaders / Versions

| Minecraft | NeoForge | Forge | Fabric |
|---|:---:|:---:|:---:|
| 1.21.1 | ✅ | — | ✅ |
| 1.20.1 | — | ✅ | ✅ |

No dependencies (Fabric API not needed). Server side is enough; it is harmless on clients.

## What it does

- XP from a kill, a furnace, breeding or a bottle drops as a single orb holding the whole amount, instead of a spray of small ones. Repeated drops at the same spot (mob farms) pile into that one orb.
- Orbs of any value that touch or nearly touch merge once a second (vanilla only merges orbs of the same value). Orbs never merge through a solid block.
- Every orb within your reach is picked up in the same tick (vanilla takes one at a time).
- The amount of XP never changes, and Mending repairs exactly as much as before. Very large totals are kept in at most two orbs so nothing is cut off when the world saves.
- Only plain experience orbs are merged; orb-like entities from other mods are left alone.
- Fewer orb entities means less work for the server and less clutter on screen. Nothing to configure.

## Installation

1. Install NeoForge (1.21.1), Forge (1.20.1) or Fabric Loader
2. Drop the jar for your loader and Minecraft version into `mods/`

## License

[All Rights Reserved](LICENSE) — modpack inclusion welcome, no credit required.

## Credits

- Author: KURONAMI
