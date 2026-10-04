"""Live Ctrl crouch pose, camera height, slower vanilla movement and release."""
import json
from pathlib import Path
from physics_status import status
from verify_wall_jump import sample_actions,command


def test():
    command('hc_look 0 180')
    start=status()
    samples=sample_actions(['hold:CTRL+W:.3','wait:.5'])
    crouched=[s for s in samples if s['guest']['crouched']]
    assert crouched,samples
    low=min(s['guest']['eye_height'] for s in crouched)
    assert low<1.3,(low,crouched)
    assert all(s['host']['enabled'] and s['guest']['active'] for s in samples)
    for s in crouched:
        feet=s['guest']['feet']; host=s['host']['feet']
        assert abs(feet[1]-host[1])<.05,(s,start)
    end=status()
    assert not end['guest']['crouched'] and end['guest']['eye_height']>1.5,end
    assert abs(end['guest']['feet'][1]-start['guest']['feet'][1])<.05,(start,end)
    assert end['host']['correction']==start['host']['correction'],(start,end)
    result=dict(result='PASS',start=start,min_eye_height=low,crouch_samples=len(crouched),end=end)
    (Path(__file__).resolve().parents[1]/'local/crouch-results.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8')
    print(json.dumps(result))


if __name__=='__main__': test()
