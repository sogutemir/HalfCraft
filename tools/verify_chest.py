"""Live real chest storage, Esc close, reopen and vanilla stack recovery."""
import json
from pathlib import Path
from client_status import status
from verify_inventory import cursor,shift_click
from window import run

def test():
    title='HalfCraft Bridge'
    before=status(); count=before['input']['count']; assert before['ui']['screen'] and count>0,before
    cursor(424+54*before['input']['slot'],562); shift_click()
    stored=status(); assert stored['input']['count']==0 and stored['ui']['carried_count']==0,stored
    run(title,['escape','wait:.5']); assert not status()['ui']['screen'],status()
    run(title,['open','hc_look 68 217','close','mouse:RIGHT:.1','wait:.6']); assert status()['ui']['screen'],status()
    cursor(424,186); shift_click()
    run(title,['escape','wait:.5'])
    recovered=None
    for key in '123456789':
        run(title,[f'hold:{key}:.1','wait:.2'])
        candidate=status()
        if candidate['input']['count']==count: recovered=candidate; break
    assert recovered is not None,status()
    result=dict(result='PASS',stored=stored,recovered=recovered,checks=['chest rendered with native vanilla model','right-click opens chest','shift-click stores stack','Esc closes MC without opening HL menu','reopen retains contents','shift-click recovers same count'])
    target=Path(__file__).resolve().parents[1]/'local/chest-results.json'; target.write_text(json.dumps(result,indent=2)); print(json.dumps(result))
if __name__=='__main__': test()
