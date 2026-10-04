# HalfCraft Bridge

First-milestone baseline below preserved. Current opt-in physics experiment uses separate collision mapping and dedicated `HalfCraftPhysics` world; see `bounded_physics.md` and `verified_physics.txt` for current lifecycle/control and measured limits.

Real GoldSrc owns BSP/campaign/NPCs/doors/triggers/scripts/camera/rendering and native saves. Real Minecraft Java/Fabric owns inventory/crafting/blocks/items/tools/combat/mobs/fluids/Redstone/survival and its world saves. Shared-memory adapters exchange observations first; no duplicate Minecraft systems.

`Half-Life → GoldSrc host → Local\HalfCraft_v1 ← Fabric guest ← Minecraft`

First milestone: map, native HL origin/look and native MC feet/velocity/onGround/worldLoaded, PIDs, independent monotonic heartbeats, explicit shutdown and reconnection. Neither side writes the other game's player position. No input takeover, collision, meshes, actor combat or HUD yet.

`fabric/` preserves upstream source, with a dedicated HalfCraft resource profile using only bridge entrypoint. `skse/` and `protocol/skycraft_protocol.h` remain reference. `goldsrc/sdk` pins official Valve SDK as separate licensed submodule; `goldsrc/host.cpp` adds observation hooks. Use separate `halfcraft_bridge` runtime so legacy `halfcraft` remains usable. Runtime inherits Valve assets through `fallback_dir`, never copies or commits them.

Protocol v1 uses fixed little-endian widths, 8-byte-aligned regions and single-writer seqlocks. Host creates mapping; guest opens existing only. Each writer owns its state block; 32-bit aligned atomics make x86 GoldSrc/x64 Java safe. Heartbeats use Windows GetTickCount milliseconds modulo 2³², timeout 8000 ms. Process-liveness validation complements timeout, especially paused/loading games. Reconnect uses owner PID/session changes; snapshots validate seq before/after. Named owner mutexes reject simultaneous same-role writers. Invalid magic/version/size rejected without reset. One mapping session per Windows login is deliberate; multi-instance support belongs to future protocol.

Game shutdown independent: mapping persists while either handle exists; live process never exits because peer exits. World loaded is separate from link ready. Normal Minecraft saves continue; coordinated cross-game save snapshots not in first milestone.

`HalfCraftWorld.openWhenReady` reuses MirrorWorld opening APIs for dedicated `HalfCraftBridge` normal Survival world (seed 17, cheats allowed), not SkyCraft void preset. Opens only from title screen after host connection, never teleports. `pauseOnLostFocus=false`; native survival including death continues in background. World/player presence does not promise a living player. Guest `started` resets on each host connection/session change so diagnostics connection age reflects reconnect, not process uptime.

Dedicated portable Prism root under ignored `local/`, authenticated Microsoft Java ownership required. Existing launcher under `H:\Games\Minecraft Launcher` is untouched. Gradle `runClient` is upstream's offline development convenience, not acceptance launcher because user requires normal authentication.

Next milestone after verified handshake: export small bounded BSP collision region through GoldSrc traces; feed Minecraft collision shapes; test real MC physics; only then controlled HL player puppet with native trigger/script handoff and fallback.
