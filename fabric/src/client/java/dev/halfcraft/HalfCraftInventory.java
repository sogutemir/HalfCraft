package dev.halfcraft;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.*;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.stats.ServerRecipeBook;
import net.minecraft.world.level.storage.*;

/** One campaign inventory; per-map vanilla block/container saves remain separate. */
public final class HalfCraftInventory {
    public static boolean nativePlayer(net.minecraft.world.entity.Entity entity) {
        return entity instanceof net.minecraft.world.entity.player.Player && entity.level().getServer()!=null
            && owned(entity.level().getServer());
    }
    private static final java.util.Set<java.util.UUID> failed=new java.util.HashSet<>();
    private static boolean owned(MinecraftServer server) { return HalfCraftWorld.owned(server.getWorldData().getLevelName()); }
    private static Path path(MinecraftServer server,ServerPlayer player) {
        return server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getParent().resolve("HalfCraftCampaign").resolve(player.getUUID()+".dat");
    }
    public static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if(!owned(server)) return;
            var rules=server.getGameRules();
            rules.set(net.minecraft.world.level.gamerules.GameRules.SPAWN_MOBS,false,server);
            rules.set(net.minecraft.world.level.gamerules.GameRules.SPAWN_MONSTERS,false,server);
            rules.set(net.minecraft.world.level.gamerules.GameRules.SPAWN_PHANTOMS,false,server);
            rules.set(net.minecraft.world.level.gamerules.GameRules.SPAWN_PATROLS,false,server);
            rules.set(net.minecraft.world.level.gamerules.GameRules.SPAWN_WANDERING_TRADERS,false,server);
            rules.set(net.minecraft.world.level.gamerules.GameRules.KEEP_INVENTORY,true,server);
            rules.set(net.minecraft.world.level.gamerules.GameRules.IMMEDIATE_RESPAWN,true,server);
        });
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server) -> {
            if(!owned(server)) return;
            handler.player.setNoGravity(true);
            failed.remove(handler.player.getUUID());
            try { restore(server,handler.player); }
            catch(IOException|RuntimeException error) { failed.add(handler.player.getUUID()); HalfCraftGuest.LOG.error("HC_INVENTORY restore failed; campaign snapshot preserved",error); handler.disconnect(net.minecraft.network.chat.Component.literal("HalfCraft campaign inventory could not be loaded. Snapshot preserved.")); }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if(!owned(server)) return;
            for(var player:server.getPlayerList().getPlayers()) player.setNoGravity(true);
            if(HalfCraftPhysics.active()) for(var player:server.getPlayerList().getPlayers()) {
                if(!player.isAlive() || player.isSpectator()) continue;
                // SkyCraft pickup seam compensates sampled native surfaces; vanilla owns delay/owner/space.
                for(var entity:player.level().getEntities(player,player.getBoundingBox().inflate(1.25,1,1.25)))
                    if(entity instanceof net.minecraft.world.entity.item.ItemEntity && !entity.isRemoved()) entity.playerTouch(player);
            }
            if(server.getTickCount()%100==0) saveAll(server);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server) -> { if(owned(server)) save(server,handler.player); });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> { if(owned(server)) saveAll(server); });
    }
    private static void saveAll(MinecraftServer server) { for(var player:server.getPlayerList().getPlayers()) save(server,player); }
    public static void save(MinecraftServer server,ServerPlayer player) {
        if(failed.contains(player.getUUID())) return;
        try {
            // Open menus hold transient stacks; vanilla close returns them before the final save.
            if(player.containerMenu!=player.inventoryMenu || !player.containerMenu.getCarried().isEmpty()
                || !player.inventoryMenu.getCraftSlots().isEmpty()) return;
            var reporter=new ProblemReporter.Collector();
            var output=TagValueOutput.createWithContext(reporter,server.registryAccess());
            var slots=output.list("inventory",ItemStackWithSlot.CODEC);
            for(int i=0;i<player.getInventory().getContainerSize();i++) {
                var item=player.getInventory().getItem(i);
                if(!item.isEmpty()) slots.add(new ItemStackWithSlot(i,item.copy()));
            }
            player.getEnderChestInventory().storeAsSlots(output.list("ender",ItemStackWithSlot.CODEC));
            output.store("recipes",ServerRecipeBook.Packed.CODEC,player.getRecipeBook().pack());
            output.putInt("version",1); output.putInt("selected",player.getInventory().getSelectedSlot());
            output.putString("world",server.getWorldData().getLevelName());
            output.putInt("level",player.experienceLevel); output.putInt("xp",player.totalExperience); output.putFloat("progress",player.experienceProgress);
            output.putFloat("health",player.getHealth()); output.putInt("food",player.getFoodData().getFoodLevel());
            output.putFloat("saturation",player.getFoodData().getSaturationLevel());
            if(!reporter.isEmpty()) throw new IOException(reporter.getReport());
            Path target=path(server,player); Files.createDirectories(target.getParent());
            Path temporary=target.resolveSibling(target.getFileName()+".tmp");
            NbtIo.writeCompressed(output.buildResult(),temporary);
            Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } catch(IOException|RuntimeException error) { HalfCraftGuest.LOG.error("HC_INVENTORY save failed; previous snapshot preserved",error); }
    }
    private static void restore(MinecraftServer server,ServerPlayer player) throws IOException {
        Path target=path(server,player); if(!Files.exists(target)) { save(server,player); return; }
        var tag=NbtIo.readCompressed(target,NbtAccounter.create(16*1024*1024L));
        var reporter=new ProblemReporter.Collector(); var input=TagValueInput.create(reporter,server.registryAccess(),tag);
        if(input.getIntOr("version",0)!=1) throw new IOException("Unsupported campaign inventory version");
        // Same world already has newer vanilla player data after a crash/death/menu shutdown.
        if(input.getStringOr("world","").equals(server.getWorldData().getLevelName())) return;
        int size=player.getInventory().getContainerSize(),selected=input.getIntOr("selected",0);
        if(selected<0 || selected>8) throw new IOException("Invalid selected slot");
        var items=new ArrayList<ItemStack>(); for(int i=0;i<size;i++) items.add(ItemStack.EMPTY);
        boolean[] seen=new boolean[size];
        for(var entry:input.list("inventory",ItemStackWithSlot.CODEC).orElseThrow(() -> new IOException("Missing inventory"))) {
            if(!entry.isValidInContainer(size) || seen[entry.slot()]) throw new IOException("Invalid campaign inventory slot");
            seen[entry.slot()]=true; items.set(entry.slot(),entry.stack());
        }
        var ender=input.list("ender",ItemStackWithSlot.CODEC).orElseThrow(() -> new IOException("Missing ender inventory"));
        boolean[] enderSeen=new boolean[player.getEnderChestInventory().getContainerSize()];
        for(var entry:ender) {
            if(!entry.isValidInContainer(enderSeen.length) || enderSeen[entry.slot()]) throw new IOException("Invalid campaign ender slot");
            enderSeen[entry.slot()]=true;
        }
        var recipes=input.read("recipes",ServerRecipeBook.Packed.CODEC).orElseThrow(() -> new IOException("Missing recipe book"));
        int xpLevel=input.getIntOr("level",0),xp=input.getIntOr("xp",0); float progress=input.getFloatOr("progress",0);
        if(xpLevel<0 || xp<0 || !Float.isFinite(progress) || progress<0 || progress>1) throw new IOException("Invalid campaign experience");
        float health=input.getFloatOr("health",player.getMaxHealth()),saturation=input.getFloatOr("saturation",5);
        int food=input.getIntOr("food",20);
        if(!Float.isFinite(health) || health<0 || health>1000 || food<0 || food>20
            || !Float.isFinite(saturation) || saturation<0 || saturation>20) throw new IOException("Invalid campaign vitals");
        if(!reporter.isEmpty()) throw new IOException(reporter.getReport());
        for(int i=0;i<size;i++) player.getInventory().setItem(i,items.get(i));
        player.getInventory().setSelectedSlot(selected); player.getInventory().setChanged();
        player.getEnderChestInventory().fromSlots(ender);
        player.experienceLevel=xpLevel; player.totalExperience=xp; player.experienceProgress=progress;
        // Death uses vanilla respawn; do not restore a dead campaign snapshot into a new map.
        player.setHealth(health>0?Math.min(health,player.getMaxHealth()):player.getMaxHealth());
        player.getFoodData().setFoodLevel(food); player.getFoodData().setSaturation(saturation);
        player.getRecipeBook().loadUntrusted(recipes,key -> server.getRecipeManager().byKey(key).isPresent());
        player.getRecipeBook().sendInitialRecipeBook(player); player.inventoryMenu.broadcastFullState();
        HalfCraftGuest.LOG.info("HC_INVENTORY campaign restored slots={}",size);
    }
}
