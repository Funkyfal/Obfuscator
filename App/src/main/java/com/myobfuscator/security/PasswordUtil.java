package com.myobfuscator.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

public class PasswordUtil {
    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 32;
    private static final int ITERATIONS = 100_000;

    private static final SecureRandom rnd = new SecureRandom();

    /** Генерирует соль + хэш для заданного пароля */
    public static String generateSaltedHash(String password) throws Exception {
        byte[] salt = new byte[SALT_BYTES];
        rnd.nextBytes(salt);
        byte[] hash = pbkdf2(password.toCharArray(), salt);
        // форматируем как Base64(salt) + ":" + Base64(hash)
        return Base64.getEncoder().encodeToString(salt)
                + ":" + Base64.getEncoder().encodeToString(hash);
    }

    /** Проверяет пароль против сохранённого «salt:hash» */
    public static boolean verify(String password, String stored) throws Exception {
        String[] parts = stored.split(":");
        byte[] salt = Base64.getDecoder().decode(parts[0]);
        byte[] expected = Base64.getDecoder().decode(parts[1]);
        byte[] actual = pbkdf2(password.toCharArray(), salt);
        if (actual.length != expected.length) return false;
        for (int i = 0; i < actual.length; i++)
            if (actual[i] != expected[i]) return false;
        return true;
    }

    private static byte[] pbkdf2(char[] pass, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(pass, salt, ITERATIONS, HASH_BYTES * 8);
        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        return skf.generateSecret(spec).getEncoded();
    }
}
