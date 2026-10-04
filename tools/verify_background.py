"""Hidden Minecraft keeps physics/input/render live; disabling forwarding restores its window."""
import ctypes
import json
import time
from pathlib import Path
from window import run,user
from client_status import status
from physics_status import status as physics

if __name__=='__main__':
    run('HalfCraft Bridge',['open','hc_background 1','hc_physics 1','hc_input 1','hc_look 0 180','close','wait:3'])
    guest=user.FindWindowW(None,'Minecraft* 26.3 - Singleplayer')
    assert guest and not user.IsWindowVisible(guest),'Launch with -Background first'
    before=physics(); run('HalfCraft Bridge',['hold:S:.3','wait:.5']); moved=physics()
    assert abs(moved['guest']['feet'][0]-before['guest']['feet'][0])>.1,(before,moved)
    samples=[]
    for _ in range(50):
        value=status(); p=physics()
        assert value['input']['enabled'] and value['render']['enabled'] and p['guest']['active'],(value,p)
        assert not user.IsWindowVisible(guest)
        samples.append(value); time.sleep(.1)
    run('HalfCraft Bridge',['open','hc_input 0','close','wait:.5'])
    assert user.IsWindowVisible(guest),'MC window did not return after hc_input 0'
    assert not status()['input']['requested'],status()
    result=dict(result='PASS',before=before,moved=moved,hidden_samples=len(samples),last=samples[-1],off=status())
    (Path(__file__).resolve().parents[1]/'local/background-results.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8')
    print(json.dumps(result))
