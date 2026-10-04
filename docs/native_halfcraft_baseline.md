# HalfCraft Native / Legacy baseline

Recorded 2026-10-04 before bridge implementation. Workspace: `C:\HalfCraftDev`.

- HEAD: `da09b4ed5f98c5b9b94a8b1368a3596490d0b51c`.
- Preservation tag: `halfcraft-native-before-skycraft-pivot`, pointing to that HEAD.
- Existing uncommitted work preserved: `deploy.py` (+1 line, atlas deployment), `generate_assets.py` (+15 lines, sprite atlas generator). Tag does **not** include these working-tree changes.
- Nested `sdk/` Git repository: HEAD `bc04a57be8487147d72e8ec3765c7e0567298a99`, same preservation tag created there. Its working tree has 19 modified files (mostly legacy `.dsp` formatting plus client rendering hooks) and two untracked client renderer sources. All retained as found; no commit/reset/stash performed.
- No native source/runtime files changed for this migration. Runtime: `K:\SteamLibrary\steamapps\common\Half-Life\halfcraft`.
- Launch: `powershell -ExecutionPolicy Bypass -File C:\HalfCraftDev\launch.ps1`.
- Verification: `python C:\HalfCraftDev\verify_engine.py`.

## Recorded working behavior (existing evidence, not newly re-tested)

`README.md` and `verified_campaign.txt` report real `c1a0` integration, BSP floor placement/break/inventory PASS, standing on placed block PASS, negative/positive chunk boundaries PASS, map-specific overlay persistence, `c1a0` → `c1a1` → `c1a0`, native save/load and inventory restoration.

Recorded NPC/script compatibility: 17 NPCs, 61 scripts, 16 doors and 13 triggers on c1a0. Activity sample: 16 NPC changes, 6 door changes, 5 scripted-NPC changes. Restore sample: 16 NPC changes and 7 scripted-NPC changes. These are baseline historical measurements; a new bridge campaign regression is tracked separately.

Native voxel implementation: 32-unit cubes, corner anchor `(0,0,0)`, centers `32*n+16`; sparse 16³ chunks; one studio entity and `SOLID_BBOX` per block; 512-block cap with 128-edict reserve. Overlay files are versioned `.hcw`, atomic replace, map/world-scoped. Native saves preserve entities and controller/inventory. No campaign BSP edits.

## Reusable GoldSrc integration

Valve SDK base: `b1b5cf5892918535619b2937bb927e46cb097ba1`, https://github.com/ValveSoftware/halflife; Valve SDK license applies, not MIT.

Useful hooks: `GameDLLInit`, `ServerActivate`, `ServerDeactivate`, `StartFrame`, `ClientCommand`; `gpGlobals->mapname`; engine edict/player iteration; `pev->origin`, `pev->v_angle`; `UTIL_TraceLine`, `UTIL_TraceHull` and trace plane normals; engine entity queries and native save/restore hooks. Bridge first milestone needs observation only; collision and player-control hooks remain references.

Build uses VS2022/v143, Win32 SDK server/client DLL projects. Deployment and engine-console automation exist in `build.ps1`, `deploy.py`, `engine_console.py`, `verify_engine.py`, `launch.ps1`. New bridge owns copied/adapted source and tools; no runtime dependency on this legacy directory.
