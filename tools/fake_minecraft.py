import argparse
import time
from halfcraft_protocol import *

if __name__=='__main__':
    parser=argparse.ArgumentParser(); parser.add_argument('--seconds',type=float,default=30); parser.add_argument('--name',default=NAME); args=parser.parse_args()
    until=time.monotonic()+args.seconds
    mapping=None; session=0; was=False
    try:
        while time.monotonic()<until:
            if mapping is None:
                try: mapping=Mapping(args.name,writable=True)
                except OSError: time.sleep(.1); continue
            host=host_snapshot(mapping); live=alive(host)
            if live: session=host['session']
            if live!=was: print('FAKE GUEST host '+('connected' if live else 'lost'),flush=True); was=live
            mapping.publish(GUEST,guest_packet(session,CONNECTED if live else GUEST_READY)); time.sleep(.1)
    finally:
        if mapping: mapping.publish(GUEST,guest_packet(session,SHUTDOWN)); mapping.close()
