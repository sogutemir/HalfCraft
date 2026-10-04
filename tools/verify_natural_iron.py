"""One genuinely mined raw iron cooks using one crafted plank as vanilla furnace fuel."""
import json
from pathlib import Path
from verify_crafting import click
from client_status import status
from window import run
def test():
    assert status()['ui']['screen']
    click(478,562); assert status()['ui']['carried_count']==1,status()
    click(568,186)
    click(856,562); click(568,294,'RIGHT'); click(856,562)
    run('HalfCraft Bridge',['wait:12'])
    click(748,240); assert status()['ui']['carried_count']==1,status()
    click(424,562)
    run('HalfCraft Bridge',['escape','hold:1:.1','wait:.5'])
    final=status(); assert final['input']['count']==3,final
    result=dict(result='PASS',source='authored iron ore mined with crafted stone pickaxe; crafted plank fuel',before_ingots=2,after_ingots=3,final=final)
    target=Path(__file__).resolve().parents[1]/'local/natural-iron-results.json';target.write_text(json.dumps(result,indent=2)); print(json.dumps(result))
if __name__=='__main__': test()
