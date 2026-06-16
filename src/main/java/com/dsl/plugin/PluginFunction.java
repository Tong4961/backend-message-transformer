package com.dsl.plugin;

public interface PluginFunction {
    String getCode();
    String getName();
    Object execute(Object input, String[] params);
}
