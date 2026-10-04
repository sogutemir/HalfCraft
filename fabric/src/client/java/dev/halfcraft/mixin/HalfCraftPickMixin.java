package dev.halfcraft.mixin;
import dev.halfcraft.HalfCraftPhysics;
import dev.halfcraft.HalfCraftInteraction;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Minecraft.class)
public abstract class HalfCraftPickMixin {
    @Inject(method="pick",at=@At("TAIL"))
    private void halfcraft$pick(float partialTick,CallbackInfo ci) {
        Minecraft mc=(Minecraft)(Object)this;
        if(HalfCraftPhysics.active() && mc.player!=null && mc.hitResult!=null) {
            var origin=mc.player.getEyePosition(partialTick);
            var actorHit=HalfCraftInteraction.nativePick(mc,origin,origin.add(mc.player.getViewVector(partialTick).scale(mc.player.entityInteractionRange())));
            if(actorHit!=null && (!(mc.hitResult instanceof BlockHitResult block) || block.getType()!=net.minecraft.world.phys.HitResult.Type.BLOCK
                || mc.level.getBlockState(block.getBlockPos()).isAir()
                || actorHit.getLocation().distanceToSqr(origin)<=block.getLocation().distanceToSqr(origin)+.0025)) {
                mc.hitResult=actorHit;
                return;
            }
            var end=origin.add(mc.player.getViewVector(partialTick).scale(mc.player.blockInteractionRange()));
            var nativeHit=HalfCraftPhysics.nativeHit(origin,end);
            if(nativeHit!=null && (mc.hitResult.getType()==net.minecraft.world.phys.HitResult.Type.MISS || nativeHit.getLocation().distanceToSqr(origin)+.0025<mc.hitResult.getLocation().distanceToSqr(origin))) {
                mc.hitResult=mc.player.getMainHandItem().getItem() instanceof net.minecraft.world.item.BlockItem ? nativeHit
                    : BlockHitResult.miss(nativeHit.getLocation(),nativeHit.getDirection(),nativeHit.getBlockPos());
            }
        }
    }
}
