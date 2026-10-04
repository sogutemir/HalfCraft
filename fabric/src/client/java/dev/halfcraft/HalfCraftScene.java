package dev.halfcraft;
import java.lang.foreign.*;
import java.lang.invoke.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import static java.lang.foreign.ValueLayout.*;
/** Immutable acknowledged SkyCraft texture/scene messages. */
public final class HalfCraftScene implements AutoCloseable {
    private static final int BYTES=4194400;
    private static final VarHandle INT=JAVA_INT.varHandle();
    private static final SymbolLookup K32=SymbolLookup.libraryLookup("kernel32",Arena.global());
    private static MethodHandle fn(String name,FunctionDescriptor d) { return Linker.nativeLinker().downcallHandle(K32.find(name).orElseThrow(),d); }
    private static final MethodHandle OPEN=fn("OpenFileMappingW",FunctionDescriptor.of(ADDRESS,JAVA_INT,JAVA_INT,ADDRESS));
    private static final MethodHandle MAP=fn("MapViewOfFile",FunctionDescriptor.of(ADDRESS,ADDRESS,JAVA_INT,JAVA_INT,JAVA_INT,JAVA_LONG));
    private static final MethodHandle UNMAP=fn("UnmapViewOfFile",FunctionDescriptor.of(JAVA_INT,ADDRESS));
    private static final MethodHandle CLOSE=fn("CloseHandle",FunctionDescriptor.of(JAVA_INT,ADDRESS));
    private MemorySegment handle=MemorySegment.NULL,memory;
    private int session,epoch;
    private boolean live;
    private int get(int o) { return (int)INT.getAcquire(memory,(long)o); }
    private void put(int o,int v) { INT.setVolatile(memory,(long)o,v); }
    public void poll(HalfCraftLink link) {
        try {
            live=false;
            if(memory!=null && link.host()!=null && get(16)!=link.host().pid()) close();
            if(memory==null) {
                try(Arena arena=Arena.ofConfined()) { handle=(MemorySegment)OPEN.invokeExact(0xF001F,0,arena.allocateFrom("Local\\HalfCraft_scene_v1",StandardCharsets.UTF_16LE)); }
                if(handle.address()==0) return;
                MemorySegment view=(MemorySegment)MAP.invokeExact(handle,0xF001F,0,0,(long)BYTES);
                if(view.address()==0) { close(); return; }
                memory=view.reinterpret(BYTES);
                if(get(4)!=0x53435248 || get(8)!=1 || get(12)!=BYTES) throw new IllegalStateException("HalfCraft scene protocol mismatch");
            }
            int seq=get(0); if(seq==0 || (seq&1)!=0) return;
            int s=get(20),e=get(32),pid=get(16),t=get(24),enabled=get(28);
            VarHandle.loadLoadFence(); if(seq!=get(0)) return;
            if(s!=session || e!=epoch) { dev.skycraft.client.render.AvatarExporter.halfcraftReset(); session=s; epoch=e; }
            live=enabled!=0 && link.matchesHost(pid,s) && HalfCraftPhysics.active() && Integer.toUnsignedLong(HalfCraftLink.tick()-t)<1000;
        } catch(Throwable error) { close(); HalfCraftGuest.LOG.error("HalfCraft scene disabled",error); }
    }
    public boolean send(int type,ByteBuffer header,ByteBuffer body) {
        if(!live || memory==null) return false;
        int seq=get(64),h=header.remaining(),b=body==null?0:body.remaining();
        if((seq&1)!=0 || seq!=get(36)) return false;
        if(h<0 || b<0 || (long)h+b>4*1024*1024) throw new IllegalArgumentException("Scene exceeds transport capacity");
        put(64,seq+1); put(68,session); put(72,epoch); put(76,type); put(80,h); put(84,b); put(88,HalfCraftLink.tick());
        MemorySegment.copy(MemorySegment.ofBuffer(header),0,memory,96,h);
        if(b>0) MemorySegment.copy(MemorySegment.ofBuffer(body),0,memory,96L+h,b);
        put(64,seq+2); return true;
    }
    @Override public void close() {
        live=false;
        try { if(memory!=null) { int ok=(int)UNMAP.invokeExact(memory); memory=null; } if(handle.address()!=0) { int ok=(int)CLOSE.invokeExact(handle); handle=MemorySegment.NULL; } }
        catch(Throwable error) { HalfCraftGuest.LOG.error("Scene cleanup failed",error); }
    }
}
