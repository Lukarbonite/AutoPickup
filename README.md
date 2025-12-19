# Auto Pickup for Fabric

![Fabric](https://img.shields.io/badge/modloader-fabric-blue?style=for-the-badge)![Minecraft](https://img.shields.io/badge/minecraft-1.21.11-green?style=for-the-badge)![License](https://img.shields.io/badge/license-AGPL%203.0-lightgrey?style=for-the-badge)

**Auto Pickup** is a simple, lightweight, server-side Fabric mod that automatically places items and experience directly into your inventory from broken blocks and slain mobs. No more chasing drops, no more lost items.

## ✨ Features

*   **Seamless Collection:** Items from broken blocks and mob drops are instantly added to your inventory.
*   **Automatic Experience:** Experience orbs are collected directly by a caching system, without ever spawning as entities in the world.
*   **Lag-Free:** Prevents item and experience orb entities from spawning, which can help reduce server lag.
*   **Smart Handling:** If your inventory is full, any items that cannot be picked up will be safely dropped at your feet.
*   **Granular Control:** A master switch and three independent gamerules give you fine-grained control over the mod's behavior.
*   **Broad Mod Compatibility:** Automatically works with most mods. Officially supports Liteminer, Veinminer, and Tree Harvester.

![OneBlock](https://github.com/user-attachments/assets/5e3afe38-de87-4a3a-a0fa-3de2fa9a7a8f)

## ⚙️ Configuration via GameRules

The mod is controlled by a master gamerule and three specific sub-rules. These can be changed by any server operator or in single-player worlds with cheats enabled.

### Master Switch (Global)

This gamerule acts as a master switch for the entire mod. If it is set to `false`, **all** auto pickup features will be disabled, regardless of the other rules.

*   **To enable (Default):**
    ```
    /gamerule autoPickup true
    ```

*   **To disable (Overrides all other rules):**
    ```
    /gamerule autoPickup false
    ```

### Block Drops

This controls auto pickup for items from blocks. It is **on by default**. It is only active if the master `autoPickup` rule is `true`.

*   **To enable (Default):**
    ```
    /gamerule autoPickupBlocks true
    ```

*   **To disable:**
    ```
    /gamerule autoPickupBlocks false
    ```

### Mob Loot

This controls auto pickup for items from mobs killed by a player. It is **off by default**. It is only active if the master `autoPickup` rule is `true`.

*   **To enable:**
    ```
    /gamerule autoPickupMobLoot true
    ```

*   **To disable (Default):**
    ```
    /gamerule autoPickupMobLoot false
    ```

### Experience

This controls auto pickup for all experience orbs. It is **on by default**. It is only active if the master `autoPickup` rule is `true`.

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

Auto Pickup is designed for maximum compatibility by hooking into fundamental Minecraft mechanics. This approach means it works automatically with most mods without needing specific integrations for each one.

*   **Veinminer/Liteminer Ready:** Mods with complex chain mining (e.g., **[Liteminer](https://modrinth.com/mod/liteminer)** and **[Veinminer](https://modrinth.com/datapack/veinminer)**) are supported. Auto Pickup maintains a per‑player mining session and a tight, scoped drop context during each broken block’s vanilla drop generation. This ensures drops are attributed only to the correct player without siphoning unrelated items or entities that happen to occur later in the tick.
*   **General Mod Support:** Works with most mods that use vanilla block‑breaking and loot hooks. If you find an incompatibility, please [open an issue](https://github.com/lukarbonite/autopickup/issues)!

![MultiBlock](https://github.com/user-attachments/assets/63267ae6-2c95-47ea-821b-2cc5b50218bb)

## 👩‍💻 For Developers: Using the API

Good news! The compatibility design is now much more robust, and in most cases, you **don't need to do anything at all**.

Auto Pickup achieves compatibility by hooking into fundamental vanilla methods (`ServerPlayerInteractionManager.tryBreakBlock`, `BlockState.getDroppedStacks`, `ServerWorld.spawnEntity`, etc.). As long as your mod uses these standard methods, Auto Pickup will work with it automatically.

If you have custom logic that falls outside of these standard methods (e.g., granting items from a quest reward), you can use the API.

**1. Add Auto Pickup as a Dependency (build.gradle)**

It is recommended to use `modCompileOnly` so your mod doesn't require Autopickup to be installed.

```groovy
repositories {
    // ... your repositories
}

dependencies {
    // ... your dependencies
    // Get the version from the Modrinth page or GitHub Releases
    modCompileOnly(files("libs/auto-pickup-x.x.x.jar"))
}
```

**2. Using the API**

The `AutoPickupApi` class provides simple, static methods. Always check if the mod is loaded before using the API.

### Item & Experience Pickup (for non-standard sources)

These methods are useful for cases that don't involve standard block or mob drops, such as quest rewards or custom commands.

*   `AutoPickupApi.tryPickup(PlayerEntity player, List<ItemStack> drops)`
*   `AutoPickupApi.tryPickupFromMob(PlayerEntity player, List<ItemStack> drops)`
*   `AutoPickupApi.tryPickupExperience(PlayerEntity player, int experience)`

```java
import com.lukarbonite.autopickup.AutoPickupApi;
import net.fabricmc.loader.api.FabricLoader;

// ...

public void giveQuestReward(PlayerEntity player, List<ItemStack> rewards, int xp) {
    List<ItemStack> remainingRewards = rewards;

    if (FabricLoader.getInstance().isModLoaded("auto-pickup")) {
        // Use the API to give items and XP
        remainingRewards = AutoPickupApi.tryPickup(player, rewards);
        AutoPickupApi.tryPickupExperience(player, xp);
    }

    // Drop any items that couldn't be picked up
    for (ItemStack stack : remainingRewards) {
        player.dropItem(stack, false);
    }
}
```

### Advanced Integration Notes

Auto Pickup now attributes block drops using a short‑lived, per‑player drop context that exists only while vanilla is generating the block’s drops. This prevents unrelated items (e.g., from farms, player hand‑drops, or animal‑produced items) from being siphoned into miners’ inventories.

If your mod doesn’t use vanilla block breaking and instead grants items directly (e.g., quest rewards, GUIs, commands, spells), you don’t need any special context — just call:

- `AutoPickupApi.tryPickup(PlayerEntity player, List<ItemStack> drops)`
- `AutoPickupApi.tryPickupFromMob(PlayerEntity player, List<ItemStack> drops)`
- `AutoPickupApi.tryPickupExperience(PlayerEntity player, int experience)`

These will insert what they can and return any remaining stacks for you to drop or handle.

Legacy note: `AutoPickupApi.setBlockBreaker(...)` is retained for backward compatibility but is no longer used by Auto Pickup’s interception logic. Prefer the methods above for custom item/XP grants.

### Behavior Notes / FAQ

- Items a player deliberately drops (Q: “Can another player mining nearby steal my hand‑dropped items?”) — No. Player‑dropped items are never intercepted.
- Items produced by entities (e.g., a chicken laying an egg) — Not intercepted.
- Farm/storage outputs (e.g., cactus farms, droppers, water streams) — Not intercepted unless the items are direct results of the player’s current block break, which they aren’t, so they will remain in the world.
- Veinminer/Liteminer — Supported. Each broken block participates in a fresh, tight drop context, so all directly‑caused block drops are still auto‑inserted.
- Multiple players breaking at once — Supported. Drop ownership is per‑player and scoped to their own break contexts; there is no cross‑pickup.

## 📜 License

This project is licensed under the **AGPL 3.0 License**. See the `LICENSE` file for more details. Feel free to use it in your modpacks.