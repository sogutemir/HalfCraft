"""Real save/load, map/changelevel resumption and manual OFF persistence."""
import json
import argparse
import math
import time
from pathlib import Path
from window import run
from physics_status import status
from bridge_status import status as bridge

if __name__=='__main__':
    parser=argparse.ArgumentParser(); parser.add_argument('--walk',action='store_true'); args=parser.parse_args()
    results={}
    def command(text): run('HalfCraft Bridge',['open',text,'close'])
    def wait(name,enabled=True):
        deadline=time.monotonic()+15
        while time.monotonic()<deadline:
            p=status(); b=bridge()
            if b['connected'] and b['host']['player'] and b['host']['map']==name and p['host']['enabled']==enabled and p['guest']['active']==enabled:
                if enabled:
                    feet=p['guest']['feet']; target=(feet[0]*40,-feet[2]*40,feet[1]*40+36.03125)
                    if math.dist(b['host']['origin'],target)>.1:
                        time.sleep(.1); continue
                return dict(physics=p,bridge=b)
            time.sleep(.1)
        raise AssertionError(dict(map=name,expected_enabled=enabled,physics=p,bridge=b))
    command('hc_physics 0'); command('map c1a0'); wait('c1a0',False)
    command('hc_physics 1'); results['initial']=wait('c1a0')
    if args.walk:
        command('hc_input 1'); command('hc_look 0 180')
        run('HalfCraft Bridge',['hold:W:5','wait:3'])
        results['walk_transition']=wait('c1a0d')
        assert results['walk_transition']['physics']['guest']['feet'][0]<-45,results['walk_transition']
        correction=results['walk_transition']['physics']['host']['correction']
        for _ in range(50):
            value=wait('c1a0d'); assert value['bridge']['guest']['ground'],value
            assert value['physics']['host']['correction']==correction,value
            time.sleep(.1)
        result=dict(result='PASS',results=results,grounded_idle_samples=50)
        (Path(__file__).resolve().parents[1]/'local/walk-transition-results.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8')
        print(json.dumps(result)); raise SystemExit
    command('save hc_transition_acceptance'); time.sleep(1); results['save']=wait('c1a0')
    command('map c1a1'); results['map']=wait('c1a1')
    command('changelevel c1a0'); results['changelevel']=wait('c1a0')
    command('load hc_transition_acceptance'); time.sleep(1); results['load']=wait('c1a0')
    assert results['load']['physics']['host']['epoch']>results['initial']['physics']['host']['epoch'],results
    for _ in range(30): wait('c1a0'); time.sleep(.1)
    command('hc_physics 0'); results['off']=wait('c1a0',False)
    command('map c1a1'); results['off_map']=wait('c1a1',False)
    command('map c1a0'); results['final']=wait('c1a0',False)
    result=dict(result='PASS',results=results)
    (Path(__file__).resolve().parents[1]/'local/transition-results.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8')
    print(json.dumps(result))
