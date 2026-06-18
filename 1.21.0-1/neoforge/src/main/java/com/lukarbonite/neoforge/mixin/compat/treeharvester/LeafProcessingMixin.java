package com.lukarbonite.neoforge.mixin.compat.treeharvester;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lukarbonite.autopickup.AutoPickupApi;
import com.lukarbonite.autopickup.util.OwnedBlockPos;
import com.natamus.treeharvester_common_neoforge.processing.LeafProcessing;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.CopyOnWriteArrayList;

@Mixin(value = LeafProcessing.class, remap = false)
public class LeafProcessingMixin {

    @WrapOperation(method = "breakTreeLeaves",
            at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CopyOnWriteArrayList;add(Ljava/lang/Object;)Z"),
            remap = false)
    private static boolean tagLeaf(CopyOnWriteArrayList instance, Object element, Operation<Boolean> original) {
        if (element instanceof BlockPos pos && AutoPickupApi.getBlockBreaker() != null) {
            return original.call(instance, new OwnedBlockPos(pos, AutoPickupApi.getBlockBreaker().getUUID()));
        }
        return original.call(instance, element);
    }
}
