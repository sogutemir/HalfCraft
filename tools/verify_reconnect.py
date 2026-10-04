"""Real normal exits/restarts, leaving both dedicated games connected at end."""
import json
import subprocess
import time
from pathlib import Path
from bridge_status import status
from halfcraft_protocol import Mapping,host_snapshot,process_alive,SHUTDOWN
from window import run

if __name__=='__main__':
    root=Path(__file__).resolve().parents[1]
    first=status(); assert first['connected'] and first['guest']['world'],first
    def wait(predicate,seconds=45):
        start=time.monotonic()
        while time.monotonic()-start<seconds:
            value=status()
            if predicate(value): return dict(seconds=time.monotonic()-start,status=value)
            time.sleep(.1)
        raise AssertionError('Reconnect timeout')
    run('HalfCraft Bridge',['close'])
    run('Minecraft* 26.3 - Singleplayer',['close-window'])
    guest_lost=wait(lambda s:not s['guest_alive'] and s['host']['state']==1)
    assert process_alive(first['host']['pid']),guest_lost
    deadline=time.monotonic()+30
    while process_alive(first['guest']['pid']):
        if time.monotonic()>deadline: raise AssertionError('Minecraft shutdown timeout')
        time.sleep(.1)
    subprocess.run(['powershell','-ExecutionPolicy','Bypass','-File',str(root/'tools/launch-dev.ps1')],check=True)
    guest_back=wait(lambda s:s['connected'] and s['guest']['world'] and s['guest']['pid']!=first['guest']['pid'])
    with Mapping() as mapping:
        run('HalfCraft Bridge',['open','quit'])
        host_lost=wait(lambda s:not s['host_alive'] and s['guest_alive'] and s['guest']['state']==2)
        assert host_snapshot(mapping)['state']==SHUTDOWN,host_snapshot(mapping)
        assert process_alive(guest_back['status']['guest']['pid']),host_lost
        deadline=time.monotonic()+15
        while process_alive(first['host']['pid']):
            if time.monotonic()>deadline: raise AssertionError('Half-Life shutdown timeout')
            time.sleep(.1)
        subprocess.run(['powershell','-ExecutionPolicy','Bypass','-File',str(root/'tools/launch-dev.ps1'),'-HostOnly'],check=True)
        host_back=wait(lambda s:s['connected'] and s['host']['player'] and s['host']['map']=='c1a0' and s['host']['pid']!=first['host']['pid'])
    assert host_back['status']['host']['session']!=first['host']['session'],host_back
    assert host_back['status']['connection_age_ms']<10000,host_back
    result=dict(result='PASS',initial=first,guest_lost=guest_lost,guest_reconnected=guest_back,host_lost=host_lost,host_reconnected=host_back)
    (root/'local'/'reconnect-results.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8')
    print(json.dumps(result))
