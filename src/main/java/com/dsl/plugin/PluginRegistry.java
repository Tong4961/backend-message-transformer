package com.dsl.plugin;

import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;
import java.util.*;

@Component
public class PluginRegistry {

    private final Map<String, PluginFunction> plugins = new HashMap<>();
    private final List<PluginFunction> pluginList;

    public PluginRegistry(List<PluginFunction> pluginList) {
        this.pluginList = pluginList;
    }

    @PostConstruct
    public void init() {
        for (PluginFunction plugin : pluginList) {
            plugins.put(plugin.getCode().toLowerCase(), plugin);
        }
    }

    public PluginFunction getPlugin(String code) {
        return plugins.get(code.toLowerCase());
    }

    public Collection<PluginFunction> getAll() {
        return plugins.values();
    }
}
