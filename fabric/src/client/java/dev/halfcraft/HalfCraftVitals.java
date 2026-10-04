package dev.halfcraft;

import java.lang.foreign.*;
import java.lang.invoke.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentLinkedQueue;
import static java.lang.foreign.ValueLayout.*;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;

/** GoldSrc supplies damage/healing events; vanilla owns health, armor, enchantments and death. */
public final class HalfCraftVitals implements AutoCloseable {
    private static final SymbolLookup K32=SymbolLookup.libraryLookup("kernel32",Arena.global());
    private static MethodHandle fn(String name,FunctionDescriptor descriptor) { return Linker.nativeLinker().downcallHandle(K32.find(name).orElseThrow(),descriptor); }
    private static final MethodHandle OPEN=fn("OpenFileMappingW",FunctionDescriptor.of(ADDRESS,JAVA_INT,JAVA_INT,ADDRESS));
    private static final MethodHandle MAP=fn("MapViewOfFile",FunctionDescriptor.of(ADDRESS,ADDRESS,JAVA_INT,JAVA_INT,JAVA_INT,JAVA_LONG));
    private static final MethodHandle UNMAP=fn("UnmapViewOfFile",FunctionDescriptor.of(JAVA_INT,ADDRESS));
    private static final MethodHandle CLOSE=fn("CloseHandle",FunctionDescriptor.of(JAVA_INT,ADDRESS));
    private static final VarHandle INT=JAVA_INT.varHandle();
    private MemorySegment handle=MemorySegment.NULL,memory;
    private record Snapshot(int session,int life,boolean alive,boolean enabled) {}
    private record Event(int seq,int session,int life,int kind,int bits,float amount,Vec3 position) {}
    private record State(int session,int life,int ack,int deaths,float health,float maxHealth,int repair) {}
    private static volatile Snapshot snapshot;
    private static volatile State state;
    private static final ConcurrentLinkedQueue<Event> events=new ConcurrentLinkedQueue<>();
    private static int applied,deathCount,serverLife,serverSession;
    private int read;
    private static boolean applying;
    private static final EquipmentSlot[] ARMOR={EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};
    public static boolean applyingNativeDamage() { return applying; }
    public static void init() {
        net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((dispatcher,context) ->
            dispatcher.register(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("hc_vitals_check").executes(command -> {
                var server=Minecraft.getInstance().getSingleplayerServer();
                if(server==null || !HalfCraftWorld.owned(server.getWorldData().getLevelName())) return 0;
                server.execute(() -> check(server.getPlayerList().getPlayers().getFirst())); return 1;
            })));
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity,source) -> {
            if(entity instanceof ServerPlayer && HalfCraftInventory.nativePlayer(entity) && snapshot!=null && snapshot.enabled()) deathCount++;
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            var s=snapshot;
            if(s==null || !s.enabled() || !HalfCraftWorld.owned(server.getWorldData().getLevelName()) || server.getPlayerList().getPlayers().isEmpty()) return;
            var player=server.getPlayerList().getPlayers().getFirst();
            if(serverSession!=s.session()) { serverSession=s.session(); serverLife=s.life(); }
            else if(serverLife!=s.life()) { serverLife=s.life(); /* Native checkpoint returns control; vanilla respawn keeps inventory. */ }
            for(Event event;(event=events.poll())!=null;) {
                if(event.session()==s.session() && event.life()==s.life() && s.alive() && player.isAlive()) {
                    if(event.kind()==1) {
                        var source=source(player,event.bits(),event.position());
                        applying=true;
                        try { player.hurtServer(player.level(),source,event.amount()); }
                        finally { applying=false; }
                        HalfCraftGuest.LOG.info("HC_MC_DAMAGE amount={} bits={} health={} armor={}",event.amount(),event.bits(),player.getHealth(),player.getArmorValue());
                    } else if(event.kind()==2) {
                        player.heal(event.amount());
                        HalfCraftGuest.LOG.info("HC_MC_HEAL amount={} health={}",event.amount(),player.getHealth());
                    } else {
                        HalfCraftGuest.LOG.info("HC_MC_REPAIR amount={}",repair(player,(int)event.amount()));
                    }
                }
                applied=event.seq();
            }
            int repair=0; for(var slot:ARMOR) repair+=player.getItemBySlot(slot).getDamageValue();
            state=new State(s.session(),s.life(),applied,deathCount,player.getHealth(),player.getMaxHealth(),repair);
        });
    }
    private static int repair(ServerPlayer player,int amount) {
        int remaining=amount;
        for(var slot:ARMOR) { var item=player.getItemBySlot(slot); int gain=Math.min(remaining,item.getDamageValue());
            if(gain>0) { item.setDamageValue(item.getDamageValue()-gain); remaining-=gain; }
        }
        return amount-remaining;
    }
    private static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
    private static void check(ServerPlayer player) {
        if(!player.isAlive() || player.isCreative() || player.isSpectator()) { HalfCraftGuest.LOG.error("HC_VITALS_CHECK needs live survival player"); return; }
        var original=new net.minecraft.world.item.ItemStack[4];
        for(int i=0;i<4;i++) original[i]=player.getItemBySlot(ARMOR[i]).copy();
        float health=player.getHealth(),absorption=player.getAbsorptionAmount(); int invulnerable=player.getInvulnerableTime();
        var reporter=new net.minecraft.util.ProblemReporter.Collector();
        var output=net.minecraft.world.level.storage.TagValueOutput.createWithContext(reporter,player.registryAccess());
        player.getFoodData().addAdditionalSaveData(output);
        try {
            for(var slot:ARMOR) player.setItemSlot(slot,net.minecraft.world.item.ItemStack.EMPTY);
            player.setAbsorptionAmount(0); player.setHealth(20); player.setInvulnerableTime(0);
            applying=true; player.hurtServer(player.level(),source(player,1<<2,player.position().add(0,0,2)),4); applying=false;
            float bare=20-player.getHealth(); require(Math.abs(bare-4)<.01,"Unarmored native damage");
            var items=new net.minecraft.world.item.Item[]{net.minecraft.world.item.Items.DIAMOND_HELMET,net.minecraft.world.item.Items.DIAMOND_CHESTPLATE,net.minecraft.world.item.Items.DIAMOND_LEGGINGS,net.minecraft.world.item.Items.DIAMOND_BOOTS};
            for(int i=0;i<4;i++) player.setItemSlot(ARMOR[i],new net.minecraft.world.item.ItemStack(items[i]));
            player.setHealth(20); player.setInvulnerableTime(0);
            applying=true; player.hurtServer(player.level(),source(player,1<<2,player.position().add(0,0,2)),4); applying=false;
            float armored=20-player.getHealth(); require(armored>0 && armored<bare,"Vanilla armor damage reduction");
            int damaged=0; for(var slot:ARMOR) damaged+=player.getItemBySlot(slot).getDamageValue();
            require(damaged>0,"Vanilla armor durability");
            require(repair(player,damaged)==damaged,"Battery repair");
            for(var slot:ARMOR) require(player.getItemBySlot(slot).getDamageValue()==0,"Armor fully repaired");
            player.setHealth(20); player.setInvulnerableTime(0);
            applying=true; player.hurtServer(player.level(),source(player,1<<5,player.position()),4); applying=false;
            float fall=20-player.getHealth(); require(Math.abs(fall-4)<.01,"Fall bypasses armor");
            var enchantments=player.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
            player.getItemBySlot(EquipmentSlot.FEET).enchant(enchantments.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.FEATHER_FALLING),4);
            player.setHealth(20); player.setInvulnerableTime(0);
            applying=true; player.hurtServer(player.level(),source(player,1<<5,player.position()),4); applying=false;
            float protectedFall=20-player.getHealth(); require(protectedFall<fall,"Feather Falling protection");
            player.setHealth(10); player.heal(5); require(player.getHealth()==15,"Health kit healing");
            HalfCraftGuest.LOG.info("HC_VITALS_CHECK PASS bare={} armored={} durability={} fall={} featherFalling={} heal=15",bare,armored,damaged,fall,protectedFall);
        } catch(Throwable error) { HalfCraftGuest.LOG.error("HC_VITALS_CHECK FAIL",error); }
        finally {
            applying=false; for(int i=0;i<4;i++) player.setItemSlot(ARMOR[i],original[i]);
            player.setHealth(health); player.setAbsorptionAmount(absorption); player.setInvulnerableTime(invulnerable);
            player.getFoodData().readAdditionalSaveData(net.minecraft.world.level.storage.TagValueInput.create(reporter,player.registryAccess(),output.buildResult()));
            player.inventoryMenu.broadcastFullState();
        }
    }
    private static DamageSource source(ServerPlayer player,int bits,Vec3 position) {
        var sources=player.damageSources(); DamageSource base;
        if((bits&(1<<5))!=0) base=sources.fall();
        else if((bits&(1<<14))!=0) base=sources.drown();
        else if((bits&(1<<3))!=0) base=sources.inFire();
        else if((bits&(1<<4))!=0) base=sources.freeze();
        else if((bits&(1<<6))!=0) base=sources.explosion(null,null);
        else if((bits&((1<<16)|(1<<18)))!=0) base=sources.magic();
        else if((bits&(1<<1))!=0) base=new DamageSource(player.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getOrThrow(DamageTypes.ARROW),position);
        else base=sources.generic();
        return new DamageSource(base.typeHolder(),position);
    }
    private int get(int offset) { return (int)INT.getAcquire(memory,(long)offset); }
    private void put(int offset,int value) { INT.setVolatile(memory,(long)offset,value); }
    public void tick(Minecraft mc,HalfCraftLink link) {
        try {
            if(memory!=null && link.host()!=null && get(16)!=link.host().pid()) close();
            if(memory==null) {
                try(var arena=Arena.ofConfined()) { handle=(MemorySegment)OPEN.invokeExact(0xF001F,0,arena.allocateFrom("Local\\HalfCraft_vitals_v1",StandardCharsets.UTF_16LE)); }
                if(handle.address()==0) return;
                MemorySegment view=(MemorySegment)MAP.invokeExact(handle,0xF001F,0,0,4224L);
                if(view.address()==0) { close(); return; } memory=view.reinterpret(4224);
                if(get(4)!=0x56435248 || get(8)!=1 || get(12)!=4224) throw new IllegalStateException("Vitals layout mismatch");
                read=get(84); applied=read; deathCount=get(88); state=null;
            }
            byte[] bytes=null;
            for(int i=0;i<16;i++) { int seq=get(0); if(seq==0 || (seq&1)!=0) continue; var copy=memory.toArray(JAVA_BYTE); VarHandle.loadLoadFence(); if(seq==get(0)) { bytes=copy; break; } }
            if(bytes==null) return;
            var b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            // GoldSrc pauses StartFrame in console; independent bridge heartbeat owns liveness.
            if(!link.matchesHost(b.getInt(16),b.getInt(20))) { snapshot=null; return; }
            var s=new Snapshot(b.getInt(20),b.getInt(32),b.getInt(36)!=0,b.getInt(28)!=0); snapshot=s;
            int write=b.getInt(40);
            if(Integer.toUnsignedLong(write-read)>128) throw new IllegalStateException("Native vitals queue overflow");
            while(read!=write && events.size()<128) {
                int offset=128+(read&127)*32,kind=b.getInt(offset+8); float amount=b.getFloat(offset+16);
                var position=new Vec3(b.getFloat(offset+20),b.getFloat(offset+24),b.getFloat(offset+28));
                if(b.getInt(offset)!=read+1 || kind<1 || kind>3 || !Float.isFinite(amount) || amount<=0 || amount>10000
                    || !Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z)) throw new IllegalStateException("Invalid native vitals event");
                events.add(new Event(read+1,s.session(),b.getInt(offset+4),kind,b.getInt(offset+12),amount,position)); read++;
            }
            var current=state;
            boolean ready=current!=null && current.session()==s.session() && current.life()==s.life() && mc.player!=null;
            int seq=(get(64)+1)&~1; put(64,seq+1);
            put(68,(int)ProcessHandle.current().pid()); put(72,s.session()); put(76,HalfCraftLink.tick()); put(80,s.life());
            put(84,ready?current.ack():get(84)); put(88,ready?current.deaths():deathCount); put(92,ready?1:0);
            memory.set(JAVA_FLOAT,96,ready?current.health():20); memory.set(JAVA_FLOAT,100,ready?current.maxHealth():20); put(104,ready?current.repair():0);
            put(64,seq+2);
        } catch(Throwable error) { close(); HalfCraftGuest.LOG.error("Native vitals disabled",error); }
    }
    @Override public void close() {
        snapshot=null; state=null; events.clear();
        try { if(memory!=null) { int ok=(int)UNMAP.invokeExact(memory); memory=null; } if(handle.address()!=0) { int ok=(int)CLOSE.invokeExact(handle); handle=MemorySegment.NULL; } }
        catch(Throwable error) { HalfCraftGuest.LOG.error("Vitals cleanup failed",error); }
    }
}
