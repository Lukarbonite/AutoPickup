package com.lukarbonite.forge;

import com.lukarbonite.autopickup.AutoPickupApi;
import com.lukarbonite.autopickup.AutoPickupCommand;
import com.lukarbonite.autopickup.AutoPickupCommon;
import com.lukarbonite.autopickup.AutoPickupConfig;
import com.lukarbonite.autopickup.AutoPickupSessions;
import com.lukarbonite.autopickup.ExperienceCache;
import com.lukarbonite.autopickup.LootSplittingLogic;
import com.lukarbonite.autopickup.PlayerConfigs;
import com.lukarbonite.autopickup.WorldConfigs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Forge 1.12.2 entry point. Block and mob drops come from Forge's own events here (they hand over
 * mutable drop lists); the rest is caught by the mixins, as on newer versions.
 */
@Mod(modid = AutoPickupCommon.MOD_ID, name = "AutoPickup", useMetadata = true,
        acceptedMinecraftVersions = "[1.12.2]",
        dependencies = "required-after:mixinbooter@[11.0,)",
        // Players without the mod can still join a server that has it, and the other way round.
        acceptableRemoteVersions = "*",
        guiFactory = "com.lukarbonite.forge.client.AutoPickupGuiFactory")
public class AutoPickupForge {

    @Mod.EventHandler
    public void onPreInit(FMLPreInitializationEvent event) {
        AutoPickupCommon.init();
        MinecraftForge.EVENT_BUS.register(this);
        if (event.getSide().isClient()) {
            com.lukarbonite.forge.client.AutoPickupForgeClient.init();
        }
    }

    @Mod.EventHandler
    public void onServerStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new AutoPickupCommand());
        event.registerServerCommand(new AutoPickupCommand.SyncCommand());
    }

    @Mod.EventHandler
    public void onServerStarted(FMLServerStartedEvent event) {
        WorldConfigs.load(FMLCommonHandler.instance().getMinecraftServerInstance());
    }

    @Mod.EventHandler
    public void onServerStopping(FMLServerStoppingEvent event) {
        AutoPickupConfig.getInstance().save();
        PlayerConfigs.save();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ExperienceCache.onServerTick(FMLCommonHandler.instance().getMinecraftServerInstance());
        AutoPickupSessions.onServerTickEnd();
        AutoPickupApi.clearBlockBreaker();
    }

    /**
     * A player's block drops, from Block#harvestBlock (so also blocks a mod harvests on the player's
     * behalf). Lowest priority so drop-changing mods have run. Same role as BlockMixin on newer versions.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onHarvestDrops(BlockEvent.HarvestDropsEvent event) {
        EntityPlayer player = event.getHarvester();
        World world = event.getWorld();
        if (player == null || world.isRemote || event.getDrops().isEmpty()) return;

        AutoPickupSessions.begin(player);
        AutoPickupSessions.addBreak(player, event.getPos());
        if (!AutoPickupApi.isMasterEnabled(player) || !AutoPickupApi.isBlocksEnabled(player)) return;

        // Vanilla rolls the drop chance per stack after this event, so roll it here for what we take.
        List<ItemStack> drops = new ArrayList<>();
        float chance = event.getDropChance();
        for (ItemStack stack : event.getDrops()) {
            if (chance >= 1.0F || world.rand.nextFloat() <= chance) drops.add(stack);
        }

        List<ItemStack> remaining = AutoPickupApi.tryPickup(player, drops);
        event.getDrops().clear();
        event.getDrops().addAll(remaining);
        event.setDropChance(1.0F);
    }

    /**
     * Mob loot, captured by Forge before it spawns. Players' own death drops (PlayerDropsEvent is a
     * subclass) are left alone. Same role as MobLootMixin on newer versions.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingDrops(LivingDropsEvent event) {
        EntityLivingBase mob = event.getEntityLiving();
        if (mob.world.isRemote || mob instanceof EntityPlayer || event.getDrops().isEmpty()) return;
        if (!(event.getSource().getTrueSource() instanceof EntityPlayer)) return;

        EntityPlayer player = (EntityPlayer) event.getSource().getTrueSource();
        if (player.isSpectator() || !AutoPickupApi.isMasterEnabled(player) || !AutoPickupApi.isMobLootEnabled(player)) return;

        List<ItemStack> loot = new ArrayList<>();
        for (EntityItem item : event.getDrops()) loot.add(item.getItem());

        List<ItemStack> remaining = LootSplittingLogic.distributeLoot(player, mob, loot);

        // Drop whatever wasn't picked up, reusing the captured entities (position, motion, delay).
        Iterator<EntityItem> it = event.getDrops().iterator();
        Iterator<ItemStack> rest = remaining.iterator();
        while (it.hasNext()) {
            EntityItem item = it.next();
            if (rest.hasNext()) item.setItem(rest.next());
            else it.remove();
        }
        while (rest.hasNext()) {
            event.getDrops().add(new EntityItem(mob.world, mob.posX, mob.posY, mob.posZ, rest.next()));
        }
    }

    /** Mob XP, before it becomes orbs. Same role as MobLootMixin's XP half on newer versions. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingExperienceDrop(LivingExperienceDropEvent event) {
        EntityPlayer player = event.getAttackingPlayer();
        if (player == null || player.isSpectator() || event.getEntityLiving() instanceof EntityPlayer) return;

        // If disabled, fall back to vanilla orbs.
        if (AutoPickupApi.isMasterEnabled(player) && AutoPickupApi.isMobXpEnabled(player)) {
            LootSplittingLogic.distributeXp(player, event.getEntityLiving(), event.getDroppedExperience());
            event.setDroppedExperience(0);
        }
    }
}
