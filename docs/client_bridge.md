# Half-Life client bridge — 2026-10-04

Built/deployed `halfcraft_bridge/cl_dlls/client.dll` alongside server DLL. SDK client sources remain unchanged: separate wrappers replace compilation units and preserve original callback calls and export table. SkyCraft `InputBridge` native Minecraft handler approach, `SkyAtlas` and `WorldExporter.MeshBuilder` reused. Two atlas accessors explicitly enabled in HalfCraft profile; Skyrim gameplay entrypoints remain disabled.

## Controls and rendering

- `hc_physics 1`, `hc_input 1`: HL-window movement/look/jump, Ctrl crouch, mouse mining/use, wheel and1–9hotbar. Left click mines actual Minecraft block targets; otherwise routes to native HL weapon. R/native reload and E/native use retained. Native movement suppressed while vanilla MC physics owns feet. Container screens suppress all gameplay attacks/use/reload.
- `I` opens the actual vanilla inventory; `E` remains HL use during gameplay. Container screens own keyboard/mouse and suppress native use/attacks/movement. Esc closes Minecraft containers before HL sees it. Shift/Ctrl/Alt queries use virtual keys; Unicode WM_CHAR input uses the chained native window procedure. Console binding stays native.
- `hc_background 1`: hide guest while takeover active, including inventory; run at 60 FPS. `hc_input 0` or peer loss restores visibility/capture. Console/menu/focus loss releases guest keys. UI readback reuses upstream `FrameExporter`; no slot or crafting implementation in GoldSrc.
- Vanilla HUD and held-item/hand animation captured on transparent background during gameplay. Loading tracker uses upstream readiness bypass; title/loading screens are not composited. Native level rendering skipped while linked; Minecraft still ticks world and renders GUI/hand.
- MC tick samples interpolate camera feet over 50 ms with snap for large corrections; vanilla eye height1.62standing/1.27crouching. Guest pose and eye height use collision Guest reserved uint32 at28 (bit0 crouch, bits8+ eye milliblocks). Native hull uses two overlapping head hull sweeps for60-unit crouched body. Host mouse look avoids delayed server `fixangle` feedback.
- Opaque/cutout real baked models, atlas UV/RGBA and native BSP depth. Second GoldSrc texture unit disabled during draw, GL state restored afterward. Target outline and selected slot/count rendered in HL.
- SkyCraft crack overlay path reused: `ClientLevelAccessor.destroyingBlocks`, real `SkyAtlas.crackUv(stage)` textures and vanilla0–9 mining stages. HL draws multiplicative, depth-tested overlays over shape bounds inflated0.004block; mouse release/break clears overlays. Like upstream, non-cube shapes use bounds rather than per-model crack projection. Hand swing/particles remain outside this delivery.
- Native BSP ray hit supplies adjacent air cell to vanilla `BlockItem` placement; closer BSP rejects interactions behind surface. Approximate sampled shapes apply, not exact BSP triangles.
- 27 nearby sections, one immutable acknowledged mesh/frame. Dirty-section hook prioritizes edits; exact block-state arrays provide scan fallback. Translucent geometry skipped. No custom Minecraft movement/mining/placement solver.

## Independent transports

Stable heartbeat remains `Local\HalfCraft_v1`, 384 bytes.

- Input: `Local\HalfCraft_input_v2`, magic `0x49435248`, v2, 4224 bytes. Layout retained; events Key1, Button2, Scroll3, Cursor4 (`code=x,value=y`), Text5 (`value=Unicode code point`). Host header0–63; guest64–127;256×16-byte events. Guest HUD target84/108,slot112,count116,latency120,seqlock124; background56.
- Render: `Local\HalfCraft_render_v3`, magic `0x52435248`, v3,75,508,672bytes. Atlas64MiB,mesh8MiB,vertex32bytes. Cracks at75497600:16×48bytes. Items at75498400:32-byte header,128×80-byte records (`id,kind,xyz,yaw,size,tint,uv[12]`). Vanilla ItemEntity motion/bob/spin/pickup retained; raw BSP collision samples apply on client and integrated server. Snapshots expire250ms.
- UI: `Local\HalfCraft_ui_v1`, magic `0x55435248`,33,177,728bytes. Host64,state32,frame32,pixels at128,3840×2160 RGBA maximum. Immutable frame until host ack; premultiplied alpha, bottom-up upload, session/epoch/size validation. State publishes container-open/GUI scale/carried count.
- Block entities: `Local\HalfCraft_scene_v1`, magic `0x53435248`,4,194,400bytes. Host64,message32,payload4MiB. Upstream `AvatarExporter` captures real animated block entities. Texture messages4 and scene messages6 retain upstream header/vertex layouts; bounded/acknowledged GoldSrc consumer validates textures, batches and vertices. This path currently exports block entities, not general mobs/particles.
- Rendering acknowledges at most one atlas and one section/frame. Atlas upload remains whole-image, not tiled; large resource packs beyond64MiB fail explicitly. Fixed-function immediate draw remains bounded, upgrade to GPU buffers at larger ranges.

## Worlds and saves

`c1a0` retains existing `HalfCraftPhysics` world; other validated map names use `HalfCraftPhysics_<lowercase-map>`. Map change restores previous MC observation position before normal vanilla save/disconnect, announces unloaded world before physics acknowledgement is cleared, then opens corresponding world. Server suspension retains ON preference through loading. Preset compatibility prompt creates backup first.

Blocks persist in per-map vanilla saves. Campaign inventory, armor/offhand, ender inventory, XP and recipe book use vanilla serialization in `saves/HalfCraftCampaign/<uuid>.dat`; atomic replacement preserves prior snapshot on write failure. Map switch closes containers and saves authoritative inventory before disconnect. Same-world reopen prefers vanilla player data. HL save-slot rollback still does not roll Minecraft saves back.

Finite c1a0 resource fixtures:3oak logs,12stone,4iron ore,2coal ore,copper ore,diamond ore. Native collision/NPC occupancy checked; existing blocks retained. Each fixture has its own vanilla command-storage marker; no reseeding on revisits. No BSP destruction, random corridor flooding or native-hit reward minting. Deep fixture availability requires its full native-clear region loaded.

## Live checks

Ignored artifacts under `local/` contain measured values:

| Check | Result |
|---|---|
| `verify_client.py` | PASS: HL W movement, Space jump, E guard, hotbar/wheel, focus/consoles/Escape menu, 50 active samples, OFF |
| `verify_blocks.py` | PASS in Survival with peaceful test world: dirt64→63, wall stop, feet y=-6→-5 on block, mining64 and landing y=-6; correction0 |
| `verify_wall_jump.py` | PASS from captured wall/dirt trap: jump1.252blocks, walk away3.018blocks, correction0; idle count stable |
| `verify_crouch.py` | PASS: Ctrl pose, eye1.27→1.62, feet stable, correction0 |
| `verify_cracks.py` | PASS: stages0/1/2 during partial mining, cancellation clears without breaking, full hold breaks; HL screenshot confirms visible cracks |
| `verify_npc_blocks.py` | PASS: native scientist WALK_MOVE stopped by block;80HL units with proxies disabled; mining14→13 proxies, placement13→14; embedded NPCs0 |
| `verify_depth.py` | PASS: 7,888 probe pixels in corridor, 0 behind BSP wall |
| `verify_client_transition.py` | PASS: input resumed after save/map/changelevel/load; c1a0 mesh192 vertices, c1a1 zero, returning/load192 |
| `verify_background.py` | PASS: invisible guest, HL movement, 50 active hidden samples, OFF restored visible window |
| `verify_transition.py` | PASS: native save/map/changelevel/load and manual OFF persistence |
| `verify_reconnect.py` | PASS: both normal peer exits/restarts, surviving game alive; matching new session |
| `verify_live.py --seconds 65` | PASS: 65.015s,66 samples, both max heartbeat age94ms with input/render enabled and hidden guest |
| Combined build | PASS: x86 server/client, Java25 Fabric, C++ layouts, Python protocol/collision/client checks,21 upstream JUnit tests |
| `verify_inventory.py` | PASS: I open/close, right split, left merge, Shift quick-move, gameplay/screen E separation, fixed camera |
| Crafting/search live | PASS:1oak log gives4planks; recipe-book E text visible;4planks produce1crafting table |
| `verify_resources.py` | PASS: real log mined,30grounded samples over3s,vanilla pickup count7→8 |
| `verify_campaign_inventory.py` | PASS:c1a0→c1a1→c1a0 retains selected log/plank counts[8,4] |
| `verify_chest.py` | PASS: real animated chest,store/reopen/recover8items,Esc closes without HL menu |
| `verify_stone_pickaxe.py` | PASS: three mined cobblestone and two crafted sticks consumed by vanilla stone pickaxe recipe |
| `verify_furnace_craft.py` | PASS: eight supplied fixture cobblestone consumed,one furnace produced |
| `verify_smelting.py` | PASS: supplied fixture raw iron/coal produce two ingots; vanilla fuel/cook timer |
| `verify_native_combat.py` | PASS: native pistol clip17→15,reload15→17 with bridge active |
| `verify_memory.py` | PASS:600hidden gameplay/inventory samples,UI age≤31ms;GC-live heap387829760→387966976bytes |

Dedicated Prism heap verified in running JVM:`-Xms8192m -Xmx16384m`. Per-tick collision debug gizmos removed after `CuboidGizmo.emit` heap exhaustion;60-second soak passed, campaign-length profiling remains pending. Full native/MC damage-authority integration, renewable trees, fluids/platforms, broad resource manifests and full-campaign balance are not verified.

Visual screenshots confirmed textured stone/dirt, real oak stair geometry and leaf alpha cutout. Dense forests, large resource packs, animated textures, prolonged campaign traversal and liquids not acceptance-tested.

Sample costs: client draw32–68 microseconds and guest export12–22 microseconds in small scene; maximum observed draw20.140ms including upload, initial atlas export273.418ms. Input event sample15–47ms; Minecraft tick adds quantization. These are small-scene samples, not FPS benchmark or full latency distribution. Initial atlas CPU build/upload can hitch; retained atlas avoids rebuilding on each map.

## Fixed live issues

Collision refresh `ready=0` caused repeated input dropout; last complete region now keeps forwarding active. Transient heartbeat seqlock busy falsely dropped guest; cached valid snapshot retains timeout/PID checks. Read-only collision mapping was used with read-modify-write atomic; client aligned read now stays read-only. GL multitexture hid atlas geometry. Mouse grab failed in unfocused guest, causing continuous Survival mining to stop; logical capture isolated from OS capture now set explicitly. Forwarding all alphabet keys opened invisible inventory; limited controls preserve native E. OFF restores actual mouse capture. Hidden-window stale frame heartbeat caused console focus jump; independent bridge owns takeover liveness, input dispatch still expires250ms.
