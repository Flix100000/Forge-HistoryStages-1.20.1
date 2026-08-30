# Contributing to History Stages

Thanks for taking the time to help out! Bug fixes, features, translations, documentation and testing are all
welcome — this guide explains how to get your contribution merged with as little back and forth as possible.

## Before you start

For anything beyond a small fix, **open a
[Contribution Offer issue](https://github.com/Flix100000/History-Stages/issues/new?template=contribution_offer.yml)
first**. It takes two minutes and makes sure nobody works on something that is already in progress, already
rejected, or planned differently.

Small and obvious changes — a typo, a one-line bug, a missing translation key — can go straight to a pull request.

Useful places to look around first:

- [Issue tracker](https://github.com/Flix100000/History-Stages/issues) — open bugs and feature requests
- [Wiki](https://github.com/Flix100000/History-Stages/wiki) — how the mod is configured and used
- [Discord](https://discord.gg/BeZzxyZ9c4) — questions, ideas, and quick feedback before you build something

## Ways to contribute

- **Code** — bug fixes, new features, performance work, compatibility with other mods.
- **Translations** — new languages are very welcome. See [Translations](#translations).
- **Documentation** — wiki pages, README improvements, clearer code comments.
- **Testing / QA** — reproducing reported bugs, testing pre-releases, writing detailed bug reports.
- **Art** — the visual side of History Stages (textures, models, icons, GUI art) is done by **PixlStudios**, the
  project's artist, and is coordinated with them. Please don't open an art pull request unprompted — it will
  most likely conflict with work that is already planned. If you have an idea for the mod's look, open a
  Contribution Offer issue and we'll discuss it together with the artist.

## Branches and versions

History Stages keeps one branch per Minecraft version and loader. There is no shared `main` branch — each branch
is self-contained.

| Branch            | Minecraft | Loader             | JDK | Status                       |
| ----------------- | --------- | ------------------ | --- | ---------------------------- |
| `neoforge-1.21.X` | 1.21.1    | NeoForge 21.1.x    | 21  | Actively maintained, leading |
| `fabric-1.21.X`   | 1.21.1    | Fabric Loader 0.18 | 21  | Actively maintained          |
| `forge-1.20.X`    | 1.20.1    | Forge 47.4.x       | 17  | Actively maintained          |
| `forge-1.19.X`    | 1.19.2    | Forge 43.5.0       | 17  | Legacy — no further updates  |

**Open your pull request against the branch of the version you are contributing to.** If you fixed a bug on
Forge 1.20, target `forge-1.20.X` — you don't need to port it anywhere else.

Ports to the other branches are very welcome but never required. Since the branches have drifted apart, a port
is its own pull request against its own branch, not extra commits on the original one.

Branches ending in `-WIP` and anything under `experiment/` are scratch branches. Don't branch off them and don't
target them with a pull request.

Create your working branch from the target branch and give it a descriptive name, for example
`fix/stage-json-load-crash` or `feat/loot-table-filter`.

## Development setup

You need Git, a JDK matching the branch (see the table above — [Temurin](https://adoptium.net/) works well), and
an IDE. IntelliJ IDEA is recommended; the repository also carries Eclipse and VS Code settings.

```bash
git clone https://github.com/Flix100000/History-Stages.git
cd History-Stages
git checkout forge-1.20.X   # or the branch you're targeting
./gradlew build
```

Use the Gradle wrapper (`./gradlew`, `gradlew.bat` on Windows) instead of a locally installed Gradle so everyone
builds with the same version.

Common tasks:

| Task                | What it does                                             |
| ------------------- | -------------------------------------------------------- |
| `./gradlew build`   | Compiles and produces the mod jar in `build/libs/`        |
| `./gradlew runClient` | Launches a development client                          |
| `./gradlew runServer` | Launches a dedicated development server                |
| `./gradlew runData` | Runs the data generators                                   |

`build/` and `run/` are generated directories and are git-ignored — never commit them, and never commit IDE
run configurations or local settings.

Every push and pull request is built by GitHub Actions. A pull request whose build fails won't be merged, so
make sure `./gradlew build` passes locally before you open one.

## Code style

- **Match the surrounding code.** Naming, formatting and structure should look like the file you are editing.
  Don't reformat unrelated code — it buries your actual change in the diff.
- **Write code, comments and Javadoc in English**, regardless of what language we speak in issues or on Discord.
- **Never hardcode user-facing text.** Labels, tooltips, buttons, chat messages, error texts and command
  feedback all go through translation keys. Reuse an existing key from
  `src/main/resources/assets/historystages/lang/en_us.json` where one fits, otherwise add a new one.
  **Adding your key to `en_us.json` is mandatory** — English is the fallback everyone sees. Adding the same key
  to other language files is optional and only makes sense for languages you actually speak.
- **Keep sides separate.** Client-only code (`Minecraft.getInstance()`, client config reads, rendering, screens)
  must never run on a dedicated server, and server-only logic must not be assumed on the client. Crashes from
  this are the single most common cause of rejected pull requests.
- **Guard loader-specific code** properly instead of scattering loader checks through shared logic.
- **Don't skip the build or hooks** to get something merged — no `--no-verify`, no disabled checks.

## Translations

New languages are one of the most useful contributions, and they don't require any Java knowledge.

1. Copy `src/main/resources/assets/historystages/lang/en_us.json` to a new file named after the Minecraft locale
   code, for example `fr_fr.json` or `pt_br.json`.
2. Translate the **values** only. Never translate or rename the keys.
3. Keep placeholders such as `%s`, `%1$s` and `%d` intact and in an order that still makes sense in your
   language — dropping one causes a crash at runtime.
4. Keep the key order of `en_us.json` so future diffs stay readable.
5. Leave keys you are unsure about untranslated rather than guessing — English is the fallback.

The maintainer only speaks German and English and can therefore only verify `de_de.json` and `en_us.json`. Every
other language file lives entirely on its contributors. If you add a language, it helps enormously if you keep
an eye on it and send an update when new keys show up in a release.

## Commit messages

- Written in English, in the imperative mood: "add stage filter", not "added" or "adds".
- Prefixed with the kind of change and, where useful, the area:
  `feat(editor): …`, `fix(loot): …`, `refactor(network): …`, `docs: …`, `chore(build): …`
- One logical change per commit. Cleanups, reformatting and unrelated fixes belong in their own commits.

## Pull requests

- Fill in the pull request template. The Testing, Breaking Changes and License sections are not optional — a
  pull request that says "tested, works" without saying how gets sent back.
- `./gradlew build` passes locally, and the CI build on the pull request is green.
- One topic per pull request. A bug fix and a new feature in the same pull request are hard to review and even
  harder to revert.
- Add screenshots or a short clip for anything that changes the GUI. Before/after if you can.
- State what you did **not** test. Honest gaps are far more useful than optimistic checkboxes.
- Expect review comments — they're about the code, not about you. Push follow-up commits to the same branch;
  the pull request updates automatically.

## License and contributor terms

History Stages is **All Rights Reserved** (see [LICENSE.txt](LICENSE.txt)). The source is public for
transparency, learning, bug reports and contributions — it is not open source under the OSI definition.

In short:

- Forking to prepare a pull request: **allowed**
- Publishing a fork, a modified build, or a re-upload as a standalone mod: **not allowed**
- Using the official jar in modpacks, videos and on servers: **allowed**
- Porting the mod to a Minecraft version or loader this repository doesn't cover: **allowed under
  conditions** — see below

### Ports to versions and loaders outside this repository

Section 3 of the license lets anyone port History Stages to a different Minecraft version or a
different mod loader without asking for permission first. This is a different thing from the branch
table further up, which is about the versions maintained here.

The conditions, in short: the port stays a technical adaptation and adds no gameplay of its own; you
contact the maintainer and hand over access to the port's source before or when you release; the
source stays publicly available; and every project page credits History Stages as the original and
links back to this repository. Your port ships the fill-in-the-blanks license template from Appendix
A next to an unmodified copy of the original license, and its license field on CurseForge and
Modrinth points at that file.

Read Section 3 and Appendix A of [LICENSE.txt](LICENSE.txt) before you start — that text is what
counts. The [Porting History Stages](https://github.com/Flix100000/History-Stages/wiki/Porting-History-Stages)
wiki page walks through the same conditions in plainer terms. Ports that meet them can be listed as
verified on the wiki and on the official CurseForge, Modrinth and GitHub pages.

By submitting a contribution you agree that it is licensed to the project owner (Flix100000) under the project's
license terms and may be relicensed by the project owner as needed. Read [LICENSE.txt](LICENSE.txt) for the full
terms before you start working.

## Credit

Every merged contribution gets an entry in [CONTRIBUTORS.md](CONTRIBUTORS.md) — code, translations, art and
documentation alike. If you'd like to be credited under a different name or handle, just say so in the pull
request.
