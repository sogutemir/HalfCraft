"""Real BSP floor, vanilla walking/jump, puppet round trip and OFF restoration."""
import json
import argparse
import math
import time
from pathlib import Path
from bridge_status import status as bridge
from physics_status import status
from window import run

if __name__=='__main__':
    parser=argparse.ArgumentParser(); parser.add_argument('--seconds',type=float,default=0); args=parser.parse_args()
    root=Path(__file__).resolve().parents[1]; samples=[]
    def command(text): run('HalfCraft Bridge',['open',text,'close'])
    command('hc_physics 0'); time.sleep(1); before=bridge()
    command('hc_physics 1'); time.sleep(2)
    def sample():
        p=status(); b=bridge(); value=dict(physics=p,bridge=b); samples.append(value)
        assert p['host']['enabled'] and p['guest']['active'],p
        assert p['solid_samples']>0,p
        target=(p['guest']['feet'][0]*40,-p['guest']['feet'][2]*40,p['guest']['feet'][1]*40+36.03125)
        # Latest-value sampling differs during movement; GoldSrc must follow within bounded displacement.
        assert math.dist(b['host']['origin'],target)<40,value
        return value
    initial=sample(); start=initial['bridge']['guest']['position']
    run('Minecraft* 26.3 - Singleplayer',['hold:W:0.5'])
    time.sleep(.3); moved=sample()
    assert math.dist(start,moved['bridge']['guest']['position'])>.2,moved
    floor=moved['bridge']['guest']['position'][1]
    run('Minecraft* 26.3 - Singleplayer',['hold:SPACE:0.1'])
    deadline=time.monotonic()+2
    while time.monotonic()<deadline: sample(); time.sleep(.04)
    heights=[s['bridge']['guest']['position'][1] for s in samples]
    assert max(heights)>floor+.5,heights
    assert abs(heights[-1]-floor)<.01 and samples[-1]['bridge']['guest']['ground'],samples[-1]
    settled=sample()
    assert math.dist(settled['bridge']['host']['origin'],(settled['physics']['guest']['feet'][0]*40,-settled['physics']['guest']['feet'][2]*40,settled['physics']['guest']['feet'][1]*40+36.03125))<.1,settled
    deadline=time.monotonic()+args.seconds
    while time.monotonic()<deadline: sample(); time.sleep(.2)
    command('hc_physics 0'); time.sleep(1); off=status(); restored=bridge()
    assert not off['host']['enabled'] and not off['guest']['active'],off
    assert math.dist(before['guest']['position'],restored['guest']['position'])<.1,restored
    result=dict(result='PASS',hold_seconds=args.seconds,jump_height=max(heights)-floor,walk_distance=math.dist(start,moved['bridge']['guest']['position']),before=before,samples=samples,off=off,restored=restored)
    (root/'local'/'physics-results.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8')
    print(json.dumps(dict(result='PASS',hold_seconds=args.seconds,jump_height=result['jump_height'],walk_distance=result['walk_distance'],samples=len(samples),initial=initial,off=off)))
