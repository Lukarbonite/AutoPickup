package com.lukehinojosa.autopickup.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.lukehinojosa.autopickup.AutoPickupApi;
import de.miraculixx.veinminer.VeinMinerEvent;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Collections;
import java.util.List;

/**
 * Mixin to add compatibility with the Veinminer mod.
 * This mixin targets Veinminer's custom drop logic directly.
 * It will safely fail to apply if Veinminer is not installed.
 */
@Mixin(value = VeinMinerEvent.class, remap = false)
public abstract class VeinMinerEventMixin {

    /**
     * Redirects the call to Block.popResource within Veinminer's drop logic.
     * This allows us to intercept every item drop from a veinmine action.
     * The method signature here MUST use Yarn mappings to match the development environment.
     *
     * @param world The world where the drop occurs.
     * @param pos The position of the drop.
     * @param stack The ItemStack being dropped.
     * @param breaker The entity that broke the block, captured from the local variables.
     */
    @Redirect(
            method = "improvedDropResources(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/entity/BlockEntity;Lnet/minecraft/entity/Entity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/util/math/BlockPos;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/block/Block;dropStack(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/item/ItemStack;)V"
            )
    )
    private void autopickup_redirectVeinminerDrops(World world, BlockPos pos, ItemStack stack, @Local(name = "breaker") Entity breaker) {
        // Only act if the breaker is a player
        if (breaker instanceof PlayerEntity player) {
            // Use our existing API to attempt the pickup.
            // The API handles the gamerule check and inventory insertion.
            List<ItemStack> remainingItems = AutoPickupApi.tryPickup(player, Collections.singletonList(stack));

            // If the item was not picked up (e.g., inventory full), then we perform the original action.
            if (!remainingItems.isEmpty()) {
                Block.dropStack(world, pos, remainingItems.get(0));
            }
            // If the item was picked up, we do nothing, effectively cancelling the drop.
        } else {
            // If the breaker is not a player (e.g., TNT), perform the original action.
            Block.dropStack(world, pos, stack);
        }
    }
}