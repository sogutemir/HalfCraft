# Coordinate mapping (observation milestone)

GoldSrc SDK `pm_shared/pm_shared.c` player hull: standing ±16 horizontal, ±36 vertical (72 units tall). Minecraft standing player 1.8 blocks tall. Choose **40 GoldSrc units/block** from `72 / 1.8`; 32 legacy voxel units and Skyrim 70 units are not bridge scales. Width differs: HL 32 units = .8 blocks versus MC .6; collision/puppet phase must resolve hull difference. Scale is geometric, not proof of identical game speeds.

GoldSrc world Cartesian X/Y plane, Z up. At yaw 0 view forward +X; yaw 90 forward +Y; positive view pitch down (`sdk/common/mathlib`/AngleVectors). HL `pev->origin` is hull center, not feet. Feet require hull-min Z (standing origin.z-36, crouched origin.z-18), later use actual mins; first protocol exports raw origin and labels it.

Minecraft: +X east, +Y up, +Z south. yaw 0 looks +Z, yaw 90 looks -X; positive pitch down. Use:

```
MC = (HL.x / 40, HL.z / 40, -HL.y / 40)
HL = (MC.x * 40, -MC.z * 40, MC.y * 40)
MC yaw = -HL yaw - 90  (mod 360)
MC pitch = HL pitch
```

Axis transform determinant +1: signed Y/Z swap preserves handedness. Rotation test: HL +X becomes MC +X (yaw -90), HL +Y becomes MC -Z (yaw ±180). Inverse HL yaw = -MC yaw - 90. Position and direction tests enforce this.

Utilities/tests use correct formula, signed canonical angles; no transforms applied to live player state. Raw host origin and raw guest feet intentionally use separate anchors. Global coordinate recentering/map namespace reserved for collision phase. `tools/test_protocol.py` checks random negative/large position round trips and cardinal view vectors with tolerance 1e-9.

## Bounded physics phase

Opt-in collision mapping adds standing feet transform `(HL.x/40,(HL.z-36.03125)/40,-HL.y/40)`, reverse adds `36.03125` to Z. 0.03125 is observed GoldSrc floor-clearance epsilon, not arbitrary scale. c1a0 `(484,318,-203.96875)` maps to `(12.1,-6,-7.95)`. Activation positions Minecraft once; guest feet then drive HL only while `hc_physics 1` is active. Main handshake still sends raw native origin/feet. `tools/test_collision.py` checks anchor round trip and negative region indexing. Crouched18-unit anchor and hull-width agreement remain pending; mismatch releases puppet through native hull check. See `bounded_physics.md`.
