package com.lukarbonite.forge;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Dev runs only. The built jar registers its mixins through the {@code MixinConfigs} manifest entry,
 * which runClient/runServer never see (they load the mod from class directories). Those two tasks
 * load this coremod with {@code -Dfml.coreMods.load} instead. The jar's manifest does not name it, so
 * it never runs in production and the config is never registered twice.
 */
@IFMLLoadingPlugin.Name("AutoPickup dev mixins")
@IFMLLoadingPlugin.MCVersion("1.12.2")
@SuppressWarnings("deprecation") // IEarlyMixinLoader: deprecated in favor of the manifest, which dev runs lack
public class DevMixinLoader implements IFMLLoadingPlugin, zone.rong.mixinbooter.IEarlyMixinLoader {

    @Override
    public List<String> getMixinConfigs() {
        return Collections.singletonList("autopickup.mixins.json");
    }

    @Override
    public String[] getASMTransformerClass() { return new String[0]; }

    @Override
    public String getModContainerClass() { return null; }

    @Override
    public String getSetupClass() { return null; }

    @Override
    public void injectData(Map<String, Object> data) {}

    @Override
    public String getAccessTransformerClass() { return null; }
}
