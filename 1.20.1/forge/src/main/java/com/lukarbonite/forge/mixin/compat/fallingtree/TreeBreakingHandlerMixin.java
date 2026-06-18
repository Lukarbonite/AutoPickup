package com.lukarbonite.forge.mixin.compat.fallingtree;

import com.lukarbonite.autopickup.AutoPickupApi;
import com.lukarbonite.autopickup.AutoPickupSessions;
import com.lukarbonite.forge.compat.fallingtree.FallingTreeLeafTracker;
import fr.rakambda.fallingtree.common.tree.Tree;
import fr.rakambda.fallingtree.common.tree.TreePart;
import fr.rakambda.fallingtree.common.tree.breaking.FallingAnimationTreeBreakingHandler;
import fr.rakambda.fallingtree.common.tree.breaking.InstantaneousTreeBreakingHandler;
import fr.rakambda.fallingtree.common.tree.breaking.ShiftDownTreeBreakingHandler;
import fr.rakambda.fallingtree.common.wrapper.IPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Injects into all FallingTree breaking handlers (instantaneous, falling-animation, and shift-down)
 * to register an AutoPickup session for the player and record every log position in
 * the tree. Recording all positions lets the linked-context and same-tick fallbacks in
 * ServerLevelMixin attribute item drops to the correct player, regardless of how
 * FallingTree internally drops the loot for each block.
 */
@Mixin(value = {InstantaneousTreeBreakingHandler.class, FallingAnimationTreeBreakingHandler.class, ShiftDownTreeBreakingHandler.class}, remap = false)
public class TreeBreakingHandlerMixin {

    @Inject(method = "breakTree", at = @At("HEAD"), remap = false)
    private void onHead(IPlayer iPlayer, Tree tree,
                        CallbackInfoReturnable<Boolean> cir) {
        Player player = (Player) iPlayer.getRaw();
        AutoPickupApi.setBlockBreaker(player);
        AutoPickupSessions.begin(player);
        for (TreePart part : tree.getParts()) {
            AutoPickupSessions.addBreak(player, (BlockPos) part.blockPos().getRaw());
        }
        FallingTreeLeafTracker.trackLeaves(tree.getParts(), player.getUUID());
    }

    @Inject(method = "breakTree", at = @At("RETURN"), remap = false)
    private void onReturn(IPlayer iPlayer, Tree tree,
                          CallbackInfoReturnable<Boolean> cir) {
        AutoPickupApi.clearBlockBreaker();
    }
}
