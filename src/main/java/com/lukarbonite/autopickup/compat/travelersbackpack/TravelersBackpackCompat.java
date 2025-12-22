package com.lukarbonite.autopickup.compat.travelersbackpack;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

import java.lang.reflect.Method;
import java.util.Optional;

public class TravelersBackpackCompat {

    private static boolean initialized = false;
    private static boolean available = false;

    // Reflected Methods and Classes
    private static Method getBackpackWrapper;
    private static Method getUpgradeManager;
    private static Method getUpgrade;
    private static Method canPickup;
    private static Method getStorage;
    private static Method insertItem;
    private static Method getSlots;
    private static Class<?> autoPickupUpgradeClass;

    private static void initialize() {
        if (initialized) return;
        initialized = true;

        try {
            // 1. ComponentUtils.getBackpackWrapper(PlayerEntity)
            Class<?> componentUtils = Class.forName("com.tiviacz.travelersbackpack.component.ComponentUtils");
            getBackpackWrapper = componentUtils.getMethod("getBackpackWrapper", PlayerEntity.class);

            // 2. BackpackWrapper methods
            Class<?> backpackWrapper = Class.forName("com.tiviacz.travelersbackpack.inventory.BackpackWrapper");
            getUpgradeManager = backpackWrapper.getMethod("getUpgradeManager");
            getStorage = backpackWrapper.getMethod("getStorage");

            // 3. UpgradeManager.getUpgrade(Class)
            Class<?> upgradeManager = Class.forName("com.tiviacz.travelersbackpack.inventory.UpgradeManager");
            getUpgrade = upgradeManager.getMethod("getUpgrade", Class.class);

            // 4. AutoPickupUpgrade class & canPickup(ItemStack)
            autoPickupUpgradeClass = Class.forName("com.tiviacz.travelersbackpack.inventory.upgrades.pickup.AutoPickupUpgrade");
            canPickup = autoPickupUpgradeClass.getMethod("canPickup", ItemStack.class);

            // 5. ItemStackHandler (Storage) methods
            Class<?> itemStackHandler = Class.forName("com.tiviacz.travelersbackpack.inventory.handler.ItemStackHandler");
            insertItem = itemStackHandler.getMethod("insertItem", int.class, ItemStack.class, boolean.class);
            getSlots = itemStackHandler.getMethod("getSlots");

            available = true;
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            // Mod not loaded or API changed, integration will be disabled
            available = false;
        }
    }

    /**
     * Attempts to insert the stack into the player's equipped Traveler's Backpack
     * via reflection if the AutoPickup upgrade is enabled.
     */
    public static ItemStack tryPickup(PlayerEntity player, ItemStack stack) {
        if (!initialized) initialize();
        if (!available) return stack;

        try {
            // BackpackWrapper wrapper = ComponentUtils.getBackpackWrapper(player);
            Object wrapper = getBackpackWrapper.invoke(null, player);
            if (wrapper == null) return stack;

            // UpgradeManager manager = wrapper.getUpgradeManager();
            Object manager = getUpgradeManager.invoke(wrapper);

            // Optional<AutoPickupUpgrade> upgradeOpt = manager.getUpgrade(AutoPickupUpgrade.class);
            Object upgradeOptObj = getUpgrade.invoke(manager, autoPickupUpgradeClass);

            if (upgradeOptObj instanceof Optional<?> opt && opt.isPresent()) {
                Object upgrade = opt.get();

                // if (upgrade.canPickup(stack))
                boolean can = (boolean) canPickup.invoke(upgrade, stack);
                if (can) {
                    // ItemStackHandler storage = wrapper.getStorage();
                    Object storage = getStorage.invoke(wrapper);

                    // int slots = storage.getSlots();
                    int slots = (int) getSlots.invoke(storage);

                    for (int i = 0; i < slots; i++) {
                        // stack = storage.insertItem(i, stack, false);
                        // insertItem returns the remainder stack
                        stack = (ItemStack) insertItem.invoke(storage, i, stack, false);

                        if (stack.isEmpty()) {
                            return ItemStack.EMPTY;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // If reflection fails at runtime, return original stack
            return stack;
        }
        return stack;
    }
}