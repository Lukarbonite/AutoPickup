package com.lukarbonite.forge.mixin;

import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class ForgeMixinPlugin implements IMixinConfigPlugin {
    private boolean hasTreeHarvester;
    private boolean hasTravelersBackpack;

    @Override
    public void onLoad(String mixinPackage) {
        MixinExtrasBootstrap.init();

        System.out.println("========================================");
        System.out.println("AUTOPICKUP FORGE MIXIN PLUGIN EXECUTING!");
        System.out.println("========================================");

        hasTreeHarvester = checkClass("com.natamus.treeharvester_common_forge.events.TreeCutEvents");
        hasTravelersBackpack = checkClass("com.tiviacz.travelersbackpack.TravelersBackpack");

        System.out.println("TreeHarvester found: " + hasTreeHarvester);
        System.out.println("TravelersBackpack found: " + hasTravelersBackpack);
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.contains(".compat.treeharvester"))
            return hasTreeHarvester;
        if (mixinClassName.contains(".compat.travelersbackpack"))
            return hasTravelersBackpack;
        return true;
    }

    private boolean checkClass(String className) {
        String path = className.replace('.', '/') + ".class";
        return this.getClass().getClassLoader().getResource(path) != null;
    }

    // Standard boilerplate for unused methods
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}