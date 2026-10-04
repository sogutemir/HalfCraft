"""Craft wooden pickaxe through real 3x3 menu. Requires >=2 logs in slot8, 4planks slot9."""
import json
from pathlib import Path
from client_status import status
from verify_inventory import cursor
from window import run

def click(x,y,button='LEFT'):
    cursor(x,y); run('HalfCraft Bridge',[f'mouse:{button}:.1','wait:.25'])
def test():
    assert status()['ui']['screen']
    # Recipe-book open layout: 3x3 x=718,772,826; y=186,240,294; output1000,240.
    click(1084,562)
    assert status()['ui']['carried_count']==4,status()
    click(718,186,'RIGHT'); click(718,240,'RIGHT'); click(1084,562)
    click(1000,240)
    assert status()['ui']['carried_count']==4,status()
    click(976,562)
    # One log becomes four additional planks.
    click(1030,562); click(718,186,'RIGHT'); click(1030,562); click(1000,240); click(1084,562)
    # Three planks along top; two sticks down middle.
    click(1084,562); click(718,186,'RIGHT'); click(772,186,'RIGHT'); click(826,186,'RIGHT'); click(1084,562)
    click(976,562); click(772,240,'RIGHT'); click(772,294,'RIGHT'); click(976,562)
    click(1000,240)
    result=status(); assert result['ui']['carried_count']==1,result
    click(922,562)
    run('HalfCraft Bridge',['shot:'+str(Path(__file__).resolve().parents[1]/'local/wooden-pickaxe-crafted.png'),'escape','hold:6:.1','wait:.4'])
    final=status(); assert final['input']['count']==1,final
    target=Path(__file__).resolve().parents[1]/'local/crafting-results.json'
    target.write_text(json.dumps(dict(result='PASS',checks=['2 planks to4sticks','log to4planks','3planks+2sticks to wooden pickaxe'],final=final),indent=2))
    print('Crafting PASS: sticks, planks, wooden pickaxe via vanilla menu')
if __name__=='__main__': test()
