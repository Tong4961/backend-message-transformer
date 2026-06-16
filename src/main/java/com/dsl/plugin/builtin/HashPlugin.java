package com.dsl.plugin.builtin;

import com.dsl.plugin.PluginFunction;
import org.springframework.stereotype.Component;
import java.security.MessageDigest;

@Component
public class HashPlugin implements PluginFunction {
    @Override
    public String getCode() { return "HASH"; }

    @Override
    public String getName() { return "哈希摘要(SHA256/MD5)"; }

    @Override
    public Object execute(Object input, String[] params) {
        if (input == null) return null;
        String algorithm = params.length > 0 ? params[0].toUpperCase() : "SHA256";
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm.equals("MD5") ? "MD5" : "SHA-256");
            byte[] hash = md.digest(String.valueOf(input).getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(input);
        }
    }
}
