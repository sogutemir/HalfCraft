package dev.halfcraft.mixin;
import dev.halfcraft.HalfCraftGuest;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Minecraft.class)
public abstract class HalfCraftStopMixin {
    @Inject(method="stop",at=@At("HEAD"))
    private void halfcraft$restoreBeforeStop(CallbackInfo ci) { HalfCraftGuest.beforeStop((Minecraft)(Object)this); }
}
