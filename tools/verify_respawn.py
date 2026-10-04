"""Live death/checkpoint recovery: no hc_physics or hc_input command after death."""
import json
import re
import time
from pathlib import Path
from physics_status import status as physics
from client_status import status
from window import run

ROOT=Path(__file__).resolve().parents[1]
LOG=Path(r'K:\SteamLibrary\steamapps\common\Half-Life\qconsole.log')

def test():
    assert physics()['host']['enabled'] and status()['input']['requested']
    run('HalfCraft Bridge',['open','save hc_respawn_check','toggle','click:180:440','wait:1'])
    start=LOG.stat().st_size
    run('HalfCraft Bridge',['open','kill','toggle','click:180:440','wait:2'])
    dead=physics(); assert not dead['host']['enabled'],dead
    run('HalfCraft Bridge',['open','hc_weapon_check','toggle'])
    text=LOG.read_bytes()[start:].decode('latin1')
    assert re.search(r'HC_WEAPON .*health=0\.0',text),text
    assert 'HC_PHYSICS OFF reason=native death/control or guest lost' in text,text
    run('HalfCraft Bridge',['open','load hc_respawn_check','toggle','wait:4','click:180:440'])
    deadline=time.monotonic()+15
    while time.monotonic()<deadline:
        p=physics(); c=status()
        if p['host']['enabled'] and p['guest']['active'] and c['input']['ready'] and c['input']['requested']: break
        time.sleep(.1)
    else: raise AssertionError((p,c))
    assert p['host']['correction']==0,p
    result=dict(result='PASS',death=dead,recovered=p,input=c['input'],recovery='native checkpoint load without re-enable commands')
    (ROOT/'local/respawn-results.json').write_text(json.dumps(result,indent=2))
    print(json.dumps(result))

if __name__=='__main__': test()
