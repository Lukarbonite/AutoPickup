package com.lukarbonite.autopickup.mixin.compat.veinminer;

import com.lukarbonite.autopickup.AutoPickupApi;
import com.lukarbonite.autopickup.AutoPickupSessions;
import com.lukarbonite.autopickup.ExperienceCache;
import de.miraculixx.veinminer.event.VeinMinerEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
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
        if (world.isClientSide() || !(world instanceof ServerLevel serverLevel)) return;

        // On NeoForge, Block.spawnAfterBreak() is a no-op. NeoForge routes ore XP through
        // getExpDrop() -> BlockDropsEvent -> popExperience(), but only when a block is broken
        // via ServerPlayerGameMode. VeinMiner bypasses SPGM for vein blocks and calls
        // spawnAfterBreak() directly, so XP is never spawned. We replicate the NeoForge XP
        // pipeline here (mirroring CommonHooks.handleBlockDrops) to compensate.
        int xp = blockState.getExpDrop(serverLevel, position, null, player, tool);
        xp = EnchantmentHelper.processBlockExperience(serverLevel, tool, xp);
        if (xp > 0) {
            if (AutoPickupApi.isMasterEnabled(player) && AutoPickupApi.isBlockXpEnabled(player)) {
                ExperienceCache.add(player, xp);
            } else {
                // Autopickup XP is disabled — spawn the orb normally so the player still
                // receives XP the vanilla way.
                blockState.getBlock().popExperience(serverLevel, position, xp);
            }
        }

        AutoPickupSessions.endDropContext(player);
    }
}
