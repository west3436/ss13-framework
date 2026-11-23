# Contributing to SS13 Framework

Thanks for helping. This guide covers the one rule that can't bend (clean-room
code), how to build and test, and how changes get merged. The framework
follows the same rules as [ZAS³](https://github.com/west3436/zas-cubed), since
much of its code moves in from there.

## The clean-room rule

SS13 Framework is MIT-licensed. vgstation13, which inspired it, is
GPLv3/AGPLv3. To keep the licenses apart, **no vgstation13 code enters this
repository, whether copied or translated.**

- **Allowed:** vg's *behaviour*, its algorithms described in your own words,
  and physical constants.
- **Not allowed:** pasting DM code, porting it line by line into Java, or
  keeping its structure, names and comments while changing the syntax. A
  translation is still a copy.
- **How to work:** read a description in prose, close the vg source, then
  write the code from the description. If none says enough, write one in
  prose first, in the issue.
- **The same goes for** issues, comments and docs: never quote vg code.
- **Code moved from ZAS³ or Expedition** is already clean-room and MIT, so it
  may move as it is.

Every PR confirms this with the first checkbox in the
[PR template](.github/pull_request_template.md). A reviewer who suspects a
copy asks for a rewrite from the description.

## Using AI

Using AI tools to write code, docs, tests or assets is fully acceptable. The
one condition: **if a PR includes a player-facing change, it must show how a
human tested that change in game.** Player-facing means anything a player
can see, hear or feel, such as items, windows, tools or messages.

In the PR template's "Human testing" section, say:

- what you tested, in a dev client, on a dedicated server, or both
- the steps you took and what happened
- evidence: screenshots, a short video or log excerpts

Passing GameTests and unit tests don't replace this for player-facing
changes, though they're still expected. AI-written code follows the same
rules as any other code, the clean-room rule included.

## Assets

Code is MIT; assets are CC BY-SA 4.0 International, under
[`LICENSE-ASSETS`](LICENSE-ASSETS). Assets can be drawn from scratch, derived
from vgstation13, or taken from anywhere else whose license allows it. Every
file under a project's `src/*/resources/assets/`, or under the `assets/` of a
built-in resource pack in `src/*/resources/resourcepacks/`, needs an entry in
[`CREDITS`](CREDITS). An entry is a block of `key: value` lines, starting with
the file's path from the repository root and its `origin`; blank lines
separate entries:

- **`origin: original`:** drawn from scratch. Nothing else is needed.
- **`origin: vgstation13`:** `source` (the `.dmi` path in the vg
  repository), `state` (the icon state) and `commit` (the vg commit hash). A
  sound's `source` is its path under vg's `sound/`, with no `state`, and
  `adapted` says how the file was changed. Use only vg's CC BY-SA 3.0 US
  assets, never the CC BY-NC-SA `goon/` folder.
- **`origin: other`:** `source` (a URL, or a description), `license` (an
  SPDX id: `CC0-1.0`, `CC-BY-3.0`, `CC-BY-4.0`, `CC-BY-SA-3.0` or
  `CC-BY-SA-4.0`, the licenses that can be adapted under CC BY-SA 4.0) and
  `attribution` (the author to credit; not needed for `CC0-1.0`). A font may
  instead keep its own license, for now only `Bitstream-Vera`, with the
  license text under `META-INF/licenses/`.

An asset that moves in from ZAS³ or Expedition brings its `CREDITS` entry
with it, with the path changed.

Leave out any asset whose origin or license is unclear. The full format is
at the top of `CREDITS`. Check it with `python scripts/check_credits.py`
(Python 3, no dependencies). It lists asset files with no entry, entries for
files that don't exist, malformed entries, `goon/` sources and licenses that
aren't allowed. CI runs it in "Build and test".

No NonCommercial or NoDerivatives asset ships in any jar.

## Building and testing

You need a JDK to run Gradle (17 or newer); the build fetches Java 21 for the
mod itself.

| Command | What it does |
|---|---|
| `./gradlew build` | Compiles both jars, runs the unit tests, and checks formatting, the API's packages and dependencies, its javadoc and its compatibility |
| `./gradlew :ss13-framework:runGameTestServer` | Boots a dedicated GameTest server with the mod and runs its GameTests |
| `./gradlew :ss13-framework:runClient` | Starts a dev client with the mod |
| `./gradlew :ss13-framework:runServer` | Starts a dev dedicated server with the mod |
| `./gradlew spotlessApply` | Formats the Java sources (palantir-java-format) |
| `./gradlew :ss13-framework-api:apiCompat` | Checks the API for binary-incompatible changes against the last release |
| `./gradlew :ss13-framework-api:javadoc` | Builds the API's javadoc and fails on a broken or missing comment: every public or protected member needs one, with its `@param`, `@return` and `@throws`. `build` runs it |
| `./gradlew publishToMavenLocal` | Puts both jars in your local Maven repository, to try them in ZAS³ or Expedition before a release |

### Projects

- **`ss13-framework-api`** is the `io.github.west3436:ss13-framework-api`
  jar: the contracts the mods share, in `io.github.west3436.ss13.api`. It
  loads as a `GAMELIBRARY`, not a mod, and may depend on nothing but
  Minecraft and NeoForge. `build` fails if it declares another dependency,
  holds a package outside `io.github.west3436.ss13.api`, or references
  another mod's classes.
- **`ss13-framework`** is the `ss13_framework` mod,
  `io.github.west3436:ss13-framework`, in `io.github.west3436.ss13`. It nests
  the API, so a mod that nests the mod gets both.

Neither depends on ZAS³, Expedition or NT Engineering. A feature that needs
gas reads `zas-api` from the mod that uses it, never from here.

### Versions

The mod stays 0.x until all three mods use it. The API has its own semver:
while it is 0.x a new minor version may break it, and from 1.0 only a new
major may. `apiCompat` compares the API with the last release named in
[`gradle.properties`](gradle.properties); after a release with a new API
version, set the three `api_baseline_` lines to it.

### Tests

- **API value types and checks** get JUnit 5 tests in `ss13-framework-api`.
  Minecraft's classes are on that classpath, but the game is never
  bootstrapped.
- **Anything in the world** gets a GameTest in the mod's `src/gametest`
  source set, which stays out of the mod jar.

## Pull requests

1. Branch from `main`. Keep a PR to one issue where you can.
2. Fill in the PR template: link the issue (`Closes #nn`) and tick the
   checklist. For a player-facing change made with AI, include the human
   testing evidence described in [Using AI](#using-ai).
3. CI must pass: "Build and test" (asset credits, build, unit tests,
   formatting, the API's javadoc with no comment missing, and GameTests) and
   "Docs links".
4. PRs are squash-merged, so the PR title becomes the commit subject. Write it
   in the imperative: "Add the TGUI window kit".

## Releases

Maintainers only.

1. Set `mod_version` and `api_version` in `gradle.properties` and merge that.
2. Push the tag `v<mod_version>` on that commit on `main`. The
   [Release](.github/workflows/release.yml) workflow builds both jars and
   drafts a GitHub release with them and their `SHA256SUMS.txt`. Read it, then
   publish it.
3. Run [Publish to Maven Central](.github/workflows/publish.yml) from the
   Actions tab with the tag. It publishes both jars, signed. A release on
   Central can never be changed or removed.
4. Set the `api_baseline_` lines in `gradle.properties` to the new release.

CI publishes `-SNAPSHOT` versions of both jars after every green build on
`main`. Publishing needs the repository secrets `CENTRAL_USERNAME` and
`CENTRAL_PASSWORD` (a Central Portal user token), and for releases
`SIGNING_KEY` and `SIGNING_PASSWORD` (an ASCII-armoured GPG key). Without
the first two, CI skips the snapshot.
