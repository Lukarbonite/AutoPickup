# Auto Pickup for Fabric

![Fabric](https://img.shields.io/badge/modloader-fabric-blue?style=for-the-badge)
![Minecraft](https://img.shields.io/badge/minecraft-1.21.5-green?style=for-the-badge)
![License](https://img.shields.io/badge/license-AGPL%203.0-lightgrey?style=for-the-badge)

**Auto Pickup** is a simple, lightweight, server-side Fabric mod that automatically places items from broken blocks directly into your inventory. No more chasing drops, no more lost items.

## ✨ Features

*   **Instant Collection:** Items from broken blocks are instantly added to your inventory.
*   **Lag-Free:** The mod is purely server-side and prevents item entities from ever being created in the world, which can help reduce server lag.
*   **Smart Handling:** If your inventory is full, items will be dropped at your feet as they would be in vanilla Minecraft.
*   **Simple Configuration:** Easily enable or disable the feature for all players using a single gamerule.
*   **High Compatibility:** Designed to work with vanilla mechanics and other mods.


![OneBlock](https://github.com/user-attachments/assets/5e3afe38-de87-4a3a-a0fa-3de2fa9a7a8f)


## ⚙️ Configuration via GameRule

The entire mod is controlled by a single gamerule, which can be changed by any server operator or in single-player worlds with cheats enabled.

*   **To enable Auto Pickup (Default):**
    ```
    /gamerule autoPickup true
    ```

*   **To disable Auto Pickup:**
    ```
    /gamerule autoPickup false
    ```

This setting applies to all players on the server.

## 📦 Installation

This is a standard Fabric mod.

1.  Ensure you have [Fabric Loader](https://fabricmc.net/use/) installed.
2.  Download the **Fabric API** and place it in your `mods` folder.
3.  Download the **Auto Pickup** JAR from the releases page.
4.  Place the `auto-pickup-x.x.x.jar` file into your `mods` folder.

That's it! The mod is purely server-side, but it will also work in single-player.

## ✅ Compatibility

Auto Pickup is designed to be highly compatible with the modded ecosystem.

*   **Veinminer:** This mod includes a built-in, no-hassle compatibility layer for [MiraculixxT's Veinminer](https://modrinth.com/datapack/veinminer) 2.4.2. All items broken as part of a veinmine action will be correctly processed by Auto Pickup.
*   **Other Mods:** It should work seamlessly with most mods that use standard block-breaking and drop mechanics. If you find an incompatibility, please [open an issue](https://github.com/lukehinojosa/autopickup/issues)!

![MultiBlock](https://github.com/user-attachments/assets/63267ae6-2c95-47ea-821b-2cc5b50218bb)


## 👩‍💻 For Developers: Using the API

If your mod features custom logic for breaking blocks or dropping items, you can easily integrate with Auto Pickup to provide your users with a seamless experience.

The `AutoPickupApi` class provides a simple, static method to process drops.

**1. Add Auto Pickup as a Dependency (build.gradle)**

It is recommended to use `modCompileOnly` so your mod doesn't require Autopickup to be installed.

```groovy
repositories {
    // ...
}

dependencies {
    // ...
    modCompileOnly "com.lukehinojosa:auto-pickup:x.x.x" // Replace with the correct group/version
}
```

**2. Use the API in your code**

First, check if the mod is loaded. Then, pass your calculated drops to the API. It will return a list of any items that could not be picked up.

```java
import com.lukehinojosa.autopickup.AutoPickupApi;
import net.fabricmc.loader.api.FabricLoader;

// ...

public void yourCustomDropMethod(PlayerEntity player, List<ItemStack> yourCalculatedDrops) {
    List<ItemStack> remainingDrops = yourCalculatedDrops;

    // Check if Auto Pickup is installed before calling its API
    if (FabricLoader.getInstance().isModLoaded("auto-pickup")) {
        // Let Auto Pickup try to handle the items
        remainingDrops = AutoPickupApi.tryPickup(player, yourCalculatedDrops);
    }

    // Drop any items that weren't picked up (e.g., inventory full)
    for (ItemStack stack : remainingDrops) {
        // Your logic to drop items in the world
        Block.dropStack(player.getWorld(), player.getBlockPos(), stack);
    }
}
```

## 📜 License

This project is licensed under the **AGPL 3.0 License**. See the `LICENSE` file for more details. Feel free to use it in your modpacks
