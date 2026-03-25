package com.lukarbonite.autopickup;

//import com.lukarbonite.autopickup.compat.travelersbackpack.TravelersBackpackCompat;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public final class AutoPickupApi {

    private static final ThreadLocal<Player> blockBreaker = new ThreadLocal<>();

    public static void setBlockBreaker(Player player) { blockBreaker.set(player); }
    public static void clearBlockBreaker() { blockBreaker.remove(); }
    public static Player getBlockBreaker() { return blockBreaker.get(); }

    /**
     * Permission Resolution Logic:
     * 1. Admin Override (Highest Priority)
     * 2. Client Preference (Only if the server specifically allows this feature to be controlled)
     * 3. Server Global Fallback (Default)
     */
    private static boolean resolve(Player player,
                                   Function<PlayerConfigs.PlayerState, Boolean> overrideGetter,
                                   Function<PlayerConfigs.PlayerState, Boolean> clientGetter,
                                   boolean specificAllowance,
                                   boolean serverDefault) {

        PlayerConfigs.PlayerState state = PlayerConfigs.getState(player.getUUID());

        // 1. Check Admin Override
        Boolean override = overrideGetter.apply(state);
        if (override != null) return override;

        // 2. Check Client preference IF server allows it for this specific setting
        if (specificAllowance) {
            return clientGetter.apply(state);
        }

        // 3. Fallback to Server global config
        return serverDefault;
    }

    public static boolean isMasterEnabled(Player player) {
        AutoPickupConfig cfg = AutoPickupConfig.getInstance();
        return resolve(player, s -> s.overrideMaster, s -> s.clientMaster, cfg.allowMaster, cfg.autoPickup);
    }
    public static boolean isBlocksEnabled(Player player) {
        AutoPickupConfig cfg = AutoPickupConfig.getInstance();
        return resolve(player, s -> s.overrideBlocks, s -> s.clientBlocks, cfg.allowBlocks, cfg.autoPickupBlocks);
    }
    public static boolean isBlockXpEnabled(Player player) {
        AutoPickupConfig cfg = AutoPickupConfig.getInstance();
        return resolve(player, s -> s.overrideBlockXp, s -> s.clientBlockXp, cfg.allowBlockXp, cfg.autoPickupBlockXp);
    }
    public static boolean isMobLootEnabled(Player player) {
        AutoPickupConfig cfg = AutoPickupConfig.getInstance();
        return resolve(player, s -> s.overrideMobLoot, s -> s.clientMobLoot, cfg.allowMobLoot, cfg.autoPickupMobLoot);
    }
    public static boolean isMobXpEnabled(Player player) {
        AutoPickupConfig cfg = AutoPickupConfig.getInstance();
        return resolve(player, s -> s.overrideMobXp, s -> s.clientMobXp, cfg.allowMobXp, cfg.autoPickupMobXp);
    }
    public static boolean isSplitMobLootEnabled(Player player) {
        AutoPickupConfig cfg = AutoPickupConfig.getInstance();
        return resolve(player, s -> s.overrideSplitMobLoot, s -> s.clientSplitMobLoot, cfg.allowSplitMobLoot, cfg.autoPickupSplitMobLoot);
    }
    public static boolean isSplitMobXpEnabled(Player player) {
        AutoPickupConfig cfg = AutoPickupConfig.getInstance();
        return resolve(player, s -> s.overrideSplitMobXp, s -> s.clientSplitMobXp, cfg.allowSplitMobXp, cfg.autoPickupSplitMobXp);
    }

    // --- Logic Implementation ---

    public static List<ItemStack> tryPickup(Player player, List<ItemStack> drops) {
        Level world = player.level();
        if (world.isClientSide() || !(world instanceof ServerLevel) || player.isSpectator()
                || !isMasterEnabled(player) || !isBlocksEnabled(player)) {
            return drops;
        }
        return insertDrops(player, drops);
    }

    public static List<ItemStack> tryPickupFromMob(Player player, List<ItemStack> drops) {
        Level world = player.level();
        if (world.isClientSide() || !(world instanceof ServerLevel) || player.isSpectator()
                || !isMasterEnabled(player) || !isMobLootEnabled(player)) {
            return drops;
        }
        return insertDrops(player, drops);
    }

    private static List<ItemStack> insertDrops(Player player, List<ItemStack> drops) {
        List<ItemStack> unpicked = new ArrayList<>();
        //boolean hasBackpack = FabricLoader.getInstance().isModLoaded("travelersbackpack");

        for (ItemStack stack : drops) {
            if (stack.isEmpty()) continue;
//            if (hasBackpack) {
//                stack = TravelersBackpackCompat.tryPickup(player, stack);
//                if (stack.isEmpty()) continue;
//            }
            if (player.getInventory().add(stack)) {
                if (!stack.isEmpty()) unpicked.add(stack);
            } else {
                unpicked.add(stack);
            }
        }
        return unpicked;
    }

    public static void tryPickupBlockExperience(Player player, int experience) {
        if (!isMasterEnabled(player) || !isBlockXpEnabled(player)) return;
        giveExperience(player, experience);
    }

    public static void tryPickupMobExperience(Player player, int experience) {
        if (!isMasterEnabled(player) || !isMobXpEnabled(player)) return;
        giveExperience(player, experience);
    }

    private static void giveExperience(Player player, int experience) {
        Level world = player.level();
        if (experience <= 0 || world.isClientSide() || !(world instanceof ServerLevel)) return;

        Optional<Holder.Reference<Enchantment>> mendingOpt = world.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Enchantments.MENDING.identifier());
        if (mendingOpt.isEmpty()) {
            player.giveExperiencePoints(experience);
            return;
        }
        Holder<Enchantment> mending = mendingOpt.get();

        List<ItemStack> mendable = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR || slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND) {
                ItemStack stack = player.getItemBySlot(slot);
                if (!stack.isEmpty() && stack.isDamaged() && EnchantmentHelper.getItemEnchantmentLevel(mending, stack) > 0) {
                    mendable.add(stack);
                }
            }
        }

        if (mendable.isEmpty()) {
            player.giveExperiencePoints(experience);
            return;
        }

        ItemStack item = mendable.get(player.getRandom().nextInt(mendable.size()));
        int repair = Math.min(experience * 2, item.getDamageValue());
        item.setDamageValue(item.getDamageValue() - repair);
        int consumed = (repair + 1) / 2;
        int remaining = experience - consumed;

        if (remaining > 0) player.giveExperiencePoints(remaining);
    }
}