package dev.halfcraft;

import java.lang.foreign.*;
import java.lang.invoke.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static java.lang.foreign.ValueLayout.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

/** Bounded BSP masks, consumed by vanilla client AND integrated-server collision. */
public final class HalfCraftPhysics implements AutoCloseable {
    static final int BYTES=4288,GUEST=4224;
    private static final SymbolLookup K32=SymbolLookup.libraryLookup("kernel32",Arena.global());
    private static MethodHandle function(String name,FunctionDescriptor descriptor) {
        return Linker.nativeLinker().downcallHandle(K32.find(name).orElseThrow(),descriptor);
    }
    private static final MethodHandle OPEN=function("OpenFileMappingW",FunctionDescriptor.of(ADDRESS,JAVA_INT,JAVA_INT,ADDRESS));
    private static final MethodHandle MAP=function("MapViewOfFile",FunctionDescriptor.of(ADDRESS,ADDRESS,JAVA_INT,JAVA_INT,JAVA_INT,JAVA_LONG));
    private static final MethodHandle UNMAP=function("UnmapViewOfFile",FunctionDescriptor.of(JAVA_INT,ADDRESS));
    private static final MethodHandle CLOSE=function("CloseHandle",FunctionDescriptor.of(JAVA_INT,ADDRESS));
    private static final VarHandle INT=JAVA_INT.varHandle();
    private MemorySegment handle=MemorySegment.NULL,memory;
    private int epoch,session,generation;
    private int correctionAck,pendingCorrection;
    private boolean teleported;
    private boolean handoffPending;
    private boolean restoreFlying;
    private java.util.concurrent.CompletableFuture<?> teleportDone;
    private Vec3 activationFeet;
    private long activationTime;
    private Vec3 restore;
    private long nextLog;
    public record Region(int x,int y,int z,List<VoxelShape> shapes,List<VoxelShape> itemShapes) {}
    private static volatile Region region;
    private static volatile boolean active;
    private static volatile boolean nativeControl;
    public static boolean active() { return active; }
    public static boolean nativeControl() { return active && nativeControl; }
    private static VoxelShape rayShape(VoxelShape shape) {
        var b=shape.bounds().inflate(-.225,0,-.225);
        return Shapes.create(b);
    }
    public static boolean occluded(Vec3 start,Vec3 end) {
        if(!active || region==null) return false;
        for(var shape:region.shapes()) {
            var hit=rayShape(shape).clip(start,end,net.minecraft.core.BlockPos.ZERO);
            if(hit!=null && hit.getLocation().distanceToSqr(start)+.0025<end.distanceToSqr(start)) return true;
        }
        return false;
    }
    public static net.minecraft.world.phys.BlockHitResult nativeHit(Vec3 start,Vec3 end) {
        if(!active || region==null) return null;
        net.minecraft.world.phys.BlockHitResult nearest=null;
        for(var shape:region.shapes()) {
            var hit=rayShape(shape).clip(start,end,net.minecraft.core.BlockPos.ZERO);
            if(hit!=null && (nearest==null || hit.getLocation().distanceToSqr(start)<nearest.getLocation().distanceToSqr(start))) nearest=hit;
        }
        if(nearest==null) return null;
        var direction=nearest.getDirection();
        var point=nearest.getLocation();
        // Air cell beside native surface lets vanilla BlockItem place without inventing fake BSP blocks.
        var cell=net.minecraft.core.BlockPos.containing(point.add(direction.getStepX()*.001,direction.getStepY()*.001,direction.getStepZ()*.001));
        return new net.minecraft.world.phys.BlockHitResult(point,direction,cell,false);
    }
    public static List<VoxelShape> near(AABB box) {
        return near(box,false);
    }
    public static List<VoxelShape> near(AABB box,boolean item) {
        Region value=region;
        if(!active || value==null) return List.of();
        List<VoxelShape> result=new ArrayList<>();
        for(var shape:item?value.itemShapes():value.shapes()) if(shape.bounds().intersects(box)) result.add(shape);
        // Fail closed at unstreamed region edge, never walk into unknown void.
        double x=value.x(),y=value.y(),z=value.z();
        for(AABB wall:List.of(new AABB(x-1,y-1,z-1,x,y+9,z+9),new AABB(x+8,y-1,z-1,x+9,y+9,z+9),
            new AABB(x-1,y-1,z-1,x+9,y+9,z),new AABB(x-1,y-1,z+8,x+9,y+9,z+9),
            new AABB(x-1,y-1,z-1,x+9,y,z+9),new AABB(x-1,y+8,z-1,x+9,y+9,z+9)))
            if(wall.intersects(box)) result.add(Shapes.create(wall));
        return result;
    }
    private int get(int off) { return (int)INT.getAcquire(memory,(long)off); }
    private void put(int off,int value) { INT.setVolatile(memory,(long)off,value); }
    private void publish(Minecraft mc,boolean enabled) {
        int seq=(get(GUEST)+1)&~1;
        put(GUEST,seq+1); put(GUEST+4,(int)ProcessHandle.current().pid()); put(GUEST+8,session); put(GUEST+12,epoch);
        put(GUEST+16,enabled?1:0); put(GUEST+20,HalfCraftLink.tick());
        put(GUEST+24,correctionAck);
        var p=mc.player;
        if(p!=null) {
            put(GUEST+28,((int)Math.round(p.getEyeHeight()*1000)<<8)|(p.isCrouching()?1:0));
            memory.set(JAVA_DOUBLE,GUEST+32,p.getX()); memory.set(JAVA_DOUBLE,GUEST+40,p.getY()); memory.set(JAVA_DOUBLE,GUEST+48,p.getZ());
            memory.set(JAVA_FLOAT,GUEST+56,p.getYRot()); memory.set(JAVA_FLOAT,GUEST+60,p.getXRot());
        }
        put(GUEST,seq+2);
    }
    private void teleport(Minecraft mc,Vec3 position,float yaw,float pitch) {
        teleport(mc,position,yaw,pitch,Vec3.ZERO);
    }
    private void teleport(Minecraft mc,Vec3 position,float yaw,float pitch,Vec3 velocity) {
        // Conservative 10-unit floor samples may sit up to .25 block above exact native feet.
        // Settle on their top, rather than repeatedly falling into native hull corrections.
        if(active && region!=null && !nativeControl) {
            double y=position.y;
            double height=mc.player.getBbHeight();
            var footprint=new AABB(position.x-.3,position.y,position.z-.3,position.x+.3,position.y+height,position.z+.3);
            for(var shape:region.shapes()) {
                var b=shape.bounds();
                if(b.maxY>y && b.maxY<=position.y+.25 && b.minY<=position.y && b.maxX>footprint.minX && b.minX<footprint.maxX && b.maxZ>footprint.minZ && b.minZ<footprint.maxZ) y=b.maxY;
            }
            position=new Vec3(position.x,y,position.z); activationFeet=position;
            // Reconcile exact HL origin with conservative streamed wall bounds on activation/correction.
            for(int pass=0;pass<8;++pass) {
                var body=new AABB(position.x-.3,position.y+.00001,position.z-.3,position.x+.3,position.y+height,position.z+.3);
                boolean moved=false;
                for(var shape:region.shapes()) {
                    var b=shape.bounds(); if(!b.intersects(body)) continue;
                    double dx=b.minX-body.maxX-.00001;
                    if(Math.abs(b.maxX-body.minX+.00001)<Math.abs(dx)) dx=b.maxX-body.minX+.00001;
                    double dz=b.minZ-body.maxZ-.00001;
                    if(Math.abs(b.maxZ-body.minZ+.00001)<Math.abs(dz)) dz=b.maxZ-body.minZ+.00001;
                    position=position.add(Math.abs(dx)<Math.abs(dz)?dx:0,0,Math.abs(dx)<Math.abs(dz)?0:dz);
                    moved=true; break;
                }
                if(!moved) break;
            }
            activationFeet=position;
            // HL-safe origin can now be inside a placed MC block. Escape upward only through
            // native-free space; otherwise pushOutOfBlocks repeatedly shoves us into the BSP wall.
            var box=mc.player.getBoundingBox().move(position.subtract(mc.player.position()));
            if(!mc.level.noCollision(mc.player,box)) {
                for(int step=1;step<=5;++step) {
                    var candidate=box.move(0,step*.25,0);
                    boolean nativeClear=true;
                    for(var shape:near(box.expandTowards(0,step*.25,0)))
                        if(shape.bounds().intersects(box.expandTowards(0,step*.25,0))) { nativeClear=false; break; }
                    if(nativeClear && mc.level.noCollision(mc.player,candidate)) {
                        position=position.add(0,step*.25,0); activationFeet=position; break;
                    }
                }
            }
        }
        mc.player.setPos(position); mc.player.setDeltaMovement(velocity); mc.player.fallDistance=0; mc.player.setYRot(yaw); mc.player.setXRot(pitch);
        var server=mc.getSingleplayerServer(); var id=mc.player.getUUID(); boolean flying=mc.player.getAbilities().flying;
        Vec3 destination=position;
        teleportDone=server.submit(() -> {
            var player=server.getPlayerList().getPlayer(id);
            if(player!=null) { player.fallDistance=0; player.getAbilities().flying=flying; player.onUpdateAbilities(); player.teleportTo(player.level(),destination.x,destination.y,destination.z,Set.of(),yaw,pitch,false); player.setDeltaMovement(velocity); if(nativeControl) player.setOnGround(true); }
        });
    }
    private void disable(Minecraft mc) {
        if(teleported && mc.player!=null && mc.player.isAlive() && mc.getSingleplayerServer()!=null && restore!=null) {
            mc.player.getAbilities().flying=restoreFlying;
            active=false; teleport(mc,restore,mc.player.getYRot(),mc.player.getXRot());
            HalfCraftGuest.LOG.info("HC_PHYSICS guest restored observation position={}",restore);
        }
        active=false; nativeControl=false; teleported=false; handoffPending=false; activationFeet=null; region=null;
        if(memory!=null) publish(mc,false);
    }
    public void beforeStop(Minecraft mc) {
        if(mc.player!=null) mc.player.closeContainer();
        var inventoryServer=mc.getSingleplayerServer();
        if(inventoryServer!=null && !inventoryServer.isStopped() && HalfCraftWorld.owned(inventoryServer.getWorldData().getLevelName()))
            inventoryServer.submit(() -> {
                for(var player:inventoryServer.getPlayerList().getPlayers()) { player.closeContainer(); HalfCraftInventory.save(inventoryServer,player); }
            }).join();
        disable(mc);
        var server=mc.getSingleplayerServer();
        if(server!=null && !server.isStopped()) server.submit(() -> {}).join();
        close();
    }
    public void tick(Minecraft mc,HalfCraftLink link) {
        try {
            if(memory!=null && link.host()!=null && get(16)!=link.host().pid()) { disable(mc); close(); }
            if(memory==null) {
                try(var arena=Arena.ofConfined()) {
                    handle=(MemorySegment)OPEN.invokeExact(0xF001F,0,arena.allocateFrom("Local\\HalfCraft_collision_v1",StandardCharsets.UTF_16LE));
                    if(handle.address()==0) return;
                    MemorySegment view=(MemorySegment)MAP.invokeExact(handle,0xF001F,0,0,(long)BYTES);
                    if(view.address()==0) { int ok=(int)CLOSE.invokeExact(handle); handle=MemorySegment.NULL; return; }
                    memory=view.reinterpret(BYTES);
                }
            }
            byte[] bytes=null;
            for(int i=0;i<16;++i) {
                int seq=get(0); if(seq==0 || (seq&1)!=0) continue;
                byte[] snapshot=memory.asSlice(0,GUEST).toArray(JAVA_BYTE); VarHandle.loadLoadFence();
                if(seq==get(0)) { bytes=snapshot; break; }
            }
            if(bytes==null) return;
            var data=java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            if(data.getInt(4)!=0x43435248 || data.getInt(8)!=2 || data.getInt(12)!=BYTES || data.getInt(60)!=8 || data.getInt(96)!=4)
                throw new IllegalStateException("HalfCraft collision layout mismatch");
            // GoldSrc pauses StartFrame during console/loading; independent bridge heartbeat owns liveness.
            boolean live=link.matchesHost(data.getInt(16),data.getInt(20)) && data.getInt(28)==1;
            if(!live || mc.player==null || !mc.player.isAlive() || !mc.isLocalServer()
                || !HalfCraftWorld.owned(mc.getSingleplayerServer().getWorldData().getLevelName())) { disable(mc); return; }
            if(epoch!=data.getInt(32) || session!=data.getInt(20)) {
                // Same-session ladder/pusher handoff must never visit the observation world's bedrock.
                if(session!=data.getInt(20)) disable(mc);
                handoffPending=teleported;
                epoch=data.getInt(32); session=data.getInt(20); generation=-1; correctionAck=0; pendingCorrection=0; activationFeet=null;
            }
            nativeControl=data.getInt(104)!=0;
            if(handoffPending && data.getInt(40)!=1) {
                mc.player.setDeltaMovement(Vec3.ZERO); mc.player.fallDistance=0;
                publish(mc,false); return;
            }
            if(data.getInt(40)==1 && generation!=data.getInt(36)) {
                int x=data.getInt(48),y=data.getInt(52),z=data.getInt(56);
                if(Math.abs((long)x)>1000000 || y < -1024 || y+8>1024 || Math.abs((long)z)>1000000)
                    throw new IllegalStateException("HalfCraft collision region outside physics world bounds");
                List<VoxelShape> shapes=new ArrayList<>();
                for(int cell=0;cell<512;++cell) {
                    long mask=data.getLong(128+cell*8);
                    if(mask==0) continue;
                    double bx=x+cell%8,by=y+cell/8%8,bz=z+cell/64;
                    if(mask==-1L) { shapes.add(Shapes.create(bx,by,bz,bx+1,by+1,bz+1)); continue; }
                    for(int sy=0;sy<4;++sy) for(int sz=0;sz<4;++sz) {
                        int sx=0;
                        while(sx<4) {
                            if((mask&(1L<<(sx+sy*4+sz*16)))==0) { ++sx; continue; }
                            int start=sx++;
                            while(sx<4 && (mask&(1L<<(sx+sy*4+sz*16)))!=0) ++sx;
                            shapes.add(Shapes.create(bx+start/4.,by+sy/4.,bz+sz/4.,bx+sx/4.,by+(sy+1)/4.,bz+(sz+1)/4.));
                        }
                    }
                }
                var itemShapes=List.copyOf(shapes);
                // Native hull is 4 units wider per side; center samples can miss another 5 units.
                shapes.replaceAll(shape -> Shapes.create(shape.bounds().inflate(.225,0,.225)));
                region=new Region(x,y,z,List.copyOf(shapes),itemShapes); generation=data.getInt(36);
                if(!teleported || handoffPending) {
                    if(!teleported) { restore=mc.player.position(); restoreFlying=mc.player.getAbilities().flying; }
                    mc.player.getAbilities().flying=false; active=true;
                    activationFeet=new Vec3(data.getDouble(64),data.getDouble(72),data.getDouble(80));
                    if(!Double.isFinite(activationFeet.x) || !Double.isFinite(activationFeet.y) || !Double.isFinite(activationFeet.z)) throw new IllegalStateException("Invalid physics spawn");
                    activationTime=System.currentTimeMillis();
                    teleport(mc,activationFeet,data.getFloat(88),data.getFloat(92));
                    teleported=true;
                    handoffPending=false;
                    HalfCraftGuest.LOG.info("HC_PHYSICS guest active epoch={} shapes={} base=[{},{},{}]",epoch,shapes.size(),x,y,z);
                }
            }
            if(teleported) {
                if(nativeControl) {
                    var feet=new Vec3(data.getDouble(64),data.getDouble(72),data.getDouble(80));
                    if(!Double.isFinite(feet.x) || !Double.isFinite(feet.y) || !Double.isFinite(feet.z)) throw new IllegalStateException("Invalid native movement");
                    mc.player.getAbilities().flying=true;
                    boolean duck=data.getInt(108)!=0;
                    mc.player.setPose(duck?net.minecraft.world.entity.Pose.CROUCHING:net.minecraft.world.entity.Pose.STANDING);
                    mc.player.setPos(feet); mc.player.setDeltaMovement(Vec3.ZERO); mc.player.fallDistance=0;
                    if(!HalfCraftInput.takeover()) { mc.player.setYRot(data.getFloat(88)); mc.player.setXRot(data.getFloat(92)); }
                    var server=mc.getSingleplayerServer(); var id=mc.player.getUUID();
                    float yaw=mc.player.getYRot(),pitch=mc.player.getXRot();
                    server.execute(() -> { var player=server.getPlayerList().getPlayer(id); if(player!=null) {
                        player.setPose(duck?net.minecraft.world.entity.Pose.CROUCHING:net.minecraft.world.entity.Pose.STANDING);
                        player.setPos(feet); player.setDeltaMovement(Vec3.ZERO); player.fallDistance=0; player.setOnGround(true);
                        player.setYRot(yaw); player.setXRot(pitch);
                    } });
                    mc.player.setOnGround(true);
                    activationFeet=null; correctionAck=data.getInt(44);
                    active=true; publish(mc,true); return;
                }
                mc.player.getAbilities().flying=false;
                if(activationFeet==null && data.getInt(44)!=correctionAck) {
                    pendingCorrection=data.getInt(44);
                    activationFeet=new Vec3(data.getDouble(64),data.getDouble(72),data.getDouble(80));
                    if(!Double.isFinite(activationFeet.x) || !Double.isFinite(activationFeet.y) || !Double.isFinite(activationFeet.z)) throw new IllegalStateException("Invalid collision correction");
                    activationTime=System.currentTimeMillis();
                    Vec3 velocity=mc.player.getDeltaMovement(), correction=activationFeet.subtract(mc.player.position());
                    Vec3 normal=new Vec3(correction.x,0,correction.z).normalize();
                    if(velocity.dot(normal)<0) velocity=velocity.subtract(normal.scale(velocity.dot(normal)));
                    if(velocity.y*correction.y<0 && Math.abs(correction.y)>.001) velocity=new Vec3(velocity.x,0,velocity.z);
                    teleport(mc,activationFeet,mc.player.getYRot(),mc.player.getXRot(),velocity);
                    HalfCraftGuest.LOG.info("HC_PHYSICS blocked movement correction={} feet={}",pendingCorrection,activationFeet);
                }
                if(activationFeet!=null) {
                    if(teleportDone.isCompletedExceptionally()) throw new IllegalStateException("Physics server teleport failed");
                    if(!teleportDone.isDone() || mc.player.position().distanceTo(activationFeet)>.5) {
                        mc.player.setPos(activationFeet);
                        if(pendingCorrection==0) mc.player.setDeltaMovement(Vec3.ZERO);
                        publish(mc,pendingCorrection!=0);
                        if(System.currentTimeMillis()-activationTime>3000) throw new IllegalStateException("Physics spawn acknowledgement timed out");
                        return;
                    }
                    correctionAck=pendingCorrection;
                    activationFeet=null;
                }
                active=true; publish(mc,true);
                if(System.currentTimeMillis()>=nextLog) {
                    nextLog=System.currentTimeMillis()+2000;
                    HalfCraftGuest.LOG.info("HC_PHYSICS sample position={} velocity={} onGround={} shapes={}",mc.player.position(),mc.player.getDeltaMovement(),mc.player.onGround(),region.shapes().size());
                }
            }
        } catch(Throwable e) { disable(mc); HalfCraftGuest.LOG.error("HalfCraft physics disabled",e); close(); }
    }
    @Override public void close() {
        active=false; region=null;
        try {
            if(memory!=null) { put(GUEST+16,0); int ok=(int)UNMAP.invokeExact(memory); memory=null; }
            if(handle.address()!=0) { int ok=(int)CLOSE.invokeExact(handle); handle=MemorySegment.NULL; }
        } catch(Throwable e) { HalfCraftGuest.LOG.error("Physics cleanup failed",e); }
    }
}
