package dev.halfcraft.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import dev.halfcraft.HalfCraftInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(InputConstants.class)
public abstract class HalfCraftInputConstantsMixin {
    @Inject(method="isKeyDown",at=@At("HEAD"),cancellable=true)
    private static void halfcraft$key(int key,CallbackInfoReturnable<Boolean> ci) { if(HalfCraftInput.takeover()) ci.setReturnValue(HalfCraftInput.keyDown(key)); }
    @Inject(method={"grabMouse","releaseMouse"},at=@At("HEAD"),cancellable=true)
    private static void halfcraft$grab(Window window,double x,double y,CallbackInfo ci) { if(HalfCraftInput.takeover()) ci.cancel(); }
}
