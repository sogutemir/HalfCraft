package dev.halfcraft.mixin;

import dev.halfcraft.HalfCraftBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public abstract class HalfCraftBlockChangeMixin {
    @Inject(method="setBlockState",at=@At("RETURN"))
    private void halfcraft$changed(BlockPos pos,BlockState state,int flags,CallbackInfoReturnable<BlockState> result) {
        if(result.getReturnValue()!=null) HalfCraftBlocks.changed(((LevelChunk)(Object)this).getLevel(),pos);
    }
}
