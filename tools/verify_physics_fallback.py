"""Real native-save, map-transition, hull rejection and peer-loss fallback checks."""
import json
import subprocess
import time
from pathlib import Path
from physics_status import status
from bridge_status import status as bridge
from halfcraft_protocol import process_alive
from window import run

if __name__=='__main__':
    root=Path(__file__).resolve().parents[1]; results={}
    def command(text): run('HalfCraft Bridge',['open',text,'close'])
    def enable(): command('hc_physics 1'); time.sleep(2); value=status(); assert value['host']['enabled'] and value['guest']['active'],value
    enable(); command('save hc_physics_acceptance'); time.sleep(1)
    results['native_save']=status(); assert results['native_save']['host']['enabled'] and results['native_save']['guest']['active'],results['native_save']
    enable(); command('map c1a1'); time.sleep(2)
    results['map_transition']=dict(physics=status(),bridge=bridge()); assert status()['host']['enabled'] and status()['guest']['active'] and bridge()['host']['map']=='c1a1',results['map_transition']
    command('map c1a0'); time.sleep(2); enable()
    run('Minecraft* 26.3 - Singleplayer',['hold:S:4']); time.sleep(.2)
    results['hull_wall']=dict(physics=status(),bridge=bridge()); assert status()['host']['enabled'] and status()['guest']['active'],results['hull_wall']
    log=Path(r'K:\SteamLibrary\steamapps\common\Half-Life\qconsole.log').read_text(errors='replace')
    assert 'HC_PHYSICS BLOCKED correction=' in log
    command('map c1a0'); time.sleep(2); enable()
    initial=bridge(); run('Minecraft* 26.3 - Singleplayer',['close-window']); time.sleep(2)
    results['guest_exit']=dict(physics=status(),bridge=bridge()); assert not status()['host']['enabled'] and process_alive(initial['host']['pid'])
    deadline=time.monotonic()+30
    while process_alive(initial['guest']['pid']):
        if time.monotonic()>deadline: raise AssertionError('Guest exit timeout')
        time.sleep(.1)
    subprocess.run(['powershell','-ExecutionPolicy','Bypass','-File',str(root/'tools/launch-dev.ps1')],check=True)
    deadline=time.monotonic()+45
    while not (bridge()['connected'] and bridge()['guest']['pid']!=initial['guest']['pid'] and bridge()['guest']['world']):
        if time.monotonic()>deadline: raise AssertionError('Guest restart timeout')
        time.sleep(.2)
    enable(); before=bridge(); run('HalfCraft Bridge',['open','quit']); time.sleep(2)
    results['host_exit']=bridge(); assert not results['host_exit']['host_alive'] and results['host_exit']['guest_alive']
    assert results['host_exit']['guest']['position'][1]<-50,results['host_exit'] # Dedicated native MC floor restored.
    subprocess.run(['powershell','-ExecutionPolicy','Bypass','-File',str(root/'tools/launch-dev.ps1'),'-HostOnly'],check=True)
    time.sleep(4); results['final']=bridge(); assert results['final']['connected'] and not status()['host']['enabled']
    (root/'local'/'physics-fallback-results.json').write_text(json.dumps(results,indent=2)+'\n',encoding='utf8')
    print(json.dumps(dict(result='PASS',results=results)))
