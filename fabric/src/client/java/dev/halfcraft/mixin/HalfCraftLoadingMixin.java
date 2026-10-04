package dev.halfcraft.mixin;
import dev.halfcraft.HalfCraftInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** SkyCraft's load tracker bypass: native world draws instead of compiled MC sections. */
@Mixin(targets="net.minecraft.client.multiplayer.LevelLoadTracker$WaitingForPlayerChunk")
public abstract class HalfCraftLoadingMixin {
    @Inject(method="isReady",at=@At("HEAD"),cancellable=true)
    private void halfcraft$ready(CallbackInfoReturnable<Boolean> ci) { if(HalfCraftInput.takeover()) ci.setReturnValue(true); }
}
