"""Live current-surface mining: release/idle safety, fivefold tick rate, stable UI frames."""
import json
import re
import struct
import time
from pathlib import Path
from client_status import read, status
from window import run

ROOT=Path(__file__).resolve().parents[1]
LOG=ROOT/'local/Prism/instances/HalfCraftBridge/.minecraft/logs/latest.log'

def diagnostic():
    data=read(r'Local\HalfCraft_interaction_v3',7824,0,7824)
    return dict(held=struct.unpack_from('<I',data,5220)[0],progress=struct.unpack_from('<f',data,5224)[0],
                completed=struct.unpack_from('<I',data,5232)[0],ticks=struct.unpack_from('<I',data,5236)[0],swing=struct.unpack_from('<I',data,5240)[0])

def test():
    run('HalfCraft Bridge',['wait:1'])
    before=diagnostic(); start=LOG.stat().st_size
    run('HalfCraft Bridge',['mouse:LEFT:6','wait:1'])
    after=diagnostic(); assert after['completed']>before['completed'],(before,after)
    assert after['held']==0 and after['swing']==0,after
    samples=[]
    for _ in range(40):
        d=diagnostic(); s=status()
        assert d['held']==0 and d['swing']==0 and d['completed']==after['completed'],d
        assert s['ui']['frame_age_ms']<500,s['ui']
        samples.append(s['ui']['frame_age_ms']); time.sleep(.05)
    text=LOG.read_bytes()[start:].decode('utf-8',errors='replace')
    records=re.findall(r'HC_ONEBLOCK source=Block\{minecraft:([^}]+)\}.*ticks=(\d+) speed=5',text)
    assert records,text
    result=dict(result='PASS',before=before,after=after,idle_samples=len(samples),max_ui_age_ms=max(samples),mining=records)
    (ROOT/'local/mining-speed-results.json').write_text(json.dumps(result,indent=2))
    print(json.dumps(result))

if __name__=='__main__': test()
