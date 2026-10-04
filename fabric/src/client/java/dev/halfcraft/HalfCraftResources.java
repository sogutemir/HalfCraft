package dev.halfcraft;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.nbt.CompoundTag;

/** Finite authored resource cells. Vanilla mining, loot, tools and crafting own all rewards. */
public final class HalfCraftResources {
    private record Cell(BlockPos pos,BlockState state) {}
    // ponytail: first c1a0 resource fixture; expand authored locations after campaign path checks.
    private static final List<Cell> START=List.of(
        new Cell(new BlockPos(10,-6,-8),Blocks.OAK_LOG.defaultBlockState()),
        new Cell(new BlockPos(10,-5,-8),Blocks.OAK_LOG.defaultBlockState()),
        new Cell(new BlockPos(10,-4,-8),Blocks.OAK_LOG.defaultBlockState()),
        new Cell(new BlockPos(7,-6,-9),Blocks.STONE.defaultBlockState()),
        new Cell(new BlockPos(7,-5,-9),Blocks.COAL_ORE.defaultBlockState()),
        new Cell(new BlockPos(7,-4,-9),Blocks.IRON_ORE.defaultBlockState()));
    private static final List<Cell> MINE=List.of(
        new Cell(new BlockPos(9,-6,-9),Blocks.STONE.defaultBlockState()),
        new Cell(new BlockPos(9,-5,-9),Blocks.STONE.defaultBlockState()),
        new Cell(new BlockPos(9,-4,-9),Blocks.STONE.defaultBlockState()),
        new Cell(new BlockPos(9,-6,-8),Blocks.STONE.defaultBlockState()),
        new Cell(new BlockPos(9,-5,-8),Blocks.STONE.defaultBlockState()),
        new Cell(new BlockPos(9,-4,-8),Blocks.STONE.defaultBlockState()),
        new Cell(new BlockPos(7,-6,-8),Blocks.STONE.defaultBlockState()),
        new Cell(new BlockPos(7,-5,-8),Blocks.STONE.defaultBlockState()),
        new Cell(new BlockPos(7,-4,-8),Blocks.STONE.defaultBlockState()),
        new Cell(new BlockPos(10,-6,-9),Blocks.STONE.defaultBlockState()),
        new Cell(new BlockPos(10,-5,-9),Blocks.STONE.defaultBlockState()),
        new Cell(new BlockPos(10,-4,-9),Blocks.IRON_ORE.defaultBlockState()));
    private static final List<Cell> DEEP=List.of(
        new Cell(new BlockPos(8,-6,-9),Blocks.IRON_ORE.defaultBlockState()),
        new Cell(new BlockPos(8,-5,-9),Blocks.IRON_ORE.defaultBlockState()),
        new Cell(new BlockPos(8,-6,-8),Blocks.COPPER_ORE.defaultBlockState()),
        new Cell(new BlockPos(8,-5,-8),Blocks.COAL_ORE.defaultBlockState()),
        new Cell(new BlockPos(8,-4,-9),Blocks.DIAMOND_ORE.defaultBlockState()));
    private static final List<Cell> OLD_LANE=List.of(
        new Cell(new BlockPos(10,-6,-7),Blocks.STONE.defaultBlockState()),new Cell(new BlockPos(10,-5,-7),Blocks.COAL_ORE.defaultBlockState()),new Cell(new BlockPos(10,-4,-7),Blocks.IRON_ORE.defaultBlockState()),
        new Cell(new BlockPos(9,-6,-7),Blocks.STONE.defaultBlockState()),new Cell(new BlockPos(9,-5,-7),Blocks.STONE.defaultBlockState()),new Cell(new BlockPos(9,-4,-7),Blocks.STONE.defaultBlockState()),
        new Cell(new BlockPos(8,-5,-7),Blocks.DIAMOND_ORE.defaultBlockState()));
    private static final List<BlockPos> NEW_LANE=List.of(new BlockPos(7,-6,-9),new BlockPos(7,-5,-9),new BlockPos(7,-4,-9),new BlockPos(7,-6,-8),new BlockPos(7,-5,-8),new BlockPos(7,-4,-8),new BlockPos(8,-4,-9));
    private static final Identifier LANE_KEY=Identifier.fromNamespaceAndPath("halfcraft_bridge","resource_lane_v3");
    private static final Identifier KEY=Identifier.fromNamespaceAndPath("halfcraft_bridge","resources_v1");
    private static final Identifier MINE_KEY=Identifier.fromNamespaceAndPath("halfcraft_bridge","resources_mine_v1");
    private static final Identifier DEEP_KEY=Identifier.fromNamespaceAndPath("halfcraft_bridge","resources_deep_v1");
    private CompletableFuture<?> pending;
    private Object level;
    private boolean done;
    private int phase;
    private boolean migrated;
    private long nextLog;
    public void tick(Minecraft mc,HalfCraftLink link) {
        if(level!=mc.level) { level=mc.level; done=false; pending=null; phase=0; migrated=false; }
        if(done || pending!=null || !HalfCraftPhysics.active() || mc.player==null || mc.level==null || link.host()==null || !link.host().map().equalsIgnoreCase("c1a0")) return;
        var server=mc.getSingleplayerServer(); if(server==null || !server.getWorldData().getLevelName().equals("HalfCraftPhysics")) return;
        var cells=phase==0?START:phase==1?MINE:DEEP;
        var key=phase==0?KEY:phase==1?MINE_KEY:DEEP_KEY;
        var sourceLevel=mc.level;
        // Check every authored cell before scheduling; preserve NPC/player/native occupancy.
        for(var cell:migrated?cells:List.<Cell>of()) {
            var box=new AABB(cell.pos()).deflate(.01);
            if(!mc.level.getBlockState(cell.pos()).isAir()) continue;
            if(box.intersects(mc.player.getBoundingBox()) || !HalfCraftPhysics.near(box,true).isEmpty()
                || !HalfCraftBlocks.canPlace(mc.level,cell.pos(),cell.state())) return;
        }
        for(int i=0;i<NEW_LANE.size();i++) {
            var pos=NEW_LANE.get(i); var box=new AABB(pos).deflate(.01);
            if(box.intersects(mc.player.getBoundingBox()) || !HalfCraftPhysics.near(box,true).isEmpty()
                || !HalfCraftBlocks.canPlace(mc.level,pos,OLD_LANE.get(i).state())) {
                if(System.currentTimeMillis()>nextLog) { nextLog=System.currentTimeMillis()+5000; HalfCraftGuest.LOG.info("HC_RESOURCES lane waits pos={} native={} actors={}",pos,HalfCraftPhysics.near(box,true).size(),HalfCraftBlocks.canPlace(mc.level,pos,OLD_LANE.get(i).state())); }
                return;
            }
        }
        pending=server.submit(() -> {
            var world=server.overworld();
            if(!server.getCommandStorage().get(LANE_KEY).getBooleanOr("moved",false)) {
                boolean authored=server.getCommandStorage().get(KEY).getBooleanOr("seeded",false);
                for(int i=0;authored && i<OLD_LANE.size();i++) {
                    var old=OLD_LANE.get(i); var destination=NEW_LANE.get(i);
                    if(world.getBlockState(old.pos()).equals(old.state()) && world.getBlockState(destination).isAir()) {
                        world.setBlock(destination,old.state(),3); world.setBlock(old.pos(),Blocks.AIR.defaultBlockState(),3);
                    }
                }
                var marker=new CompoundTag(); marker.putBoolean("moved",true); server.getCommandStorage().set(LANE_KEY,marker);
                HalfCraftGuest.LOG.info("HC_RESOURCES lane migration complete");
            }
            if(!migrated) return;
            var saved=server.getCommandStorage().get(key);
            if(saved.getBooleanOr("seeded",false)) return;
            for(var cell:cells) if(!world.getBlockState(cell.pos()).isAir()) return;
            for(var cell:cells) world.setBlock(cell.pos(),cell.state(),3);
            var marker=new CompoundTag(); marker.putBoolean("seeded",true); server.getCommandStorage().set(key,marker);
            HalfCraftGuest.LOG.info("HC_RESOURCES seeded c1a0 cells={}",cells.size());
        });
        pending.whenComplete((value,error) -> mc.execute(() -> {
            if(mc.level!=sourceLevel) return;
            if(error!=null) { HalfCraftGuest.LOG.error("HC_RESOURCES seed failed",error); pending=null; }
            else { pending=null; if(!migrated) migrated=true; else done=++phase>=3; }
        }));
    }
}
