# HalfCraft

Real Half-Life / GoldSrc + authenticated Minecraft Java / Fabric, connected through Windows shared memory. **First observation-only bridge milestone verified on 2026-10-04.** Measured results: [verified_bridge.txt](verified_bridge.txt).

**Current phase:** Native Half-Life movement with Minecraft inventory/crafting, chest storage, HUD/hand, renewable resource mining, block entities and campaign inventory continuity. Minecraft health, hunger and worn armor handle bridged native damage, healing and armor repair. Enemy combat uses Minecraft weapons with confirmed-kill loot and XP. Full-campaign acceptance remains incomplete; the linked reports and earlier physics results include historical baselines.

Latest combined build passed: GoldSrc host/client, C++/Java layout checks, 21 upstream Java tests, Python protocol/collision/client checks and seeded loot checks. Live native damage/fall/heal probes passed. Actual fall/explosion/Xen/turret damage, death/respawn and the latest raw/cooked beef drops still need live acceptance; build success does not establish those gameplay results.

Nearby Minecraft blocks now stop native HL NPCs through invisible engine solids; mining removes solids and placement recreates them. [NPC collision, live checks and streaming limits](docs/npc_blocks.md). Wall/dirt-edge jump trap also fixed and live-tested.

## Play from Half-Life

Build/deploy now includes both `dlls/hl.dll` and `cl_dlls/client.dll`. Launch dedicated games, then F10 console:

```text
hc_physics 1
hc_input 1
hc_background 1
```

Close console. W/A/S/D, Space, Ctrl and mouse look retain native Half-Life movement. Left click attacks/mines with Minecraft items; right click uses them; 1–9/wheel selects the Minecraft hotbar. `I` opens inventory; `E` stays HL use outside containers. Native firing/reloading is suppressed while the bridge owns input. Inventory/container owns mouse/keyboard; Esc closes container. Console/menu/focus loss releases forwarded keys. Input/physics default OFF; save/load suspends physics, so enable it again after loading.

Nearby baked models and animated block entities use real textures and native depth. Vanilla HUD/held item/screens appear over HL. Map-specific worlds keep blocks separate; campaign inventory/armor/offhand/ender inventory/recipes/XP/health/hunger follow map changes. Minecraft saves remain independent of HL save-slot rollback. Renewable resource mining uses vanilla tools and smelting with 5× mining progress; it does not cut holes in the native BSP.

Confirmed bridge enemy kills grant 100 XP (normal), 200 XP (military), or 500 XP (heavy), plus arrows, feathers, gunpowder, string and a chance of a bow. Beef drops are 2–4 per rewarded kill; a fire-marked lethal hit selects cooked beef. Native/environmental kills and delayed fire deaths are not covered by this reward path. Military gun/ammo `DropItem` calls are suppressed while Minecraft vitals own the player; map-authored pickups remain.

```powershell
python C:\HalfCraftBridge\tools\client_status.py
python C:\HalfCraftBridge\tools\verify_client.py
python C:\HalfCraftBridge\tools\verify_depth.py
python C:\HalfCraftBridge\tools\verify_background.py
python C:\HalfCraftBridge\tools\verify_client_transition.py
```

Renderer bounded to27nearby sections,OpenGL,opaque/cutout block mesh and4MiB block-entity messages. Full mob/particle/translucent fluid rendering remains pending. Dedicated Prism JVM uses8GB minimum/16GB maximum heap.60s memory soak passed after collision debug-gizmo removal.

## Historical bounded physics experiment

The following movement results describe the earlier Minecraft-driven prototype. Current movement authority is GoldSrc; Minecraft follows native feet/pose. Dedicated worlds now span y=-1024 through y=1023, and the old y=-64 test floor is removed during migration.

BSP samples feed vanilla collision without per-tick debug gizmos. Distant bedrock is test-world bottom. Activation disables flight and waits for server teleport before puppet acknowledgement; OFF restores prior position/flight state. `python C:\HalfCraftBridge\tools\verify_spawn.py` checks grounded spawn/restore.

Launch both dedicated games, open Half-Life console with F10:

```text
hc_physics 1
hc_physics 0
```

`1` streams nearby BSP samples, aligns Minecraft feet once, then lets real Minecraft movement drive Half-Life player. Use Minecraft window with `hc_input 0`, or Half-Life window with `hc_input 1`. `0` restores native HL control and prior Minecraft test-world position. Physics defaults OFF.

```powershell
python C:\HalfCraftBridge\tools\physics_status.py
python C:\HalfCraftBridge\tools\verify_physics.py --seconds 65
python C:\HalfCraftBridge\tools\verify_physics_fallback.py
```

Final run: 2.154844 blocks walking, sampled jump rise 1.176759 blocks (another run captured 1.252203), BSP floor landing, 65-second active hold / 378 samples, settled puppet error <0.1 HL unit. Save, map transition, hull-wall rejection and peer exit fallback passed; c1a0 campaign regression passed.

Dedicated physics worlds use vanilla flat generator: bedrock floor at y=-64, air above. Original `HalfCraftBridge` Survival world retained. Preset reopening automatically backs up dedicated physics worlds. Collision mapping `Local\HalfCraft_collision_v1`, protocol version 2: 4288 bytes, 8³ blocks, 4³ occupancy samples/block, 10 HL units/sample. Slopes/thin surfaces, crouch hull, elevators/trains/ladders/liquids remain bounded. GoldSrc hull mismatches use correction/ack; native takeovers release control.

Half-Life owns movement, BSP maps, NPCs, doors, triggers, scripts, campaign and rendering. Minecraft executes blocks, inventory, combat item rules and vitals; client bridge draws exported blocks inside Black Mesa.

## Preserved projects and provenance

- Native / Legacy: `C:\HalfCraftDev`; runtime `K:\SteamLibrary\steamapps\common\Half-Life\halfcraft`. Both preservation tags and original dirty work retained; [baseline](docs/native_halfcraft_baseline.md).
- Bridge workspace: `C:\HalfCraftBridge`; separate runtime `K:\SteamLibrary\steamapps\common\Half-Life\halfcraft_bridge`.
- Canonical SkyCraft upstream / `upstream` remote: https://github.com/chasmlol/SkyCraft.
- Migration base: `bfcaf178524b92c2cdeb88e4ce0f13ef9ded6f32`.
- Original upstream history and attribution are retained; `upstream` remains available for future updates.
- Root MIT `LICENSE` and `THIRD-PARTY-NOTICES.md` preserved. Valve SDK submodule has its own license, pin `b1b5cf5892918535619b2937bb927e46cb097ba1`. No LibertyCraft GPL host code imported.
- [Archived upstream README](docs/skycraft_upstream_readme.md), [SkyCraft audit](docs/skycraft_architecture_audit.md), [LibertyCraft notes](docs/libertycraft_port_notes.md), [design](docs/HALFCRAFT_DESIGN.md), [coordinates](docs/coordinate_mapping.md).

## Build, deploy, launch

Requires Windows, VS2022 C++ tools/v143 + Windows SDK, Python, Java 25, owned Half-Life and Minecraft Java. Local Java: `C:\Users\sogut\jdk\jdk-25.0.1+8`. Fabric uses Minecraft `26.3`, loader `0.19.5`, Fabric API `0.161.0+26.3`.

Clone with `git clone --recurse-submodules <repository-url>`, or run `git submodule update --init --recursive` after cloning. Set `JAVA_HOME` to your Java 25 installation. The example paths and runtime defaults below match the development machine; review `tools/deploy.ps1` and `tools/launch-dev.ps1` for your Half-Life installation. `tools/package.ps1` is the preserved upstream Skyrim packager, not a HalfCraft release command.

```powershell
# Combined build: host, Fabric, C++/Java layouts, protocol and coordinate checks
powershell -ExecutionPolicy Bypass -File C:\HalfCraftBridge\tools\build.ps1

# Separate builds
powershell -ExecutionPolicy Bypass -File C:\HalfCraftBridge\tools\build.ps1 -HostOnly
powershell -ExecutionPolicy Bypass -File C:\HalfCraftBridge\tools\build.ps1 -FabricOnly

# Close Half-Life before deployment
powershell -ExecutionPolicy Bypass -File C:\HalfCraftBridge\tools\deploy.ps1
powershell -ExecutionPolicy Bypass -File C:\HalfCraftBridge\tools\launch-dev.ps1
python C:\HalfCraftBridge\tools\bridge_status.py
```

Host-first startup experimentally verified; guest opens host-created mapping. Both windows remain visible. Portable Prism `11.1.1` and dedicated instance/world `HalfCraftBridge` live under ignored `local/`. Prism requires normal Microsoft/Mojang ownership authentication. Existing launcher `H:\Games\Minecraft Launcher` is not used or modified. Dedicated normal Survival world opens after connection; seed 17, cheats enabled, no position takeover. Existing worlds and account configuration are retained. Save/shutdown must finish before relaunch.

Launch avoids starting another Half-Life process if one already exists; that existing process must be running `halfcraft_bridge`. `-HostOnly` launches only host. Runtime inherits assets through `fallback_dir "valve"`; deploy writes only `halfcraft_bridge`.

## What ran

- Real `c1a0` + real Minecraft world: host PID `14764`, guest PID `30100`, matching session `38334492`.
- 65.031 seconds / 66 samples: both heartbeats continuously alive; maximum sample ages 110 ms each.
- Map, HL origin/view angles, MC position/velocity/world/`onGround` visible in shared memory and rate-limited game logs. Manual native movement changes observed on both sides; neither adapter forces movement.
- Normal guest exit/relaunch and host exit/relaunch passed; surviving game remained running. Final reconnect host PID `10796`, guest PID `9108`, session `38808506`.
- `c1a0` baseline: 17 NPCs, 61 scripts, 16 doors. Six-second sample: 16 NPC changes, 6 door changes, 5 scripted NPC changes. After native save/load: 16 NPC changes, 8 scripted NPC changes.
- `c1a0 → c1a1 → c1a0`, native save/load, protocol rejection/heartbeat/reconnect and 1000 coordinate round trips passed.

## Protocol v1

`protocol/halfcraft_protocol.h` is authoritative. Mapping `Local\HalfCraft_v1`, magic `0x46435248` (`HRCF`), version 1; little-endian, fixed-width fields, 8-byte struct alignment, total 384 bytes.

| Region | Absolute offset | Bytes | Fields |
|---|---:|---:|---|
| Header | 0 | 64 | magic, version, byte size, reserved |
| Host | 64 | 192 | seqlock, PID, heartbeat, state, session, player flag, start time; map at +32, origin double[3] at +96, angles float[3] at +120 |
| Guest | 256 | 128 | seqlock, PID, heartbeat, state, host session, world flag, onGround, connection start; position double[3] at +32, velocity double[3] at +56 |

States: `DISCONNECTED=0`, `HOST_READY=1`, `GUEST_READY=2`, `CONNECTED=3`, `LINK_ERROR=4`, `SHUTDOWN=5`. Each endpoint owns its region, publishes through aligned 32-bit seqlocks, ticks every 100 ms on independent thread. `GetTickCount()` modulo 2³², timeout 8000 ms, PID/session validation and named owner mutexes. Peer exit does not close surviving game.

## Reuse and added sources

SkyCraft Gradle/Loom/Fabric toolchain, Java FFM kernel32 access, seqlock transport and `MirrorWorld` lifecycle approach reused. `fabric/build.gradle` adds `-Phalfcraft` resource profile. New `dev.halfcraft` guest entrypoint/link/world opens normal world and exchanges observations. Original `dev.skycraft` sources compile but SkyCraft entrypoints and mixins are disabled in HalfCraft jar. Skyrim `skse/`, original protocol and tools remain isolated reference.

GoldSrc additions: `goldsrc/host.cpp`, `cbase_bridge.cpp`, `bridge.props`, `layout_check.cpp`, `runtime/liblist.gam`, `runtime/autoexec.cfg`, licensed `sdk` submodule and provenance README. Wrappers call original SDK callbacks; no dependency on legacy workspace. Tools include build/deploy/launch, authenticated instance setup, status, fake endpoints and real acceptance checks.

## Repeat checks

```powershell
python C:\HalfCraftBridge\tools\test_protocol.py
python C:\HalfCraftBridge\tools\verify_live.py --seconds 65 --output C:\HalfCraftBridge\local\heartbeat-65s.json
python C:\HalfCraftBridge\tools\verify_campaign.py
python C:\HalfCraftBridge\tools\verify_reconnect.py
```

Real checks require dedicated connected games. Campaign check changes maps and isolated `hc_bridge_acceptance` save slot. Reconnect check normally closes/restarts each dedicated game. Raw evidence stays in ignored `local/`; report contains only relevant measurements. Proprietary game assets/binaries and account credentials excluded from Git.

## Limits and next milestone

First observation milestone above is historical. Current limits: sampled Minecraft block/item collision, translucent fluids, general mob/particle rendering, full native damage/death acceptance and save-slot rollback. Native movement and enemy logic run; Minecraft natural mob spawning is disabled in dedicated worlds. Existing mobs are not deleted. One host/guest pair per Windows login; 40 HL units/block.

Next scope: actual damage-source and death/respawn checks, raw/cooked kill rewards, full campaign traversal and resource balance. See [roadmap](docs/playability_roadmap.md) for earlier milestone context.
