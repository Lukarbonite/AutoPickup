# Auto Pickup for Fabric

![Fabric](https://img.shields.io/badge/modloader-fabric-blue?style=for-the-badge)![Minecraft](https://img.shields.io/badge/minecraft-1.21.0--1-green?style=for-the-badge)![License](https://img.shields.io/badge/license-AGPL%203.0-lightgrey?style=for-the-badge)

**Auto Pickup** is a simple, lightweight, server-side Fabric mod that automatically places items and experience directly into your inventory from broken blocks and slain mobs. No more chasing drops, no more lost items.

## ✨ Features

*   **Seamless Collection:** Items from broken blocks and mob drops are instantly added to your inventory.
*   **Automatic Experience:** Experience orbs are collected directly by a caching system, without ever spawning as entities in the world.
*   **Lag-Free:** Prevents item and experience orb entities from spawning, which can help reduce server lag.
*   **Smart Handling:** If your inventory is full, any items that cannot be picked up will be safely dropped at your feet.
*   **Broad Mod Compatibility:** Automatically works with most mods.
*   **Granular Control:** Toggle specific features via commands or config file.
*   **Client Control:** Optional setting to allow players to toggle their own auto-pickup settings using Mod Menu.

![OneBlock](https://github.com/user-attachments/assets/5e3afe38-de87-4a3a-a0fa-3de2fa9a7a8f)

## ⚙️ Configuration

Auto Pickup is no longer controlled by GameRules. It now uses a configuration file (`config/autopickup.toml`) and in-game commands.

### 🖥️ Mod Menu Integration (Client)
If you have **Mod Menu** installed, you can configure the mod directly via the Mods button in the main menu or pause screen.
*   **Note:** Changing settings here will only affect the server if the server admin has enabled `allowClientControl`.

### 📜 Commands (Server Admin)
Server operators (OP Level 2+) can configure the global defaults using the `/autopickup` command.

| Command | Description                                                                                                                                     | Default |
| :--- |:------------------------------------------------------------------------------------------------------------------------------------------------| :--- |
| `/autopickup` | View current configuration status.                                                                                                              | - |
| `/autopickup master <bool>` | Global master switch. If false, disables mod entirely.                                                                                          | `true` |
| `/autopickup blocks <bool>` | Toggle picking up items from broken blocks.                                                                                                     | `true` |
| `/autopickup mobloot <bool>` | Toggle picking up loot from killed mobs.                                                                                                        | `false` |
| `/autopickup xp <bool>` | Toggle picking up experience orbs.                                                                                                              | `true` |
| `/autopickup allowClientControl <bool>` | Toggle so players can use their own config settings to toggle pickup on/off individually. If false, the server defaults are forced on everyone. | `false` |

### 📂 Configuration File
The configuration is stored in `config/autopickup.toml`. You can edit this file directly if you prefer.

```toml
# AutoPickup Configuration

auto_pickup = true
auto_pickup_blocks = true
auto_pickup_mob_loot = false
auto_pickup_xp = true
allow_client_control = false
```

## 📦 Installation

This is a standard Fabric mod.

1.  Ensure you have [Fabric Loader](https://fabricmc.net/use/) installed.
2.  Download the **Fabric API** and place it in your `mods` folder.
3.  Download the **Auto Pickup** JAR from the releases page.
4.  Place the `auto-pickup-x.x.x.jar` file into your `mods` folder.

## ✅ Compatibility

Auto Pickup is designed for maximum compatibility by hooking into fundamental Minecraft mechanics.

*   **Veinminer / Liteminer:** Fully supported. Mining a vein will instantly pick up all drops and XP.
*   **Tree Harvester:** Fully supported. When chopping a tree, logs and other drops are picked up, while saplings needed for auto-replanting are replanted.
*   **Traveler's Backpack:** Fully supported (1.21.0-10). If you have a backpack with the **Auto Pickup Upgrade**, items will be routed to your backpack filter first. If the backpack is full or the item isn't filtered, it falls back to your main inventory.
*   **General Mod Support:** Works with most mods that use vanilla block‑breaking and loot hooks.

![MultiBlock](https://github.com/user-attachments/assets/63267ae6-2c95-47ea-821b-2cc5b50218bb)

## 👩‍💻 For Developers: Using the API

The compatibility design hooks into `ServerPlayerInteractionManager.tryBreakBlock`, `BlockState.getDroppedStacks`, and `ServerWorld.spawnEntity`. As long as your mod uses these standard methods, Auto Pickup will work with it automatically.

If you have custom logic (e.g., giving items via command or quest), you can use the API to route items through the Auto Pickup logic.

**1. Add Auto Pickup as a Dependency**

```groovy
dependencies {
    modCompileOnly(files("libs/auto-pickup-x.x.x.jar"))
}
```

**2. Using the API**

```java
import com.lukarbonite.autopickup.AutoPickupApi;
import net.fabricmc.loader.api.FabricLoader;

public void giveQuestReward(PlayerEntity player, List<ItemStack> rewards, int xp) {
    if (FabricLoader.getInstance().isModLoaded("auto-pickup")) {
        // This handles inventory insertion, backpack routing, and config checks automatically.
        // Returns list of items that could NOT be picked up (full inventory).
        List<ItemStack> remaining = AutoPickupApi.tryPickup(player, rewards);
        
        // Handle Experience
        AutoPickupApi.tryPickupExperience(player, xp);
        
        // Drop remaining items
        for (ItemStack stack : remaining) {
            player.dropItem(stack, false);
        }
    } else {
        // Fallback logic
    }
}
```

## 📜 License

This project is licensed under the **AGPL 3.0 License**. See the `LICENSE` file for more details. Feel free to use it in your modpacks.