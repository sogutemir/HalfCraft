"""Live HL-only mining, spinning drop snapshot, screenshot and vanilla pickup."""
import json
import time
from pathlib import Path
from client_status import status
from window import run
from verify_wall_jump import command
from physics_status import status as physics


def test():
    command('hc_look 45 180')
    before=status()
    assert before['input']['target'],before
    run('HalfCraft Bridge',['mouse:LEFT:2','wait:.3'])
    dropped=status()
    assert dropped['render']['item_count']>0,dropped
    old_ids={item['id'] for item in before['render']['items']}
    item=next(item for item in dropped['render']['items'] if item['id'] not in old_ids)
    assert item['kind']==1,item
    time.sleep(.3)
    animated=status()
    same=next(value for value in animated['render']['items'] if value['id']==item['id'])
    assert abs(same['yaw']-item['yaw'])>1,(item,same)
    floor=physics()['guest']['feet'][1]
    grounded=[]
    for _ in range(30):
        value=next(value for value in status()['render']['items'] if value['id']==item['id'])
        assert floor+.1<=value['position'][1]<=floor+.6,(floor,value)
        grounded.append(value); time.sleep(.1)
    run('HalfCraft Bridge',['shot:'+str(Path(__file__).resolve().parents[1]/'local/dropped-item-live.png')])
    command('hc_look 0 180')
    run('HalfCraft Bridge',['hold:W:.6','wait:1'])
    picked=status()
    assert all(value['id']!=item['id'] for value in picked['render']['items']),picked
    assert picked['input']['count']>dropped['input']['count'],(dropped,picked)
    result=dict(result='PASS',item=item,animated=same,grounded_samples=len(grounded),min_grounded_y=min(value['position'][1] for value in grounded),before=before,dropped=dropped,picked=picked)
    (Path(__file__).resolve().parents[1]/'local/dropped-items-results.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8')
    print(json.dumps(result))


if __name__=='__main__': test()
