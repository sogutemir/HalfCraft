"""Real activation: stable grounded feet, no upward launch, OFF position restoration."""
import json
import math
import time
from pathlib import Path
from bridge_status import status
from physics_status import status as physics
from window import run

def command(text): run('HalfCraft Bridge',['open',text,'close'])
if __name__=='__main__':
    command('hc_physics 0'); time.sleep(1); before=status()['guest']['position']
    command('hc_physics 1'); time.sleep(2)
    samples=[]; start=time.monotonic()
    while time.monotonic()-start<5:
        value=status(); p=physics()
        assert p['host']['enabled'] and p['guest']['active'],p
        assert value['guest']['ground'],value
        assert abs(value['guest']['position'][1]-p['host']['feet'][1])<.01,value
        samples.append(value); time.sleep(.1)
    assert max(v['guest']['position'][1] for v in samples)-min(v['guest']['position'][1] for v in samples)<.01
    command('hc_physics 0'); time.sleep(1); after=status()['guest']['position']
    assert math.dist(before,after)<.1,(before,after)
    result=dict(result='PASS',samples=len(samples),feet=samples[0]['guest']['position'],onGround=True,before=before,restored=after)
    (Path(__file__).resolve().parents[1]/'local/spawn-results.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8')
    print(json.dumps(result))
