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

/** Server-world collision boxes mirrored as GoldSrc solids, independent of player position. */
public final class HalfCraftBlocks implements AutoCloseable {
    private static final int CAPACITY=1024,BYTES=30848,ACTOR_CAPACITY=256;
    private record Solids(net.minecraft.server.MinecraftServer server,java.util.List<AABB> boxes,boolean ready) {}
    private static volatile Solids solids=new Solids(null,java.util.List.of(),false);
    // Server-thread only. Retain chunk shapes after unload; unloaded walls still stop native NPCs.
    private static net.minecraft.server.MinecraftServer owner;
    private static final java.util.Map<net.minecraft.world.level.ChunkPos,java.util.List<AABB>> chunks=new java.util.HashMap<>();
    private static final java.util.Set<net.minecraft.world.level.ChunkPos> dirty=new java.util.LinkedHashSet<>();
    private static void reset(net.minecraft.server.MinecraftServer server) {
        owner=server; chunks.clear(); dirty.clear(); solids=new Solids(server,java.util.List.of(),false);
    }
    public static void changed(net.minecraft.world.level.Level level,BlockPos pos) {
        if(level instanceof net.minecraft.server.level.ServerLevel serverLevel && level==serverLevel.getServer().overworld()
            && HalfCraftWorld.owned(serverLevel.getServer().getWorldData().getLevelName())) {
            if(owner!=serverLevel.getServer()) reset(serverLevel.getServer());
            dirty.add(net.minecraft.world.level.ChunkPos.containing(pos));
        }
    }
    public static void init() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents.CHUNK_LOAD.register((level,chunk,newChunk) -> changed(level,chunk.getPos().getWorldPosition()));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if(!HalfCraftWorld.owned(server.getWorldData().getLevelName())) return;
            if(owner!=server) reset(server);
            // Existing worlds may contain walls in chunks never visited during this session.
            var region=server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("region");
            if(java.nio.file.Files.isDirectory(region)) try(var files=java.nio.file.Files.newDirectoryStream(region,"r.*.*.mca")) {
                for(var file:files) {
                    var match=java.util.regex.Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca").matcher(file.getFileName().toString());
                    if(!match.matches()) continue;
                    int rx=Integer.parseInt(match.group(1)),rz=Integer.parseInt(match.group(2));
                    try(var channel=java.nio.channels.FileChannel.open(file,java.nio.file.StandardOpenOption.READ)) {
                        var header=java.nio.ByteBuffer.allocate(4096);
                        while(header.hasRemaining()) if(channel.read(header)<0) throw new java.io.EOFException("Truncated region header: "+file);
                        header.flip();
                        for(int i=0;i<1024;i++) if(header.getInt()!=0) {
                            long x=(long)rx*32+i%32,z=(long)rz*32+i/32;
                            if(x>=-128 && x<128 && z>=-128 && z<128) dirty.add(new net.minecraft.world.level.ChunkPos((int)x,(int)z));
                        }
                    }
                }
            } catch(Exception error) { HalfCraftGuest.LOG.error("HalfCraft saved block collision scan failed",error); return; }
            solids=new Solids(server,java.util.List.of(),dirty.isEmpty());
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            if(owner!=server || !HalfCraftWorld.owned(server.getWorldData().getLevelName()) || server.overworld()==null) return;
            try {
                // ponytail: one saved chunk per tick, 1024 merged boxes; raise protocol/entity budget for larger builds.
                if(!dirty.isEmpty()) {
                    var pos=dirty.iterator().next();
                    var level=server.overworld(); var chunk=level.getChunk(pos.x(),pos.z());
                    var boxes=new ArrayList<AABB>();
                    chunk.findBlocks(state -> !state.isAir(),(block,state) -> {
                        for(var box:state.getCollisionShape(level,block,CollisionContext.empty()).toAabbs()) boxes.add(box.move(block));
                    });
                    if(boxes.isEmpty()) chunks.remove(pos); else chunks.put(pos,boxes);
                    var all=new ArrayList<AABB>(); for(var cached:chunks.values()) all.addAll(cached);
                    // Merge touching boxes only when other dimensions match exactly; preserve slabs/stairs/holes.
                    for(int axis=0;axis<3;axis++) {
                        final int a=axis;
                        all.sort(java.util.Comparator.comparingDouble((AABB b) -> min(b,(a+1)%3)).thenComparingDouble(b -> max(b,(a+1)%3))
                            .thenComparingDouble(b -> min(b,(a+2)%3)).thenComparingDouble(b -> max(b,(a+2)%3)).thenComparingDouble(b -> min(b,a)));
                        var merged=new ArrayList<AABB>();
                        for(var box:all) {
                            var last=merged.isEmpty()?null:merged.getLast();
                            if(last!=null && max(last,a)==min(box,a) && max(box,a)-min(last,a)<=16
                                && min(last,(a+1)%3)==min(box,(a+1)%3) && max(last,(a+1)%3)==max(box,(a+1)%3)
                                && min(last,(a+2)%3)==min(box,(a+2)%3) && max(last,(a+2)%3)==max(box,(a+2)%3)) merged.set(merged.size()-1,last.minmax(box));
                            else merged.add(box);
                        }
                        all=merged;
                    }
                    if(all.size()>CAPACITY) throw new IllegalStateException("HalfCraft block collision capacity exceeded: "+all.size());
                    dirty.remove(pos);
                    solids=new Solids(server,java.util.List.copyOf(all),solids.ready() || dirty.isEmpty());
                }
            } catch(Throwable error) { HalfCraftGuest.LOG.error("HalfCraft server block collision export failed",error); }
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> { if(owner==server) reset(null); });
    }
    private static double min(AABB b,int axis) { return axis==0?b.minX:axis==1?b.minY:b.minZ; }
    private static double max(AABB b,int axis) { return axis==0?b.maxX:axis==1?b.maxY:b.maxZ; }
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
            var snapshot=solids;
            boolean live=enabled!=0 && link.matchesHost(pid,session) && HalfCraftPhysics.active() && mc.player!=null && mc.level!=null
                && snapshot.server()==mc.getSingleplayerServer() && snapshot.ready();
            actors=new Actors(java.util.List.copyOf(actorBoxes),heartbeat,live && ready!=0);
            int seq=get(64); if((seq&1)!=0 || (seq!=ack && Integer.toUnsignedLong(HalfCraftLink.tick()-get(80))<2000)) return;
            var boxes=live?snapshot.boxes():java.util.List.<AABB>of();
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
