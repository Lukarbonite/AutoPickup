package com.lukarbonite.neoforge.mixin.compat.veinminer;

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

        // Open a tight drop context around this chained block. VeinMiner spawns both
        // item drops and XP orbs directly during destroyBlock(), so this context lets
        // BlockMixin (items) and ExperienceOrbAwardMixin (XP) attribute and capture
        // them for the breaking player.
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

        // VeinMiner has finished spawning this block's drops and XP (captured via the
        // drop context opened at HEAD). Close the context and clear breaker state.
        AutoPickupSessions.endDropContext(player);
        AutoPickupApi.clearBlockBreaker();
    }
}
