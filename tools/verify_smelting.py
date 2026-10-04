"""Real furnace cooks two supplied raw iron with one coal; no bridge recipe logic."""
import json
from pathlib import Path
from verify_crafting import click
from client_status import status
from window import run
def test():
    assert status()['ui']['screen']
    click(478,562); assert status()['ui']['carried_count']==2,status()
    click(568,186)
    click(532,562); assert status()['ui']['carried_count']==1,status()
    click(568,294)
    run('HalfCraft Bridge',['wait:22','shot:'+str(Path(__file__).resolve().parents[1]/'local/furnace-smelted.png')])
    click(748,240); assert status()['ui']['carried_count']==2,status()
    click(424,562)
    run('HalfCraft Bridge',['escape','hold:1:.1','wait:.4'])
    final=status(); assert final['input']['count']==2,final
    target=Path(__file__).resolve().parents[1]/'local/smelting-results.json'
    target.write_text(json.dumps(dict(result='PASS',fixture='raw iron and coal supplied for isolated furnace acceptance',final=final),indent=2))
    print('Smelting PASS: vanilla furnace produced two iron ingots')
if __name__=='__main__': test()
