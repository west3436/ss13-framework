# SS13 Framework

Shared SS13 parts for Minecraft (NeoForge 1.21.1) mods: crafting components and stock parts, TGUI windows, a device base, tools, power, areas, gravity and the Normal/Hardcore switch. Used by [ZAS³](https://github.com/west3436/zas-cubed), [Expedition](https://github.com/west3436/expedition) and [NT Engineering](https://github.com/west3436/nt-engineering).

> **Status: Phase 0.** The build makes both jars, but they hold nothing yet: Phase A moves the shared parts in. The plan lives in [docs/design-proposal.md](docs/design-proposal.md).

## Jars

| Jar | Coordinates | What it is |
| --- | --- | --- |
| `ss13_framework` mod | `io.github.west3436:ss13-framework` | The shared items, windows and devices. Nests the API. |
| API | `io.github.west3436:ss13-framework-api` | The contracts, in `io.github.west3436.ss13.api`. A `GAMELIBRARY` that depends only on Minecraft and NeoForge. |

Both are on Maven Central, with `-SNAPSHOT` builds from `main` in the [Central snapshots repository](https://central.sonatype.com/repository/maven-snapshots/). How a mod nests them is [#3](https://github.com/west3436/ss13-framework/issues/3).

## Building

`./gradlew build` builds and checks both jars; `./gradlew :ss13-framework:runGameTestServer` runs the GameTests. [CONTRIBUTING.md](CONTRIBUTING.md) has the rest, including the clean-room rule every contribution follows.

## Goals

- **One owner for shared items.** Machine frames, circuit boards, stock parts and tools are registered once, not by each mod.
- **One window kit.** Every device window in all three mods uses the TGUI look.
- **Shared contracts.** Power providers, areas, gravity, machine construction and presets, so the mods work together without depending on each other.
- **Nothing to install.** Each mod nests the framework; players never add it themselves.

## License

- **Code:** MIT (see [LICENSE](LICENSE)). The project is clean-room: vgstation13 (GPLv3/AGPLv3) is a design reference only, and contributions must follow the same rule.
- **Art assets:** CC BY-SA 4.0. Some assets derive from vgstation13 sprites under CC BY-SA 3.0; sources and attribution go in CREDITS. Nothing from vgstation13's `goon/` folder (CC BY-NC-SA) is used.
