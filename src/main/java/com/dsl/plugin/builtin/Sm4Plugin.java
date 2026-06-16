package com.dsl.plugin.builtin;

import com.dsl.plugin.PluginFunction;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.Base64;

@Component
public class Sm4Plugin implements PluginFunction {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Override
    public String getCode() { return "SM4"; }

    @Override
    public String getName() { return "SM4加密"; }

    @Override
    public Object execute(Object input, String[] params) {
        if (input == null) return null;
        try {
            String key = params.length > 0 ? params[0] : "1234567890123456";
            // SM4 key must be 16 bytes
            if (key.length() < 16) {
                key = String.format("%-16s", key).replace(' ', '0');
            } else if (key.length() > 16) {
                key = key.substring(0, 16);
            }

            SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "SM4");
            Cipher cipher = Cipher.getInstance("SM4/ECB/PKCS5Padding", "BC");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            byte[] encrypted = cipher.doFinal(String.valueOf(input).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            return "SM4_ERROR: " + e.getMessage();
        }
    }
}
