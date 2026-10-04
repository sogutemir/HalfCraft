package dev.halfcraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.halfcraft.HalfCraftGuest;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** SkyCraft lifecycle adaptation: preserve backup, automate dedicated test-world prompt only. */
@Mixin(WorldOpenFlows.class)
public abstract class HalfCraftWorldOpenMixin {
    @WrapOperation(method="openWorldCheckWorldStemCompatibility",at=@At(value="INVOKE",
        target="Lnet/minecraft/client/gui/screens/worldselection/WorldOpenFlows;askForBackup(Lnet/minecraft/world/level/storage/LevelStorageSource$LevelStorageAccess;ZLjava/lang/Runnable;Ljava/lang/Runnable;)V"))
    private void halfcraft$backup(WorldOpenFlows self,LevelStorageSource.LevelStorageAccess access,boolean customized,Runnable proceed,Runnable cancel,Operation<Void> original) {
        if(("HalfCraftPhysics".equals(access.getLevelId()) || access.getLevelId().matches("HalfCraftPhysics_[a-z0-9_-]{1,63}")) && !customized) {
            try { long bytes=access.makeWorldBackup(); HalfCraftGuest.LOG.info("HalfCraft physics world backup bytes={}",bytes); proceed.run(); return; }
            catch(java.io.IOException e) { HalfCraftGuest.LOG.error("Physics world backup failed; keeping normal prompt",e); }
        }
        original.call(self,access,customized,proceed,cancel);
    }
}
