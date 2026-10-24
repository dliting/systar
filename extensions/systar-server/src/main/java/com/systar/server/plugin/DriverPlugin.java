package com.systar.server.plugin;

import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * One discovered driver plugin: a self-contained directory under the drivers
 * search path. Identity is the directory name.
 *
 * @param name          directory name (plugin identity, sort key)
 * @param dir           absolute plugin directory
 * @param classLoader   parent-first classloader over driver.jar + lib/*.jar
 * @param typeResources type-definition XMLs found under this plugin's own URLs
 * @param seedFiles     seed CSVs under seed/, sorted by file name
 * @param ownedClasses  FQCNs provided by this plugin's jars (from entry names)
 */
public record DriverPlugin(String name, Path dir, URLClassLoader classLoader,
                           List<PluginTypeResource> typeResources,
                           List<Path> seedFiles,
                           Set<String> ownedClasses) {
}
