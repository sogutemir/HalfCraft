# HalfCraft playable campaign roadmap

## Current milestone: inventory and stability

- Vanilla Minecraft screens captured with upstream `FrameExporter`; GoldSrc OpenGL compositor.
- I opens inventory; gameplay E remains HL `+use`. Screen input suppresses HL movement, attacks, and use.
- Input v2 includes cursor and Unicode text. UI v1 carries acknowledged RGBA frames and screen state.
- Remove per-tick collision debug gizmos: hidden/suppressed level rendering must not accumulate debug geometry.
- Dedicated Prism instance: `MinMemAlloc=8192`, `MaxMemAlloc=16384`. Restart Prism after changing settings: its cached instance configuration can overwrite external edits.
- Live passed: split/merge, shift-click, I/E/Esc ownership, fixed camera, recipe E text, logs-to-planks/table, chest store/reopen/recover, campaign stacks across c1a0/c1a1. Drag, Unicode layouts, prolonged memory and campaign-wide acceptance remain pending.

## Resource collection design

Upstream reference: `fabric/src/main/java/dev/skycraft/world/SkyDig.java` and `skse/src/Dig.cpp`, `DigMesh.cpp`, `DigPhysics.cpp`.
SkyCraft marks native geometry cells dug, cuts native visuals/collision, reveals vanilla blocks, and uses vanilla tool/drop logic. Stone reveals deterministic small ore clusters. Skyrim material/object metadata identifies natural rocks and trees.

GoldSrc BSP has no equivalent runtime cutout adapter. Treating every concrete wall as stone or every wooden texture as a tree would break campaign doors, scripted paths, and progression. First resource milestone uses real Minecraft blocks in approved playable spaces.

1. Establish one c1a0 resource fixture outside door/NPC/script paths. Logs, stone, coal, iron and a chest provide measurable survival gathering/crafting/container acceptance. Generate once on integrated server; preserve player blocks and already harvested cells.
2. Extend with per-map approved resource manifests, native-clear placement, NPC exclusion, and bounded counts. No general room flooding or arbitrary BSP mutation. Timber crates supply logs; maintenance rubble supplies stone; industrial storage supplies coal/iron/copper. Cavern maps host richer deposits and rare redstone/lapis/gold/diamond/emerald blocks. Appearance must clearly communicate Minecraft resources.
3. Preserve inventory across campaign map-specific Minecraft worlds before distributing resources. Current worlds have separate vanilla player data; map changes must not lose/duplicate carried stacks or armor. Save slots do not currently roll Minecraft saves back; define and verify explicit persistence policy.
4. Complete vanilla loop: logs to planks/sticks/table; wooden pickaxe to stone tools; coal/furnace to ingots; iron tools to rare ores. Tool requirements, durability, fortune/silk touch, XP and drops remain vanilla. Spawn only resource blocks; never mint rewards from native hit callbacks.
5. Renewable wood uses vanilla saplings and dirt at approved sites with enough vertical room. Verify tree growth cannot intersect BSP, doors or NPC hulls before enabling arbitrary growth. Finite campaign deposits persist; no reset-on-map-entry farming.

## Later milestones, each separately verified

- Vanilla block entities, translucent render paths, container/workstation interactions and visual feedback.
- Campaign map changes, inventory persistence, save/load, reconnect and graceful shutdown.
- Health/death, native damage, tool/combat input and progression policy; avoid duplicate native/MC damage.
- Physics acceptance for crouch/ceilings, stairs, slopes, moving doors/platforms, ladders/water and scripted takeover.
- HUD/hand/particles, lighting, entity rendering as needed for visible gameplay. Do not spawn invisible threats.
- End-to-end campaign traversal, resource availability, crafting progression, FPS/memory measurements.

Implemented finite c1a0 log/stone/coal/iron/copper/diamond fixtures, shared campaign inventory, animated block entities and HUD/hand capture. Live gathering/resting/pickup, wooden/stone pickaxe crafting, chest storage, furnace crafting/smelting, native fire/reload and two-map inventory retention passed. Furnace acceptance used supplied ingredients; whole natural gathering-to-ingot route, campaign-wide locations, renewable trees, unified damage/death and broad traversal remain pending.60-second hidden GUI/gameplay memory soak passed. Native campaign NPC/scripts/doors/save/load regression passed.

“Playable” requires verified gathering, crafting, storage, building, native interactions, persistence and campaign travel. A successful build alone does not establish these.
