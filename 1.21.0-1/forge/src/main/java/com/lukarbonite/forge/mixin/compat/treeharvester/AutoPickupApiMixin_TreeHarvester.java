package com.lukarbonite.forge.mixin.compat.treeharvester;

import com.lukarbonite.autopickup.AutoPickupApi;
import com.natamus.treeharvester_common_forge.config.ConfigHandler;
import com.natamus.treeharvester_common_forge.data.Variables;
import com.natamus.treeharvester_common_forge.util.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import oshi.util.tuples.Triplet;

import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Mixin(value = AutoPickupApi.class, remap = false)
public class AutoPickupApiMixin_TreeHarvester {

    @SuppressWarnings("unchecked")
    @Inject(method = "tryPickup", at = @At("HEAD"))
    private static void onTryPickup(Player player, List<ItemStack> drops,
                                    CallbackInfoReturnable<List<ItemStack>> cir) {
        if (player.level().isClientSide() || !ConfigHandler.replaceSaplingOnTreeHarvest) {
            return;
        }

        Date now = new Date();
        Iterable<Triplet<Date, BlockPos, CopyOnWriteArrayList<BlockPos>>> positions = Variables.saplingPositions;

        for (ItemStack stack : drops) {
            if (stack.isEmpty()) continue;
            Item item = stack.getItem();
            if (!(item instanceof BlockItem blockItem)) continue;
            Block block = blockItem.getBlock();
            if (!Util.isSapling(block)) continue;

            for (Triplet<Date, BlockPos, CopyOnWriteArrayList<BlockPos>> triplet : positions) {
                long ms = now.getTime() - triplet.getA().getTime();
                if (ms > 2000L) {
                    Variables.saplingPositions.remove(triplet);
                    continue;
                }
                CopyOnWriteArrayList<BlockPos> lowerLogs = triplet.getC();
                for (BlockPos lowerLog : lowerLogs) {
                    if (stack.getCount() > 0) {
                        player.level().setBlock(lowerLog, block.defaultBlockState(), 3);
                        stack.shrink(1);
                        lowerLogs.remove(lowerLog);
                    }
                }
                if (lowerLogs.isEmpty()) Variables.saplingPositions.remove(triplet);
                if (stack.isEmpty()) break;
            }
        }
    }
}
