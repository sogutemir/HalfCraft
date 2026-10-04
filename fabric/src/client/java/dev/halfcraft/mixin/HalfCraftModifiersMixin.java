package dev.halfcraft.mixin;
import dev.halfcraft.HalfCraftInput;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Minecraft.class)
public abstract class HalfCraftModifiersMixin {
    @Inject(method="hasShiftDown",at=@At("HEAD"),cancellable=true)
    private void halfcraft$shift(CallbackInfoReturnable<Boolean> ci) { if(HalfCraftInput.takeover()) ci.setReturnValue(HalfCraftInput.keyDown(225)||HalfCraftInput.keyDown(229)); }
    @Inject(method="hasControlDown",at=@At("HEAD"),cancellable=true)
    private void halfcraft$control(CallbackInfoReturnable<Boolean> ci) { if(HalfCraftInput.takeover()) ci.setReturnValue(HalfCraftInput.keyDown(224)||HalfCraftInput.keyDown(228)); }
    @Inject(method="hasAltDown",at=@At("HEAD"),cancellable=true)
    private void halfcraft$alt(CallbackInfoReturnable<Boolean> ci) { if(HalfCraftInput.takeover()) ci.setReturnValue(HalfCraftInput.keyDown(226)||HalfCraftInput.keyDown(230)); }
}
