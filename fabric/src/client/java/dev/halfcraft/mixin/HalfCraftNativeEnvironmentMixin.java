package dev.halfcraft.mixin;

import dev.halfcraft.HalfCraftInventory;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native geometry and loading pauses are not MC suffocation/void hazards. GoldSrc owns these. */
@Mixin(LivingEntity.class)
public abstract class HalfCraftNativeEnvironmentMixin {
    @Inject(method="hurtServer",at=@At("HEAD"),cancellable=true)
    private void halfcraft$nativeEnvironment(ServerLevel level,DamageSource source,float damage,CallbackInfoReturnable<Boolean> result) {
        if(HalfCraftInventory.nativePlayer((LivingEntity)(Object)this) && !dev.halfcraft.HalfCraftVitals.applyingNativeDamage()
            && (source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.FALL))) result.setReturnValue(false);
    }
}
