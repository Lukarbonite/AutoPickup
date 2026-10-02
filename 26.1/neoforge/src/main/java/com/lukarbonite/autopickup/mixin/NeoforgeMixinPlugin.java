package com.lukarbonite.autopickup.mixin;

import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class NeoforgeMixinPlugin implements IMixinConfigPlugin {

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // TreeHarvester 9.5 moved from com.natamus to com.serilum: apply only the copy matching the installed jar
        if (mixinClassName.contains(".compat.treeharvester.natamus.") && !classExists("com/natamus/treeharvester_common_neoforge/config/ConfigHandler.class")) {
            return false;
        }
        if (mixinClassName.contains(".compat.treeharvester.serilum.") && !classExists("com/serilum/treeharvester_common_neoforge/config/ConfigHandler.class")) {
            return false;
        }
        if (mixinClassName.contains(".compat.treeharvester")) {
            try {
                return FMLLoader.getCurrent().getLoadingModList() != null &&
                        FMLLoader.getCurrent().getLoadingModList().getModFileById("treeharvester") != null;
            } catch (Exception e) {
                return false;
            }
        }
        if (mixinClassName.contains(".compat.travelersbackpack")) {
            try {
                return FMLLoader.getCurrent().getLoadingModList() != null &&
                        FMLLoader.getCurrent().getLoadingModList().getModFileById("travelersbackpack") != null;
            } catch (Exception e) {
                return false;
            }
        }
        if (mixinClassName.contains(".compat.veinminer")) {
            try {
                return FMLLoader.getCurrent().getLoadingModList() != null &&
                        FMLLoader.getCurrent().getLoadingModList().getModFileById("veinminer") != null;
            } catch (Exception e) {
                return false;
            }
        }
        if (mixinClassName.contains(".compat.fallingtree")) {
            try {
                return FMLLoader.getCurrent().getLoadingModList() != null &&
                        FMLLoader.getCurrent().getLoadingModList().getModFileById("fallingtree") != null;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    private boolean classExists(String resourcePath) {
        return getClass().getClassLoader().getResource(resourcePath) != null;
    }

    // Standard boilerplate for unused methods
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}