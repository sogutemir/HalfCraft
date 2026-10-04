import argparse
import time
from halfcraft_protocol import *

if __name__=='__main__':
    parser=argparse.ArgumentParser(); parser.add_argument('--seconds',type=float,default=30); parser.add_argument('--name',default=NAME); args=parser.parse_args()
    with Mapping(args.name,create=True) as mapping:
        session=tick()^os.getpid(); until=time.monotonic()+args.seconds; was=False
        try:
            while time.monotonic()<until:
                guest=guest_snapshot(mapping); live=alive(guest) and guest['session']==session
                if live!=was: print('FAKE HOST guest '+('connected' if live else 'lost'),flush=True); was=live
                mapping.publish(HOST,host_packet(session,CONNECTED if live else HOST_READY)); time.sleep(.1)
        finally: mapping.publish(HOST,host_packet(session,SHUTDOWN))
