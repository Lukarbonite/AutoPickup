package com.lukarbonite.neoforge.mixin;

import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class NeoforgeMixinPlugin implements IMixinConfigPlugin {

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.contains(".compat.treeharvester")) {
            try {
                return FMLLoader.getLoadingModList() != null &&
                       FMLLoader.getLoadingModList().getModFileById("treeharvester") != null;
            } catch (Exception e) {
                return false;
            }
        }
        if (mixinClassName.contains(".compat.fallingtree")) {
            try {
                return FMLLoader.getLoadingModList() != null &&
                       FMLLoader.getLoadingModList().getModFileById("fallingtree") != null;
            } catch (Exception e) {
                return false;
            }
        }
        if (mixinClassName.contains(".compat.travelersbackpack")) {
            // Just check if the mod is present in the container list.
            // Do NOT use Class.forName here.
            return FMLLoader.getLoadingModList().getModFileById("travelersbackpack") != null;
        }
        if (mixinClassName.contains(".compat.veinminer")) {
            try {
                return FMLLoader.getLoadingModList() != null &&
                       FMLLoader.getLoadingModList().getModFileById("veinminer") != null;
            } catch (Exception e) {
                return false;
            }
        }
        if (mixinClassName.contains(".compat.pandafallingtrees")) {
            try {
                return FMLLoader.getLoadingModList() != null &&
                       FMLLoader.getLoadingModList().getModFileById("fallingtrees") != null;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
