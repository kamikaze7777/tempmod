# bot-fleet-control   

A control system for a fleet of headless Baritone bots (HeadlessMc + hmc-specifics +
Baritone, running on Fabric 26.2) driven from your **real** Minecraft client, without
touching in-game chat — so it never collides with Meteor's Baritone addon or anything
else parsing chat client-side.

## Architecture

```
 Your real client (Meteor etc.)          Bot instances (HeadlessMc, no render)
 ┌─────────────────────────┐             ┌───────────────┐ ┌───────────────┐
 │  client-mod              │            │ HeadlessMc #1  │ │ HeadlessMc #2 │  ...
 │  registers /bc (client-  │   TCP      │ (stdin/stdout) │ │ (stdin/stdout)│
 │  side only command)      │──socket──▶ │                │ │               │
 │  never touches server    │  (local)   └───────▲────────┘ └───────▲───────┘
 │  chat                    │◀── log ──┐         │                  │
 └─────────────────────────┘   lines   │         │ stdin: msg/./cmd │
                                        │         │ stdout: log tail│
                                 ┌──────┴─────────┴──────────────────┐
                                 │              hub                   │
                                 │  - spawns/supervises HeadlessMc     │
                                 │    processes                        │
                                 │  - local TCP server for client-mod  │
                                 │  - routes commands to the right     │
                                 │    bot's stdin                      │
                                 │  - tails each bot's stdout, pushes  │
                                 │    filtered lines back to client    │
                                 └──────────────────────────────────────┘
```

**Why a local socket instead of in-game chat:** anything typed into server chat is
visible to every chat-parsing client-side mod (Meteor's Baritone addon included), and
to other players on the server. The client-mod registers `/bc` via Fabric API's
`ClientCommandManager`, which intercepts the command **before** it's ever sent to the
server — it never becomes a chat packet, never touches Meteor's parser, never shows up
to anyone else.

## Components

- **`client-mod/`** — Fabric client-side mod. Registers `/bc <bot> <command...>` and
  `/bc list`. Sends commands to the hub over a local TCP socket, renders bot log lines
  it receives back as HUD/chat-overlay text (client-side only, not sent to server).
- **`hub/`** — plain Java process. Owns the HeadlessMc subprocesses, exposes the local
  control socket, tails bot output.

## Prerequisites (per the versions confirmed as current for 26.2 at time of writing —
recheck before building if it's been a while)

- Minecraft 26.2, Fabric Loader 0.19.3, Fabric Loom 1.17, **Gradle 9.7.1**
- Fabric API 0.158.0+26.2
- **Java 26 for both subprojects.** Minecraft 26.2 itself requires at least
  Java 25 to run (confirmed the hard way — Loom needs to launch the game to
  set up its dev environment, fails outright below that). Running 26 rather
  than pinning to the 25 floor per your own toolchain preference — this is
  one version past what's been directly confirmed to work, so if CI throws
  a version-pin error again, this is the first place to look.
- HeadlessMc's own Java requirement for running 26.2 varies — check their docs;
  likely also 25+ given the same underlying game requirement, but verify rather
  than assume.
- HeadlessMc (latest release) + `hmc-specifics` (26.2 build) + Baritone (26.2-compatible
  build) + optionally `hmc.assets.dummy` for the bots
- Krypton 0.3.1 on the bot instances (native 26.2 support) for the networking-stack
  optimizations

## ⚠️ Before this compiles

Minecraft 26.1+ ships with **official Mojang mappings only** — Yarn is discontinued as
of this version. Two symbols are now confirmed against **decompiled 26.2 source**,
not docs:

- `net.minecraft.resources.Identifier.fromNamespaceAndPath(namespace, path)` —
  class name stayed `Identifier` (never `ResourceLocation`); only the package moved
  off the old `net.minecraft.util`.
- `GuiGraphicsExtractor.text(Font, String, int, int, int)` — there is no
  `drawTextWithShadow`. `text(...)` defaults `dropShadow=true`. `Font` is
  `net.minecraft.client.gui.Font`. Text color is ARGB (`0xFFFFFFFF`, not `0xFFFFFF`).

Other names (`net.minecraft.client.Minecraft`, `font`, `ClientCommands`,
`HudElementRegistry`, `Component.literal`) match current Fabric 26.2 docs +
Mojang mapping dumps, but still let the IDE flag anything the 0.157.0+26.2
classpath disagrees with before assuming a clean compile.


## Building locally

No `gradlew` wrapper is committed (couldn't be generated in the sandbox this was
scaffolded in — offline, no reachable Gradle distribution). Easiest fix, run once
in each subproject with any local Gradle install:

```
cd client-mod && gradle wrapper --gradle-version 9.5.1 && ./gradlew build
cd ../hub && gradle wrapper --gradle-version 8.10 && ./gradlew build
```

Or just use your local `gradle` directly without a wrapper:

```
cd client-mod && gradle build      # produces build/libs/botfleet-control-*.jar
cd ../hub && gradle build          # produces build/libs/hub-*.jar
```

CI (GitHub Actions) doesn't need any of this — it provisions Gradle itself via
`gradle/actions/setup-gradle`.

## Running

```
# On the machine running the bots:
java -jar hub/build/libs/hub-*.jar --config hub-config.json

# Drop client-mod's jar into your real client's mods folder alongside Fabric API.
# In-game: /bc list
#          /bc bot1 #mine diamond_ore
```

See `hub-config.example.json` for the bot roster / HeadlessMc launch config format.
