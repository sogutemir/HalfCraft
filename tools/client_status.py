"""Read client input/render mailboxes; no writes or account access."""
import ctypes
import json
import struct
import time
from halfcraft_protocol import k, age

def read(name,size,offset=0,length=128):
    handle=k.OpenFileMappingW(4,False,name)
    if not handle: raise ctypes.WinError(ctypes.get_last_error())
    address=k.MapViewOfFile(handle,4,0,0,size)
    if not address: k.CloseHandle(handle); raise ctypes.WinError(ctypes.get_last_error())
    try:
        for _ in range(128):
            seq=struct.unpack('<I',ctypes.string_at(address+offset,4))[0]
            if seq&1: time.sleep(.001); continue
            raw=ctypes.string_at(address+offset,length)
            if struct.unpack('<I',ctypes.string_at(address+offset,4))[0]==seq: return raw
        raise RuntimeError('Client snapshot busy')
    finally: k.UnmapViewOfFile(address); k.CloseHandle(handle)

def status():
    raw=read(r'Local\HalfCraft_input_v2',4224)
    v=struct.unpack_from('<10I2f2I',raw)
    assert v[1:4]==(0x49435248,2,4224),v
    g=struct.unpack_from('<5I',raw,64)
    inputs=dict(pid=v[4],session=v[5],age_ms=age(v[6]),enabled=bool(v[7]),write=v[8],reset=v[9],yaw=v[10],pitch=v[11],epoch=v[12],requested=bool(v[13]),read=g[0],guest_pid=g[1],guest_age_ms=age(g[2]),ready=bool(g[3]),guest_session=g[4])
    inputs.update(target=bool(struct.unpack_from('<I',raw,108)[0]),bounds=struct.unpack_from('<6f',raw,84),slot=struct.unpack_from('<I',raw,112)[0],count=struct.unpack_from('<I',raw,116)[0])
    inputs['last_input_ms']=struct.unpack_from('<I',raw,120)[0]
    raw=read(r'Local\HalfCraft_render_v3',75508672)
    v=struct.unpack_from('<12I',raw)
    assert v[1:4]==(0x52435248,3,75508672),v
    a=struct.unpack_from('<6I',raw,64); m=struct.unpack_from('<2I3i3I',raw,96)
    render=dict(pid=v[4],session=v[5],age_ms=age(v[6]),enabled=bool(v[7]),reset=v[8],atlas_ack=v[9],mesh_ack=v[10],epoch=v[11],atlas_seq=a[0],generation=a[1],width=a[2],height=a[3],atlas_bytes=a[4],atlas_session=a[5],mesh_seq=m[0],mesh_generation=m[1],section=m[2:5],vertices=m[5],mesh_bytes=m[6],mesh_session=m[7])
    render.update(draw_us=struct.unpack_from('<I',raw,48)[0],max_draw_us=struct.unpack_from('<I',raw,52)[0],sections=struct.unpack_from('<I',raw,56)[0],export_us=struct.unpack_from('<I',raw,88)[0],max_export_us=struct.unpack_from('<I',raw,92)[0])
    render['total_vertices']=struct.unpack_from('<I',raw,60)[0]
    cracks=read(r'Local\HalfCraft_render_v3',75508672,75497600,800)
    c=struct.unpack_from('<5I',cracks)
    assert c[4]<=16,c
    render.update(crack_count=c[4],crack_age_ms=age(c[3]),crack_stages=[struct.unpack_from('<i',cracks,32+i*48+40)[0] for i in range(c[4])])
    items=read(r'Local\HalfCraft_render_v3',75508672,75498400,32+128*80)
    header=struct.unpack_from('<5I',items)
    assert header[4]<=128,header
    render.update(item_count=header[4],item_age_ms=age(header[3]),items=[])
    for i in range(header[4]):
        record=struct.unpack_from('<2I5fI12f',items,32+i*80)
        render['items'].append(dict(id=record[0],kind=record[1],position=record[2:5],yaw=record[5],size=record[6],tint=record[7]))
    raw=read(r'Local\HalfCraft_ui_v1',33177728,0,64)
    u=struct.unpack('<16I',raw)
    assert u[1:4]==(0x55435248,1,33177728),u
    state=struct.unpack('<8I',read(r'Local\HalfCraft_ui_v1',33177728,64,32))
    frame=struct.unpack('<8I',read(r'Local\HalfCraft_ui_v1',33177728,96,32))
    ui=dict(enabled=bool(u[7]),session=u[5],epoch=u[8],width=u[9],height=u[10],ack=u[11],cursor=u[12:14],screen=bool(state[5]),guest_age_ms=age(state[4]),gui_scale=state[6],carried_count=state[7],frame_seq=frame[0],frame_width=frame[1],frame_height=frame[2],frame_age_ms=age(frame[6]),frame_id=frame[7])
    return dict(input=inputs,render=render,ui=ui)

if __name__=='__main__': print(json.dumps(status()))
