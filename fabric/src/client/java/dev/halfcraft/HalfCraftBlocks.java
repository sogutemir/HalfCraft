package dev.halfcraft;

import java.lang.foreign.*;
import java.lang.invoke.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import static java.lang.foreign.ValueLayout.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Near-player vanilla collision boxes mirrored as GoldSrc solids for NPCs. */
public final class HalfCraftBlocks implements AutoCloseable {
    private static final int CAPACITY=1024,BYTES=30848,ACTOR_CAPACITY=256;
    private record Actors(java.util.List<AABB> boxes,int time,boolean ready) {}
    private static volatile Actors actors=new Actors(java.util.List.of(),0,false);
    public static boolean canPlace(net.minecraft.world.level.Level level,net.minecraft.core.BlockPos pos,net.minecraft.world.level.block.state.BlockState state) {
        if(!HalfCraftPhysics.active()) return true;
        var snapshot=actors;
        if(!snapshot.ready() || Integer.toUnsignedLong(HalfCraftLink.tick()-snapshot.time())>500) return false;
        var shape=state.getCollisionShape(level,pos,CollisionContext.empty()).move(pos.getX(),pos.getY(),pos.getZ());
        for(var box:snapshot.boxes())
            if(net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(shape,net.minecraft.world.phys.shapes.Shapes.create(box),net.minecraft.world.phys.shapes.BooleanOp.AND)) return false;
        return true;
    }
    private static final SymbolLookup K32=SymbolLookup.libraryLookup("kernel32",Arena.global());
    private static MethodHandle fn(String name,FunctionDescriptor type) { return Linker.nativeLinker().downcallHandle(K32.find(name).orElseThrow(),type); }
    private static final MethodHandle OPEN=fn("OpenFileMappingW",FunctionDescriptor.of(ADDRESS,JAVA_INT,JAVA_INT,ADDRESS));
    private static final MethodHandle MAP=fn("MapViewOfFile",FunctionDescriptor.of(ADDRESS,ADDRESS,JAVA_INT,JAVA_INT,JAVA_INT,JAVA_LONG));
    private static final MethodHandle UNMAP=fn("UnmapViewOfFile",FunctionDescriptor.of(JAVA_INT,ADDRESS));
    private static final MethodHandle CLOSE=fn("CloseHandle",FunctionDescriptor.of(JAVA_INT,ADDRESS));
    private static final VarHandle INT=JAVA_INT.varHandle();
    private MemorySegment handle=MemorySegment.NULL,memory;
    private int generation;
    private int get(int offset) { return (int)INT.getAcquire(memory,(long)offset); }
    private void put(int offset,int value) { INT.setVolatile(memory,(long)offset,value); }
    public void tick(Minecraft mc,HalfCraftLink link) {
        try {
            if(memory!=null && link.host()!=null && get(16)!=link.host().pid()) close();
            if(memory==null) {
                try(var arena=Arena.ofConfined()) { handle=(MemorySegment)OPEN.invokeExact(0xF001F,0,arena.allocateFrom("Local\\HalfCraft_blocks_v2",StandardCharsets.UTF_16LE)); }
                if(handle.address()==0) return;
                MemorySegment view=(MemorySegment)MAP.invokeExact(handle,0xF001F,0,0,(long)BYTES);
                if(view.address()==0) { close(); return; }
                memory=view.reinterpret(BYTES);
                if(get(4)!=0x42435248 || get(8)!=2 || get(12)!=BYTES) throw new IllegalStateException("HalfCraft blocks protocol mismatch");
            }
            int hostSeq=get(0); if(hostSeq==0 || (hostSeq&1)!=0) return;
            byte[] host=memory.asSlice(0,64).toArray(JAVA_BYTE);
            byte[] payload=memory.asSlice(24704,ACTOR_CAPACITY*24).toArray(JAVA_BYTE);
            VarHandle.loadLoadFence(); if(hostSeq!=get(0)) return;
            var header=java.nio.ByteBuffer.wrap(host).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            var data=java.nio.ByteBuffer.wrap(payload).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            int pid=header.getInt(16),session=header.getInt(20),epoch=header.getInt(32),enabled=header.getInt(28),ack=header.getInt(36);
            int actorCount=header.getInt(44),ready=header.getInt(48),heartbeat=header.getInt(24);
            if(actorCount<0 || actorCount>ACTOR_CAPACITY) throw new IllegalStateException("HalfCraft actor count invalid");
            var actorBoxes=new ArrayList<AABB>();
            for(int i=0;i<actorCount;++i) {
                float[] values=new float[6];
                for(int j=0;j<6;++j) {
                    values[j]=data.getFloat(i*24+j*4);
                    if(!Float.isFinite(values[j]) || Math.abs(values[j])>2048) throw new IllegalStateException("HalfCraft actor bounds invalid");
                }
                if(values[3]<=values[0] || values[4]<=values[1] || values[5]<=values[2]) throw new IllegalStateException("HalfCraft actor box invalid");
                actorBoxes.add(new AABB(values[0],values[1],values[2],values[3],values[4],values[5]));
            }
            boolean live=enabled!=0 && link.matchesHost(pid,session) && HalfCraftPhysics.active() && mc.player!=null && mc.level!=null;
            actors=new Actors(java.util.List.copyOf(actorBoxes),heartbeat,live && ready!=0);
            int seq=get(64); if((seq&1)!=0 || (seq!=ack && Integer.toUnsignedLong(HalfCraftLink.tick()-get(80))<2000)) return;
            var boxes=new ArrayList<AABB>();
            if(live) {
                var center=mc.player.blockPosition();
                // ponytail: 9x7x9 blocks and1024 boxes; chunk collision stream replaces this for distant NPCs.
                for(var pos:BlockPos.betweenClosed(center.offset(-4,-2,-4),center.offset(4,4,4))) {
                    if(!mc.level.hasChunkAt(pos)) continue;
                    var state=mc.level.getBlockState(pos);
                    if(state.isAir()) continue;
                    for(var box:state.getCollisionShape(mc.level,pos,CollisionContext.empty()).toAabbs()) {
                        var world=box.move(pos.getX(),pos.getY(),pos.getZ());
                        if(boxes.size()==CAPACITY) throw new IllegalStateException("HalfCraft block collision capacity exceeded");
                        boxes.add(world);
                    }
                }
            }
            put(64,seq+1); put(68,(int)ProcessHandle.current().pid()); put(72,session); put(76,epoch);
            put(80,HalfCraftLink.tick()); put(84,boxes.size()); put(88,++generation); put(92,live?1:0);
            for(int i=0;i<boxes.size();++i) {
                var b=boxes.get(i); long offset=128L+i*24;
                memory.set(JAVA_FLOAT,offset,(float)b.minX); memory.set(JAVA_FLOAT,offset+4,(float)b.minY); memory.set(JAVA_FLOAT,offset+8,(float)b.minZ);
                memory.set(JAVA_FLOAT,offset+12,(float)b.maxX); memory.set(JAVA_FLOAT,offset+16,(float)b.maxY); memory.set(JAVA_FLOAT,offset+20,(float)b.maxZ);
            }
            put(64,seq+2);
        } catch(Throwable error) { close(); HalfCraftGuest.LOG.error("HalfCraft block collision export failed",error); }
    }
    @Override public void close() {
        actors=new Actors(java.util.List.of(),0,false);
        try {
            if(memory!=null) { int ok=(int)UNMAP.invokeExact(memory); memory=null; }
            if(handle.address()!=0) { int ok=(int)CLOSE.invokeExact(handle); handle=MemorySegment.NULL; }
        } catch(Throwable error) { HalfCraftGuest.LOG.error("HalfCraft block collision cleanup failed",error); }
    }
}
