Of course! This is the perfect final step. Removing the specific Veinminer integration simplifies your mod significantly and makes the "broad compatibility" a much stronger and more accurate selling point.

I will update the README to reflect this final, cleaner architecture. The key changes are:

1.  **Rewriting the Compatibility section:** I will remove the specific mention of a "deep integration" for Veinminer and group it with Liteminer as an example of a mod that is now *automatically* compatible thanks to the new universal approach.
2.  **Cleaning up the Developer section:** I will remove the old `try...finally` example, as it is now the incorrect pattern, and ensure the new "set-it-and-forget-it" context pattern is clearly explained.

Here is the final, updated `README.md`.

---

# Auto Pickup for Fabric

![Fabric](https://img.shields.io/badge/modloader-fabric-blue?style=for-the-badge)![Minecraft](https://img.shields.io/badge/minecraft-1.21.9-green?style=for-the-badge)![License](https://img.shields.io/badge/license-AGPL%203.0-lightgrey?style=for-the-badge)

**Auto Pickup** is a simple, lightweight, server-side Fabric mod that automatically places items and experience directly into your inventory from broken blocks and slain mobs. No more chasing drops, no more lost items.

## ✨ Features

*   **Seamless Collection:** Items from broken blocks and mob drops are instantly added to your inventory.
*   **Automatic Experience:** Experience orbs are collected directly by a caching system, without ever spawning as entities in the world.
*   **Lag-Free:** Prevents item and experience orb entities from spawning, which can help reduce server lag.
*   **Smart Handling:** If your inventory is full, any items that cannot be picked up will be safely dropped at your feet.
*   **Granular Control:** A master switch and three independent gamerules give you fine-grained control over the mod's behavior.
*   **Broad Mod Compatibility:** Automatically works with most mods, including complex ones like Liteminer and Veinminer, with no extra configuration.

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

*   **Broad Compatibility:** Mods with complex block-breaking logic, such as **[Liteminer](https://modrinth.com/mod/liteminer)** and **[Veinminer](https://modrinth.com/datapack/veinminer)**, are now automatically supported. Because Auto Pickup detects the player at the very start of the block-breaking process, all items and experience from the entire operation are correctly collected.
*   **General Mod Support:** It should work seamlessly with most other mods that use standard block-breaking and loot-dropping mechanics. If you find an incompatibility, please [open an issue](https://github.com/lukarbonite/autopickup/issues)!

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

### Block Breaker Context (Advanced)

This feature is for mods with **highly custom block-breaking logic** that does not trigger vanilla's `ServerPlayerInteractionManager.tryBreakBlock` (e.g., a magic spell that replaces blocks with air and spawns items manually).

In these rare cases, you must provide the player context so Auto Pickup knows who is responsible for the drops.

*   `AutoPickupApi.setBlockBreaker(PlayerEntity player)`: Sets the current player context. The context is automatically cleared at the end of the server tick.

**The correct pattern is to set the context once at the start of your operation. DO NOT clear it yourself.**

```java
import com.lukarbonite.autopickup.AutoPickupApi;
import net.fabricmc.loader.api.FabricLoader;

// ...

public void yourMagicSpellThatBreaksBlocks(ServerWorld world, BlockPos center, PlayerEntity caster) {
    // If Auto Pickup is loaded, set the context at the beginning of your operation.
    if (FabricLoader.getInstance().isModLoaded("auto-pickup")) {
        AutoPickupApi.setBlockBreaker(caster);
    }

    // ... your custom logic to break multiple blocks and spawn ItemEntities ...
    // For example:
    for (BlockPos pos : getBlocksInSpellRadius(center)) {
        // Auto Pickup's ServerWorldMixin will now see the context and
        // automatically pick up this item for the 'caster'.
        world.spawnEntity(new ItemEntity(world, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(Items.DIAMOND)));
        world.setBlockState(pos, Blocks.AIR.getDefaultState());
    }

    // You are done. The context will be cleared automatically at the end of the tick.
}
```

## 📜 License

This project is licensed under the **AGPL 3.0 License**. See the `LICENSE` file for more details. Feel free to use it in your modpacks.