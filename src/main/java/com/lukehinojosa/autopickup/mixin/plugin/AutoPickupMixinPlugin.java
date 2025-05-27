package com.lukehinojosa.autopickup.mixin.plugin; // Or your chosen package

import com.lukehinojosa.autopickup.AutoPickup; // For logging
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class AutoPickupMixinPlugin implements IMixinConfigPlugin {
    private static final String VEINMINER_EVENT_CLASS_NAME = "de.miraculixx.veinminer.VeinMinerEvent";
    private boolean isVeinMinerLoaded = false;

    @Override
    public void onLoad(String mixinPackage) {
        // Check if VeinMiner is loaded
        try {
            Class.forName(VEINMINER_EVENT_CLASS_NAME, false, this.getClass().getClassLoader());
            isVeinMinerLoaded = true;
            AutoPickup.LOGGER.info("VeinMiner detected. Enabling AutoPickup compatibility mixin.");
        } catch (ClassNotFoundException e) {
            isVeinMinerLoaded = false;
            AutoPickup.LOGGER.info("VeinMiner not detected. AutoPickup compatibility mixin will not be applied.");
        } catch (Exception e) { // Catch any other potential errors during class loading check
            isVeinMinerLoaded = false;
            AutoPickup.LOGGER.error("An unexpected error occurred while checking for VeinMiner. Compatibility mixin will not be applied.", e);
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null; // Not needed for this
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // Check if the mixin being considered is our VeinMiner compatibility mixin
        if (mixinClassName.endsWith("VeinMinerEventMixin")) { // Check against the simple name or full name
            // Only apply if VeinMiner was detected
            return isVeinMinerLoaded;
        }
        // Apply all other mixins (like your BlockMixin) normally
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        // Not needed for this
    }

    @Override
    public List<String> getMixins() {
        return null; // We list mixins in the JSON file
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // Not needed for this
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // Not needed for this
    }
}