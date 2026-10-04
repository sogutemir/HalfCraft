package dev.halfcraft.mixin;
import dev.halfcraft.HalfCraftInput;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(MouseHandler.class)
public abstract class HalfCraftMouseMixin {
    @Inject(method="onButton",at=@At("HEAD"),cancellable=true)
    private void halfcraft$button(long window,MouseButtonInfo button,int action,CallbackInfo ci) { if(HalfCraftInput.takeover() && !HalfCraftInput.dispatching()) ci.cancel(); }
    @Inject(method="onScroll",at=@At("HEAD"),cancellable=true)
    private void halfcraft$scroll(long window,double x,double y,CallbackInfo ci) { if(HalfCraftInput.takeover() && !HalfCraftInput.dispatching()) ci.cancel(); }
    @Inject(method="onMove",at=@At("HEAD"),cancellable=true)
    private void halfcraft$move(long window,double x,double y,double dx,double dy,CallbackInfo ci) { if(HalfCraftInput.takeover() && !HalfCraftInput.dispatching()) ci.cancel(); }
}
