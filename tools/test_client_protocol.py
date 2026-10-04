"""Runnable client transport self-check: bounds, uint32 wrap and queue reset."""
import struct
INPUT_BYTES=128+256*16
ATLAS=64*1024*1024
MESH=8*1024*1024
assert INPUT_BYTES==4224 and 128+ATLAS+MESH+32+16*48+32+128*80==75508672
assert struct.calcsize('<2I5fI12f')==80
assert struct.calcsize('<5f4B2I')==32
assert 128+3840*2160*4==33177728
assert struct.calcsize('<16I')==64 and struct.calcsize('<8I')==32
assert 1280*720*4<=3840*2160*4
cursor=struct.pack('<IIiI',4,424,562,123)
assert struct.unpack('<IIiI',cursor)==(4,424,562,123)
text=struct.pack('<IIiI',5,0,ord('ğ'),123)
assert chr(struct.unpack('<IIiI',text)[2])=='ğ'
assert 96+4*1024*1024==4194400
assert struct.calcsize('<3d2I')==32 and struct.calcsize('<4I')==16
def distance(write,read): return (write-read)&0xffffffff
assert distance(2,0xfffffffe)==4
assert distance(256,0)==256
assert distance(257,0)>256
assert ((0xffffffff+1)&0xffffffff)==0
queue=bytearray(256*16)
read=write=0xfffffffe
for code in (26,22,4,7):
    assert distance(write,read)<256
    struct.pack_into('<4I',queue,(write&255)*16,1,code,1,0)
    write=(write+1)&0xffffffff
result=[]
while read!=write:
    result.append(struct.unpack_from('<4I',queue,(read&255)*16)[1])
    read=(read+1)&0xffffffff
assert result==[26,22,4,7]
assert 2048*2576*4<=ATLAS
assert (MESH//32)*32==MESH
print('Client protocol PASS: layouts, atlas/mesh bounds, queue uint32 wrap and capacity')
