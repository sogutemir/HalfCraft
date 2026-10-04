"""Read independent collision/puppet mapping without changing its regions."""
import ctypes
import json
import struct
from halfcraft_protocol import k,age

NAME=r'Local\HalfCraft_collision_v1'
SIZE,GUEST=4288,4224
def status():
    handle=k.OpenFileMappingW(4,False,NAME)
    if not handle: raise ctypes.WinError(ctypes.get_last_error())
    address=k.MapViewOfFile(handle,4,0,0,SIZE)
    if not address: k.CloseHandle(handle); raise ctypes.WinError(ctypes.get_last_error())
    try:
        def snapshot(off,size):
            for _ in range(64):
                seq=struct.unpack('<I',ctypes.string_at(address+off,4))[0]
                if seq==0 or seq&1: continue
                raw=ctypes.string_at(address+off,size)
                if struct.unpack('<I',ctypes.string_at(address+off,4))[0]==seq: return raw
            raise RuntimeError('Collision snapshot busy')
        raw=snapshot(0,GUEST); values=struct.unpack_from('<12I3iI3d2f2I',raw)
        assert values[1:4]==(0x43435248,2,SIZE),values
        assert values[15]==8 and values[21]==4,values
        host=dict(pid=values[4],session=values[5],age_ms=age(values[6]),enabled=bool(values[7]),epoch=values[8],
            generation=values[9],ready=bool(values[10]),correction=values[11],base=values[12:15],feet=values[16:19],yaw=values[19],pitch=values[20],occupied=values[22])
        masks=struct.unpack_from('<512Q',raw,128)
        guest_raw=snapshot(GUEST,64); g=struct.unpack('<8I3d2f',guest_raw)
        guest=dict(pid=g[1],session=g[2],epoch=g[3],active=bool(g[4]),age_ms=age(g[5]),correction_ack=g[6],crouched=bool(g[7]&1),eye_height=(g[7]>>8)/1000,feet=g[8:11],yaw=g[11],pitch=g[12])
        return dict(host=host,guest=guest,nonempty_cells=sum(bool(mask) for mask in masks),solid_samples=sum(mask.bit_count() for mask in masks))
    finally: k.UnmapViewOfFile(address); k.CloseHandle(handle)

if __name__=='__main__': print(json.dumps(status()))
