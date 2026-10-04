"""Aim at a native breakable/monster, then run: python tools/verify_native_melee.py"""
import ctypes
import json
import struct
import time
from halfcraft_protocol import k, age
from window import run


def snapshot():
    handle = k.OpenFileMappingW(4, False, r'Local\HalfCraft_interaction_v3')
    if not handle:
        raise ctypes.WinError(ctypes.get_last_error())
    address = k.MapViewOfFile(handle, 4, 0, 0, 7824)
    if not address:
        k.CloseHandle(handle)
        raise ctypes.WinError(ctypes.get_last_error())
    try:
        for _ in range(64):
            seq = struct.unpack('<I', ctypes.string_at(address, 4))[0]
            if not seq or seq & 1:
                continue
            raw = ctypes.string_at(address, 5184)
            if seq != struct.unpack('<I', ctypes.string_at(address, 4))[0]:
                continue
            h = struct.unpack_from('<12I', raw)
            assert h[1:4] == (0x58435248, 3, 7824), h
            assert h[7] and age(h[6]) < 1000, ('Native interaction inactive/stale', h)
            assert h[10] <= 128, h
            actors = {}
            for i in range(h[10]):
                actor = struct.unpack_from('<II7fI', raw, 64 + i * 40)
                actors[actor[0]] = dict(serial=actor[1], bounds=actor[2:8], health=actor[8], breakable=bool(actor[9] & 1))
            return dict(session=h[5], epoch=h[8], target=h[11], target_id=struct.unpack_from('<I', raw, 60)[0], actors=actors)
        raise RuntimeError('Native interaction snapshot busy')
    finally:
        k.UnmapViewOfFile(address)
        k.CloseHandle(handle)


def test():
    before = snapshot()
    target = before['target_id']
    assert before['target'] == 2 and target in before['actors'], 'Aim within melee reach at damageable native crate or monster'
    actor = before['actors'][target]
    run('HalfCraft Bridge', ['mouse:LEFT:.1', 'wait:.7', 'mouse:LEFT:.1', 'wait:.7', 'mouse:LEFT:.1'])
    time.sleep(.5)
    after = snapshot()
    assert (before['session'], before['epoch']) == (after['session'], after['epoch']), 'Map/link changed during test'
    remaining = after['actors'].get(target)
    assert remaining is None or remaining['serial'] != actor['serial'] or remaining['health'] < actor['health'], (target, actor, remaining)
    print(json.dumps(dict(result='PASS', target_id=target, before=actor, after=remaining)))


if __name__ == '__main__':
    test()
