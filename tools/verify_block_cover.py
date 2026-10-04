"""Live native block cover regression. Existing wall and clear WALK_MOVE lane required.

Creates/removes temporary scientist, soldier and zombie; checks real FireBullets.
Saves separate hc_block_cover_check checkpoint, moves away using noclip, then restores.
Requires 1280x720 diagnostic runtime. User worlds/inventory are not edited.
"""
import json
import re
import struct
import time
from pathlib import Path
from client_status import read
from physics_status import status as physics
from window import run

ROOT=Path(__file__).resolve().parents[1]
RUNTIME=Path(r'K:\SteamLibrary\steamapps\common\Half-Life\halfcraft_bridge')
LOG=RUNTIME.parent/'qconsole.log'


def blocks():
    h=struct.unpack_from('<13I',read(r'Local\HalfCraft_blocks_v2',30848,0,64))
    assert h[1:4]==(0x42435248,2,30848),h
    return dict(enabled=bool(h[7]),epoch=h[8],proxies=h[10])


def command(text):
    run('HalfCraft Bridge',['open',text,'close','wait:.5'])


def probe(actor):
    offset=LOG.stat().st_size
    command('developer 1')
    try:
        command('hc_blocks_probe '+actor)
        with LOG.open('rb') as log:
            log.seek(offset); evidence=log.read().decode(errors='replace')
        matches=re.findall(r'HC_BLOCK_COVER [^\r\n]*',evidence)
        movement=re.findall(r'HC_BLOCKS_PROBE [^\r\n]*',evidence)
        assert 'HC_BLOCK_ACTOR '+actor in evidence,evidence
        assert movement and re.search(r'PASS stopped=1 clear=80\.000',movement[-1]),evidence
        assert matches and matches[-1]=='HC_BLOCK_COVER PASS',evidence
        assert 'HC_BLOCK_BULLET 1' in evidence and 'HC_BLOCK_SIGHT 1' in evidence and 'HC_BLOCK_DAMAGE blocked=1 clear=1' in evidence,evidence
        return actor+': '+movement[-1]+'; '+matches[-1]
    finally:
        command('developer 0')


def test():
    before=blocks()
    assert before['enabled'] and before['proxies']>0,before
    evidence=[probe(actor) for actor in ('monster_scientist','monster_human_grunt','monster_zombie')]
    start=physics()['host']['feet']
    command('save hc_block_cover_check')
    try:
        command('hc_look 0 0')
        command('sv_cheats 1')
        command('noclip')
        run('HalfCraft Bridge',['hold:W:3','wait:2'])
        distant=blocks(); end=physics()['host']['feet']
        distance=sum((a-b)**2 for a,b in zip(start,end))**.5
        assert distance>5,(start,end,distance)
        assert distant['enabled'] and distant['proxies']==before['proxies'],(before,distant)
    finally:
        command('load hc_block_cover_check')
        command('sv_cheats 0')
    deadline=time.monotonic()+60
    while time.monotonic()<deadline:
        restored=blocks()
        if restored['enabled'] and restored['proxies']==before['proxies'] and restored['epoch']>before['epoch']: break
        time.sleep(.2)
    else: raise AssertionError(('Blocks did not return after load',before,restored))
    evidence.append(probe('monster_human_grunt'))
    result=dict(result='PASS',before=before,distant=distant,restored=restored,distance_blocks=distance,native_checks=evidence)
    (ROOT/'local/block-cover-results.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8')
    print(json.dumps(result))


if __name__=='__main__': test()
