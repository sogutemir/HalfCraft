package dev.halfcraft;

import java.lang.foreign.*;
import java.lang.invoke.*;
import java.nio.charset.StandardCharsets;
import static java.lang.foreign.ValueLayout.*;

/** SkyCraft FFM/seqlock architecture, with independent heartbeat and explicit handle cleanup. */
public final class HalfCraftLink implements AutoCloseable {
    public static final int MAGIC=0x46435248, VERSION=1, BYTES=384, HOST=64, GUEST=256, TIMEOUT=8000;
    public static final String NAME="Local\\HalfCraft_v1";
    private static final VarHandle INT=JAVA_INT.varHandle();
    private static final SymbolLookup K32=SymbolLookup.libraryLookup("kernel32",Arena.global());
    private static MethodHandle function(String name, FunctionDescriptor descriptor) {
        return Linker.nativeLinker().downcallHandle(K32.find(name).orElseThrow(),descriptor);
    }
    private static final MethodHandle OPEN=function("OpenFileMappingW",FunctionDescriptor.of(ADDRESS,JAVA_INT,JAVA_INT,ADDRESS));
    private static final MethodHandle MAP=function("MapViewOfFile",FunctionDescriptor.of(ADDRESS,ADDRESS,JAVA_INT,JAVA_INT,JAVA_INT,JAVA_LONG));
    private static final MethodHandle UNMAP=function("UnmapViewOfFile",FunctionDescriptor.of(JAVA_INT,ADDRESS));
    private static final MethodHandle CLOSE=function("CloseHandle",FunctionDescriptor.of(JAVA_INT,ADDRESS));
    private static final MethodHandle TICK=function("GetTickCount",FunctionDescriptor.of(JAVA_INT));
    private static final MethodHandle MUTEX=function("CreateMutexW",FunctionDescriptor.of(ADDRESS,ADDRESS,JAVA_INT,ADDRESS));
    private static final MethodHandle ERROR=function("GetLastError",FunctionDescriptor.of(JAVA_INT));
    private final int pid=(int)ProcessHandle.current().pid();
    private MemorySegment handle=MemorySegment.NULL, memory, owner=MemorySegment.NULL;
    private int hostPid, session, started;
    private boolean connected;
    private HostState latestHost;
    private long lastLog;
    private volatile Sample sample=new Sample(false,false,new double[3],new double[3]);
    public record Sample(boolean world,boolean ground,double[] position,double[] velocity) {}
    public record HostState(int pid,int heartbeat,int state,int session,boolean player,String map,double[] origin,float[] angles) {}
    public static int tick() {
        try { return (int)TICK.invokeExact(); } catch(Throwable e) { throw new IllegalStateException(e); }
    }
    private static void closeHandle(MemorySegment h) throws Throwable { if(h.address()!=0) { int ok=(int)CLOSE.invokeExact(h); } }
    public void sample(Sample value) { sample=value; }
    public synchronized boolean connected() { return connected; }
    public synchronized boolean matchesHost(int pid,int generation) { return connected && hostPid==pid && session==generation; }
    public synchronized HostState host() { return connected ? latestHost : null; }
    private int get(int offset) { return (int)INT.getAcquire(memory,(long)offset); }
    private void put(int offset,int value) { INT.setVolatile(memory,(long)offset,value); }
    private HostState readHost() {
        for(int attempt=0;attempt<16;++attempt) {
            int seq=get(HOST);
            if(seq==0 || (seq&1)!=0) continue;
            int hp=get(HOST+4), heartbeat=get(HOST+8), state=get(HOST+12), generation=get(HOST+16);
            boolean player=get(HOST+20)!=0;
            byte[] name=new byte[64]; int length=0;
            for(int i=0;i<64;++i) { name[i]=memory.get(JAVA_BYTE,HOST+32L+i); if(name[i]==0) break; ++length; }
            double[] origin=new double[3]; float[] angles=new float[3];
            for(int i=0;i<3;++i) { origin[i]=memory.get(JAVA_DOUBLE,HOST+96L+i*8); angles[i]=memory.get(JAVA_FLOAT,HOST+120L+i*4); }
            VarHandle.loadLoadFence();
            if(get(HOST)==seq) return new HostState(hp,heartbeat,state,generation,player,new String(name,0,length,StandardCharsets.UTF_8),origin,angles);
        }
        return null;
    }
    public synchronized void poll() {
        try {
            if(memory==null) {
                try(Arena arena=Arena.ofConfined()) {
                    if(owner.address()==0) {
                        owner=(MemorySegment)MUTEX.invokeExact(MemorySegment.NULL,0,arena.allocateFrom(NAME+"_guest_owner",StandardCharsets.UTF_16LE));
                        int error=(int)ERROR.invokeExact();
                        if(owner.address()==0 || error==183) { closeHandle(owner); owner=MemorySegment.NULL; throw new IllegalStateException("Another HalfCraft guest owns mapping"); }
                    }
                    handle=(MemorySegment)OPEN.invokeExact(0xF001F,0,arena.allocateFrom(NAME,StandardCharsets.UTF_16LE));
                    if(handle.address()==0) return;
                    MemorySegment view=(MemorySegment)MAP.invokeExact(handle,0xF001F,0,0,(long)BYTES);
                    if(view.address()==0) { closeHandle(handle); handle=MemorySegment.NULL; return; }
                    memory=view.reinterpret(BYTES);
                    if(get(0)!=MAGIC || get(4)!=VERSION || get(8)!=BYTES) { close(); throw new IllegalStateException("HalfCraft protocol mismatch"); }
                    started=tick();
                }
            }
            HostState host=readHost();
            if(host!=null) latestHost=host;
            else host=latestHost;
            boolean live=host!=null && host.pid()!=0 && host.state()!=5 && host.state()!=4
                && Integer.toUnsignedLong(tick()-host.heartbeat())<TIMEOUT
                && ProcessHandle.of(Integer.toUnsignedLong(host.pid())).map(ProcessHandle::isAlive).orElse(false);
            if(live && (!connected || host.pid()!=hostPid || host.session()!=session)) {
                hostPid=host.pid(); session=host.session();
                started=tick();
                HalfCraftGuest.LOG.info("HalfCraft host connected pid={} session={}",hostPid,Integer.toUnsignedLong(session));
            }
            if(!live && connected) HalfCraftGuest.LOG.info("HalfCraft host lost pid={}",hostPid);
            connected=live;
            writeGuest(live ? 3 : 2);
            long now=System.currentTimeMillis();
            if(live && now-lastLog>=5000) {
                lastLog=now;
                HalfCraftGuest.LOG.info("HC host map={} player={} origin={} angles={} guest={} velocity={} world={} onGround={}",host.map(),host.player(),java.util.Arrays.toString(host.origin()),java.util.Arrays.toString(host.angles()),java.util.Arrays.toString(sample.position()),java.util.Arrays.toString(sample.velocity()),sample.world(),sample.ground());
            }
        } catch(Throwable e) { throw new IllegalStateException("HalfCraft mapping operation failed",e); }
    }
    private void writeGuest(int state) {
        Sample value=sample; int seq=get(GUEST);
        // Restart may inherit odd seq from crashed writer; normalize to even before publication.
        seq=(seq+1)&~1;
        put(GUEST,seq+1);
        put(GUEST+4,pid); put(GUEST+8,tick()); put(GUEST+12,state); put(GUEST+16,session);
        put(GUEST+20,value.world()?1:0); put(GUEST+24,value.ground()?1:0); put(GUEST+28,started);
        for(int i=0;i<3;++i) { memory.set(JAVA_DOUBLE,GUEST+32L+i*8,value.position()[i]); memory.set(JAVA_DOUBLE,GUEST+56L+i*8,value.velocity()[i]); }
        put(GUEST,seq+2);
    }
    @Override public synchronized void close() {
        try {
            if(memory!=null) { writeGuest(5); int ok=(int)UNMAP.invokeExact(memory); memory=null; }
            closeHandle(handle); handle=MemorySegment.NULL;
            closeHandle(owner); owner=MemorySegment.NULL;
            connected=false;
        } catch(Throwable e) { HalfCraftGuest.LOG.error("HalfCraft cleanup failed",e); }
    }
    public static void layoutCheck() {
        var header=MemoryLayout.structLayout(JAVA_INT.withName("magic"),JAVA_INT, JAVA_INT, JAVA_INT,MemoryLayout.paddingLayout(48));
        var host=MemoryLayout.structLayout(JAVA_INT.withName("seq"),JAVA_INT,JAVA_INT,JAVA_INT,JAVA_INT,JAVA_INT,JAVA_INT,JAVA_INT,MemoryLayout.sequenceLayout(64,JAVA_BYTE).withName("map"),MemoryLayout.sequenceLayout(3,JAVA_DOUBLE).withName("origin"),MemoryLayout.sequenceLayout(3,JAVA_FLOAT).withName("angles"),MemoryLayout.paddingLayout(60));
        var guest=MemoryLayout.structLayout(MemoryLayout.sequenceLayout(8,JAVA_INT),MemoryLayout.sequenceLayout(3,JAVA_DOUBLE).withName("position"),MemoryLayout.sequenceLayout(3,JAVA_DOUBLE).withName("velocity"),MemoryLayout.paddingLayout(48));
        if(header.byteSize()!=64 || host.byteSize()!=192 || guest.byteSize()!=128 || host.byteOffset(MemoryLayout.PathElement.groupElement("origin"))!=96 || guest.byteOffset(MemoryLayout.PathElement.groupElement("velocity"))!=56) throw new AssertionError("HalfCraft Java layout mismatch");
        var collision=MemoryLayout.structLayout(MemoryLayout.sequenceLayout(12,JAVA_INT),MemoryLayout.sequenceLayout(4,JAVA_INT),MemoryLayout.sequenceLayout(3,JAVA_DOUBLE).withName("feet"),MemoryLayout.sequenceLayout(2,JAVA_FLOAT),MemoryLayout.sequenceLayout(2,JAVA_INT),MemoryLayout.paddingLayout(24));
        var physicsGuest=MemoryLayout.structLayout(MemoryLayout.sequenceLayout(8,JAVA_INT),MemoryLayout.sequenceLayout(3,JAVA_DOUBLE),MemoryLayout.sequenceLayout(2,JAVA_FLOAT));
        if(collision.byteSize()!=128 || collision.byteOffset(MemoryLayout.PathElement.groupElement("feet"))!=64 || physicsGuest.byteSize()!=64 || 128+512*8+physicsGuest.byteSize()!=4288) throw new AssertionError("HalfCraft collision Java layout mismatch");
    }
    public static void main(String[] args) { layoutCheck(); System.out.println("Java layout PASS bytes="+BYTES+" host="+HOST+" guest="+GUEST); }
}
