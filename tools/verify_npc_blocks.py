"""Live block proxies: real scientist WALK_MOVE plus mining/removal and placement."""
import ctypes
import json
import re
import struct
import time
from pathlib import Path
from halfcraft_protocol import k
from client_status import status as client
from window import run

ROOT=Path(__file__).resolve().parents[1]
LOG=Path(r'K:\SteamLibrary\steamapps\common\Half-Life\qconsole.log')


def status():
    handle=k.OpenFileMappingW(4,False,r'Local\HalfCraft_blocks_v2')
    if not handle:
        raise ctypes.WinError(ctypes.get_last_error())
    address=k.MapViewOfFile(handle,4,0,0,30848)
    if not address:
        k.CloseHandle(handle)
        raise ctypes.WinError(ctypes.get_last_error())
    try:
        for _ in range(64):
            seq=struct.unpack('<I',ctypes.string_at(address,4))[0]
            if not seq or seq&1:
                continue
            raw=ctypes.string_at(address,64)
            if seq==struct.unpack('<I',ctypes.string_at(address,4))[0]:
                values=struct.unpack_from('<11I',raw)
                assert values[1:4]==(0x42435248,2,30848),values
                return dict(enabled=bool(values[7]),epoch=values[8],ack=values[9],proxies=values[10])
        raise RuntimeError('Blocks snapshot busy')
    finally:
        k.UnmapViewOfFile(address)
        k.CloseHandle(handle)


def command(text):
    run('HalfCraft Bridge',['open',text,'close','wait:.5'])


def test():
    offset=LOG.stat().st_size
    command('hc_blocks_check')
    command('hc_blocks_probe')
    with LOG.open('rb') as log:
        log.seek(offset)
        evidence=log.read().decode('utf8',errors='replace')
    assert re.search(r'HC_BLOCKS_PROBE PASS .*stopped=1 .*clear_distance=80\.000',evidence),evidence
    assert 'HC_BLOCKS embedded_npcs=0' in evidence,evidence
    command('hc_look 89 0')
    run('HalfCraft Bridge',['hold:2:.1','wait:.5'])
    target=client()['input']
    assert target['target'] and target['count']>0,target
    before=status()
    run('HalfCraft Bridge',['mouse:LEFT:2','wait:1'])
    broken=status()
    assert broken['proxies']==before['proxies']-1,(before,broken)
    run('HalfCraft Bridge',['hold:SPACE:.05','mouse:RIGHT:.2','wait:1'])
    placed=status()
    assert placed['proxies']==before['proxies'],(before,broken,placed)
    command('hc_physics 0')
    off=status()
    assert not off['enabled'] and off['proxies']==0,off
    command('hc_physics 1')
    time.sleep(3)
    resumed=status()
    assert resumed['enabled'] and resumed['proxies']==placed['proxies'],(placed,resumed)
    result=dict(result='PASS',npc_evidence=evidence.strip(),before=before,broken=broken,placed=placed,off=off,resumed=resumed)
    (ROOT/'local/npc-blocks-results.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8')
    print(json.dumps(result))


if __name__=='__main__':
    test()
