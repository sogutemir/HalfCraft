"""Live c1a0 authored-log mining, sustained BSP resting and vanilla pickup."""
import json
import time
from pathlib import Path
from client_status import status
from physics_status import status as physics
from window import run

def test():
    title='HalfCraft Bridge'
    run(title,['open','hc_look 25 180','close','wait:.5'])
    before=status(); assert before['input']['target'],before
    ids={item['id'] for item in before['render']['items']}
    run(title,['mouse:LEFT:3.5','wait:.5'])
    mined=status()
    item=next(item for item in mined['render']['items'] if item['id'] not in ids)
    floor=physics()['guest']['feet'][1]
    samples=[]
    for _ in range(30):
        value=next(value for value in status()['render']['items'] if value['id']==item['id'])
        assert floor+.05<=value['position'][1]<=floor+.7,(floor,value)
        samples.append(value); time.sleep(.1)
    run(title,['hold:W:.35','wait:.5'])
    picked=status()
    assert all(value['id']!=item['id'] for value in picked['render']['items']),picked
    assert picked['input']['count']>before['input']['count'],picked
    result=dict(result='PASS',before_count=before['input']['count'],after_count=picked['input']['count'],item=item,grounded_samples=len(samples),min_y=min(value['position'][1] for value in samples),max_y=max(value['position'][1] for value in samples),picked=picked)
    target=Path(__file__).resolve().parents[1]/'local/resources-results.json'
    target.write_text(json.dumps(result,indent=2)); print(json.dumps(result))
if __name__=='__main__': test()
