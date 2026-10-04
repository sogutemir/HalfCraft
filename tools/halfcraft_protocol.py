"""Windows-only diagnostic/fake endpoint. Real endpoints use native atomic seqlocks."""
import ctypes
from ctypes import wintypes as w
import os
import struct

MAGIC, VERSION, SIZE, HOST, GUEST, TIMEOUT = 0x46435248, 1, 384, 64, 256, 8000
NAME = r'Local\HalfCraft_v1'
DISCONNECTED, HOST_READY, GUEST_READY, CONNECTED, ERROR, SHUTDOWN = range(6)
k = ctypes.WinDLL('kernel32', use_last_error=True)
k.OpenFileMappingW.argtypes = (w.DWORD,w.BOOL,w.LPCWSTR); k.OpenFileMappingW.restype = w.HANDLE
k.CreateFileMappingW.argtypes = (w.HANDLE,ctypes.c_void_p,w.DWORD,w.DWORD,w.DWORD,w.LPCWSTR); k.CreateFileMappingW.restype = w.HANDLE
k.MapViewOfFile.argtypes = (w.HANDLE,w.DWORD,w.DWORD,w.DWORD,ctypes.c_size_t); k.MapViewOfFile.restype = ctypes.c_void_p
k.UnmapViewOfFile.argtypes = (ctypes.c_void_p,); k.UnmapViewOfFile.restype = w.BOOL
k.CloseHandle.argtypes = (w.HANDLE,); k.CloseHandle.restype = w.BOOL
k.GetTickCount.restype = w.DWORD
k.OpenProcess.argtypes = (w.DWORD,w.BOOL,w.DWORD); k.OpenProcess.restype = w.HANDLE
k.WaitForSingleObject.argtypes = (w.HANDLE,w.DWORD); k.WaitForSingleObject.restype = w.DWORD
k.CreateMutexW.argtypes=(ctypes.c_void_p,w.BOOL,w.LPCWSTR); k.CreateMutexW.restype=w.HANDLE

def tick(): return k.GetTickCount()
def age(stamp): return (tick()-stamp)&0xffffffff
def process_alive(pid):
    handle=k.OpenProcess(0x100000,False,pid)
    if not handle: return False
    try: return k.WaitForSingleObject(handle,0)==258
    finally: k.CloseHandle(handle)

class Mapping:
    def __init__(self, name=NAME, create=False, writable=False):
        self.handle=self.address=self.owner=None
        self.name=name
        try:
            if create:
                self.owner=k.CreateMutexW(None,False,name+'_host_owner')
                if not self.owner or ctypes.get_last_error()==183: raise RuntimeError('Another host owns mapping')
                self.handle=k.CreateFileMappingW(ctypes.c_void_p(-1),None,4,0,SIZE,name)
                existed=ctypes.get_last_error()==183
            else:
                self.handle=k.OpenFileMappingW(0xF001F if writable else 4,False,name)
                existed=True
            if not self.handle: raise ctypes.WinError(ctypes.get_last_error())
            self.address=k.MapViewOfFile(self.handle,0xF001F if (create or writable) else 4,0,0,SIZE)
            if not self.address: raise ctypes.WinError(ctypes.get_last_error())
            if create and not existed:
                ctypes.memset(self.address,0,SIZE)
                self.write(0,struct.pack('<4I',MAGIC,VERSION,SIZE,0))
            self.validate()
        except BaseException:
            self.close(); raise
    def bytes(self,off,n): return ctypes.string_at(self.address+off,n)
    def write(self,off,data):
        if off<0 or off+len(data)>SIZE: raise ValueError('Mapping bounds')
        ctypes.memmove(self.address+off,bytes(data),len(data))
    def validate(self):
        if struct.unpack('<3I',self.bytes(0,12))!=(MAGIC,VERSION,SIZE): raise ValueError('HalfCraft protocol mismatch')
    def snapshot(self,off,size):
        for _ in range(64):
            seq,=struct.unpack('<I',self.bytes(off,4))
            if seq==0 or seq&1: continue
            data=self.bytes(off,size)
            if struct.unpack('<I',self.bytes(off,4))[0]==seq: return data
        return None
    def publish(self,off,data):
        seq=struct.unpack('<I',self.bytes(off,4))[0]
        seq=((seq+1)&~1)&0xffffffff
        self.write(off,struct.pack('<I',(seq+1)&0xffffffff))
        self.write(off+4,data[4:])
        self.write(off,struct.pack('<I',(seq+2)&0xffffffff))
    def close(self):
        if self.address: k.UnmapViewOfFile(self.address); self.address=None
        if self.handle: k.CloseHandle(self.handle); self.handle=None
        if self.owner: k.CloseHandle(self.owner); self.owner=None
    def __enter__(self): return self
    def __exit__(self,*args): self.close()

def host_snapshot(mapping):
    raw=mapping.snapshot(HOST,192)
    if raw is None: return None
    seq,pid,beat,state,session,player,started,_=struct.unpack_from('<8I',raw)
    return dict(seq=seq,pid=pid,beat=beat,state=state,session=session,player=bool(player),started=started,
                map=raw[32:96].split(b'\0',1)[0].decode('utf8',errors='replace'),origin=struct.unpack_from('<3d',raw,96),angles=struct.unpack_from('<3f',raw,120))
def guest_snapshot(mapping):
    raw=mapping.snapshot(GUEST,128)
    if raw is None: return None
    seq,pid,beat,state,session,world,ground,started=struct.unpack_from('<8I',raw)
    return dict(seq=seq,pid=pid,beat=beat,state=state,session=session,world=bool(world),ground=bool(ground),started=started,
                position=struct.unpack_from('<3d',raw,32),velocity=struct.unpack_from('<3d',raw,56))
def alive(snapshot):
    return snapshot is not None and snapshot['state'] not in (SHUTDOWN,ERROR) and snapshot['beat']!=0 and age(snapshot['beat'])<TIMEOUT and process_alive(snapshot['pid'])
def host_packet(session,state=HOST_READY):
    return struct.pack('<8I64s3d3f60x',0,os.getpid(),tick(),state,session,1,tick(),0,b'fake_c1a0',123,456,78,0,90,0)
def guest_packet(session,state=GUEST_READY):
    return struct.pack('<8I6d48x',0,os.getpid(),tick(),state,session,1,1,tick(),1,2,3,.1,.2,.3)

def hl_to_mc(p): return p[0]/40,p[2]/40,-p[1]/40
def mc_to_hl(p): return p[0]*40,-p[2]*40,p[1]*40
def hl_yaw_to_mc(yaw): return (-yaw-90+180)%360-180
