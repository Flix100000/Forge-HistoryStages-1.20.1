# Security Policy

## Supported Versions

| Version                    | Supported |
| -------------------------- | --------- |
| 1.21 (NeoForge and Fabric) | Yes       |
| 1.20 (Forge)               | Yes       |
| 1.19 (Forge)               | No        |

## What counts as a security issue

Anything a player could abuse on a server they don't administrate, for example:

- Bypassing stage locks or unlocking stages without doing the research
- Crashing or hanging a server with a manipulated packet or stage file
- Reading or writing files outside the mod's own config and data directories

Ordinary bugs, crashes you can trigger in your own singleplayer world, and compatibility problems are **not**
security issues — please report those in the [issue tracker](https://github.com/Flix100000/History-Stages/issues)
as usual.

## Reporting

**Please don't open a public issue for these.** A working exploit description is usable on every server running
the mod until the fix has reached them, which takes a lot longer than the fix itself.

Report it privately instead, either way works:

- [Report a vulnerability](https://github.com/Flix100000/History-Stages/security/advisories/new) on GitHub
- A direct message to the maintainer on the [Discord server](https://discord.gg/BeZzxyZ9c4)

Helpful to include: the mod and Minecraft version, what an attacker can achieve, and how to reproduce it.

History Stages is maintained by one person in his free time, so there's no guaranteed response time — but
security reports get looked at first. Once a fix is released, feel free to write about the issue publicly.
