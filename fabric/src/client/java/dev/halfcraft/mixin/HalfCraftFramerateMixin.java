package dev.halfcraft.mixin;
import com.mojang.blaze3d.platform.FramerateLimitTracker;
import dev.halfcraft.HalfCraftInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(FramerateLimitTracker.class)
public abstract class HalfCraftFramerateMixin {
    @Inject(method="getFramerateLimit",at=@At("HEAD"),cancellable=true)
    private void halfcraft$limit(CallbackInfoReturnable<Integer> ci) { if(HalfCraftInput.takeover()) ci.setReturnValue(60); }
}
