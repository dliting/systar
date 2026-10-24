package com.systar.server.plugin;

import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Single entry point for instantiating driver implementation classes.
 * <p>
 * Resolution order: the plugin that owns the class (per the discovery-time
 * class index, first plugin in directory order wins) loads it through its own
 * parent-first classloader; otherwise the class resolves from the server
 * classpath. Replaces the four bare {@code Class.forName} call sites so plugin
 * classes are visible to asset instantiation and mode derivation.
 */
@Component
public class DriverClassRegistry {

    private final DriverPluginLoader pluginLoader;
    private final ClassLoader parent;

    public DriverClassRegistry(DriverPluginLoader pluginLoader) {
        this.pluginLoader = pluginLoader;
        this.parent = getClass().getClassLoader();
    }

    /**
     * @throws ClassNotFoundException when neither a plugin nor the server
     *         classpath provides the class; callers wrap this into an
     *         {@code AssetException} carrying the asset row context.
     */
    public Class<?> loadClass(String fqcn) throws ClassNotFoundException {
        Optional<DriverPlugin> owner = pluginLoader.pluginOwning(fqcn);
        if (owner.isPresent()) {
            return owner.get().classLoader().loadClass(fqcn);
        }
        return Class.forName(fqcn, true, parent);
    }
}
