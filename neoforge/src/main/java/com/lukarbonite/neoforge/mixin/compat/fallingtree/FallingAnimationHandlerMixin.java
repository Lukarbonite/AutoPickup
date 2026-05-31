package com.lukarbonite.neoforge.mixin.compat.fallingtree;

import com.lukarbonite.autopickup.AutoPickupSessions;
import fr.rakambda.fallingtree.common.tree.IBreakAttemptResult;
import fr.rakambda.fallingtree.common.tree.Tree;
import fr.rakambda.fallingtree.common.tree.TreePart;
import fr.rakambda.fallingtree.common.tree.breaking.FallingAnimationTreeBreakingHandler;
import fr.rakambda.fallingtree.common.wrapper.IBlockPos;
import fr.rakambda.fallingtree.common.wrapper.ILevel;
import fr.rakambda.fallingtree.common.wrapper.IPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.LinkedList;

@Mixin(value = FallingAnimationTreeBreakingHandler.class, remap = false)
public class FallingAnimationHandlerMixin {

    // Pre-register all log positions so their FallingBlockEntities can be attributed to the
    // player even after blockBreaker is cleared and the session has expired.
    @Inject(method = "breakTree", at = @At("HEAD"), remap = false)
    private void onBreakTreeHead(boolean canHarvestBlock, IPlayer iPlayer, Tree tree,
                                  CallbackInfoReturnable<IBreakAttemptResult> cir) {
        Player player = (Player) iPlayer.getRaw();
        for (TreePart part : tree.getParts()) {
            AutoPickupSessions.preRegisterFallPosition((BlockPos) part.blockPos().getRaw(), player);
        }
    }

    @Inject(method = "fallLeaf", at = @At("HEAD"), remap = false)
    private void onFallLeafHead(LinkedList<IBlockPos> visited, IPlayer iPlayer, ILevel iLevel,
                                 int depth, IBlockPos iBlockPos, CallbackInfo ci) {
        Player player = (Player) iPlayer.getRaw();
        BlockPos pos = ((BlockPos) iBlockPos.getRaw()).immutable();
        AutoPickupSessions.addBreak(player, pos);
        // Pre-register leaf position for FALL_ALL_BLOCK (dropLeavesAsItems=false), where
        // fallLeaf creates a FallingBlockEntity instead of calling playerDestroy directly.
        AutoPickupSessions.preRegisterFallPosition(pos, player);
        AutoPickupSessions.beginDropContext(player, pos);
    }

    @Inject(method = "fallLeaf", at = @At("RETURN"), remap = false)
    private void onFallLeafReturn(LinkedList<IBlockPos> visited, IPlayer iPlayer, ILevel iLevel,
                                   int depth, IBlockPos iBlockPos, CallbackInfo ci) {
        Player player = (Player) iPlayer.getRaw();
        AutoPickupSessions.endDropContext(player);
    }
}
