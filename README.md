# SS13 Framework

Shared SS13 parts for Minecraft (NeoForge 1.21.1) mods: crafting components and stock parts, TGUI windows, a device base, tools, power, areas, gravity and the Normal/Hardcore switch. Used by [ZAS³](https://github.com/west3436/zas-cubed), [Expedition](https://github.com/west3436/expedition) and [NT Engineering](https://github.com/west3436/nt-engineering).

> **Status: Phase A.** The first shared contract is in: gravity at a position ([`Gravity`](ss13-framework-api/src/main/java/io/github/west3436/ss13/api/gravity/Gravity.java)), composed from providers such as planets and gravity generators. The rest of Phase A moves the shared parts in. The plan lives in [docs/design-proposal.md](docs/design-proposal.md).

## Jars

| Jar | Coordinates | What it is |
| --- | --- | --- |
| `ss13_framework` mod | `io.github.west3436:ss13-framework` | The shared items, windows and devices. Nests the API. |
| API | `io.github.west3436:ss13-framework-api` | The contracts, in `io.github.west3436.ss13.api`. A `GAMELIBRARY` that depends only on Minecraft and NeoForge. |

Both are on Maven Central, with `-SNAPSHOT` builds from `main` in the [Central snapshots repository](https://central.sonatype.com/repository/maven-snapshots/).

## Nesting the framework

A mod nests both jars by jar-in-jar, with [ModDevGradle](https://github.com/neoforged/ModDevGradle), so players never install the framework on their own:

```groovy
dependencies {
    // Compile against the mod, which brings the API with it.
    implementation 'io.github.west3436:ss13-framework:0.1.0'

    // Nest both. Start each range at the version you build against, and end it before the next
    // version that may break it: the next minor while the framework is 0.x, the next major from 1.0.
    jarJar('io.github.west3436:ss13-framework') {
        transitive = false
        version {
            strictly '[0.1.0,0.2.0)'
            prefer '0.1.0'
        }
    }
    jarJar('io.github.west3436:ss13-framework-api') {
        version {
            strictly '[0.1.0,0.2.0)'
            prefer '0.1.0'
        }
    }
}
```

When several mods nest the framework, NeoForge loads one copy of each jar. It takes the ranges every mod gives, keeps only the versions all of them allow, and loads the first copy it finds among those. It doesn't look for the newest. The newest copy wins only because each range starts at the version that mod nests, which leaves the older copies out. So:

- **Never start a range below the version you nest.** A lower floor lets NeoForge load an older copy than the one you built against, and your mod fails at runtime on whatever is missing.
- **While the framework is 0.x, mods released together must nest the same minor version.** One mod on `[0.1.0,0.2.0)` and another on `[0.2.0,0.3.0)` have no version in common, and the game doesn't start.

The [example consumer](example-consumer/build.gradle) nests the framework this way. CI runs it on a server beside a second consumer that nests a newer copy, and checks that one copy loads and that it's the newest.

## Building

`./gradlew build` builds and checks both jars; `./gradlew :ss13-framework:runGameTestServer` runs the GameTests, and `./gradlew :example-consumer:runOneCopyGameTestServer` the one-copy check. [CONTRIBUTING.md](CONTRIBUTING.md) has the rest, including the clean-room rule every contribution follows.

## Goals

- **One owner for shared items.** Machine frames, circuit boards, stock parts and tools are registered once, not by each mod.
- **One window kit.** Every device window in all three mods uses the TGUI look.
- **Shared contracts.** Power providers, areas, gravity, machine construction and presets, so the mods work together without depending on each other.
- **Nothing to install.** Each mod nests the framework; players never add it themselves.

## License

- **Code:** MIT (see [LICENSE](LICENSE)). The project is clean-room: vgstation13 (GPLv3/AGPLv3) is a design reference only, and contributions must follow the same rule.
- **Art assets:** CC BY-SA 4.0. Some assets derive from vgstation13 sprites under CC BY-SA 3.0; sources and attribution go in CREDITS. Nothing from vgstation13's `goon/` folder (CC BY-NC-SA) is used.
