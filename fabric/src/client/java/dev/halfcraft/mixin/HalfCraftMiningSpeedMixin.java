package dev.halfcraft.mixin;

import dev.halfcraft.HalfCraftPhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class HalfCraftMiningSpeedMixin {
    @Inject(method="getDestroyProgress",at=@At("RETURN"),cancellable=true)
    private void halfcraft$speed(Player player,BlockGetter level,BlockPos pos,CallbackInfoReturnable<Float> result) {
        if(HalfCraftPhysics.active()) result.setReturnValue(result.getReturnValue()*5);
    }
}
