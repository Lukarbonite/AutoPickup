package com.lukarbonite.autopickup.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class AutoPickupMixinPlugin implements IMixinConfigPlugin {

    private boolean hasVeinMiner;
    private boolean hasTreeHarvester;

    @Override
    public void onLoad(String mixinPackage) {
        hasVeinMiner = FabricLoader.getInstance().isModLoaded("veinminer");
        hasTreeHarvester = FabricLoader.getInstance().isModLoaded("treeharvester");
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // Veinminer Logic
        if (mixinClassName.contains(".compat.veinminer.")) {
            if (!hasVeinMiner) return false;

            // 1.20.4 and below check
            if (mixinClassName.endsWith("VeinminerLegacyMixin")) {
                return isClassPresent("de.miraculixx.veinminer.Veinminer");
            }

            // 1.20.5+ check
            if (mixinClassName.endsWith("VeinminerEventMixin")) {
                return isClassPresent("de.miraculixx.veinminer.VeinminerEvent");
            }
        }

        // Tree Harvester Logic
        if (mixinClassName.contains(".compat.treeharvester.")) {
            return hasTreeHarvester;
        }

        return true;
    }

    /**
     * Helper to check if a specific class exists on the current classpath
     * without initializing it.
     */
    private boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, getClass().getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public String getRefMapperConfig() { return null; }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() { return null; }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}