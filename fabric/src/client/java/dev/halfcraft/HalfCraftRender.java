package dev.halfcraft;

import java.lang.foreign.*;
import java.lang.invoke.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import static java.lang.foreign.ValueLayout.*;

/** Two acknowledged mailboxes: payload stays immutable until Half-Life consumes it. */
public final class HalfCraftRender implements AutoCloseable {
    public static final int ATLAS_CAPACITY=64*1024*1024,MESH_CAPACITY=8*1024*1024,CRACK_OFFSET=128+ATLAS_CAPACITY+MESH_CAPACITY,ITEM_OFFSET=CRACK_OFFSET+800,BYTES=ITEM_OFFSET+32+128*80;
    private static final VarHandle INT=JAVA_INT.varHandle();
    private static final SymbolLookup K32=SymbolLookup.libraryLookup("kernel32",Arena.global());
    private static MethodHandle fn(String name,FunctionDescriptor descriptor) { return Linker.nativeLinker().downcallHandle(K32.find(name).orElseThrow(),descriptor); }
    private static final MethodHandle OPEN=fn("OpenFileMappingW",FunctionDescriptor.of(ADDRESS,JAVA_INT,JAVA_INT,ADDRESS));
    private static final MethodHandle MAP=fn("MapViewOfFile",FunctionDescriptor.of(ADDRESS,ADDRESS,JAVA_INT,JAVA_INT,JAVA_INT,JAVA_LONG));
    private static final MethodHandle UNMAP=fn("UnmapViewOfFile",FunctionDescriptor.of(JAVA_INT,ADDRESS));
    private static final MethodHandle CLOSE=fn("CloseHandle",FunctionDescriptor.of(JAVA_INT,ADDRESS));
    private MemorySegment handle=MemorySegment.NULL,memory;
    private int session,reset,epoch;
    public int generation() { return (session*31+reset)*31+epoch; }
    private int get(int offset) { return (int)INT.getAcquire(memory,(long)offset); }
    private void put(int offset,int value) { INT.setVolatile(memory,(long)offset,value); }
    public boolean poll(HalfCraftLink link) {
        try {
            if(memory!=null && link.host()!=null && get(16)!=link.host().pid()) close();
            if(memory==null) {
                try(Arena arena=Arena.ofConfined()) { handle=(MemorySegment)OPEN.invokeExact(0xF001F,0,arena.allocateFrom("Local\\HalfCraft_render_v3",StandardCharsets.UTF_16LE)); }
                if(handle.address()==0) return false;
                MemorySegment view=(MemorySegment)MAP.invokeExact(handle,0xF001F,0,0,(long)BYTES);
                if(view.address()==0) { close(); return false; }
                memory=view.reinterpret(BYTES);
                if(get(4)!=0x52435248 || get(8)!=3 || get(12)!=BYTES) throw new IllegalStateException("HalfCraft render protocol mismatch");
            }
            int seq=get(0); if((seq&1)!=0) return false;
            int pid=get(16),s=get(20),r=get(32),e=get(44),heartbeat=get(24),enabled=get(28);
            VarHandle.loadLoadFence(); if(seq!=get(0)) return false;
            session=s; reset=r; epoch=e;
            return enabled!=0 && link.matchesHost(pid,s) && HalfCraftPhysics.active() && Integer.toUnsignedLong(HalfCraftLink.tick()-heartbeat)<1000;
        } catch(Throwable e) { close(); throw new IllegalStateException("HalfCraft render mapping failed",e); }
    }
    public boolean atlas(int generation,int width,int height,ByteBuffer pixels) {
        long bytes=(long)width*height*4;
        if(width<=0 || height<=0 || width>8192 || height>8192 || bytes>ATLAS_CAPACITY || bytes!=pixels.remaining()) throw new IllegalArgumentException("HalfCraft atlas exceeds transport bounds");
        int seq=get(64); if((seq&1)!=0 || seq!=get(36)) return false;
        put(64,seq+1); put(68,generation); put(72,width); put(76,height); put(80,(int)bytes); put(84,session);
        MemorySegment.copy(MemorySegment.ofBuffer(pixels),0,memory,128,bytes); put(64,seq+2); return true;
    }
    public boolean meshReady() { int seq=get(96); return (seq&1)==0 && seq==get(40); }
    public void items(int generation,ByteBuffer records) {
        if(records.remaining()%80!=0 || records.remaining()>128*80) throw new IllegalArgumentException("HalfCraft item bounds exceeded");
        int seq=(get(ITEM_OFFSET)+1)&~1;
        put(ITEM_OFFSET,seq+1); put(ITEM_OFFSET+4,session); put(ITEM_OFFSET+8,generation);
        put(ITEM_OFFSET+12,HalfCraftLink.tick()); put(ITEM_OFFSET+16,records.remaining()/80);
        MemorySegment.copy(MemorySegment.ofBuffer(records),0,memory,ITEM_OFFSET+32L,records.remaining());
        put(ITEM_OFFSET,seq+2);
    }
    public void cracks(int generation,ByteBuffer records) {
        if(records.remaining()%48!=0 || records.remaining()>16*48) throw new IllegalArgumentException("HalfCraft crack bounds exceeded");
        int seq=(get(CRACK_OFFSET)+1)&~1;
        put(CRACK_OFFSET,seq+1); put(CRACK_OFFSET+4,session); put(CRACK_OFFSET+8,generation);
        put(CRACK_OFFSET+12,HalfCraftLink.tick()); put(CRACK_OFFSET+16,records.remaining()/48);
        MemorySegment.copy(MemorySegment.ofBuffer(records),0,memory,CRACK_OFFSET+32L,records.remaining());
        put(CRACK_OFFSET,seq+2);
    }
    public void exportTime(long micros) { put(88,(int)Math.min(micros,Integer.MAX_VALUE)); put(92,Math.max(get(92),get(88))); }
    public boolean mesh(int generation,int x,int y,int z,int count,ByteBuffer vertices) {
        if(count<0 || count%3!=0 || (long)count*32!=vertices.remaining() || vertices.remaining()>MESH_CAPACITY) throw new IllegalArgumentException("HalfCraft mesh exceeds transport bounds");
        int seq=get(96); if(!meshReady()) return false;
        put(96,seq+1); put(100,generation); put(104,x); put(108,y); put(112,z); put(116,count); put(120,vertices.remaining()); put(124,session);
        MemorySegment.copy(MemorySegment.ofBuffer(vertices),0,memory,128L+ATLAS_CAPACITY,vertices.remaining()); put(96,seq+2); return true;
    }
    @Override public void close() {
        try { if(memory!=null) { int ok=(int)UNMAP.invokeExact(memory); memory=null; } if(handle.address()!=0) { int ok=(int)CLOSE.invokeExact(handle); handle=MemorySegment.NULL; } }
        catch(Throwable e) { HalfCraftGuest.LOG.error("HalfCraft render cleanup failed",e); }
    }
}
