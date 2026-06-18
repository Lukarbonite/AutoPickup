package com.lukarbonite.forge.mixin.compat.fallingtree;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lukarbonite.autopickup.AutoPickupApi;
import com.lukarbonite.autopickup.AutoPickupSessions;
import fr.rakambda.fallingtree.common.tree.breaking.LeafForceBreaker;
import fr.rakambda.fallingtree.common.wrapper.IBlockPos;
import fr.rakambda.fallingtree.common.wrapper.IBlockState;
import fr.rakambda.fallingtree.common.wrapper.ILevel;
import fr.rakambda.fallingtree.common.wrapper.IPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = LeafForceBreaker.class, remap = false)
public class LeafForceBreakerMixin {

    @WrapOperation(
            method = "lambda$forceBreakDecayLeaves$0",
            at = @At(value = "INVOKE",
                    target = "Lfr/rakambda/fallingtree/common/wrapper/IBlockState;dropResources(Lfr/rakambda/fallingtree/common/wrapper/ILevel;Lfr/rakambda/fallingtree/common/wrapper/IBlockPos;)V"),
            remap = false
    )
    private void wrapLeafDropResources(IBlockState state, ILevel iLevel, IBlockPos iPos, Operation<Void> original,
                                        ILevel levelArg, IPlayer iPlayer, Object tree, IBlockPos leafPos) {
        Player player = AutoPickupApi.getBlockBreaker();
        BlockPos pos = ((BlockPos) iPos.getRaw()).immutable();
        if (player != null) {
            AutoPickupSessions.addBreak(player, pos);
            AutoPickupSessions.beginDropContext(player, pos);
        }
        try {
            original.call(state, iLevel, iPos);
        } finally {
            if (player != null) {
                AutoPickupSessions.endDropContext(player);
            }
        }
    }
}
