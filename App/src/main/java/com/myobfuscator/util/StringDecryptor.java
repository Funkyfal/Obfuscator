package com.myobfuscator.util;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class StringDecryptor {
    // Ключ будет подставлен через шаблон
    private static final String BASE64_KEY = "{{BASE64_KEY}}";
    private static final byte[] KEY_BYTES = Base64.getDecoder().decode("{{BASE64_KEY}}");
    private static final SecretKeySpec KEY = new SecretKeySpec(KEY_BYTES, "AES");

    public static String decryptBase64(String base64) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        byte[] data = Base64.getDecoder().decode(base64);
        // здесь ваш AES-дешифратор, как прежде:
        Cipher c = Cipher.getInstance("AES/ECB/PKCS5Padding");
        c.init(Cipher.DECRYPT_MODE, KEY);
        return new String(c.doFinal(data), StandardCharsets.UTF_8);
    }
}
