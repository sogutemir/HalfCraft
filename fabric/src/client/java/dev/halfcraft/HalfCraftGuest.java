package dev.halfcraft;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Visible observation-only profile; no SkyCraft gameplay or renderer mixins activated. */
public final class HalfCraftGuest implements ClientModInitializer {
    private static HalfCraftGuest instance;
    public static void beforeStop(net.minecraft.client.Minecraft minecraft) { if(instance!=null) instance.physics.beforeStop(minecraft); }
    public static final Logger LOG=LoggerFactory.getLogger("halfcraft");
    private final HalfCraftLink link=new HalfCraftLink();
    private final HalfCraftPhysics physics=new HalfCraftPhysics();
    private final HalfCraftInput input=new HalfCraftInput();
    private final HalfCraftUI ui=new HalfCraftUI();
    public static void captureUI(net.minecraft.client.Minecraft minecraft) { if(instance!=null) instance.ui.capture(minecraft); }
    private final HalfCraftRender render=new HalfCraftRender();
    private final HalfCraftScene scene=new HalfCraftScene();
    private final HalfCraftBlocks blocks=new HalfCraftBlocks();
    private final HalfCraftResources resources=new HalfCraftResources();
    private final HalfCraftInteraction interaction=new HalfCraftInteraction();
    private final HalfCraftVitals vitals=new HalfCraftVitals();
    private final dev.skycraft.client.render.HalfCraftExporter exporter=new dev.skycraft.client.render.HalfCraftExporter();
    public static void sectionDirty(int x,int y,int z) { if(instance!=null) instance.exporter.dirty(x,y,z); }
    public static void renderFrame(net.minecraft.client.Minecraft minecraft) {
        if(instance==null) return;
        try {
            if(instance.render.poll(instance.link)) {
                instance.scene.poll(instance.link);
                long started=System.nanoTime(); instance.exporter.frame(minecraft,instance.render,instance.scene);
                instance.render.exportTime((System.nanoTime()-started)/1000);
            }
        }
        catch(RuntimeException e) { LOG.error("HalfCraft render export failed",e); instance.render.close(); }
    }
    private final java.util.concurrent.ScheduledExecutorService heartbeat=java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread=new Thread(r,"HalfCraft heartbeat"); thread.setDaemon(true); return thread;
    });
    @Override public void onInitializeClient() {
        instance=this;
        HalfCraftInventory.init();
        HalfCraftInteraction.init();
        HalfCraftVitals.init();
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents.CHUNK_LOAD.register((level,chunk,newChunk) -> {
            if(!HalfCraftWorld.owned(level.getServer().getWorldData().getLevelName())) return;
            // Old flat bridge saves generated an unreachable bedrock floor at -64.
            // Preserve player blocks; survival never supplied bedrock in these worlds.
            var base=chunk.getPos().getWorldPosition();
            for(int x=0;x<16;x++) for(int z=0;z<16;z++) {
                var pos=new net.minecraft.core.BlockPos(base.getX()+x,-64,base.getZ()+z);
                if(chunk.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.BEDROCK)) {
                    chunk.setBlockState(pos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),0); chunk.markUnsaved();
                }
            }
        });
        HalfCraftLink.layoutCheck();
        LOG.info("HalfCraft guest initialized pid={}",ProcessHandle.current().pid());
        heartbeat.scheduleWithFixedDelay(() -> { try { link.poll(); } catch(RuntimeException e) { LOG.error("HalfCraft link failed",e); } },0,100,java.util.concurrent.TimeUnit.MILLISECONDS);
        ClientTickEvents.START_CLIENT_TICK.register(minecraft -> { input.tick(minecraft,link); ui.tick(minecraft,link); });
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
            if(link.connected()) HalfCraftWorld.openWhenReady(minecraft,link,physics);
            physics.tick(minecraft,link);
            vitals.tick(minecraft,link);
            interaction.tick(minecraft,link);
            blocks.tick(minecraft,link);
            resources.tick(minecraft,link);
            var player=minecraft.player;
            if(player==null || minecraft.level==null) { link.sample(new HalfCraftLink.Sample(false,false,new double[3],new double[3])); return; }
            var velocity=player.getDeltaMovement();
            link.sample(new HalfCraftLink.Sample(true,player.onGround(),new double[]{player.getX(),player.getY(),player.getZ()},new double[]{velocity.x,velocity.y,velocity.z}));
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(minecraft -> { heartbeat.shutdownNow(); input.close(); interaction.close(); vitals.close(); ui.close(); render.close(); scene.close(); blocks.close(); physics.close(); link.close(); });
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { heartbeat.shutdownNow(); link.close(); },"HalfCraft shutdown"));
    }
}
