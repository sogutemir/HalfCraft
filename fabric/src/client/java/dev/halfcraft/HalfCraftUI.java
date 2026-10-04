package dev.halfcraft;

import java.lang.foreign.*;
import java.lang.invoke.*;
import java.nio.charset.StandardCharsets;
import static java.lang.foreign.ValueLayout.*;
import net.minecraft.client.Minecraft;
import dev.skycraft.client.FrameExporter;

/** Vanilla screens, transported using SkyCraft's asynchronous framebuffer readback. */
public final class HalfCraftUI implements AutoCloseable {
    public static final int BYTES=33177728;
    private static final VarHandle INT=JAVA_INT.varHandle();
    private static final SymbolLookup K32=SymbolLookup.libraryLookup("kernel32",Arena.global());
    private static MethodHandle fn(String name,FunctionDescriptor descriptor) { return Linker.nativeLinker().downcallHandle(K32.find(name).orElseThrow(),descriptor); }
    private static final MethodHandle OPEN=fn("OpenFileMappingW",FunctionDescriptor.of(ADDRESS,JAVA_INT,JAVA_INT,ADDRESS));
    private static final MethodHandle MAP=fn("MapViewOfFile",FunctionDescriptor.of(ADDRESS,ADDRESS,JAVA_INT,JAVA_INT,JAVA_INT,JAVA_LONG));
    private static final MethodHandle UNMAP=fn("UnmapViewOfFile",FunctionDescriptor.of(JAVA_INT,ADDRESS));
    private static final MethodHandle CLOSE=fn("CloseHandle",FunctionDescriptor.of(JAVA_INT,ADDRESS));
    private MemorySegment handle=MemorySegment.NULL,memory;
    private int session,epoch;
    private boolean live;
    private int get(int offset) { return (int)INT.getAcquire(memory,(long)offset); }
    private void put(int offset,int value) { INT.setVolatile(memory,(long)offset,value); }
    public void tick(Minecraft mc,HalfCraftLink link) {
        try {
            if(memory!=null && link.host()!=null && get(16)!=link.host().pid()) close();
            if(memory==null) {
                try(Arena arena=Arena.ofConfined()) { handle=(MemorySegment)OPEN.invokeExact(0xF001F,0,arena.allocateFrom("Local\\HalfCraft_ui_v1",StandardCharsets.UTF_16LE)); }
                if(handle.address()==0) return;
                MemorySegment view=(MemorySegment)MAP.invokeExact(handle,0xF001F,0,0,(long)BYTES);
                if(view.address()==0) { close(); return; }
                memory=view.reinterpret(BYTES);
                if(get(4)!=0x55435248 || get(8)!=1 || get(12)!=BYTES) throw new IllegalStateException("HalfCraft UI protocol mismatch");
            }
            int seq=get(0); if(seq==0 || (seq&1)!=0) return;
            int pid=get(16),s=get(20),e=get(32),heartbeat=get(24),enabled=get(28),w=get(36),h=get(40);
            VarHandle.loadLoadFence(); if(seq!=get(0)) return;
            session=s; epoch=e;
            live=enabled!=0 && link.matchesHost(pid,s) && HalfCraftPhysics.active() && mc.player!=null && Integer.toUnsignedLong(HalfCraftLink.tick()-heartbeat)<1000;
            if(live && w>=320 && h>=240 && w<=3840 && h<=2160 && (mc.getWindow().getScreenWidth()!=w || mc.getWindow().getScreenHeight()!=h)) mc.getWindow().setWindowed(w,h);
            int state=(get(64)+1)&~1; put(64,state+1);
            put(68,(int)ProcessHandle.current().pid()); put(72,s); put(76,e); put(80,HalfCraftLink.tick());
            put(84,live && mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>?1:0); put(88,mc.getWindow().getGuiScale());
            put(92,mc.player==null?0:mc.player.containerMenu.getCarried().getCount()); put(64,state+2);
        } catch(Throwable error) { close(); HalfCraftGuest.LOG.error("HalfCraft UI disabled",error); }
    }
    public void capture(Minecraft mc) {
        if(!live || !HalfCraftInput.takeover() || (mc.gui.screen()!=null && !(mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>))) return;
        FrameExporter.capture(mc,(pixels,w,h,id) -> {
            int seq=get(96);
            if((seq&1)!=0 || seq!=get(44) || w<=0 || h<=0 || w>3840 || h>2160 || pixels.byteSize()!=(long)w*h*4) return;
            put(96,seq+1); put(100,w); put(104,h); put(108,1); put(112,session); put(116,epoch); put(120,HalfCraftLink.tick()); put(124,(int)id);
            MemorySegment.copy(pixels,0,memory,128,pixels.byteSize()); put(96,seq+2);
        });
    }
    @Override public void close() {
        live=false;
        try { if(memory!=null) { put(84,0); int ok=(int)UNMAP.invokeExact(memory); memory=null; } if(handle.address()!=0) { int ok=(int)CLOSE.invokeExact(handle); handle=MemorySegment.NULL; } }
        catch(Throwable error) { HalfCraftGuest.LOG.error("HalfCraft UI cleanup failed",error); }
    }
}
