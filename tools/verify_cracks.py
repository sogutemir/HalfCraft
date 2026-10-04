"""Live vanilla break stages and cancellation, with HL screenshot during partial mining."""
import json
import ctypes
import threading
import time
from pathlib import Path
from client_status import status
from window import run,user,w,ImageGrab
from verify_wall_jump import command


def test():
    command('hc_look 45 180')
    before=status()
    assert before['input']['target'],before
    samples=[]; errors=[]
    shot=Path(__file__).resolve().parents[1]/'local/cracks-live.png'
    captured=False
    def action():
        try: run('HalfCraft Bridge',['mouse:LEFT:.2','wait:.7'])
        except Exception as error: errors.append(error)
    worker=threading.Thread(target=action); worker.start()
    while worker.is_alive():
        sample=status(); samples.append(sample)
        if not captured and sample['render']['crack_stages'] and max(sample['render']['crack_stages'])>=2:
            bounds=w.RECT(); user.GetWindowRect(user.FindWindowW(None,'HalfCraft Bridge'),ctypes.byref(bounds))
            ImageGrab.grab(bbox=(bounds.left,bounds.top,bounds.right,bounds.bottom)).save(shot)
            captured=True
        time.sleep(.01)
    worker.join()
    if errors: raise errors[0]
    stages=sorted({stage for s in samples for stage in s['render']['crack_stages']})
    assert len(stages)>=2,stages
    assert samples[-1]['render']['crack_count']==0,samples[-1]
    assert samples[-1]['render']['total_vertices']==before['render']['total_vertices'],(before,samples[-1])
    run('HalfCraft Bridge',['mouse:LEFT:2','wait:1'])
    end=status()
    assert end['render']['total_vertices']<before['render']['total_vertices'],(before,end)
    assert end['render']['crack_count']==0,end
    result=dict(result='PASS',stages=stages,before=before,end=end)
    (Path(__file__).resolve().parents[1]/'local/cracks-results.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8')
    print(json.dumps(result))


if __name__=='__main__': test()
