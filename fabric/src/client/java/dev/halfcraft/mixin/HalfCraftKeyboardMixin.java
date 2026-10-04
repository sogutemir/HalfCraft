package dev.halfcraft.mixin;
import dev.halfcraft.HalfCraftInput;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(KeyboardHandler.class)
public abstract class HalfCraftKeyboardMixin {
    @Inject(method="keyPress",at=@At("HEAD"),cancellable=true)
    private void halfcraft$key(long window,int action,KeyEvent event,CallbackInfo ci) { if(HalfCraftInput.takeover() && !HalfCraftInput.dispatching()) ci.cancel(); }
}
