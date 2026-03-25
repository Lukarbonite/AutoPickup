package com.lukarbonite.autopickup.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * NeoForge-side client preference store. Mirrors {@code ClientConfigManager}'s
 * interface so {@link AutoPickupNeoForgeClient} can build the sync mask.
 *
 * <p>Defaults match the server defaults. A full per-server profile system
 * (equivalent to the Fabric TOML profiles) can be added here later.
 */
@OnlyIn(Dist.CLIENT)
public final class NeoForgeClientConfig {

    // --- Allowances pushed down from the server ---
    public static boolean allowMaster       = true;
    public static boolean allowBlocks       = true;
    public static boolean allowBlockXp      = true;
    public static boolean allowMobLoot      = true;
    public static boolean allowMobXp        = true;
    public static boolean allowSplitMobLoot = true;
    public static boolean allowSplitMobXp   = true;

    // --- Client preferences ---
    private static boolean master       = true;
    private static boolean blocks       = true;
    private static boolean blockXp      = true;
    private static boolean mobLoot      = false;
    private static boolean mobXp        = false;
    private static boolean splitMobLoot = false;
    private static boolean splitMobXp   = false;

    private NeoForgeClientConfig() {}

    public static boolean isMaster()       { return master; }
    public static boolean isBlocks()       { return blocks; }
    public static boolean isBlockXp()      { return blockXp; }
    public static boolean isMobLoot()      { return mobLoot; }
    public static boolean isMobXp()        { return mobXp; }
    public static boolean isSplitMobLoot() { return splitMobLoot; }
    public static boolean isSplitMobXp()   { return splitMobXp; }
}
