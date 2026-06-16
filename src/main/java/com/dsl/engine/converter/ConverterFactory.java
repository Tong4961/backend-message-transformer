package com.dsl.engine.converter;

import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;
import java.util.*;

@Component
public class ConverterFactory {

    private final Map<String, FunctionConverter> converters = new HashMap<>();
    private final List<FunctionConverter> converterList;

    public ConverterFactory(List<FunctionConverter> converterList) {
        this.converterList = converterList;
    }

    @PostConstruct
    public void init() {
        for (FunctionConverter converter : converterList) {
            converters.put(converter.getName().toLowerCase(), converter);
        }
    }

    public FunctionConverter getConverter(String name) {
        return converters.get(name.toLowerCase());
    }

    public Collection<FunctionConverter> getAll() {
        return converters.values();
    }

    /**
     * Execute a converter chain like: trim -> upper -> substring(0,10)
     */
    public Object executeChain(Object input, String chain) {
        if (chain == null || chain.trim().isEmpty()) return input;

        Object current = input;
        String[] steps = chain.split("->");
        for (String step : steps) {
            step = step.trim();
            if (step.isEmpty()) continue;

            String funcName;
            String[] params = new String[0];

            int parenIdx = step.indexOf('(');
            if (parenIdx > 0) {
                funcName = step.substring(0, parenIdx).trim();
                String paramStr = step.substring(parenIdx + 1);
                if (paramStr.endsWith(")")) paramStr = paramStr.substring(0, paramStr.length() - 1);
                params = parseParams(paramStr);
            } else {
                funcName = step;
            }

            FunctionConverter converter = getConverter(funcName);
            if (converter == null) {
                throw new RuntimeException("未知的转换函数: " + funcName);
            }
            current = converter.convert(current, params);
        }
        return current;
    }

    private String[] parseParams(String paramStr) {
        if (paramStr == null || paramStr.trim().isEmpty()) return new String[0];
        // Simple param parsing - split by comma, trim quotes
        String[] raw = paramStr.split(",");
        String[] result = new String[raw.length];
        for (int i = 0; i < raw.length; i++) {
            String p = raw[i].trim();
            if ((p.startsWith("\"") && p.endsWith("\"")) || (p.startsWith("'") && p.endsWith("'"))) {
                p = p.substring(1, p.length() - 1);
            }
            result[i] = p;
        }
        return result;
    }
}
