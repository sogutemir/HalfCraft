"""HL-only vanilla placement: occupied NPC cell rejected, adjacent free space allowed."""
import ctypes
import json
import struct
import time
from pathlib import Path
from halfcraft_protocol import k
from client_status import status
from verify_npc_blocks import command, status as blocks
from window import run


def actors():
    handle=k.OpenFileMappingW(4,False,r'Local\HalfCraft_blocks_v2')
    if not handle: raise ctypes.WinError(ctypes.get_last_error())
    address=k.MapViewOfFile(handle,4,0,0,30848)
    if not address:
        k.CloseHandle(handle)
        raise ctypes.WinError(ctypes.get_last_error())
    try:
        for _ in range(64):
            seq=struct.unpack('<I',ctypes.string_at(address,4))[0]
            if not seq or seq&1: continue
            header=ctypes.string_at(address,64)
            payload=ctypes.string_at(address+24704,256*24)
            if seq!=struct.unpack('<I',ctypes.string_at(address,4))[0]: continue
            count,ready=struct.unpack_from('<2I',header,44)
            assert ready and count<=256,(count,ready)
            return [struct.unpack_from('<6f',payload,i*24) for i in range(count)]
        raise RuntimeError('Actor snapshot busy')
    finally:
        k.UnmapViewOfFile(address); k.CloseHandle(handle)


def test():
    command('hc_look 45 180')
    run('HalfCraft Bridge',['hold:2:.1','wait:.5'])
    before=status()['input']; bounds=before['bounds']
    assert before['target'] and before['count']>0,before
    x,z=(bounds[0]+bounds[3])/2,(bounds[2]+bounds[5])/2
    command(f'hc_npc_place_probe {x} {bounds[1]} {z}')
    time.sleep(.5)
    occupied=actors(); initial=blocks()
    try:
        run('HalfCraft Bridge',['mouse:RIGHT:.1','wait:1'])
        denied=status()['input']; unchanged=actors()
        assert denied['count']==before['count'],(before,denied)
        assert blocks()['proxies']==initial['proxies'],(initial,blocks())
        assert occupied==unchanged,(occupied,unchanged)
        # Move only test fixture into neighboring cell; same target now genuinely free.
        command(f'hc_npc_place_probe {x} {bounds[1]} {z-1}')
        run('HalfCraft Bridge',['mouse:RIGHT:.1','wait:1'])
        allowed=status()['input']; placed=blocks()
        assert allowed['count']==before['count']-1,(before,allowed)
        assert placed['proxies']==initial['proxies']+1,(initial,placed)
        run('HalfCraft Bridge',['mouse:LEFT:2','wait:1'])
        assert blocks()['proxies']==initial['proxies']
        result=dict(result='PASS',denied_count=denied['count'],allowed_count=allowed['count'],
                    npc_unchanged=True,proxies_before=initial['proxies'],proxies_placed=placed['proxies'])
        (Path(__file__).resolve().parents[1]/'local/npc-placement-results.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8')
        print(json.dumps(result))
    finally:
        command('hc_npc_place_probe 0')


if __name__=='__main__': test()
