package dev.halfcraft.mixin;

import dev.halfcraft.HalfCraftBlocks;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla placement still chooses adjacent face; native NPCs veto overlapping shapes on both sides. */
@Mixin(BlockItem.class)
public abstract class HalfCraftPlacementMixin {
    @Inject(method="canPlace",at=@At("RETURN"),cancellable=true)
    private void halfcraft$placement(BlockPlaceContext context,BlockState state,CallbackInfoReturnable<Boolean> cir) {
        if(cir.getReturnValue() && !HalfCraftBlocks.canPlace(context.getLevel(),context.getClickedPos(),state)) cir.setReturnValue(false);
    }
}
