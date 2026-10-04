"""Native pistol firing/reload with bridge active and no Minecraft block target."""
import json
import re
from pathlib import Path
from client_status import status
from window import run

LOG=Path(r'K:\SteamLibrary\steamapps\common\Half-Life\qconsole.log')
def clip():
    before=LOG.stat().st_size
    run('HalfCraft Bridge',['open','hc_weapon_check','close'])
    with LOG.open('rb') as source: source.seek(before); text=source.read().decode('latin1')
    matches=re.findall(r'HC_WEAPON name=weapon_9mmhandgun clip=(\d+)',text)
    assert matches,text
    return int(matches[-1])
def test():
    assert status()['input']['enabled'] and not status()['input']['target'],status()
    before=clip(); assert before>2,before
    run('HalfCraft Bridge',['mouse:LEFT:.5','wait:.5'])
    after=clip(); assert after<before,(before,after)
    run('HalfCraft Bridge',['hold:R:.1','wait:2'])
    reloaded=clip(); assert reloaded>after,(after,reloaded)
    result=dict(result='PASS',before=before,after=after,reloaded=reloaded)
    target=Path(__file__).resolve().parents[1]/'local/native-combat-results.json'; target.write_text(json.dumps(result,indent=2)); print(json.dumps(result))
if __name__=='__main__': test()
