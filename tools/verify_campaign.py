"""Real observation-only bridge regression, isolated save slot, dedicated runtime only."""
import json
import re
import time
from pathlib import Path
from bridge_status import status
from window import run

if __name__=='__main__':
    root=Path(__file__).resolve().parents[1]
    log=Path(r'K:\SteamLibrary\steamapps\common\Half-Life\qconsole.log')
    before=log.stat().st_size
    def command(text): run('HalfCraft Bridge',['open',text,'close','wait:1'])
    def wait_map(name):
        deadline=time.monotonic()+15
        while time.monotonic()<deadline:
            value=status()
            if value['connected'] and value['host']['player'] and value['host']['map']==name: return value
            time.sleep(.2)
        raise AssertionError('Map/link not ready: '+name)
    command('map c1a0'); wait_map('c1a0')
    command('developer 1'); command('hc_bridge_check'); time.sleep(6); command('hc_bridge_check')
    command('save hc_bridge_acceptance'); saved=wait_map('c1a0')
    command('map c1a1'); alternate=wait_map('c1a1'); time.sleep(5)
    guest_log=root/'local/Prism/instances/HalfCraftBridge/.minecraft/logs/latest.log'
    assert 'HC host map=c1a1 player=true' in guest_log.read_text(encoding='utf8',errors='replace'),'Minecraft did not log c1a1'
    command('map c1a0'); returned=wait_map('c1a0')
    command('load hc_bridge_acceptance'); restored=wait_map('c1a0')
    command('hc_bridge_check'); time.sleep(6); command('hc_bridge_check')
    text=log.read_bytes()[before:].decode('utf8',errors='replace')
    rows=re.findall(r'HC_REGRESSION map=c1a0 npcs=(\d+) scripts=(\d+) doors=(\d+) npc_changes=(\d+) door_changes=(\d+) scripted_npc_changes=(\d+)',text)
    assert len(rows)>=4,text
    assert all(int(row[0])>0 and int(row[1])>0 and int(row[2])>0 for row in rows),rows
    assert int(rows[1][3])>0 and int(rows[1][5])>0,rows
    assert int(rows[-1][3])>0 and int(rows[-1][5])>0,rows
    assert 'Saving game to SAVE\\hc_bridge_acceptance.sav' in text,text
    assert 'Loading game from SAVE\\hc_bridge_acceptance.sav' in text,text
    assert 'Host_Error' not in text,text
    result=dict(result='PASS',activity=rows,saved=saved,alternate=alternate,returned=returned,restored=restored)
    (root/'local'/'campaign-results.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8')
    (root/'local'/'campaign-console.txt').write_text(text,encoding='utf8')
    print(json.dumps(result))
