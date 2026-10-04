"""Input resumes, map-specific blocks stay separate, returning/save-load restores same mesh count."""
import json
import time
from pathlib import Path
from window import run
from client_status import status
from bridge_status import status as bridge

def command(text): run('HalfCraft Bridge',['open',text,'close'])
def wait(name):
    deadline=time.monotonic()+30
    while time.monotonic()<deadline:
        value=status(); b=bridge()
        if b['host']['map']==name and value['input']['enabled'] and value['render']['atlas_seq']==value['render']['atlas_ack'] and value['render']['width']:
            time.sleep(1); return status()
        time.sleep(.1)
    raise AssertionError((name,value,b))

if __name__=='__main__':
    command('hc_background 0'); command('hc_physics 0'); command('map c1a0'); time.sleep(3)
    command('hc_physics 1'); command('hc_input 1'); first=wait('c1a0')
    time.sleep(2); first=status(); assert first['render']['total_vertices']>0,first
    command('save hc_client_acceptance'); saved=wait('c1a0')
    assert saved['render']['total_vertices']==first['render']['total_vertices'],(first,saved)
    command('map c1a1'); other=wait('c1a1'); time.sleep(2); other=status()
    assert other['render']['total_vertices']==0,other
    command('changelevel c1a0'); returned=wait('c1a0'); time.sleep(2); returned=status()
    assert returned['render']['total_vertices']==first['render']['total_vertices'],(first,returned)
    command('load hc_client_acceptance'); loaded=wait('c1a0'); time.sleep(2); loaded=status()
    assert loaded['render']['total_vertices']==first['render']['total_vertices'],(first,loaded)
    assert loaded['input']['epoch']>first['input']['epoch'],(first,loaded)
    result=dict(result='PASS',first=first,saved=saved,other_map=other,returned=returned,loaded=loaded)
    (Path(__file__).resolve().parents[1]/'local/client-transition-results.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8')
    print(json.dumps(result))
