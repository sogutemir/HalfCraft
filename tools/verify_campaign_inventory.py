"""Live map-specific worlds must retain both selected log/plank stacks across campaign travel."""
import json
from pathlib import Path
from client_status import status
from window import run

def stacks():
    result=[]
    for key in ('1','5','6','8'):
        run('HalfCraft Bridge',[f'hold:{key}:.1','wait:.3'])
        result.append(status()['input']['count'])
    return result
def test():
    run('HalfCraft Bridge',['open','hc_physics 1','hc_input 1','close','wait:2'])
    before=stacks(); assert all(before),before
    samples=[]
    for map_name in ('c1a1','c1a0'):
        run('HalfCraft Bridge',['open',f'map {map_name}','close','wait:12','open','hc_physics 1','hc_input 1','close','wait:3'])
        counts=stacks(); assert counts==before,(map_name,before,counts)
        samples.append(dict(map=map_name,counts=counts,status=status()))
    result=dict(result='PASS',before=before,transitions=samples)
    target=Path(__file__).resolve().parents[1]/'local/campaign-inventory-results.json'
    target.write_text(json.dumps(result,indent=2)); print(json.dumps(result))
if __name__=='__main__': test()
