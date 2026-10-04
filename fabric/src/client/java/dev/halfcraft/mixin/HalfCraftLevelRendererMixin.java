package dev.halfcraft.mixin;
import dev.halfcraft.HalfCraftInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LevelRenderer.class)
public abstract class HalfCraftLevelRendererMixin {
    @Inject(method="render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZZ)V",at=@At("HEAD"),cancellable=true)
    private void halfcraft$skipLevel(CallbackInfo ci) { if(HalfCraftInput.takeover()) ci.cancel(); }
}
