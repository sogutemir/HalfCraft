package dev.halfcraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.halfcraft.HalfCraftPhysics;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractArrow.class)
public abstract class HalfCraftArrowMixin {
    @WrapOperation(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;clipIncludingBorder(Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;"))
    private BlockHitResult halfcraft$wall(Level level,ClipContext context,Operation<BlockHitResult> original) {
        var hit=original.call(level,context);
        var nativeHit=HalfCraftPhysics.nativeHit(context.getFrom(),context.getTo());
        return nativeHit!=null && (hit.getType()==HitResult.Type.MISS || nativeHit.getLocation().distanceToSqr(context.getFrom())<hit.getLocation().distanceToSqr(context.getFrom()))?nativeHit:hit;
    }
}
