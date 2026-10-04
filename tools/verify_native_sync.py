"""Read-only live native movement/expanded-height and monotonic overlay check."""
import json
import math
import struct
import time
from pathlib import Path
from client_status import read,status
from physics_status import status as physics

ROOT=Path(__file__).resolve().parents[1]
LOG=ROOT/'local/Prism/instances/HalfCraftBridge/.minecraft/logs/latest.log'

def test():
    start=LOG.stat().st_size
    frames=[]; heights=[]; errors=[]
    for _ in range(100):
        p=physics(); ui=status()['ui']
        header=read(r'Local\HalfCraft_collision_v1',4288,0,128)
        assert struct.unpack_from('<I',header,104)[0]==1, 'Native movement not enabled'
        assert p['host']['enabled'] and p['guest']['active'],p
        assert p['host']['epoch']==p['guest']['epoch'] and p['host']['correction']==0,p
        assert ui['frame_age_ms']<500,ui
        heights.append(p['guest']['feet'][1]); frames.append(ui['frame_id'])
        errors.append(math.dist(p['host']['feet'],p['guest']['feet']))
        time.sleep(.05)
    assert all(b>=a for a,b in zip(frames,frames[1:])),frames
    text=LOG.read_bytes()[start:].decode('utf-8',errors='replace')
    assert 'moved wrongly!' not in text and 'HalfCraft physics disabled' not in text,text
    result=dict(result='PASS',samples=len(frames),min_y=min(heights),max_y=max(heights),max_pose_lag_blocks=max(errors),first_frame=frames[0],last_frame=frames[-1],fresh_movement_warnings=0)
    (ROOT/'local/native-sync-results.json').write_text(json.dumps(result,indent=2))
    print(json.dumps(result))

if __name__=='__main__': test()
