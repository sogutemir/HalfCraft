"""Live native damage/fall/heal/repair check. Console must already be open.
Probe changes current player state; run from safe checkpoint with health/repair headroom.
"""
import struct
import time
from client_status import read
from window import run

def status():
    data=read(r'Local\HalfCraft_vitals_v1',4224,0,4224)
    assert data is not None, 'Vitals mapping missing'
    assert struct.unpack_from('<3I',data,4)==(0x56435248,1,4224)
    return dict(life=struct.unpack_from('<I',data,32)[0],write=struct.unpack_from('<I',data,40)[0],
                ack=struct.unpack_from('<I',data,84)[0],ready=struct.unpack_from('<I',data,92)[0],
                health=struct.unpack_from('<f',data,96)[0],repair=struct.unpack_from('<I',data,104)[0])

def probe(kind,amount):
    before=status()
    run('HalfCraft Bridge',[f'hc_vitals_probe {kind} {amount}'])
    until=time.monotonic()+5
    while time.monotonic()<until:
        current=status()
        if current['write']!=before['write'] and current['ack']==current['write']: return current
        time.sleep(.1)
    raise AssertionError(('Vitals event not acknowledged',before,status()))

if __name__=='__main__':
    import argparse
    parser=argparse.ArgumentParser(); parser.add_argument('kind',choices=['status','damage','fall','blast','slash','shock','bullet','heal','repair']); parser.add_argument('amount',type=float,nargs='?',default=10)
    args=parser.parse_args()
    if args.kind=='status': print(status())
    else:
        before=status(); after=probe(args.kind,args.amount)
        if args.kind in ('damage','fall','blast','slash','shock','bullet'): assert after['health']<before['health'],(before,after)
        elif args.kind=='heal': assert after['health']>before['health'],(before,after)
        else: assert after['repair']<before['repair'],(before,after)
        print('Native vitals PASS',args.kind,before,after)
