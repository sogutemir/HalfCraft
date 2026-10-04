# Bounded BSP physics experiment

Second phase, verified 2026-10-04. Opt-in `hc_physics 1`; `hc_physics 0` releases control. Default observation mode remains. No legacy workspace/runtime changes.

## Ownership and source reuse

GoldSrc exports nearby collision samples; Minecraft `CollisionGetter.getBlockCollisions` gains real `VoxelShape` boxes on client and integrated server. Vanilla walking/gravity/jump/onGround/fall logic runs unchanged. No custom physics solver or fake Minecraft inventory/block systems. Source reference: SkyCraft `SkyCollision`, `BlockCollisionsMixin`, `MirrorWorld`, `WorldOpenFlowsMixin`; HalfCraft uses separate scoped mixins and mapping. Original Skyrim entrypoints/mixins stay disabled.

`goldsrc/collision.cpp` uses `POINT_CONTENTS` for world BSP, brush abs bounds for broad phase and `pfnTraceModel` point hull for actual brush occupancy. 4096 points/frame, 32768 points/full region; refresh starts 500ms after completion. 8³-block region centered around native HL feet. 4³ samples/block, each 10 HL units / 0.25 block. Shapes are collision only, not placed Minecraft blocks. Full cells merge into single boxes; partial cells merge x-runs.

`HalfCraftPhysics` test world: vanilla flat generator, bedrock bottom at -64; BSP tests at y=-6 have no native Minecraft floor beneath them. Prior Survival world preserved. Dedicated custom-preset reopening creates normal Minecraft backup before proceeding; backup failure retains vanilla prompt.

## Protocol

Independent `Local\HalfCraft_collision_v1`, magic `0x43435248`, version 2. Stable `Local\HalfCraft_v1` unchanged.

| Region | Offset / bytes | Layout |
|---|---|---|
| Host | 0 / 128 | seq0, magic4, version8, bytes12, pid16, session20, heartbeat24, enabled28, epoch32, generation36, ready40, correction44; signed base int[3]48; dim60; feet double[3]64; yaw88/pitch92; sub96; occupied100; padding104 |
| Masks | 128 / 4096 | 512 uint64, cells x fastest then y then z; bit x+4*y+16*z |
| Guest | 4224 / 64 | seq0, pid4, session8, epoch12, active16, heartbeat20, correctionAck24; feet double[3]32; yaw56/pitch60 |

Little-endian, pack/alignment 8; total 4288. Host publishes complete header/masks under seqlock. `ready=0` during build; guest keeps last complete immutable shapes. Session matches stable bridge; epoch acknowledges intentional takeover. Last good guest snapshot tolerates writer/read collision; 2000ms stale physics yields native control. Guest requires host link/session, fresh region heartbeat, local integrated server and exact dedicated world name. Bounded region edges add collision walls rather than allowing unknown-geometry falls.

## Coordinates and control

40 HL units/block. Standing feet MC `(HL.x/40, (HL.z-36.03125)/40, -HL.y/40)`; reverse adds `36.03125`. 36 is standing hull half height; 0.03125 is observed GoldSrc floor clearance. c1a0 origin `(484,318,-203.96875)` becomes `(12.1,-6,-7.95)`. Guest yaw `-HL.yaw-90`, pitch same. Crouch-specific offsets are not complete.

One initial Minecraft teleport on activation, no continuous host correction: MC physics owns movement after ack. Integrated-server teleport and local prediction aligned. Half-Life original PreThink/PostThink callbacks run; wrapper temporarily sets MOVETYPE_NONE/gravity0, then applies acknowledged guest feet after native hull sweep. Maximum displacement80 HL units/update; non-finite samples/look rejected. Ground flag follows guest. Native engine may reset WALK, wrapper reapplies NONE next tick. Native trigger Touch callbacks dispatched for overlapping native trigger bounds because NONE skips normal walk trigger pass.

Save/restore and ServerDeactivate suspend BEFORE original serialization, retain ON preference and resume after native player/world readiness. Native death/frozen/train/ladder/water, teleport and stale/disconnected guest release native control. Hull rejection clips motion against native planes, preserving sliding/jumping, with version2 correction/ack and3s acknowledgement timeout. Guest correction retains velocity along clear axes. Native up/forward/down step path respects `sv_stepsize`. Floor samples round up within10units; guest teleport settles onto sampled support. Shapes dilate horizontally0.225block:4units for32-unit HL hull versus24-unit MC body, plus5units for center-sample uncertainty. Activation/correction reconciles streamed wall bounds and escapes placed-block overlap upward only through native-free space. OFF restores move/gravity and previous MC position/flight. Minecraft.stop restores before vanilla save.

## Measured acceptance and limits

Spawn visibility fix: Minecraft native per-tick Gizmos draw nearby collision boxes (teal fill/stroke), no block writes and no persistent world edits. Distant bedrock at -64 is separate from BSP floor (c1a0 -6). Activation disables flight on client/server and acknowledges only after server teleport completes and local feet are within0.5block of target; 3s timeout disables. OFF restores saved flight state. Runnable `verify_spawn.py` passed 50 samples over5s, all grounded at y=-6, no vertical launch, exact prior-position restoration. Screenshot `local/spawn-fix.png` visually confirmed visible floor/wall geometry.

See `verified_physics.txt`, raw ignored `local/physics-results.json`, `physics-fallback-results.json`, `campaign-results.json`. Floor, walk, jump/land, 65s active hold, OFF restoration, native-save/map fallback, hull rejection, both peer exits/relaunch, campaign NPC/script/door activity passed.

Sampled collision remains approximate: thin geometry/slopes, moving platforms/trains, crouch and full campaign triggers remain ceilings. Native hull guards NPCs; actor proxies not exported. Client input/render and native use now available; see [client bridge](client_bridge.md). Minecraft blocks have vanilla collision; verified standing atop placed dirt without hull correction. Save-slot synchronization and crash restoration guarantee remain unfinished.

Wall/dirt trap regression: `python tools/verify_wall_jump.py` loads captured `hc_wall_stuck` native save with existing c1a0 Minecraft blocks. PASS: jump1.252blocks, escape3.018blocks, correction0; idle correction count stable before/after jump. Save fixture remains local and requires unchanged trap blocks.

Next: exact BSP triangles/static hull surfaces and moving brush transforms; verify slope/elevator/trigger continuity before full campaign support.
