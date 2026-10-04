package dev.halfcraft.mixin;
import dev.halfcraft.HalfCraftGuest;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LevelExtractor.class)
public abstract class HalfCraftSectionMixin {
    @Inject(method="setSectionDirty(IIIZ)V",at=@At("HEAD"))
    private void halfcraft$dirty(int x,int y,int z,boolean playerChanged,CallbackInfo ci) { HalfCraftGuest.sectionDirty(x,y,z); }
}
