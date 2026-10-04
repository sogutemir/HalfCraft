"""Native hull rejection must correct Minecraft without turning physics OFF."""
import json
import time
from pathlib import Path
from window import run
from physics_status import status
from bridge_status import status as bridge

if __name__=='__main__':
    run('HalfCraft Bridge',['open','hc_physics 0','map c1a0','close','wait:3'])
    run('HalfCraft Bridge',['open','hc_physics 1','close','wait:2'])
    before=status(); assert before['host']['enabled'] and before['guest']['active'],before
    run('Minecraft* 26.3 - Singleplayer',['hold:S:4','wait:1'])
    wall=status(); assert wall['host']['enabled'] and wall['guest']['active'],wall
    assert wall['guest']['correction_ack']==wall['host']['correction'],wall
    samples=[]
    for _ in range(50):
        value=status(); assert value['host']['enabled'] and value['guest']['active'],value
        samples.append(value); time.sleep(.1)
    run('Minecraft* 26.3 - Singleplayer',['hold:W:1','wait:.5'])
    away=status(); assert away['host']['enabled'] and away['guest']['active'],away
    assert abs(away['guest']['feet'][0]-wall['guest']['feet'][0])>.2,away
    run('HalfCraft Bridge',['open','hc_physics 0','close','wait:1'])
    off=status(); assert not off['host']['enabled'] and not off['guest']['active'],off
    result=dict(result='PASS',before=before,wall=wall,active_samples=len(samples),away=away,off=off,bridge=bridge())
    (Path(__file__).resolve().parents[1]/'local/wall-results.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8')
    print(json.dumps(result))
