# Auto Pickup for Fabric

![Fabric](https://img.shields.io/badge/modloader-fabric-blue?style=for-the-badge)![Minecraft](https://img.shields.io/badge/minecraft-1.21.7-green?style=for-the-badge)![License](https://img.shields.io/badge/license-AGPL%203.0-lightgrey?style=for-the-badge)

**Auto Pickup** is a simple, lightweight, server-side Fabric mod that automatically places items and experience directly into your inventory from broken blocks and slain mobs. No more chasing drops, no more lost items.

## ✨ Features

*   **Instant Collection:** Items and experience from broken blocks and mob drops are instantly added to your inventory and experience bar.
*   **Automatic Experience:** Experience orbs are collected directly, without ever spawning as entities.
*   **Lag-Free:** Prevents item and experience orb entities from spawning in the world, which can help reduce server lag.
*   **Smart Handling:** If your inventory is full, items will be **ejected from your player** with a "spitting" effect.
*   **Separate Controls:** Independent gamerules for block drops and mob loot give you fine-grained control over the mod's behavior.
*   **High Compatibility:** Designed to work with vanilla mechanics and other mods.

![OneBlock](https://github.com/user-attachments/assets/5e3afe38-de87-4a3a-a0fa-3de2fa9a7a8f)

## ⚙️ Configuration via GameRules

The mod is controlled by two separate gamerules, which can be changed by any server operator or in single-player worlds with cheats enabled.

### Block Drops

This controls auto pickup from blocks. It is **on by default**.

*   **To enable Auto Pickup for blocks (Default):**
    ```
    /gamerule autoPickup true
    ```

*   **To disable Auto Pickup for blocks:**
    ```
    /gamerule autoPickup false
    ```

### Mob Loot

This controls auto pickup from mobs killed by a player. It is **off by default**.

*   **To enable Auto Pickup for mob loot:**
    ```
    /gamerule autoPickupMobLoot true
    ```

*   **To disable Auto Pickup for mob loot (Default):**
    ```
    /gamerule autoPickupMobLoot false
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

*   **Veinminer:** This mod includes a built-in, no-hassle compatibility layer for [MiraculixxT's Veinminer](https://modrinth.com/datapack/veinminer). All items and experience from blocks broken as part of a veinmine action will be correctly processed.
*   **Other Mods:** It should work seamlessly with most mods that use standard block-breaking and loot-dropping mechanics. Similarly, drops from most vanilla and modded mobs are also supported. If you find an incompatibility, please [open an issue](https://github.com/lukarbonite/autopickup/issues)!

![MultiBlock](https://github.com/user-attachments/assets/63267ae6-2c95-47ea-821b-2cc5b50218bb)

## 👩‍💻 For Developers: Using the API

If your mod features custom logic for breaking blocks or dropping items, you can easily integrate with Auto Pickup to provide your users with a seamless experience.

**1. Add Auto Pickup as a Dependency (build.gradle)**

It is recommended to use `modCompileOnly` so your mod doesn't require Autopickup to be installed.

```groovy
repositories {
    // ...
}

dependencies {
    // ...
    modCompileOnly "com.lukarbonite:auto-pickup:x.x.x" // Replace with the correct group/version
}
```

**2. Using the API**

The `AutoPickupApi` class provides simple, static methods to process drops for different contexts.

### API for Block Drops

To ensure both items and experience from your custom blocks are automatically picked up, you need to provide a "context" for who is breaking the block.

Use `AutoPickupApi.setBlockBreaker(player)` before your drop logic and `AutoPickupApi.clearBlockBreaker()` in a `finally` block to ensure it's always cleaned up.

```java
import com.lukarbonite.autopickup.AutoPickupApi;
import net.fabricmc.loader.api.FabricLoader;

// ...

public void yourFullDropMethod(BlockState state, ServerWorld world, BlockPos pos, PlayerEntity player, ItemStack tool) {
    if (!FabricLoader.getInstance().isModLoaded("auto-pickup")) { /* Your default drop logic */ return; }

    AutoPickupApi.setBlockBreaker(player);
    try {
        List<ItemStack> drops = Block.getDroppedStacks(state, world, pos, null, player, tool);
        List<ItemStack> remainingDrops = AutoPickupApi.tryPickup(player, drops);

        for (ItemStack stack : remainingDrops) {
            player.dropItem(stack, true);
        }
        // Trigger vanilla experience drop logic (which Auto Pickup will intercept via the context)
        state.onStacksDropped(world, pos, tool, true);
    } finally {
        // IMPORTANT: Always clear the context
        AutoPickupApi.clearBlockBreaker();
    }
}
```

### API for Mob Loot

Handling drops from custom mobs is even simpler.

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
*Note: Experience from custom mobs is handled automatically as long as they extend `LivingEntity` and use the vanilla experience-dropping methods.*

## 📜 License

This project is licensed under the **AGPL 3.0 License**. See the `LICENSE` file for more details. Feel free to use it in your modpacks.