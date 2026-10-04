"""Real 3x3 menu: three cobblestone and two sticks produce one stone pickaxe."""
from verify_crafting import click
from client_status import status
from window import run
from pathlib import Path
import json

def test():
    assert status()['ui']['screen']
    click(424,562); assert status()['ui']['carried_count']>=3,status()
    for x in (490,544,598): click(x,186,'RIGHT')
    click(424,562)
    click(748,562); assert status()['ui']['carried_count']==2,status()
    click(544,240,'RIGHT'); click(544,294,'RIGHT')
    click(772,240); assert status()['ui']['carried_count']==1,status()
    click(640,562)
    run('HalfCraft Bridge',['escape','hold:5:.1','wait:.4'])
    final=status(); assert final['input']['count']==1,final
    target=Path(__file__).resolve().parents[1]/'local/stone-pickaxe-results.json'
    target.write_text(json.dumps(dict(result='PASS',final=final),indent=2))
    print('Stone pickaxe PASS: vanilla recipe consumed three cobblestone and two sticks')
if __name__=='__main__': test()
