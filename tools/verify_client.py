"""Live HL-only movement, jump, focus reset, E guard and mailbox acknowledgements."""
import json
import time
from pathlib import Path
from window import run
from client_status import status
from physics_status import status as physics

def wait_active():
    deadline=time.monotonic()+10
    while time.monotonic()<deadline:
        value=status()
        if value['input']['enabled'] and value['input']['ready']: return value
        time.sleep(.1)
    raise AssertionError(value)

def test():
    run('HalfCraft Bridge',['open','hc_input 0','hc_physics 0','map c1a0','close','wait:3'])
    run('HalfCraft Bridge',['open','hc_physics 1','hc_input 1','hc_look 0 180','close','wait:2'])
    before=wait_active(); initial=physics()
    run('HalfCraft Bridge',['hold:W:.35','wait:.5'])
    walked=physics(); consumed=status()
    assert abs(walked['guest']['feet'][0]-initial['guest']['feet'][0])>.2,(initial,walked)
    assert consumed['input']['write']==consumed['input']['read'],consumed
    assert consumed['input']['write']>=before['input']['write']+2,consumed
    run('HalfCraft Bridge',['hold:E:.1','wait:.2'])
    guarded=wait_active(); assert guarded['input']['ready'],guarded
    run('HalfCraft Bridge',['hold:2:.1','wait:.2'])
    assert status()['input']['slot']==1,status()
    run('HalfCraft Bridge',['wheel:-1','wait:.2'])
    assert status()['input']['slot']==2,status()
    baseline=physics()['guest']['feet'][1]
    run('HalfCraft Bridge',['hold:SPACE:.1'])
    jumped=physics(); assert jumped['guest']['feet'][1]>baseline+.1,jumped
    run('HalfCraft Bridge',['wait:1'])
    run('Minecraft* 26.3 - Singleplayer',['wait:.4'])
    focus=status(); assert not focus['input']['enabled'],focus
    run('HalfCraft Bridge',['wait:.5'])
    resumed=wait_active()
    run('HalfCraft Bridge',['open','wait:.4'])
    console=status(); assert not console['input']['enabled'],console
    run('HalfCraft Bridge',['close','wait:.4'])
    wait_active()
    run('HalfCraft Bridge',['escape','wait:.4'])
    menu=status(); assert not menu['input']['enabled'],menu
    run('HalfCraft Bridge',['escape','wait:.4'])
    wait_active()
    samples=[]
    for _ in range(50):
        value=physics(); assert value['host']['enabled'] and value['guest']['active'],value
        samples.append(value); time.sleep(.1)
    render=status()['render']
    assert render['width']>0 and render['atlas_seq']==render['atlas_ack'],render
    run('HalfCraft Bridge',['open','hc_input 0','close','wait:.5'])
    off=status(); assert not off['input']['enabled'] and not off['input']['requested'],off
    result=dict(result='PASS',before=before,walked=walked,jumped=jumped,E_guard=guarded,focus=focus,console=console,menu=menu,resumed=resumed,active_samples=len(samples),render=render,off=off)
    path=Path(__file__).resolve().parents[1]/'local/client-results.json'
    path.write_text(json.dumps(result,indent=2)+'\n',encoding='utf8')
    print(json.dumps(result))

if __name__=='__main__': test()
