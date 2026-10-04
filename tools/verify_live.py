"""Measure real two-game connection; keep raw samples outside Git."""
import argparse
import json
import time
from pathlib import Path
from bridge_status import status

if __name__=='__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('--seconds',type=float,default=65)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    if args.seconds<60: parser.error('Acceptance requires at least 60 seconds')
    samples=[]; started=time.monotonic()
    while True:
        sample=status()
        assert sample['connected'],sample
        assert sample['host']['state']==3 and sample['guest']['state']==3,sample
        assert sample['host']['map']=='c1a0' and sample['host']['player'],sample
        assert sample['guest']['world'],sample
        sample['elapsed_seconds']=time.monotonic()-started
        samples.append(sample)
        if sample['elapsed_seconds']>=args.seconds: break
        time.sleep(1)
    args.output.write_text(json.dumps(samples,indent=2)+'\n',encoding='utf8')
    print(json.dumps(dict(result='PASS',seconds=samples[-1]['elapsed_seconds'],samples=len(samples),
        host_pid=samples[-1]['host']['pid'],guest_pid=samples[-1]['guest']['pid'],
        max_host_age_ms=max(s['host_age_ms'] for s in samples),
        max_guest_age_ms=max(s['guest_age_ms'] for s in samples),
        host_positions=len({tuple(s['host']['origin']) for s in samples}),
        guest_positions=len({tuple(s['guest']['position']) for s in samples}),
        last=samples[-1])))
