"""Run without games: layout, bounds, rejection, seqlock, process/reconnect/shutdown and coordinates."""
import math
import random
import subprocess
import sys
import time
from pathlib import Path
from halfcraft_protocol import *

assert struct.calcsize('<8I64s3d3f60x')==192
assert struct.calcsize('<8I6d48x')==128
random.seed(17)
for _ in range(1000):
    position=tuple(random.uniform(-100000,100000) for _ in range(3))
    assert max(abs(a-b) for a,b in zip(position,mc_to_hl(hl_to_mc(position))))<1e-9
for yaw in (0,90,180,-90):
    angle=math.radians(yaw); converted=hl_to_mc((math.cos(angle)*40,math.sin(angle)*40,0))
    mc=math.radians(hl_yaw_to_mc(yaw))
    assert abs(converted[0]+math.sin(mc))<1e-12 and abs(converted[2]-math.cos(mc))<1e-12
name=NAME+'_test_'+str(os.getpid())
root=Path(__file__).parent
def wait(predicate):
    deadline=time.monotonic()+5
    while time.monotonic()<deadline:
        if predicate(): return
        time.sleep(.02)
    raise AssertionError('Timed out')
with Mapping(name,create=True) as host:
    for off,data in ((-1,b'x'),(SIZE,b'x')):
        try: host.write(off,data)
        except ValueError: pass
        else: raise AssertionError('Out-of-bounds write accepted')
    session=tick()^os.getpid(); host.publish(HOST,host_packet(session))
    with Mapping(name) as reader:
        assert host_snapshot(reader)['map']=='fake_c1a0'
        assert host_snapshot(reader)['origin']==(123.,456.,78.)
        assert alive(host_snapshot(reader))
        host.write(HOST,struct.pack('<I',3)); assert reader.snapshot(HOST,192) is None
        host.publish(HOST,host_packet(session))
        host.write(4,struct.pack('<I',99))
        try: Mapping(name)
        except ValueError: pass
        else: raise AssertionError('Version mismatch accepted')
        host.write(4,struct.pack('<I',VERSION))
        host.write(0,struct.pack('<I',0))
        try: Mapping(name)
        except ValueError: pass
        else: raise AssertionError('Bad magic accepted')
        host.write(0,struct.pack('<I',MAGIC))
        for attempt in range(2):
            child=subprocess.Popen([sys.executable,str(root/'fake_minecraft.py'),'--name',name,'--seconds','1.2'])
            try:
                wait(lambda: alive(guest_snapshot(reader)))
                guest=guest_snapshot(reader); assert guest['pid']==child.pid and guest['session']==session
                assert guest['position']==(1.,2.,3.) and guest['velocity']==(.1,.2,.3)
                child.wait(timeout=5); assert child.returncode==0
                assert guest_snapshot(reader)['state']==SHUTDOWN and not alive(guest_snapshot(reader))
            finally:
                if child.poll() is None: child.kill(); child.wait()
        raw=bytearray(guest_packet(session)); struct.pack_into('<I',raw,8,(tick()-TIMEOUT-1)&0xffffffff)
        host.publish(GUEST,raw); assert not alive(guest_snapshot(reader))
        host.publish(HOST,host_packet(session,SHUTDOWN)); assert not alive(host_snapshot(reader))
print('Protocol PASS: layout, create/open, bounds, magic/version, PIDs, heartbeat expiry, seqlock, guest reconnect, clean shutdown; coordinates 1000 round trips/cardinal yaw')
