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

- Minecraft 26.2, Fabric Loader 0.19.3, Fabric Loom 1.17, Gradle 9.5.1
- Fabric API 0.158.0+26.2
- Java 21+ (HeadlessMc's own requirement varies by MC version — check their docs for
  26.2 specifically)
- HeadlessMc (latest release) + `hmc-specifics` (26.2 build) + Baritone (26.2-compatible
  build) + optionally `hmc.assets.dummy` for the bots
- Krypton 0.3.1 on the bot instances (native 26.2 support) for the networking-stack
  optimizations

## ⚠️ Before this compiles

Minecraft 26.1+ ships with **official Mojang mappings only** — Yarn is discontinued as
of this version. The class/method names below (`net.minecraft.client.Minecraft`,
`LocalPlayer`, etc.) follow long-standing Mojang-mapping convention, but I have not
verified them against the actual 26.2 Fabric API sources at time of writing. **Open the
project in your IDE with Fabric API 0.158.0+26.2 on the classpath and let it flag any
renamed symbols before you assume this builds clean.** This is a real gap, not
boilerplate caution — flagging it plainly rather than asserting false certainty on a
version this new.

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
