"""Hidden GUI/gameplay soak; JVM heap measured after GC, no debug-gizmo accumulation."""
import json
import re
import subprocess
import time
from pathlib import Path
from client_status import status
from window import run,user
ROOT=Path(__file__).resolve().parents[1]
JCMD=Path(r'C:\Users\sogut\jdk\jdk-25.0.1+8\bin\jcmd.exe')
def heap(pid):
    subprocess.run([str(JCMD),str(pid),'GC.run'],check=True,capture_output=True)
    output=subprocess.check_output([str(JCMD),str(pid),'GC.heap_info'],text=True)
    match=re.search(r'used (\d+)K',output); assert match,output
    return int(match[1])*1024
def test():
    run('HalfCraft Bridge',['open','hc_background 1','hc_physics 1','hc_input 1','close','wait:1'])
    pid=status()['input']['guest_pid']; before=heap(pid)
    guest=user.FindWindowW(None,'Minecraft* 26.3 - Singleplayer'); assert guest
    samples=[]
    for phase in range(2):
        run('HalfCraft Bridge',['hold:I:.1','wait:.5'])
        for _ in range(300):
            value=status()
            assert value['input']['enabled'] and value['render']['enabled'] and value['ui']['frame_age_ms']<1000,value
            assert not user.IsWindowVisible(guest)
            samples.append(value['ui']['frame_age_ms']); time.sleep(.1)
    after=heap(pid)
    assert after-before<256*1024*1024,(before,after)
    result=dict(result='PASS',samples=len(samples),before_live_bytes=before,after_live_bytes=after,max_ui_age_ms=max(samples),guest_pid=pid)
    (ROOT/'local/memory-results.json').write_text(json.dumps(result,indent=2)); print(json.dumps(result))
if __name__=='__main__': test()
