# GoldSrc host provenance

Official Valve Half-Life SDK submodule pinned to `b1b5cf5892918535619b2937bb927e46cb097ba1`, same upstream foundation as native HalfCraft. `sdk/LICENSE` governs SDK and linked game DLL; root MIT does not relicense it. No LibertyCraft GPL source imported.

`cbase_bridge.cpp` includes SDK's original `cbase.cpp`, replacing callback table entries with wrappers from `host.cpp`. Every wrapper calls original callback. Existing campaign entities/save handling retained; no native voxel system imported. `bridge.props` swaps compilation unit without editing SDK files. Native reference knowledge: `GameDLLInit`, `StartFrame`, map `gpGlobals->mapname`, `INDEXENT(1)`, `edict_t::v.origin/v_angle`, entity classname iteration. Client DLL untouched; bridge observes server state. Source-only repo, DLL output ignored.

`hc_bridge_check` diagnostic adapts native reference `HCActivity` observation: entity origin/frame/sequence changes and `CBaseMonster::m_pCine` scripted-NPC classification. Bridge report counts all `scripted_` classes. No gameplay takeover. `developer 1` exposes `ALERT(at_console)` diagnostics. `verify_campaign.py` leaves measured native save/map/NPC/script results under ignored `local/`.

Second phase `collision.cpp` adds explicit `hc_physics 1|0`, point-content/TraceModel collision sampling and guarded player puppet. Separate protocol keeps original heartbeat stable. Additional original PreThink/PostThink/save/restore wrappers and native trigger dispatch documented in `docs/bounded_physics.md`; default OFF. SDK source stays unchanged.
