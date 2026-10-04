package dev.halfcraft.mixin;
import dev.halfcraft.HalfCraftGuest;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Minecraft.class)
public abstract class HalfCraftFrameMixin {
    @Inject(method="renderFrame",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/GameRenderer;render()V",shift=At.Shift.AFTER))
    private void halfcraft$ui(boolean advance,CallbackInfo ci) { HalfCraftGuest.captureUI((Minecraft)(Object)this); }
    @Inject(method="renderFrame",at=@At("TAIL"))
    private void halfcraft$frame(boolean advance,CallbackInfo ci) { HalfCraftGuest.renderFrame((Minecraft)(Object)this); }
}
