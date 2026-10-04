package dev.halfcraft.mixin;

import dev.halfcraft.HalfCraftPhysics;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** GoldSrc owns movement; MC retains ticking inventory, combat, hunger and animations. */
@Mixin(LivingEntity.class)
public abstract class HalfCraftNativeMovementMixin {
    @Inject(method="travel",at=@At("HEAD"),cancellable=true)
    private void halfcraft$native(Vec3 input,CallbackInfo ci) {
        if((Object)this instanceof Player && HalfCraftPhysics.nativeControl()) ci.cancel();
    }
}
