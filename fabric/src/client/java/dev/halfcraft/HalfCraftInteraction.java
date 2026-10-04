package dev.halfcraft;

import java.lang.foreign.*;
import java.lang.invoke.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import static java.lang.foreign.ValueLayout.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import dev.skycraft.combat.SkyrimActorEntity;

/** Native surfaces are renewable OneBlock sources; vanilla owns loot, tools and combat. */
public final class HalfCraftInteraction implements AutoCloseable {
    private static final ResourceKey<EntityType<?>> KEY=ResourceKey.create(Registries.ENTITY_TYPE,Identifier.fromNamespaceAndPath("halfcraft_bridge","native_actor"));
    public static final EntityType<SkyrimActorEntity> TYPE=net.minecraft.core.Registry.register(BuiltInRegistries.ENTITY_TYPE,KEY,
        EntityType.Builder.<SkyrimActorEntity>of(SkyrimActorEntity::new,MobCategory.MISC).sized(.6f,1.8f).noSave().noSummon().noLootTable().clientTrackingRange(10).updateInterval(1).build(KEY));
    private static final SymbolLookup K32=SymbolLookup.libraryLookup("kernel32",Arena.global());
    private static MethodHandle fn(String n,FunctionDescriptor d) { return Linker.nativeLinker().downcallHandle(K32.find(n).orElseThrow(),d); }
    private static final MethodHandle OPEN=fn("OpenFileMappingW",FunctionDescriptor.of(ADDRESS,JAVA_INT,JAVA_INT,ADDRESS));
    private static final MethodHandle MAP=fn("MapViewOfFile",FunctionDescriptor.of(ADDRESS,ADDRESS,JAVA_INT,JAVA_INT,JAVA_INT,JAVA_LONG));
    private static final MethodHandle UNMAP=fn("UnmapViewOfFile",FunctionDescriptor.of(JAVA_INT,ADDRESS));
    private static final MethodHandle CLOSE=fn("CloseHandle",FunctionDescriptor.of(JAVA_INT,ADDRESS));
    private static final VarHandle INT=JAVA_INT.varHandle();
    private MemorySegment handle=MemorySegment.NULL,memory;
    private record Actor(int id,int serial,AABB box) {}
    private record Snapshot(int session,int epoch,int target,int targetId,Vec3 point,List<Actor> actors) {}
    private record Hit(int session,int epoch,int id,int serial,float damage,int flags) {}
    private record Death(int seq,int session,int epoch,int id,int serial,int tier) {}
    private static volatile Snapshot snapshot;
    private static volatile boolean held;
    private static volatile float progress;
    private static volatile AABB miningBox;
    private static final Map<Integer,SkyrimActorEntity> proxies=new HashMap<>();
    private static final Map<Integer,Integer> serials=new HashMap<>();
    private static final ConcurrentLinkedQueue<Hit> hits=new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<Death> deaths=new ConcurrentLinkedQueue<>();
    private int deathRead;
    private static volatile int deathApplied;
    private static BlockPos miningPos;
    private static ItemStack miningTool=ItemStack.EMPTY;
    private static final Block[] SOURCES={Blocks.STONE,Blocks.OAK_LOG,Blocks.DIRT,Blocks.COAL_ORE,Blocks.IRON_ORE,Blocks.GOLD_ORE,Blocks.DIAMOND_ORE,Blocks.ANCIENT_DEBRIS};
    private static net.minecraft.world.level.block.state.BlockState source;
    private static boolean pending;
    private static volatile int completed,miningTicks;
    public static void init() {
        net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(TYPE,LivingEntity.createLivingAttributes());
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(TYPE,net.minecraft.client.renderer.entity.NoopRenderer::new);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(HalfCraftInteraction::serverTick);
    }
    public static boolean nativeTool(Minecraft mc) {
        if(mc.player==null) return false;
        var item=mc.player.getMainHandItem();
        return item.is(net.minecraft.tags.ItemTags.PICKAXES) || item.is(net.minecraft.tags.ItemTags.SHOVELS)
            || item.is(net.minecraft.tags.ItemTags.AXES) || item.is(net.minecraft.tags.ItemTags.SWORDS)
            || item.is(Items.BOW) || item.is(Items.CROSSBOW) || mc.hitResult instanceof EntityHitResult;
    }
    public static boolean miningButton(Minecraft mc,boolean down) {
        if(!down && held) { held=false; return true; }
        if(down && mc.gui.screen()==null && !(mc.hitResult instanceof EntityHitResult) && snapshot!=null && snapshot.target()==1
            && mc.player!=null && !(mc.hitResult instanceof BlockHitResult b && !mc.level.getBlockState(b.getBlockPos()).isAir())) {
            held=true; return true;
        }
        return false;
    }
    public static void stopMining() { held=false; progress=0; miningBox=null; }
    public static int miningStage() { return held && progress>0 ? Math.min(9,(int)(progress*10)) : -1; }
    public static AABB miningBox() { return miningBox; }
    public static EntityHitResult nativePick(Minecraft mc,Vec3 origin,Vec3 end) {
        var s=snapshot;
        if(s==null || s.target()!=2 || mc.level==null) return null;
        for(var a:s.actors()) {
            if(a.id()!=s.targetId()) continue;
            var point=a.box().contains(origin)?Optional.of(origin):a.box().clip(origin,end);
            if(point.isEmpty()) return null;
            for(var entity:mc.level.entitiesForRendering())
                if(entity instanceof SkyrimActorEntity proxy && proxy.getType()==TYPE && proxy.formId()==a.id()) return new EntityHitResult(proxy,point.get());
        }
        return null;
    }
    private static void serverTick(net.minecraft.server.MinecraftServer server) {
        var s=snapshot;
        if(s==null || !HalfCraftPhysics.active() || server.getPlayerList().getPlayers().isEmpty()) {
            proxies.values().forEach(Entity::discard); proxies.clear(); serials.clear(); progress=0; miningPos=null; return;
        }
        var player=server.getPlayerList().getPlayers().getFirst(); var level=player.level();
        for(Death death;(death=deaths.poll())!=null;) {
            if(death.session()==s.session() && death.epoch()==s.epoch() && player.isAlive() && !player.isSpectator() && !player.isCreative()) {
                var loot=HalfCraftLoot.combat(new java.util.Random(level.getRandom().nextLong()),death.tier()&3,(death.tier()&4)!=0);
                drop(level,player,new ItemStack(Items.ARROW,loot.arrows()));
                drop(level,player,new ItemStack(Items.FEATHER,loot.feathers()));
                drop(level,player,new ItemStack(Items.GUNPOWDER,loot.gunpowder()));
                drop(level,player,new ItemStack(Items.STRING,loot.string()));
                drop(level,player,new ItemStack(loot.cooked()?Items.COOKED_BEEF:Items.BEEF,loot.beef()));
                if(loot.bow()) {
                    var bow=new ItemStack(Items.BOW);
                    if(loot.enchanted()) {
                        var registry=level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
                        bow.enchant(registry.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.POWER),level.getRandom().nextInt(3)+1);
                        if(level.getRandom().nextBoolean()) bow.enchant(registry.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING),level.getRandom().nextInt(3)+1);
                    }
                    drop(level,player,bow);
                }
                // Native combat owns movement; grant real vanilla XP directly so it cannot fall through BSP.
                player.giveExperiencePoints(loot.xp());
                HalfCraftGuest.LOG.info("HC_ENEMY_LOOT id={} serial={} tier={} arrows={} feathers={} gunpowder={} string={} bow={} enchanted={} xp={} beef={} cooked={}",death.id(),death.serial(),death.tier()&3,loot.arrows(),loot.feathers(),loot.gunpowder(),loot.string(),loot.bow(),loot.enchanted(),loot.xp(),loot.beef(),loot.cooked());
            }
            deathApplied=death.seq();
        }
        Set<Integer> live=new HashSet<>();
        for(var a:s.actors()) {
            live.add(a.id()); var proxy=proxies.get(a.id());
            if(proxy!=null && (proxy.level()!=level || proxy.isRemoved() || !Objects.equals(serials.get(a.id()),a.serial()))) { proxy.discard(); proxies.remove(a.id()); proxy=null; }
            if(proxy==null) {
                proxy=new SkyrimActorEntity(TYPE,level); proxy.setFormId(a.id());
                if(!level.addFreshEntity(proxy)) continue;
                proxies.put(a.id(),proxy); serials.put(a.id(),a.serial());
            }
            var box=a.box(); proxy.setSize((float)Math.max(box.getXsize(),box.getZsize()),(float)box.getYsize());
            proxy.setPos((box.minX+box.maxX)/2,box.minY,(box.minZ+box.maxZ)/2);
            proxy.setBoundingBox(box);
            var hit=proxy.takeHit();
            if(hit!=null && hit[0]>0 && hits.size()<128) {
                int flags=Float.floatToRawIntBits(hit[4]);
                hits.add(new Hit(s.session(),s.epoch(),a.id(),a.serial(),hit[0],
                    ((flags&dev.skycraft.link.Proto.HIT_PROJECTILE)!=0?1:0)|((proxy.isOnFire() || (flags&dev.skycraft.link.Proto.HIT_FIRE)!=0)?2:0)));
            }
        }
        for(var it=proxies.entrySet().iterator();it.hasNext();) { var entry=it.next(); if(!live.contains(entry.getKey())) { entry.getValue().discard(); serials.remove(entry.getKey()); it.remove(); } }
        if(!held || s.target()!=1 || !player.isAlive() || player.isSpectator() || player.getEyePosition().distanceToSqr(s.point())>25) { progress=0; miningPos=null; miningBox=null; return; }
        var pos=BlockPos.containing(s.point()); var tool=player.getMainHandItem();
        if(!pos.equals(miningPos) || !ItemStack.matches(tool,miningTool)) {
            miningPos=pos; miningTool=tool.copy(); progress=0; miningTicks=0;
            // ponytail: fixed weighted renewable loot; add BSP material classification when texture export exists.
            source=SOURCES[HalfCraftLoot.source(level.getRandom().nextInt(1000),tool.is(Items.DIAMOND_PICKAXE))].defaultBlockState();
        }
        miningBox=new AABB(pos).inflate(.004);
        progress+=source.getDestroyProgress(player,level,pos);
        miningTicks++;
        if(progress<1) return;
        if(!player.isCreative()) {
            var used=tool.copy(); boolean harvest=player.hasCorrectToolForDrops(source);
            tool.mineBlock(level,source,pos,player);
            if(harvest) {
                var drops=Block.getDrops(source,level,pos,null,player,used);
                for(var stack:drops) drop(level,player,stack);
            }
        }
        level.levelEvent(LevelEvent.PARTICLES_AND_SOUND_DESTROY_BLOCK,pos,Block.getId(source));
        completed++;
        HalfCraftGuest.LOG.info("HC_ONEBLOCK source={} harvested={} pos={} ticks={} speed=5",source,player.hasCorrectToolForDrops(source),pos,miningTicks);
        progress=0; miningPos=null;
    }
    private static void drop(net.minecraft.server.level.ServerLevel level,net.minecraft.server.level.ServerPlayer player,ItemStack stack) {
        var item=new net.minecraft.world.entity.item.ItemEntity(level,player.getX(),player.getY()+.5,player.getZ(),stack);
        item.setDefaultPickUpDelay(); level.addFreshEntity(item);
    }
    private int get(int n) { return (int)INT.getAcquire(memory,(long)n); }
    private void put(int n,int v) { INT.setVolatile(memory,(long)n,v); }
    public void tick(Minecraft mc,HalfCraftLink link) {
        try {
            if(memory!=null && link.host()!=null && get(16)!=link.host().pid()) close();
            if(memory==null) {
                try(var arena=Arena.ofConfined()) { handle=(MemorySegment)OPEN.invokeExact(0xF001F,0,arena.allocateFrom("Local\\HalfCraft_interaction_v3",StandardCharsets.UTF_16LE)); }
                if(handle.address()==0) return;
                MemorySegment view=(MemorySegment)MAP.invokeExact(handle,0xF001F,0,0,7824L);
                if(view.address()==0) { close(); return; } memory=view.reinterpret(7824);
                if(get(4)!=0x58435248 || get(8)!=3 || get(12)!=7824) throw new IllegalStateException("Interaction layout mismatch");
                deathRead=get(5244); deathApplied=deathRead;
            }
            byte[] copy=null;
            for(int i=0;i<16;i++) { int seq=get(0); if(seq==0 || (seq&1)!=0) continue; var bytes=memory.asSlice(0,7824).toArray(JAVA_BYTE); VarHandle.loadLoadFence(); if(seq==get(0)) { copy=bytes; break; } }
            if(copy==null) return; var b=ByteBuffer.wrap(copy).order(ByteOrder.LITTLE_ENDIAN);
            if(!link.matchesHost(b.getInt(16),b.getInt(20)) || b.getInt(28)==0 || Integer.toUnsignedLong(HalfCraftLink.tick()-b.getInt(24))>1000) { snapshot=null; held=false; return; }
            int count=b.getInt(40); if(count<0 || count>128) throw new IllegalStateException("Invalid actor count");
            List<Actor> actors=new ArrayList<>();
            for(int i=0;i<count;i++) {
                int off=64+i*40; float[] box=new float[6];
                for(int j=0;j<6;j++) { box[j]=b.getFloat(off+8+j*4); if(!Float.isFinite(box[j]) || Math.abs(box[j])>32768) throw new IllegalStateException("Invalid actor bounds"); }
                if(box[3]<=box[0] || box[4]<=box[1] || box[5]<=box[2] || box[3]-box[0]>64 || box[4]-box[1]>64 || box[5]-box[2]>64) continue;
                actors.add(new Actor(b.getInt(off),b.getInt(off+4),new AABB(box[0],box[1],box[2],box[3],box[4],box[5])));
            }
            Vec3 point=new Vec3(b.getFloat(48),b.getFloat(52),b.getFloat(56));
            if(!Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z)) throw new IllegalStateException("Invalid native target");
            snapshot=new Snapshot(b.getInt(20),b.getInt(32),b.getInt(44),b.getInt(60),point,List.copyOf(actors));
            int deathWrite=b.getInt(5248);
            if(Integer.toUnsignedLong(deathWrite-deathRead)>128) throw new IllegalStateException("Native death queue overflow");
            while(deathRead!=deathWrite && deaths.size()<128) {
                int offset=5264+(deathRead&127)*20;
                if(b.getInt(offset)!=deathRead+1 || b.getInt(offset+4)<=0 || b.getInt(offset+16)<0 || b.getInt(offset+16)>6 || (b.getInt(offset+16)&3)>2) throw new IllegalStateException("Invalid native death event");
                deaths.add(new Death(deathRead+1,snapshot.session(),b.getInt(offset+12),b.getInt(offset+4),b.getInt(offset+8),b.getInt(offset+16))); deathRead++;
            }
            // Vanilla dimensions are square; native crates and actors need their exact rectangular hull.
            if(mc.level!=null) for(var entity:mc.level.entitiesForRendering())
                if(entity instanceof SkyrimActorEntity proxy && proxy.getType()==TYPE)
                    for(var a:actors) if(proxy.formId()==a.id()) { proxy.setBoundingBox(a.box()); break; }
            put(5220,held?1:0); memory.set(JAVA_FLOAT,5224,progress); put(5228,miningStage()); put(5232,completed); put(5236,miningTicks); put(5240,mc.player!=null && mc.player.isSwinging()?1:0);
            if(held && mc.player!=null && mc.gui.screen()==null && !mc.player.isSwinging()) mc.player.swing(InteractionHand.MAIN_HAND,mc.player.getMainHandItem().getAttackAnimation(),false);
            if(pending && get(5184)!=b.getInt(36)) return; pending=false;
            var hit=hits.poll();
            if(hit!=null && (hit.session()!=snapshot.session() || hit.epoch()!=snapshot.epoch())) hit=null;
            int seq=(get(5184)+1)&~1; put(5184,seq+1);
            put(5188,(int)ProcessHandle.current().pid()); put(5192,snapshot.session()); put(5196,snapshot.epoch()); put(5200,HalfCraftLink.tick());
            put(5204,hit==null?0:hit.id()); put(5208,hit==null?0:hit.serial()); put(5212,hit==null?0:hit.flags()); memory.set(JAVA_FLOAT,5216,hit==null?0:hit.damage());
            put(5244,deathApplied); put(5184,seq+2); pending=true;
        } catch(Throwable e) { close(); HalfCraftGuest.LOG.error("Native interaction disabled",e); }
    }
    @Override public void close() {
        snapshot=null; held=false; hits.clear(); deaths.clear(); pending=false;
        try { if(memory!=null) { int ok=(int)UNMAP.invokeExact(memory); memory=null; } if(handle.address()!=0) { int ok=(int)CLOSE.invokeExact(handle); handle=MemorySegment.NULL; } }
        catch(Throwable e) { HalfCraftGuest.LOG.error("Interaction cleanup failed",e); }
    }
}
