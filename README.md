# AutoPickup

![Fabric](https://img.shields.io/badge/modloaders-fabric-blue?style=for-the-badge)![Neoforge](https://img.shields.io/badge/neoforge-orange?style=for-the-badge)![Forge](https://img.shields.io/badge/forge-purple?style=for-the-badge)
![Minecraft](https://img.shields.io/badge/minecraft-1.20%20--%2026.1.2-green?style=for-the-badge)
![License](https://img.shields.io/badge/license-AGPL%203.0-lightgrey?style=for-the-badge)

**AutoPickup** is a highly configurable server-side mod that automates the collection of items and experience orbs. It features session tracking, a multi-tier permission system, and extensive mod compatibility to ensure drops are attributed to the correct players, even when using high-speed mining mods or engaging in group combat.

---

## ✨ Features

### Core Functionality
- **🎒 Seamless Collection:** Items from broken blocks and mob kills are instantly added to your inventory
- **⭐ Smart Experience Handling:** Experience is cached and applied after a short delay, ensuring tools take durability damage *before* Mending activates
- **🚀 Lag-Free:** Prevents item and experience orb entities from spawning, reducing server load
- **📦 Overflow Protection:** If your inventory is full, items safely drop at your feet
- **🔧 Granular Control:** Toggle individual features independently via commands or config GUI

### Advanced Features
- **👥 Mob Loot Splitting:** Share mob drops and XP with nearby players who participated in the kill
- **🎮 Per-Player Permissions:** Server admins can override settings for specific players
- **💾 Client Profiles:** Automatic per-server/world configuration profiles for seamless server switching
- **🔌 Extensive Mod Compatibility:** Works automatically with VeinMiner, TreeHarvester, Traveler's Backpack, FallingTree, Panda's Falling Trees and more

![One-Block Mining](https://github.com/Lukarbonite/AutoPickup/raw/1.21.0-1/assets/ONE_BLOCK.gif)

---

## ⚙️ Configuration

AutoPickup uses a flexible three-tier permission system:

1. **Server Defaults** - Global fallback settings
2. **Admin Overrides** - Per-player forced settings (highest priority)
3. **Client Preferences** - Individual player choices (only if server allows)

### 🖥️ Configuration Menu (Client)

You can configure your personal preferences via the menu. Use **Mod Menu** for a easy access on Fabric.
Otherwise, use /autopickup gui

**Settings available:**
- Master Toggle
- Block Item Pickup
- Block Experience Pickup
- Mob Loot Pickup
- Mob Experience Pickup
- Split Mob Loot (share drops with nearby attackers)
- Split Mob XP (share experience with nearby attackers)

> **Note:** Your client settings only apply if the server admin has enabled the corresponding "Allow" setting for that feature.

![YACL Menu](https://github.com/Lukarbonite/AutoPickup/raw/1.21.0-1/assets/YACL_MENU.gif)

---

### 📜 Commands (Server Admin)

Server operators (OP Level 2+) can manage global defaults and player overrides using `/autopickup`.

#### Menu Access

| Command           | Description                              |
|:------------------|:-----------------------------------------|
| `/autopickup gui` | Access to configuration menu via command |

#### Global Configuration

| Command | Description                                    | Default |
|:--------|:-----------------------------------------------|:--------|
| `/autopickup` | Display current configuration status           | - |
| `/autopickup master <true\|false>` | Master toggle - disables mod entirely if false | `true` |
| `/autopickup blocks <true\|false>` | Autopickup items from broken blocks            | `true` |
| `/autopickup blockxp <true\|false>` | Autopickup experience from broken blocks       | `true` |
| `/autopickup mobloot <true\|false>` | Autopickup loot from killed mobs               | `false` |
| `/autopickup mobxp <true\|false>` | Autopickup experience from killed mobs         | `false` |
| `/autopickup splitmobloot <true\|false>` | Share mob loot among nearby attackers          | `false` |
| `/autopickup splitmobxp <true\|false>` | Share mob XP among nearby attackers            | `false` |

#### Client Control Allowances

These commands determine whether players can override server defaults with their client settings.

| Command | Description | Default |
|:--------|:------------|:--------|
| `/autopickup allow master <true\|false>` | Allow clients to toggle master switch | `false` |
| `/autopickup allow blocks <true\|false>` | Allow clients to toggle block item pickup | `false` |
| `/autopickup allow blockxp <true\|false>` | Allow clients to toggle block XP pickup | `false` |
| `/autopickup allow mobloot <true\|false>` | Allow clients to toggle mob loot pickup | `false` |
| `/autopickup allow mobxp <true\|false>` | Allow clients to toggle mob XP pickup | `false` |
| `/autopickup allow splitmobloot <true\|false>` | Allow clients to toggle mob loot splitting | `false` |
| `/autopickup allow splitmobxp <true\|false>` | Allow clients to toggle mob XP splitting | `false` |

#### Per-Player Overrides

Force specific settings for individual players (overrides both server defaults and client preferences).

| Command | Description |
|:--------|:------------|
| `/autopickup override <player> master <true\|false\|default>` | Override player's master toggle |
| `/autopickup override <player> blocks <true\|false\|default>` | Override player's block pickup |
| `/autopickup override <player> blockxp <true\|false\|default>` | Override player's block XP |
| `/autopickup override <player> mobloot <true\|false\|default>` | Override player's mob loot |
| `/autopickup override <player> mobxp <true\|false\|default>` | Override player's mob XP |
| `/autopickup override <player> splitmobloot <true\|false\|default>` | Override player's loot splitting |
| `/autopickup override <player> splitmobxp <true\|false\|default>` | Override player's XP splitting |
| `/autopickup override <player> clear` | Remove all overrides for a player |

> Use `default` to remove a specific override while keeping others.

---

### 📂 Configuration Files

**Server Config:** `config/AutoPickup/global_config.json`

```json
{
   "autoPickup": true,
   "autoPickupBlocks": true,
   "autoPickupBlockXp": true,
   "autoPickupMobLoot": false,
   "autoPickupMobXp": false,
   "autoPickupSplitMobLoot": false,
   "autoPickupSplitMobXp": false,
   "allowMaster": true,
   "allowBlocks": true,
   "allowBlockXp": true,
   "allowMobLoot": true,
   "allowMobXp": true,
   "allowSplitMobLoot": true,
   "allowSplitMobXp": true
}
```

**Client Config:** `config/AutoPickup/client_default.toml` (or per-server profiles in `presets/`)

```toml
# AutoPickup Client Configuration
master = true
blocks = true
blockXp = true
mobLoot = false
mobXp = false
splitMobLoot = false
splitMobXp = false
```

**Player Overrides:** `config/AutoPickup/player_overrides.json`

```json
{
   "player-uuid-here": {
      "overrideMaster": true,
      "overrideBlocks": null,
      ...
   }
}
```

### Optional But Recommended Client Dependencies

- **Mod Menu** - Access config screen in-game
- **YetAnotherConfigLib (YACL)** - Required for YACL config GUI (Vanilla GUI fallback)

---

## ✅ Compatibility

AutoPickup hooks into core Minecraft mechanics and works seamlessly with most mods out of the box.

### 🔧 Explicitly Supported Mods

| Mod                        |   Status   | Notes                                                                               |
|:---------------------------|:----------:|:------------------------------------------------------------------------------------|
| **VeinMiner**              |    Full    | All vein blocks picked up instantly with XP                                         |
| **TreeHarvester**          |    Full    | Logs/leaves collected; saplings auto-replanted if enabled                           |
| **Traveler's Backpack**    |    Full    | Items route to backpack filter first (If the Auto Pickup upgrade is there it works) |
| **FallingTree**            |    Full    | Tree items handle the different break modes                                         |
| **Panda's Falling Trees**  |    Full    | Tree items are handled by AutoPickup after fall animation                           |
| **RightClickHarvest**      |    Full    | Right-click crop harvests (including tall sugarcane) picked up automatically        |
| **General Block Breakers** | Compatible | Any mod using vanilla break hooks                                                   |

### 📋 Technical Details

AutoPickup intercepts drops at these injection points:

- `Block.dropStacks()` - Primary block drop handling
- `ServerWorld.spawnEntity()` - Item entity spawning and falling block entity tracking (FallingTree)
- `LivingEntity.dropLoot()` - Mob loot generation
- `Block.dropExperience()` - Block XP orbs
- `LivingEntity.dropExperience()` - Mob XP orbs
- `ServerPlayerGameMode.useItemOn()` - Right-click harvesting (sweet berries, jukebox disc ejection, crops via the RightClickHarvest mod, etc.)
- `ItemFrame.hurt()` - Items removed from item frames by left-clicking

This broad compatibility means **most mods work automatically** without explicit support.

![MultiBlock Mining](https://github.com/Lukarbonite/AutoPickup/raw/1.21.0-1/assets/MULTI_BLOCK.gif)

---

## 🎮 Mob Loot Splitting

When **Split Mob Loot** or **Split Mob XP** is enabled, rewards from mob kills are shared among participants:

### How It Works

1. **Damage Tracking:** The mod tracks the last 10 unique players who damaged each mob
2. **Eligibility Check:** On mob death, only players who:
   - Are online and not spectating
   - Have **Master** enabled
   - Have the specific **Mob Pickup** feature enabled
   - Have the specific **Split** feature enabled
   - Are in the same world as the killer
3. **Distribution:**
   - Items are split evenly; the killer receives any remainder
   - Experience is split evenly; the killer receives any remainder
   - Items that don't fit in inventories are dropped at the mob's location

### Example

Player A (killer), B, and C all damaged a zombie. Settings:

- **Player A:** Master ✅, Mob Loot ✅, Split Mob Loot ✅
- **Player B:** Master ✅, Mob Loot ✅, Split Mob Loot ✅
- **Player C:** Master ✅, Mob Loot ✅, Split Mob Loot ❌

**Result:** Only A and B receive split loot. Player C gets nothing because they disabled splitting.

---

## 👩‍💻 Developer API

### Adding as a Dependency

**Gradle:**

```groovy
dependencies {
   compileOnly(files("libs/autopickup-platform-x.x.x.jar"))
}
```

### Using the API

```java
import com.lukarbonite.autopickup.AutoPickupApi;

public class QuestRewards {

   public void giveReward(Player player, List<ItemStack> items, int xp) {
      if (!isModLoaded("autopickup")) {
         // Fallback: manual drops
         items.forEach(stack -> player.dropItem(stack, false));
         player.addExperience(xp);
         return;
      }

      // AutoPickup handles:
      // - Inventory insertion
      // - Traveler's Backpack routing
      // - Config checks (Master, Blocks, etc.)
      List<ItemStack> remaining = AutoPickupApi.tryPickup(player, items);

      // Drop items that didn't fit
      remaining.forEach(stack -> player.dropItem(stack, false));

      // AutoPickup handles:
      // - Mending calculations
      // - Config checks
      AutoPickupApi.tryPickupBlockExperience(player, xp);
   }

   // For mob-related rewards
   public void giveMobReward(Player player, List<ItemStack> loot, int xp) {
      List<ItemStack> remaining = AutoPickupApi.tryPickupFromMob(player, loot);
      remaining.forEach(stack -> player.dropItem(stack, false));

      AutoPickupApi.tryPickupMobExperience(player, xp);
   }
}
```

### API Methods

| Method | Description                                                  |
|:-------|:-------------------------------------------------------------|
| `tryPickup(PlayerEntity, List<ItemStack>)` | Pickup items from blocks (respects Master + Blocks settings) |
| `tryPickupFromMob(PlayerEntity, List<ItemStack>)` | Pickup items from mobs (respects Master + MobLoot settings)  |
| `tryPickupBlockExperience(PlayerEntity, int)` | Give block XP (respects Master + BlockXP settings)           |
| `tryPickupMobExperience(PlayerEntity, int)` | Give mob XP (respects Master + MobXP settings)               |
| `isMasterEnabled(PlayerEntity)` | Check if autopickup is enabled for player                    |
| `isBlocksEnabled(PlayerEntity)` | Check block item pickup setting                              |
| `isBlockXpEnabled(PlayerEntity)` | Check block XP pickup setting                                |
| `isMobLootEnabled(PlayerEntity)` | Check mob loot pickup setting                                |
| `isMobXpEnabled(PlayerEntity)` | Check mob XP pickup setting                                  |

> **Note:** All `try*` methods return lists of items that could not be picked up (full inventory).

---

## 📜 License

This project is licensed under the **GNU Affero General Public License v3.0** (AGPL-3.0).

You are free to:
- ✅ Use in modpacks
- ✅ Modify for personal use
- ✅ Distribute modified versions (must also be AGPL-3.0)

See the [LICENSE](LICENSE) file for full details.