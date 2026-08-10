<!--
Thanks for opening a PR! Fill in what's relevant and delete the sections you don't need.
Sections marked (required) should stay.

New here? ../CONTRIBUTING.md explains which branch to target, how to build the mod,
and what we look for in a review.
-->

## Related Issue
<!-- Use "Closes #123" to auto-close the issue on merge, or "Refs #123" if it's only related. -->
Closes #

## Summary (required)
<!-- One or two sentences: what does this PR do, at a high level? -->


## What changed
<!-- Bullet list of the actual code/asset/config changes. Mention new/renamed/removed files or classes. -->
-
-

## Why
<!-- The motivation. What was broken, missing, or worth improving? Link to discussion if relevant. -->


## Type of Change (required)
- [ ] Bug fix
- [ ] New feature
- [ ] Compatibility fix (other mod)
- [ ] Refactoring / cleanup
- [ ] Performance improvement
- [ ] Configuration / stage JSON change
- [ ] Translation / localization
- [ ] Assets (textures, models, sounds)
- [ ] Documentation (wiki, README, code comments)
- [ ] Build / CI / Gradle
- [ ] Dependency update
- [ ] Test / dev tooling
- [ ] Other (describe in Summary)

## Breaking Changes (required)
<!--
Does this PR change anything users will notice on update?
Examples: stage JSON schema change, config key renamed/removed, network packet change,
behavior change that existing setups rely on, dropped MC/NeoForge version.
If yes, describe what breaks and how users should migrate. If no, write "None".
-->


## Testing (required)
<!-- How did you actually test this? Be specific. -->

**Environment:**
- Minecraft version: <!-- e.g. 1.20.1, 1.21.1 -->
- Mod loader + version: <!-- e.g. Forge 47.4.18, NeoForge 21.1.222, Fabric 0.16.x -->
- Branch this PR targets: <!-- e.g. main, neoforge-1.21, fabric-1.21 -->
- Environment: <!-- Singleplayer / Dedicated Server + Client / both -->
- Other mods loaded during test: <!-- e.g. JEI, KubeJS, Curios, or "none" -->

**What I tested:**
-
-

**What I did not test (but probably should):**
-

## Compatibility Check
- [ ] Existing stage JSON files still load without errors
- [ ] Existing config keys still work (or migration is handled)
- [ ] No client-only code runs on the dedicated server side (e.g. no `Minecraft.getInstance()` / client config reads on server)
- [ ] No server-only code runs on the client side
- [ ] Loader-specific code is properly guarded (`@OnlyIn`, loader-specific source sets, or service abstraction)

## Checklist (required)
- [ ] `gradlew build` passes locally
- [ ] Changes follow the existing code style of the project
- [ ] Public APIs / commands / events are documented in code or readme where appropriate
- [ ] Version number / changelog updated (if this is release-bound)

## License Acknowledgement (required)
<!--
History Stages is licensed All Rights Reserved (see LICENSE.txt).
By submitting this pull request, you agree that your contribution
is licensed to the project owner (Flix100000) under the project's
license terms and may be relicensed by the project owner as needed.
-->
- [ ] I have read [LICENSE.txt](../LICENSE.txt) and agree that, by submitting this pull request, my contribution is licensed to the project owner under the project's All Rights Reserved license and may be relicensed by the project owner as needed.

## Screenshots / Videos
<!-- Required for UI changes. Before/after if you can. -->


## Notes for Reviewers
<!-- Anything tricky, intentional, or out of scope? Things you want a second pair of eyes on? -->
