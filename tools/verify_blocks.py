"""Live HL-only Survival place, collide, jump onto block and break. Dirt must be in slot 2."""
import json
import time
from pathlib import Path
from window import run
from client_status import status
from physics_status import status as physics

def command(text): run('HalfCraft Bridge',['open',text,'close','wait:.3'])

def test():
    command('hc_input 1'); command('hc_look 45 180')
    run('HalfCraft Bridge',['hold:2:.1','wait:.5'])
    before=status(); start=physics()
    assert before['input']['enabled'] and before['input']['count']>0,before
    run('HalfCraft Bridge',['mouse:RIGHT:.1','wait:1'])
    placed=status(); after=physics()
    assert placed['input']['count']==before['input']['count']-1,(before,placed)
    assert placed['render']['mesh_seq']>before['render']['mesh_seq'],(before,placed)
    assert placed['render']['mesh_ack']==placed['render']['mesh_seq'],placed
    assert placed['input']['target'],placed
    box=placed['input']['bounds']
    assert abs(box[4]-box[1]-1)<.01,box
    command('hc_look 0 180')
    run('HalfCraft Bridge',['hold:W:.7','wait:.3'])
    wall=physics()
    assert wall['guest']['feet'][0]>=box[3]+.29,(box,wall)
    run('HalfCraft Bridge',['hold:W+SPACE:.35','wait:1'])
    top=physics()
    assert abs(top['guest']['feet'][1]-box[4])<.05,(box,top)
    assert top['host']['enabled'] and top['guest']['active'],top
    command('hc_look 89 180')
    run('HalfCraft Bridge',['mouse:LEFT:2','wait:1'])
    broken=status(); landed=physics()
    assert broken['render']['mesh_seq']>placed['render']['mesh_seq'],(placed,broken)
    assert landed['guest']['feet'][1]<top['guest']['feet'][1]-.5,(top,landed)
    assert landed['host']['enabled'] and landed['guest']['active'],landed
    result=dict(result='PASS',before=before,start=start,placed=placed,wall=wall,top=top,broken=broken,landed=landed)
    (Path(__file__).resolve().parents[1]/'local/blocks-results.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8')
    print(json.dumps(result))

if __name__=='__main__': test()
