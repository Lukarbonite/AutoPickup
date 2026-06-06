package com.lukarbonite.autopickup.mixin.compat.fallingtree;

import com.lukarbonite.autopickup.AutoPickupApi;
import com.lukarbonite.autopickup.AutoPickupSessions;
import com.lukarbonite.autopickup.compat.fallingtree.FallingTreeLeafTracker;
import fr.rakambda.fallingtree.common.tree.IBreakAttemptResult;
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

@Mixin(value = {InstantaneousTreeBreakingHandler.class, FallingAnimationTreeBreakingHandler.class, ShiftDownTreeBreakingHandler.class}, remap = false)
public class TreeBreakingHandlerMixin {

    @Inject(method = "breakTree", at = @At("HEAD"), remap = false)
    private void onHead(boolean canHarvestBlock, IPlayer iPlayer, Tree tree,
                        CallbackInfoReturnable<IBreakAttemptResult> cir) {
        Player player = (Player) iPlayer.getRaw();
        AutoPickupApi.setBlockBreaker(player);
        AutoPickupSessions.begin(player);
        for (TreePart part : tree.getParts()) {
            AutoPickupSessions.addBreak(player, (BlockPos) part.blockPos().getRaw());
        }
        FallingTreeLeafTracker.trackLeaves(tree.getParts(), player.getUUID());
    }

    @Inject(method = "breakTree", at = @At("RETURN"), remap = false)
    private void onReturn(boolean canHarvestBlock, IPlayer iPlayer, Tree tree,
                          CallbackInfoReturnable<IBreakAttemptResult> cir) {
        AutoPickupApi.clearBlockBreaker();
    }
}
