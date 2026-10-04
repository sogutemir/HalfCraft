package dev.halfcraft.mixin;

import dev.halfcraft.HalfCraftPhysics;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Host publishes authoritative position; client movement packets must not replay it against MC blocks. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class HalfCraftNativePacketMixin {
    @Shadow public ServerPlayer player;

    @Inject(method="handleMovePlayer",at=@At(value="INVOKE",target="Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",shift=At.Shift.AFTER),cancellable=true)
    private void halfcraft$nativePosition(ServerboundMovePlayerPacket packet,CallbackInfo ci) {
        if(HalfCraftPhysics.nativeControl() && player.level().getServer().isSingleplayer()) ci.cancel();
    }
}
