"""Live vanilla inventory checks. Requires Survival, >=2 selected items, 1280x720, GUI scale 3."""
import json
import time
from pathlib import Path
from client_status import status
from window import run, user

TITLE='HalfCraft Bridge'
def shift_click():
    user.keybd_event(0x10,0,0,0); time.sleep(.2)
    try:
        user.mouse_event(2,0,0,0,0); time.sleep(.1); user.mouse_event(4,0,0,0,0); time.sleep(.4)
    finally: user.keybd_event(0x10,0,2,0)
    time.sleep(.2)
def cursor(x,y):
    run(TITLE,['open',f'hc_ui_cursor {x} {y}','toggle','click:180:440','wait:.5'])
def test():
    before=status()
    assert before['input']['count']>=2,before
    if not before['ui']['screen']: run(TITLE,['hold:I:.1','wait:.6'])
    assert status()['ui']['screen']
    slot=before['input']['slot']; x=424+54*slot
    cursor(x,562)
    count=status()['input']['count']
    run(TITLE,['mouse:RIGHT:.1','wait:.5'])
    split=status()
    assert split['ui']['carried_count']==(count+1)//2,split
    assert split['input']['count']==count//2,split
    run(TITLE,['mouse:LEFT:.1','wait:.5'])
    assert status()['ui']['carried_count']==0
    assert status()['input']['count']==count
    yaw,pitch=status()['input']['yaw'],status()['input']['pitch']
    run(TITLE,['move:20:0','hold:E:.1','wait:.5'])
    assert status()['ui']['screen']
    assert (status()['input']['yaw'],status()['input']['pitch'])==(yaw,pitch)
    cursor(x,562)
    shift_click()
    assert status()['input']['count']==0,status()
    cursor(424,386)
    shift_click()
    assert status()['input']['count']==count,status()
    run(TITLE,['hold:I:.1','wait:.5'])
    assert not status()['ui']['screen']
    run(TITLE,['hold:E:.1','wait:.3'])
    assert not status()['ui']['screen']
    run(TITLE,['hold:I:.1','wait:.5','shot:'+str(Path(__file__).resolve().parents[1]/'local/inventory-live.png')])
    final=status()
    assert final['ui']['screen'] and final['input']['count']==count,final
    result=dict(result='PASS',split=split,final=final,checks=['I open/close','right-click split','left-click merge','shift quick-move and restore','screen E stays in GUI','gameplay E does not open inventory','mouse does not rotate camera'])
    target=Path(__file__).resolve().parents[1]/'local/inventory-results.json'
    target.write_text(json.dumps(result,indent=2)); print(json.dumps(result))
if __name__=='__main__': test()
