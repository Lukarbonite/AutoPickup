package com.lukarbonite.autopickup.mixin.compat.fallingtree;

import com.lukarbonite.autopickup.AutoPickupSessions;
import fr.rakambda.fallingtree.common.tree.IBreakAttemptResult;
import fr.rakambda.fallingtree.common.tree.Tree;
import fr.rakambda.fallingtree.common.tree.TreePart;
import fr.rakambda.fallingtree.common.tree.breaking.FallingAnimationTreeBreakingHandler;
import fr.rakambda.fallingtree.common.wrapper.IBlockPos;
import fr.rakambda.fallingtree.common.wrapper.IPlayer;
import fr.rakambda.fallingtree.common.wrapper.IServerLevel;
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

    @Inject(method = "breakTree", at = @At("HEAD"), remap = false)
    private void onBreakTreeHead(boolean canHarvestBlock, IPlayer iPlayer, Tree tree,
                                  CallbackInfoReturnable<IBreakAttemptResult> cir) {
        Player player = (Player) iPlayer.getRaw();
        for (TreePart part : tree.getBreakableParts()) {
            AutoPickupSessions.preRegisterFallPosition((BlockPos) part.blockPos().getRaw(), player);
        }
    }

    @Inject(method = "fallLeaf", at = @At("HEAD"), remap = false)
    private void onFallLeafHead(LinkedList<IBlockPos> visited, IPlayer iPlayer, IServerLevel iLevel,
                                 int depth, IBlockPos iBlockPos, CallbackInfo ci) {
        Player player = (Player) iPlayer.getRaw();
        BlockPos pos = ((BlockPos) iBlockPos.getRaw()).immutable();
        AutoPickupSessions.addBreak(player, pos);
        AutoPickupSessions.preRegisterFallPosition(pos, player);
        AutoPickupSessions.beginDropContext(player, pos);
    }

    @Inject(method = "fallLeaf", at = @At("RETURN"), remap = false)
    private void onFallLeafReturn(LinkedList<IBlockPos> visited, IPlayer iPlayer, IServerLevel iLevel,
                                   int depth, IBlockPos iBlockPos, CallbackInfo ci) {
        Player player = (Player) iPlayer.getRaw();
        AutoPickupSessions.endDropContext(player);
    }
}
