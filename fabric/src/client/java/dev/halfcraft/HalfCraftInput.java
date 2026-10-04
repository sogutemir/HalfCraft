package dev.halfcraft;

import java.lang.foreign.*;
import java.lang.invoke.*;
import java.nio.charset.StandardCharsets;
import static java.lang.foreign.ValueLayout.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.sdl.SDLKeyboard;

/** SkyCraft InputBridge native-handler replay, isolated from Skyrim combat and transport. */
public final class HalfCraftInput implements AutoCloseable {
    private static final VarHandle INT=JAVA_INT.varHandle();
    private static final SymbolLookup K32=SymbolLookup.libraryLookup("kernel32",Arena.global());
    private static MethodHandle fn(String name,FunctionDescriptor descriptor) { return Linker.nativeLinker().downcallHandle(K32.find(name).orElseThrow(),descriptor); }
    private static final MethodHandle OPEN=fn("OpenFileMappingW",FunctionDescriptor.of(ADDRESS,JAVA_INT,JAVA_INT,ADDRESS));
    private static final MethodHandle MAP=fn("MapViewOfFile",FunctionDescriptor.of(ADDRESS,ADDRESS,JAVA_INT,JAVA_INT,JAVA_INT,JAVA_LONG));
    private static final MethodHandle UNMAP=fn("UnmapViewOfFile",FunctionDescriptor.of(JAVA_INT,ADDRESS));
    private static final MethodHandle CLOSE=fn("CloseHandle",FunctionDescriptor.of(JAVA_INT,ADDRESS));
    private static final boolean[] KEYS=new boolean[512],BUTTONS=new boolean[8];
    private static boolean takeover,dispatching;
    private MemorySegment handle=MemorySegment.NULL,memory;
    private int reset,session;
    private byte[] hostSnapshot;
    private boolean wasEnabled;
    private Boolean pause;
    private boolean hidden;
    private int modifiers;
    private double cursorX,cursorY;
    private com.mojang.blaze3d.platform.InputConstants.Key inventoryKey;
    private boolean wasScreen;
    public static boolean takeover() { return takeover; }
    public static boolean dispatching() { return dispatching; }
    public static boolean keyDown(int sc) { return sc>=0 && sc<KEYS.length && KEYS[sc]; }
    private int get(int offset) { return (int)INT.getAcquire(memory,(long)offset); }
    private void put(int offset,int value) { INT.setVolatile(memory,(long)offset,value); }
    private void key(Minecraft mc,int sc,boolean down) {
        if(sc<=0 || sc>=KEYS.length) return;
        boolean wasDown=KEYS[sc];
        if(!down && !wasDown) return;
        KEYS[sc]=down;
        modifiers=(KEYS[225]?1:0)|(KEYS[229]?2:0)|(KEYS[224]?0x40:0)|(KEYS[228]?0x80:0)|(KEYS[226]?0x100:0)|(KEYS[230]?0x200:0);
        mc.keyboardHandler.keyPress(mc.getWindow().handle(),down?(wasDown?-1:1):0,new KeyEvent(sc,SDLKeyboard.SDL_GetKeyFromScancode(sc,(short)modifiers,true),modifiers));
    }
    private void button(Minecraft mc,int code,boolean down) {
        if(code<=0 || code>=BUTTONS.length || BUTTONS[code]==down) return;
        BUTTONS[code]=down;
        if(code==1 && HalfCraftInteraction.miningButton(mc,down)) return;
        mc.mouseHandler.onButton(mc.getWindow().handle(),new MouseButtonInfo(code,modifiers),down?1:0);
    }
    private void release(Minecraft mc) {
        HalfCraftInteraction.stopMining();
        dispatching=true;
        try { for(int sc=1;sc<KEYS.length;sc++) if(KEYS[sc]) key(mc,sc,false); for(int b=1;b<BUTTONS.length;b++) if(BUTTONS[b]) button(mc,b,false); }
        finally { dispatching=false; }
    }
    public void tick(Minecraft mc,HalfCraftLink link) {
        try {
            if(memory!=null && link.host()!=null && get(16)!=link.host().pid()) close();
            if(memory==null) {
                try(Arena arena=Arena.ofConfined()) { handle=(MemorySegment)OPEN.invokeExact(0xF001F,0,arena.allocateFrom("Local\\HalfCraft_input_v2",StandardCharsets.UTF_16LE)); }
                if(handle.address()==0) return;
                MemorySegment view=(MemorySegment)MAP.invokeExact(handle,0xF001F,0,0,4224L);
                if(view.address()==0) { close(); return; }
                memory=view.reinterpret(4224);
                if(get(4)!=0x49435248 || get(8)!=2 || get(12)!=4224) throw new IllegalStateException("HalfCraft input protocol mismatch");
                put(64,get(32)); reset=get(36); session=get(20);
            }
            byte[] snapshot=null;
            for(int attempt=0;attempt<16;attempt++) {
                int seq=get(0); if(seq==0 || (seq&1)!=0) continue;
                byte[] copy=memory.asSlice(0,64).toArray(JAVA_BYTE); VarHandle.loadLoadFence();
                if(seq==get(0)) { snapshot=copy; break; }
            }
            if(snapshot!=null) hostSnapshot=snapshot;
            if(hostSnapshot==null) return;
            var host=java.nio.ByteBuffer.wrap(hostSnapshot).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            boolean matched=link.matchesHost(host.getInt(16),host.getInt(20));
            boolean armed=matched && HalfCraftPhysics.active() && mc.player!=null && host.getInt(52)!=0;
            if(takeover!=armed) {
                takeover=armed;
                if(armed) {
                    pause=mc.options.pauseOnLostFocus; mc.options.pauseOnLostFocus=false;
                    inventoryKey=com.mojang.blaze3d.platform.InputConstants.getKey(mc.options.keyInventory.saveString());
                    mc.options.keyInventory.setKey(com.mojang.blaze3d.platform.InputConstants.getKey("key.keyboard.i"));
                    net.minecraft.client.KeyMapping.resetMapping();
                } else if(pause!=null) {
                    mc.options.pauseOnLostFocus=pause; pause=null;
                    if(inventoryKey!=null) { mc.options.keyInventory.setKey(inventoryKey); net.minecraft.client.KeyMapping.resetMapping(); inventoryKey=null; }
                    mc.mouseHandler.releaseMouse();
                }
            }
            boolean hide=armed && host.getInt(56)!=0;
            if(armed) ((dev.halfcraft.mixin.HalfCraftMouseAccessor)mc.mouseHandler).halfcraft$grabbed(mc.gui.screen()==null);
            if(hide!=hidden) {
                hidden=hide;
                if(hide) org.lwjgl.sdl.SDLVideo.SDL_HideWindow(mc.getWindow().handle());
                else org.lwjgl.sdl.SDLVideo.SDL_ShowWindow(mc.getWindow().handle());
            }
            put(68,(int)ProcessHandle.current().pid()); put(72,HalfCraftLink.tick()); put(80,host.getInt(20));
            int hudSeq=(get(124)+1)&~1;
            put(124,hudSeq+1); put(108,0);
            if(mc.player!=null) {
                put(112,mc.player.getInventory().getSelectedSlot()); put(116,mc.player.getMainHandItem().getCount());
                if(HalfCraftInteraction.nativeTool(mc)) put(108,3);
                if(mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit && hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK) {
                    var shape=mc.level.getBlockState(hit.getBlockPos()).getShape(mc.level,hit.getBlockPos());
                    if(!shape.isEmpty()) {
                        var box=shape.bounds().move(hit.getBlockPos());
                        double[] bounds={box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ};
                        for(int i=0;i<6;i++) memory.set(JAVA_FLOAT,84L+i*4,(float)bounds[i]);
                        put(108,2);
                    } else if(mc.player.getMainHandItem().getItem() instanceof net.minecraft.world.item.BlockItem) {
                        var pos=hit.getBlockPos();
                        float[] bounds={pos.getX(),pos.getY(),pos.getZ(),pos.getX()+1,pos.getY()+1,pos.getZ()+1};
                        for(int i=0;i<6;i++) memory.set(JAVA_FLOAT,84L+i*4,bounds[i]);
                        put(108,1);
                    }
                }
            }
            put(124,hudSeq+2);
            boolean ready=matched && HalfCraftPhysics.active() && mc.player!=null;
            put(76,ready?1:0);
            boolean enabled=ready && host.getInt(28)!=0 && Integer.toUnsignedLong(HalfCraftLink.tick()-host.getInt(24))<250;
            if(reset!=host.getInt(36) || session!=host.getInt(20) || !enabled) { release(mc); put(64,get(32)); reset=host.getInt(36); session=host.getInt(20); }
            if(wasEnabled!=enabled) {
                wasEnabled=enabled;
                HalfCraftGuest.LOG.info("HC_INPUT active={}",enabled);
            }
            if(!enabled) return;
            boolean screen=mc.gui.screen()!=null;
            if(screen!=wasScreen) { release(mc); wasScreen=screen; }
            float yaw=host.getFloat(40),pitch=host.getFloat(44);
            if(!Float.isFinite(yaw) || !Float.isFinite(pitch) || Math.abs(pitch)>90) return;
            if(mc.gui.screen()==null) { mc.player.setYRot(yaw); mc.player.setXRot(pitch); }
            int read=get(64),write=get(32);
            if(Integer.toUnsignedLong(write-read)>256) { release(mc); put(64,write); return; }
            dispatching=true;
            try {
                while(read!=write) {
                    int offset=128+(read&255)*16,type=get(offset),code=get(offset+4),value=get(offset+8);
                    put(120,HalfCraftLink.tick()-get(offset+12));
                    switch(type) {
                        case 1 -> key(mc,code,value!=0);
                        case 2 -> button(mc,code,value!=0);
                         case 3 -> { if(Math.abs((long)value)<=1200) mc.mouseHandler.onScroll(mc.getWindow().handle(),0,value/120.0); }
                         case 4 -> {
                             if(mc.gui.screen()!=null && code<=3840 && value>=0 && value<=2160) {
                                 mc.mouseHandler.onMove(mc.getWindow().handle(),code,value,code-cursorX,value-cursorY); cursorX=code; cursorY=value;
                             }
                         }
                         case 5 -> { if(mc.gui.screen()!=null && Character.isValidCodePoint(value) && !(value>=0xD800 && value<=0xDFFF)) mc.keyboardHandler.textInput(mc.getWindow().handle(),new String(Character.toChars(value))); }
                        default -> throw new IllegalStateException("Invalid HalfCraft input event");
                    }
                    put(64,++read);
                }
            } finally { dispatching=false; }
        } catch(Throwable e) { release(mc); takeover=false; close(); HalfCraftGuest.LOG.error("HalfCraft input disabled",e); }
    }
    @Override public void close() {
        release(Minecraft.getInstance()); takeover=false; hostSnapshot=null;
        Minecraft.getInstance().mouseHandler.releaseMouse();
        if(pause!=null) { Minecraft.getInstance().options.pauseOnLostFocus=pause; pause=null; }
        if(inventoryKey!=null) { Minecraft.getInstance().options.keyInventory.setKey(inventoryKey); net.minecraft.client.KeyMapping.resetMapping(); inventoryKey=null; }
        if(hidden) { org.lwjgl.sdl.SDLVideo.SDL_ShowWindow(Minecraft.getInstance().getWindow().handle()); hidden=false; }
        try { if(memory!=null) { put(76,0); int ok=(int)UNMAP.invokeExact(memory); memory=null; } if(handle.address()!=0) { int ok=(int)CLOSE.invokeExact(handle); handle=MemorySegment.NULL; } }
        catch(Throwable e) { HalfCraftGuest.LOG.error("HalfCraft input cleanup failed",e); }
    }
}
