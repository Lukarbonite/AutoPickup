package com.lukarbonite.fabric.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class FabricMixinPlugin implements IMixinConfigPlugin {

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.contains(".compat.veinminer")) {
            return FabricLoader.getInstance().isModLoaded("veinminer");
        }
        // TreeHarvester 9.5 moved from com.natamus to com.serilum: apply only the copy matching the installed jar
        if (mixinClassName.contains(".compat.treeharvester.natamus.") && !classExists("com/natamus/treeharvester_common_fabric/config/ConfigHandler.class")) {
            return false;
        }
        if (mixinClassName.contains(".compat.treeharvester.serilum.") && !classExists("com/serilum/treeharvester_common_fabric/config/ConfigHandler.class")) {
            return false;
        }
        if (mixinClassName.contains(".compat.treeharvester")) {
            return FabricLoader.getInstance().isModLoaded("treeharvester");
        }
        if (mixinClassName.contains(".compat.travelersbackpack")) {
            return FabricLoader.getInstance().isModLoaded("travelersbackpack");
        }
        if (mixinClassName.contains(".compat.fallingtree")) {
            return FabricLoader.getInstance().isModLoaded("fallingtree");
        }
        if (mixinClassName.contains(".compat.pandafallingtrees")) {
            return FabricLoader.getInstance().isModLoaded("fallingtrees");
        }
        return true;
    }

    private boolean classExists(String resourcePath) {
        return getClass().getClassLoader().getResource(resourcePath) != null;
    }

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
