# Auto Pickup for Fabric

![Fabric](https://img.shields.io/badge/modloader-fabric-blue?style=for-the-badge)![Minecraft](https://img.shields.io/badge/minecraft-1.21.8-green?style=for-the-badge)![License](https://img.shields.io/badge/license-AGPL%203.0-lightgrey?style=for-the-badge)

**Auto Pickup** is a simple, lightweight, server-side Fabric mod that automatically places items and experience directly into your inventory from broken blocks and slain mobs. No more chasing drops, no more lost items.

## ✨ Features

*   **Seamless Collection:** Items from broken blocks and mob drops are instantly added to your inventory.
*   **Correct Mending Order:** Experience is applied *after* tool durability damage, ensuring Mending works correctly with tools and mods like Veinminer.
*   **Automatic Experience:** Experience orbs are collected directly by a caching system, without ever spawning as entities in the world.
*   **Lag-Free:** Prevents item and experience orb entities from spawning, which can help reduce server lag.
*   **Smart Handling:** If your inventory is full, any items that cannot be picked up will be safely dropped at your feet.
*   **Granular Control:** Three independent gamerules for blocks, mobs, and experience give you fine-grained control over the mod's behavior.
*   **High Compatibility:** Designed to work seamlessly with vanilla mechanics and other mods.

![OneBlock](https://github.com/user-attachments/assets/5e3afe38-de87-4a3a-a0fa-3de2fa9a7a8f)

## ⚙️ Configuration via GameRules

The mod is controlled by three separate gamerules, which can be changed by any server operator or in single-player worlds with cheats enabled.

### Block Drops

This controls auto pickup for items from blocks. It is **on by default**.

*   **To enable (Default):**
    ```
    /gamerule autoPickup true
    ```

*   **To disable:**
    ```
    /gamerule autoPickup false
    ```

### Mob Loot

This controls auto pickup for items from mobs killed by a player. It is **off by default**.

*   **To enable:**
    ```
    /gamerule autoPickupMobLoot true
    ```

*   **To disable (Default):**
    ```
    /gamerule autoPickupMobLoot false
    ```

### Experience

This controls auto pickup for all experience orbs. It is **on by default**.

*   **To enable (Default):**
    ```
    /gamerule autoPickupXp true
    ```

*   **To disable:**
    ```
    /gamerule autoPickupXp false
    ```

## 📦 Installation

This is a standard Fabric mod.

1.  Ensure you have [Fabric Loader](https://fabricmc.net/use/) installed.
2.  Download the **Fabric API** and place it in your `mods` folder.
3.  Download the **Auto Pickup** JAR from the releases page.
4.  Place the `auto-pickup-x.x.x.jar` file into your `mods` folder.

That's it! The mod is purely server-side, but it will also work in single-player.

## ✅ Compatibility

Auto Pickup is designed to be highly compatible with the modded ecosystem.

*   **Veinminer:** This mod includes deep, built-in compatibility for [Miraculixx's Veinminer](https://modrinth.com/datapack/veinminer). All items and experience from blocks broken as part of a veinmine action will be correctly picked up, and the Mending enchantment will be applied properly after all durability damage is dealt.
*   **Other Mods:** It should work seamlessly with most mods that use standard block-breaking and loot-dropping mechanics. Similarly, drops from most vanilla and modded mobs are also supported. If you find an incompatibility, please [open an issue](https://github.com/lukarbonite/autopickup/issues)!

![MultiBlock](https://github.com/user-attachments/assets/63267ae6-2c95-47ea-821b-2cc5b50218bb)

## 👩‍💻 For Developers: Using the API

Good news! The API is now much simpler, and in most cases, you don't need to do anything at all.

Auto Pickup is designed to be "zero-config" for other developers. It achieves compatibility by hooking into fundamental vanilla methods (`Block.dropStacks` and `LivingEntity.dropExperience`). As long as your mod uses these standard methods for handling drops, Auto Pickup will work with it automatically.

If you still wish to check if the mod is loaded or want to use the API for specific cases, you can do so.

**1. Add Auto Pickup as a Dependency (build.gradle)**

It is recommended to use `modCompileOnly` so your mod doesn't require Autopickup to be installed.

```groovy
repositories {
    // ... your repositories
}

dependencies {
    // ... your dependencies
    // Get the version from the Modrinth page
    modCompileOnly(files("libs/auto-pickup-x.x.x.jar"))
}
```

**2. Using the API**

The `AutoPickupApi` class provides simple, static methods to process drops and experience. Always check if the mod is loaded before using the API.

### Item Pickup

These methods attempt to add items to a player's inventory and return a list of items that could not be picked up.

*   `AutoPickupApi.tryPickup(PlayerEntity player, List<ItemStack> drops)`
    *   For items from **blocks**. Respects the `autoPickup` gamerule.
*   `AutoPickupApi.tryPickupFromMob(PlayerEntity player, List<ItemStack> drops)`
    *   For items from **mobs**. Respects the `autoPickupMobLoot` gamerule.

```java
import com.lukarbonite.autopickup.AutoPickupApi;
import net.fabricmc.loader.api.FabricLoader;

// ...

public void yourCustomMobDropMethod(PlayerEntity player, List<ItemStack> yourMobDrops) {
    List<ItemStack> remainingDrops = yourMobDrops;

    if (FabricLoader.getInstance().isModLoaded("auto-pickup")) {
        // Use the specific API for mob loot
        remainingDrops = AutoPickupApi.tryPickupFromMob(player, yourMobDrops);
    }

    // Drop any leftovers at the player
    for (ItemStack stack : remainingDrops) {
        player.dropItem(stack, true);
    }
}
```

### Experience Pickup & Mending

This method handles giving experience to the player and ensures the Mending logic is applied correctly.

*   `AutoPickupApi.tryPickupExperience(PlayerEntity player, int experience)`
    *   Gives experience and handles Mending. Respects the `autoPickupXp` gamerule. This is useful for custom sources of experience, like quest rewards.

### Block Breaker Context (Advanced)

This is the most important feature for compatibility with custom block-breaking logic. Auto Pickup's mixins need to know *which player* caused a block to break to correctly process drops and experience. For custom logic (e.g., a magic spell that breaks blocks), you must provide this context.

*   `AutoPickupApi.setBlockBreaker(PlayerEntity player)`: Sets the current player context.
*   `AutoPickupApi.clearBlockBreaker()`: **Crucially**, clears the context.
*   `AutoPickupApi.getBlockBreaker()`: Gets the current player context, if any.

**Always wrap your drop logic in a `try...finally` block to guarantee the context is cleared.**

```java
import com.lukarbonite.autopickup.AutoPickupApi;
import net.fabricmc.loader.api.FabricLoader;

// ...

public void yourFullDropMethod(BlockState state, ServerWorld world, BlockPos pos, PlayerEntity player, ItemStack tool) {
    // Fallback to your default logic if Auto Pickup isn't installed
    if (!FabricLoader.getInstance().isModLoaded("auto-pickup")) {
        // ... your default drop logic ...
        return;
    }

    // This is the core pattern for compatibility
    AutoPickupApi.setBlockBreaker(player);
    try {
        // Let vanilla (and thus, Auto Pickup's mixins) handle the drops
        Block.dropStacks(state, world, pos, null, player, tool);
    } finally {
        // IMPORTANT: Always clear the context to prevent bugs and memory leaks
        AutoPickupApi.clearBlockBreaker();
    }
}
```

## 📜 License

This project is licensed under the **AGPL 3.0 License**. See the `LICENSE` file for more details. Feel free to use it in your modpacks.