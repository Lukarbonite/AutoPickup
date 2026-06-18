package com.lukarbonite.autopickup.mixin;

import com.lukarbonite.autopickup.AutoPickupSessions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.BiConsumer;

/**
 * Intercepts dropFromEntityInteractLootTable on any LivingEntity (e.g. Armadillo brush
 * interaction) to attribute dropped items to the interacting player. Injecting here
 * rather than in entity-specific methods avoids client/server thread-safety issues:
 * this method is only ever called server-side (it requires a ServerLevel).
 */
@Mixin(LivingEntity.class)
public class BrushEntityMixin {

    @Inject(method = "dropFromEntityInteractLootTable", at = @At("HEAD"))
    private void autopickup_openBrushEntityContext(ServerLevel level,
                                                    ResourceKey<LootTable> lootTable,
                                                    Entity brusher,
                                                    ItemInstance tool,
                                                    BiConsumer<ServerLevel, ItemStack> consumer,
                                                    CallbackInfoReturnable<Boolean> cir) {
        if (!(brusher instanceof Player player)) return;
        LivingEntity self = (LivingEntity) (Object) this;
        AutoPickupSessions.begin(player);
        AutoPickupSessions.beginDropContext(player, self.blockPosition());
    }

    @Inject(method = "dropFromEntityInteractLootTable", at = @At("TAIL"))
    private void autopickup_closeBrushEntityContext(ServerLevel level,
                                                     ResourceKey<LootTable> lootTable,
                                                     Entity brusher,
                                                     ItemInstance tool,
                                                     BiConsumer<ServerLevel, ItemStack> consumer,
                                                     CallbackInfoReturnable<Boolean> cir) {
        if (!(brusher instanceof Player player)) return;
        AutoPickupSessions.endDropContext(player);
    }
}
