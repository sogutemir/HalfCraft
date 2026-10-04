package dev.skycraft.client.render;

import dev.halfcraft.HalfCraftGuest;
import dev.halfcraft.HalfCraftRender;
import java.nio.ByteBuffer;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** Uses SkyCraft's real baked-quad exporter and atlas; no Skyrim world/combat coupling. */
public final class HalfCraftExporter {
    private final WorldExporter.MeshBuilder mesh=new WorldExporter.MeshBuilder();
    private final Map<Long,net.minecraft.world.level.block.state.BlockState[]> sent=new HashMap<>();
    private SkyAtlas atlas;
    private ClientLevel level;
    private ModelBlockRenderer renderer;
    private int transportGeneration,generation,cursor;
    private final Set<Long> dirty=new LinkedHashSet<>();
    private final ByteBuffer cracks=ByteBuffer.allocateDirect(16*48).order(java.nio.ByteOrder.LITTLE_ENDIAN);
    private final ByteBuffer items=ByteBuffer.allocateDirect(128*80).order(java.nio.ByteOrder.LITTLE_ENDIAN);
    private final Map<net.minecraft.world.level.block.state.BlockState,float[]> itemFaces=new HashMap<>();
    public synchronized void dirty(int x,int y,int z) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.player!=null && Math.abs(x-SectionPos.blockToSectionCoord(mc.player.getBlockX()))<=1
            && Math.abs(y-SectionPos.blockToSectionCoord(mc.player.getBlockY()))<=1
            && Math.abs(z-SectionPos.blockToSectionCoord(mc.player.getBlockZ()))<=1) dirty.add(SectionPos.asLong(x,y,z));
    }
    public void frame(Minecraft mc,HalfCraftRender transport,dev.halfcraft.HalfCraftScene scene) {
        if(mc.player==null || mc.level==null) return;
        if(level!=mc.level || transportGeneration!=transport.generation() || atlas==null || atlas.stale(mc)) {
            SkyAtlas next=atlas!=null && !atlas.stale(mc) ? atlas : SkyAtlas.build(mc);
            if(!transport.atlas(generation+1,next.width,next.height,next.pixels.duplicate().clear())) return;
            generation++; transportGeneration=transport.generation(); atlas=next; level=mc.level;
            mesh.halfcraftAtlas=atlas; mesh.opaqueOnly=true;
            renderer=new ModelBlockRenderer(mc.options.ambientOcclusion().get(),true,mc.getBlockColors());
            sent.clear(); synchronized(this) { dirty.clear(); } cursor=0;
            itemFaces.clear();
            HalfCraftGuest.LOG.info("HC_RENDER atlas={}x{} generation={}",atlas.width,atlas.height,generation);
        }
        cracks.clear();
        for(var progress:((dev.skycraft.client.mixin.ClientLevelAccessor)level).skycraft$destroyingBlocks().values()) {
            int stage=progress.getProgress();
            if(stage<0 || stage>9 || cracks.remaining()<48) continue;
            var pos=progress.getPos();
            var shape=level.getBlockState(pos).getShape(level,pos);
            if(shape.isEmpty() || pos.distToCenterSqr(mc.player.position())>64) continue;
            var box=shape.bounds().move(pos).inflate(.004);
            for(double value:new double[]{box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ}) cracks.putFloat((float)value);
            for(float uv:atlas.crackUv(stage)) cracks.putFloat(uv);
            cracks.putInt(stage).putInt(0);
        }
        int nativeStage=dev.halfcraft.HalfCraftInteraction.miningStage();
        var nativeBox=dev.halfcraft.HalfCraftInteraction.miningBox();
        if(nativeStage>=0 && nativeBox!=null && cracks.remaining()>=48) {
            for(double value:new double[]{nativeBox.minX,nativeBox.minY,nativeBox.minZ,nativeBox.maxX,nativeBox.maxY,nativeBox.maxZ}) cracks.putFloat((float)value);
            for(float uv:atlas.crackUv(nativeStage)) cracks.putFloat(uv);
            cracks.putInt(nativeStage).putInt(0);
        }
        transport.cracks(generation,cracks.flip());
        items.clear();
        float partialTick=mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        for(var entity:level.entitiesForRendering()) {
            if(!(entity instanceof net.minecraft.world.entity.item.ItemEntity item) || item.isRemoved()
                || item.getItem().isEmpty() || item.distanceToSqr(mc.player)>96*96 || items.remaining()<80) continue;
            var stack=item.getItem();
            var p=item.getPosition(partialTick);
            float age=item.getAge()+partialTick;
            float bob=net.minecraft.util.Mth.sin(age/10f+item.bobOffs)*.1f+.1f;
            float spin=net.minecraft.world.entity.item.ItemEntity.getSpin(age,item.bobOffs)*net.minecraft.util.Mth.RAD_TO_DEG;
            float[] uv=null; int kind=0,tint=0; float size=.5f,y=(float)p.y+bob+.25f;
            if(stack.getItem() instanceof net.minecraft.world.item.BlockItem blockItem) {
                var state=blockItem.getBlock().defaultBlockState();
                if(state.getRenderShape()==RenderShape.MODEL && net.minecraft.world.level.block.Block.isShapeFullBlock(state.getShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,BlockPos.ZERO))) {
                    uv=itemFaces.computeIfAbsent(state,s -> WorldExporter.cubeFaces(mc,s,atlas));
                    if(uv!=null) { kind=1; size=.25f; y=(float)p.y+bob+size*.5f+.02f; tint=WorldExporter.cubeTint(mc,state); }
                }
            }
            if(uv==null) uv=WorldExporter.iconUv(mc,level,stack,atlas);
            if(uv==null) continue;
            items.putInt(item.getId()).putInt(kind).putFloat((float)p.x).putFloat(y).putFloat((float)p.z).putFloat(spin).putFloat(size).putInt(tint);
            for(int i=0;i<12;++i) items.putFloat(i<uv.length?uv[i]:0);
        }
        transport.items(generation,items.flip());
        AvatarExporter.halfcraftBlockEntities(mc,atlas,partialTick,scene::send);
        if(!transport.meshReady()) return;
        int px=SectionPos.blockToSectionCoord(mc.player.getBlockX()),py=SectionPos.blockToSectionCoord(mc.player.getBlockY()),pz=SectionPos.blockToSectionCoord(mc.player.getBlockZ());
        for(var iterator=sent.keySet().iterator();iterator.hasNext();) {
            long key=iterator.next(); int x=SectionPos.x(key),y=SectionPos.y(key),z=SectionPos.z(key);
            if(Math.abs(x-px)>1 || Math.abs(y-py)>1 || Math.abs(z-pz)>1) {
                if(transport.mesh(generation,x,y,z,0,ByteBuffer.allocate(0))) iterator.remove(); return;
            }
        }
        // ponytail: 27 nearby sections, one scan/mesh per frame; dirty-section hooks replace scans at larger ranges.
        int index=cursor++%27,x=px+index%3-1,y=py+index/3%3-1,z=pz+index/9-1;
        boolean force=false;
        synchronized(this) {
            if(!dirty.isEmpty()) { long key=dirty.iterator().next(); dirty.remove(key); x=SectionPos.x(key); y=SectionPos.y(key); z=SectionPos.z(key); force=true; }
        }
        if(Math.abs(x-px)>1 || Math.abs(y-py)>1 || Math.abs(z-pz)>1) return;
        var chunk=level.getChunkSource().getChunk(x,z,ChunkStatus.FULL,false); if(chunk==null) return;
        int sectionIndex=level.getSectionIndexFromSectionY(y); if(sectionIndex<0 || sectionIndex>=chunk.getSections().length) return;
        var section=chunk.getSections()[sectionIndex]; long key=SectionPos.asLong(x,y,z);
        var states=new net.minecraft.world.level.block.state.BlockState[4096];
        for(int by=0;by<16;by++) for(int bz=0;bz<16;bz++) for(int bx=0;bx<16;bx++) states[bx+16*bz+256*by]=section.getBlockState(bx,by,bz);
        if(!force && Arrays.equals(sent.get(key),states)) return;
        if(section.hasOnlyAir() && !sent.containsKey(key)) return;
        mesh.reset(); mesh.cardinal=level.cardinalLighting();
        BlockPos.MutableBlockPos pos=new BlockPos.MutableBlockPos();
        for(int by=0;by<16;by++) for(int bz=0;bz<16;bz++) for(int bx=0;bx<16;bx++) {
            var state=section.getBlockState(bx,by,bz); if(state.getRenderShape()!=RenderShape.MODEL) continue;
            pos.set(x*16+bx,y*16+by,z*16+bz);
            var model=mc.getModelManager().getBlockStateModelSet().get(state);
            renderer.tesselateBlock(mesh,bx,by,bz,level,pos.immutable(),state,model,state.getSeed(pos));
        }
        if(transport.mesh(generation,x,y,z,mesh.vertexCount(),mesh.bytes())) sent.put(key,states);
    }
}
