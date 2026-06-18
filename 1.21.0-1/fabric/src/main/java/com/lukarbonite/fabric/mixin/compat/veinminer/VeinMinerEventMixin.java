package com.lukarbonite.fabric.mixin.compat.veinminer;

import com.lukarbonite.autopickup.AutoPickupApi;
import com.lukarbonite.autopickup.AutoPickupSessions;
import de.miraculixx.veinminer.event.VeinMinerEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = VeinMinerEvent.class, remap = false)
public abstract class VeinMinerEventMixin {

    @Inject(
            method = "destroyBlock",
            at = @At("HEAD")
    )
    private void autopickup_setupContext(
            BlockState blockState,
            ItemStack tool,
            Level world,
            BlockPos position,
            Player player,
            BlockPos initialSource,
            CallbackInfo ci
    ) {
        if (world.isClientSide() || !(world instanceof ServerLevel)) return;

        // Establish the pickup context so global drop listeners intercept it
        AutoPickupApi.setBlockBreaker(player);
        AutoPickupSessions.addBreak(player, position);
        AutoPickupSessions.beginDropContext(player, position);
    }

    @Inject(
            method = "destroyBlock",
            at = @At("RETURN")
    )
    private void autopickup_clearContext(
            BlockState blockState,
            ItemStack tool,
            Level world,
            BlockPos position,
            Player player,
            BlockPos initialSource,
            CallbackInfo ci
    ) {
        if (world.isClientSide() || !(world instanceof ServerLevel)) return;

        // Clean up memory after VeinMiner is done processing this block
        AutoPickupSessions.endDropContext(player);
        AutoPickupApi.clearBlockBreaker();
    }
}