"""Vanilla furnace recipe; test fixture supplies eight cobblestone in hotbar1."""
from verify_crafting import click
from client_status import status
from window import run
from pathlib import Path
import json
def test():
    assert status()['ui']['screen']
    click(424,562); assert status()['ui']['carried_count']==8,status()
    for x,y in ((490,186),(544,186),(598,186),(490,240),(598,240),(490,294),(544,294),(598,294)):
        click(x,y,'RIGHT')
    click(772,240); assert status()['ui']['carried_count']==1,status()
    click(586,562)
    run('HalfCraft Bridge',['escape','hold:4:.1','wait:.4'])
    final=status(); assert final['input']['count']==1,final
    target=Path(__file__).resolve().parents[1]/'local/furnace-crafting-results.json'
    target.write_text(json.dumps(dict(result='PASS',fixture='eight cobblestone supplied for menu acceptance',final=final),indent=2))
    print('Furnace crafting PASS: eight cobblestone consumed, one furnace returned')
if __name__=='__main__': test()
