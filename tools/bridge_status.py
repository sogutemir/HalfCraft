import argparse
import json
import time
from halfcraft_protocol import *

def status(name=NAME):
    with Mapping(name) as mapping:
        host,guest=host_snapshot(mapping),guest_snapshot(mapping)
        result=dict(protocol=VERSION,mapping=name,host=host,guest=guest,
                    host_alive=alive(host),guest_alive=alive(guest),host_age_ms=age(host['beat']) if host else None,guest_age_ms=age(guest['beat']) if guest else None)
        result['connected']=result['host_alive'] and result['guest_alive'] and host['session']==guest['session']
        result['connection_age_ms']=age(guest['started']) if result['connected'] else None
        return result

if __name__=='__main__':
    parser=argparse.ArgumentParser(); parser.add_argument('--json',action='store_true'); parser.add_argument('--watch',action='store_true'); args=parser.parse_args()
    while True:
        try:
            result=status()
            if args.json: print(json.dumps(result))
            else:
                print('HalfCraft Bridge\n----------------\nProtocol: v1')
                for role in ('host','guest'):
                    data=result[role]
                    print(f"{role.title()} PID: {data['pid'] if data else 'not published'}")
                    print(f"{role.title()} heartbeat: {'alive' if result[role+'_alive'] else 'lost'}; last update {result[role+'_age_ms']} ms ago")
                print(f"Connected: {result['connected']}; connection age: {result['connection_age_ms']} ms")
                print(f"Half-Life: {result['host']}"); print(f"Minecraft: {result['guest']}")
        except (OSError,ValueError) as error:
            print(f'HalfCraft Bridge: unavailable ({error})')
            if not args.watch: raise SystemExit(1)
        if not args.watch: break
        time.sleep(1)
