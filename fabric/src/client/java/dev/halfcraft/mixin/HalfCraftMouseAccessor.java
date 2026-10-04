package dev.halfcraft.mixin;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(MouseHandler.class)
public interface HalfCraftMouseAccessor {
    @Accessor("mouseGrabbed") void halfcraft$grabbed(boolean value);
}
