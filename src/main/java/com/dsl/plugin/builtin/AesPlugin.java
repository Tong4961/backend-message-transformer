package com.dsl.plugin.builtin;

import com.dsl.plugin.PluginFunction;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Component
public class AesPlugin implements PluginFunction {
    @Override
    public String getCode() { return "AES"; }

    @Override
    public String getName() { return "AES加密"; }

    @Override
    public Object execute(Object input, String[] params) {
        if (input == null) return null;
        try {
            String key = params.length > 0 ? params[0] : "1234567890123456";
            SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(), "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            byte[] encrypted = cipher.doFinal(String.valueOf(input).getBytes());
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            return String.valueOf(input);
        }
    }
}
