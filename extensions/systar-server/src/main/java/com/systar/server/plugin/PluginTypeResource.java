package com.systar.server.plugin;

import org.springframework.core.io.Resource;

/** A type-definition XML owned by one plugin, with its attribution location. */
public record PluginTypeResource(String pluginName, Resource resource, String location) {
}
