# LibertyCraft port reference

Inspected clone https://github.com/mrborghini/libertycraft at `58e72bcdd6a02629a29c3bc0f6161141eb2e05ab`, outside bridge tree in approved temporary directory.

Licenses: README licenses Fabric/protocol/tools MIT, `asi/` GPL-3.0 due to IV-SDK. No LibertyCraft code imported. In particular GTA host rendering/collision/input source is architectural evidence only.

- `README.md`, `protocol/libertycraft_protocol.h`, `fabric/.../link/Proto.java`: preserves SkyCraft v11 packet byte layout/offsets and rings, but magic becomes `0x5954424C` (`LBTY`), namespace `Local\\LibertyCraft_v1`, scale 1 GTA metre/block. **Byte layout compatibility does not mean wire identity or full semantic compatibility.** Extra flags/events identify vehicles, directional hurt and hit points; Skyrim skill constants retained unused.
- Fabric modules retained/adapted: `InputBridge`, `MirrorWorld`, `FrameExporter`, `ProxySync`, `WorldExporter`, `AvatarExporter`, `TriCollider`, world collision/water/ray/dig machinery and collision/render mixins. Packages renamed `dev.libertycraft`; `SkyClient` becomes `HostClient`, `SkyLink` becomes host link, `SkyrimActorEntity` becomes `HostActorEntity`. Directory diff confirms modifications, not unchanged guest.
- `fabric/.../link/PosixLink.java`: `/dev/shm/libertycraft-bridge` mapped file for native Linux MC + GTA under Proton; Windows named section remains an option. Transport replaced independently of gameplay schema.
- SKSE replaced by `asi/src/dllmain.cpp` + IV-SDK GTA plugin. `Game.cpp`, `HostDrive.cpp`, `NikoBody.cpp` handle player/cutscenes/vehicles, not transferable RE/CommonLib hooks.
- `asi/src/Collision.cpp`: region/epoch worker jobs and bounded per-frame probe budget, `collision/Rays.h`, `Geometry.h`, `Objects.h`, `Water.h`; samples streamed GTA map, objects and water instead of Havok. Sends `ColRegion`/triangles/occupancy masks. Lesson: keep guest collider, replace geometry exporter with host-native queries and moving-world invalidation.
- `asi/src/Render.cpp`, `render/`, `Overlay.cpp`: host D3D9 renderer/overlay replaces Skyrim D3D11 compositor; Fabric exports meshes/atlas/GUI as before. GoldSrc OpenGL/software frame needs separate hooks; neither compositor is drop-in.
- `asi/src/Input.cpp`, Fabric `InputBridge`: GTA allowlist and control handoff differ. Vehicle mode uses new `HostDrive`, `HostDriveClient`, mount helpers; do not carry it into HL handshake.
- `HostActorEntity`, `HostCombat`, `ProxyPush`, `ProxyPushClient`: peds/vehicles represented as host proxies; protocol vehicle tag and segmented vehicle records avoid Skyrim FormID assumptions. HL later requires map-scoped edict index + generation, not persistent pointers.

Useful port lesson: preserve real MC systems, transport/ring ideas and guest geometry consumers; isolate host SDK, IDs, player ownership, native rendering and lifecycle. First HL milestone keeps both players independently controlled and visible. LibertyCraft's README milestones are its claims, not local measurements of this bridge.
