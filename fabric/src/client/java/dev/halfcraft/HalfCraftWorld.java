package dev.halfcraft;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;

/** Derived from SkyCraft MirrorWorld.openWhenReady; separate normal world, no host teleport. */
final class HalfCraftWorld {
    private static String attempted;
    static boolean owned(String name) { return name.equals("HalfCraftPhysics") || name.matches("HalfCraftPhysics_[a-z0-9_-]{1,63}"); }
    static void openWhenReady(Minecraft minecraft,HalfCraftLink link,HalfCraftPhysics physics) {
        var host=link.host();
        if(host==null || !host.player() || !host.map().matches("[a-zA-Z0-9_-]{1,63}")) return;
        String world=host.map().equalsIgnoreCase("c1a0") ? "HalfCraftPhysics" : "HalfCraftPhysics_"+host.map().toLowerCase(java.util.Locale.ROOT);
        if(minecraft.level!=null) {
            var server=minecraft.getSingleplayerServer();
            if(server!=null && owned(server.getWorldData().getLevelName()) && !world.equals(server.getWorldData().getLevelName())) {
                link.sample(new HalfCraftLink.Sample(false,false,new double[3],new double[3]));
                link.poll();
                if(minecraft.player!=null) minecraft.player.closeContainer();
                server.submit(() -> {
                    for(var player:server.getPlayerList().getPlayers()) { player.closeContainer(); HalfCraftInventory.save(server,player); }
                }).join();
                physics.beforeStop(minecraft);
                minecraft.disconnectWithSavingScreen();
                minecraft.gui.setScreen(new TitleScreen());
                attempted=null;
                HalfCraftGuest.LOG.info("HC_WORLD switching map={} world={}",host.map(),world);
            }
            return;
        }
        if(world.equals(attempted) || minecraft.gui.overlay()!=null || !(minecraft.gui.screen() instanceof TitleScreen title)) return;
        attempted=world;
        minecraft.options.pauseOnLostFocus=false;
        if(minecraft.getLevelSource().levelExists(world)) {
            HalfCraftGuest.LOG.info("HalfCraft opening dedicated world");
            minecraft.createWorldOpenFlows().openWorld(world,() -> minecraft.gui.setScreen(title));
        } else {
            HalfCraftGuest.LOG.info("HalfCraft creating dedicated world");
            var settings=new LevelSettings(world,GameType.SURVIVAL,new LevelSettings.DifficultySettings(Difficulty.NORMAL,false,false),true,WorldDataConfiguration.DEFAULT);
            minecraft.createWorldOpenFlows().createFreshLevel(world,settings,new WorldOptions(17L,true,false),
                registries -> registries.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(ResourceKey.create(Registries.WORLD_PRESET,Identifier.fromNamespaceAndPath("halfcraft_bridge","physics"))).value().createWorldDimensions(),title);
        }
    }
}
