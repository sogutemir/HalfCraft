package dev.halfcraft.mixin;

import dev.halfcraft.HalfCraftPhysics;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.*;

/** Native Shapes feed vanilla movement; no custom gravity/jump/collision solver. */
@Mixin(CollisionGetter.class)
public interface HalfCraftCollisionMixin {
    @Inject(method="getBlockCollisions",at=@At("RETURN"),cancellable=true)
    private void halfcraft$collision(Entity entity,AABB box,CallbackInfoReturnable<Iterable<VoxelShape>> cir) {
        if(HalfCraftPhysics.active() && (entity instanceof Player || entity instanceof ItemEntity)) {
            List<VoxelShape> result=new ArrayList<>(); cir.getReturnValue().forEach(result::add);
            if(!(entity instanceof Player) || !HalfCraftPhysics.nativeControl()) result.addAll(HalfCraftPhysics.near(box,entity instanceof ItemEntity));
            cir.setReturnValue(result);
        }
    }
}
