package com.lukarbonite.autopickup;

import com.lukarbonite.autopickup.compat.travelersbackpack.TravelersBackpackCompat;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public final class AutoPickupApi {

    private static final ThreadLocal<PlayerEntity> blockBreaker = new ThreadLocal<>();

    public static void setBlockBreaker(PlayerEntity player) { blockBreaker.set(player); }
    public static void clearBlockBreaker() { blockBreaker.remove(); }
    public static PlayerEntity getBlockBreaker() { return blockBreaker.get(); }

    /**
     * Permission Resolution Logic:
     * 1. Admin Override (Highest Priority)
     * 2. Client Preference (Only if the server specifically allows this feature to be controlled)
     * 3. Server Global Fallback (Default)
     */
    private static boolean resolve(PlayerEntity player,
                                   Function<PlayerConfigs.PlayerState, Boolean> overrideGetter,
                                   Function<PlayerConfigs.PlayerState, Boolean> clientGetter,
                                   boolean specificAllowance,
                                   boolean serverDefault) {

        PlayerConfigs.PlayerState state = PlayerConfigs.getState(player.getUuid());

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

    public static boolean isMasterEnabled(PlayerEntity player) {
        AutoPickupConfig cfg = AutoPickupConfig.getInstance();
        return resolve(player, s -> s.overrideMaster, s -> s.clientMaster, cfg.allowMaster, cfg.autoPickup);
    }
    public static boolean isBlocksEnabled(PlayerEntity player) {
        AutoPickupConfig cfg = AutoPickupConfig.getInstance();
        return resolve(player, s -> s.overrideBlocks, s -> s.clientBlocks, cfg.allowBlocks, cfg.autoPickupBlocks);
    }
    public static boolean isBlockXpEnabled(PlayerEntity player) {
        AutoPickupConfig cfg = AutoPickupConfig.getInstance();
        return resolve(player, s -> s.overrideBlockXp, s -> s.clientBlockXp, cfg.allowBlockXp, cfg.autoPickupBlockXp);
    }
    public static boolean isMobLootEnabled(PlayerEntity player) {
        AutoPickupConfig cfg = AutoPickupConfig.getInstance();
        return resolve(player, s -> s.overrideMobLoot, s -> s.clientMobLoot, cfg.allowMobLoot, cfg.autoPickupMobLoot);
    }
    public static boolean isMobXpEnabled(PlayerEntity player) {
        AutoPickupConfig cfg = AutoPickupConfig.getInstance();
        return resolve(player, s -> s.overrideMobXp, s -> s.clientMobXp, cfg.allowMobXp, cfg.autoPickupMobXp);
    }
    public static boolean isSplitMobLootEnabled(PlayerEntity player) {
        AutoPickupConfig cfg = AutoPickupConfig.getInstance();
        return resolve(player, s -> s.overrideSplitMobLoot, s -> s.clientSplitMobLoot, cfg.allowSplitMobLoot, cfg.autoPickupSplitMobLoot);
    }
    public static boolean isSplitMobXpEnabled(PlayerEntity player) {
        AutoPickupConfig cfg = AutoPickupConfig.getInstance();
        return resolve(player, s -> s.overrideSplitMobXp, s -> s.clientSplitMobXp, cfg.allowSplitMobXp, cfg.autoPickupSplitMobXp);
    }

    // --- Logic Implementation ---

    public static List<ItemStack> tryPickup(PlayerEntity player, List<ItemStack> drops) {
        World world = player.getEntityWorld();
        if (world.isClient() || !(world instanceof ServerWorld) || player.isSpectator()
                || !isMasterEnabled(player) || !isBlocksEnabled(player)) {
            return drops;
        }
        return insertDrops(player, drops);
    }

    public static List<ItemStack> tryPickupFromMob(PlayerEntity player, List<ItemStack> drops) {
        World world = player.getEntityWorld();
        if (world.isClient() || !(world instanceof ServerWorld) || player.isSpectator()
                || !isMasterEnabled(player) || !isMobLootEnabled(player)) {
            return drops;
        }
        return insertDrops(player, drops);
    }

    private static List<ItemStack> insertDrops(PlayerEntity player, List<ItemStack> drops) {
        List<ItemStack> unpicked = new ArrayList<>();
        boolean hasBackpack = FabricLoader.getInstance().isModLoaded("travelersbackpack");

        for (ItemStack stack : drops) {
            if (stack.isEmpty()) continue;
            if (hasBackpack) {
                stack = TravelersBackpackCompat.tryPickup(player, stack);
                if (stack.isEmpty()) continue;
            }
            if (player.getInventory().insertStack(stack)) {
                if (!stack.isEmpty()) unpicked.add(stack);
            } else {
                unpicked.add(stack);
            }
        }
        return unpicked;
    }

    public static void tryPickupBlockExperience(PlayerEntity player, int experience) {
        if (!isMasterEnabled(player) || !isBlockXpEnabled(player)) return;
        giveExperience(player, experience);
    }

    public static void tryPickupMobExperience(PlayerEntity player, int experience) {
        if (!isMasterEnabled(player) || !isMobXpEnabled(player)) return;
        giveExperience(player, experience);
    }

    private static void giveExperience(PlayerEntity player, int experience) {
        World world = player.getEntityWorld();
        if (experience <= 0 || world.isClient() || !(world instanceof ServerWorld)) return;

        Optional<RegistryEntry.Reference<Enchantment>> mendingOpt = world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getEntry(Enchantments.MENDING.getValue());
        if (mendingOpt.isEmpty()) {
            player.addExperience(experience);
            return;
        }
        RegistryEntry<Enchantment> mending = mendingOpt.get();

        List<ItemStack> mendable = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR || slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND) {
                ItemStack stack = player.getEquippedStack(slot);
                if (!stack.isEmpty() && stack.isDamaged() && EnchantmentHelper.getLevel(mending, stack) > 0) {
                    mendable.add(stack);
                }
            }
        }

        if (mendable.isEmpty()) {
            player.addExperience(experience);
            return;
        }

        ItemStack item = mendable.get(player.getRandom().nextInt(mendable.size()));
        int repair = Math.min(experience * 2, item.getDamage());
        item.setDamage(item.getDamage() - repair);
        int consumed = (repair + 1) / 2;
        int remaining = experience - consumed;

        if (remaining > 0) player.addExperience(remaining);
    }
}