package com.myobfuscator.security;

import java.io.*;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

public class SystemBindingUtil {
    private static final Path CONFIG_DIR  = Paths.get(System.getProperty("user.home"), ".myapp");
    private static final Path TIME_FILE   = CONFIG_DIR.resolve("install_time.txt");
    private static final Path PASS_FILE = CONFIG_DIR.resolve("password.dat");

    /** Собираем UUID корневого тома как «серийник диска» */
    private static String getDiskSerial() {
        try {
            Process p = new ProcessBuilder("diskutil", "info", "/").start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("Volume UUID:")) {
                        return line.substring("Volume UUID:".length()).trim();
                    }
                }
            }
            p.waitFor();
        } catch (Exception e) { /* игнорируем, вернём UNKNOWN */ }
        return "UNKNOWN_DISK";
    }

    /** Собираем все MAC-адреса сетевых интерфейсов */
    private static List<String> getMacs() {
        List<String> macs = new ArrayList<>();
        try {
            for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                byte[] hw = nif.getHardwareAddress();
                if (hw != null && hw.length == 6) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < hw.length; i++) {
                        sb.append(String.format("%02X", hw[i]));
                        if (i < hw.length - 1) sb.append(":");
                    }
                    macs.add(sb.toString());
                }
            }
        } catch (Exception ignored) {}
        Collections.sort(macs);
        return macs;
    }

    /** Чтение или запись времени «установки» */
    private static Instant getInstallTime() {
        try {
            if (Files.notExists(TIME_FILE)) {
                Files.createDirectories(CONFIG_DIR);
                Instant now = Instant.now();
                Files.writeString(TIME_FILE, now.toString(), StandardOpenOption.CREATE_NEW);
                return now;
            } else {
                return Instant.parse(Files.readString(TIME_FILE).trim());
            }
        } catch (Exception e) {
            return Instant.EPOCH;
        }
    }

    /** Собираем строку и считаем SHA-256 */
    public static String computeSystemHash() {
        return rawHash();
    }

    private static String getInstallPath() {
        try {
            Path cfg = CONFIG_DIR.resolve("install_path.txt");
            if (Files.notExists(cfg)) {
                // первый запуск — запоминаем ту папку, откуда запустили JAR
                String cwd = new File(".").getCanonicalPath();
                Files.writeString(cfg, cwd, StandardOpenOption.CREATE_NEW);
                return cwd;
            } else {
                // дальнейшие запуски — просто читаем
                return Files.readString(cfg).trim();
            }
        } catch (Exception e) {
            return "";
        }
    }

    public static void checkBinding(String expectedHash, String expectedPath) {
        // 1) проверка папки
        String cwd;
        try {
            cwd = new File(".").getCanonicalPath();
        } catch (IOException e) {
            System.exit(1);
            return;
        }
        if (! expectedPath.equals(cwd)) {
            System.err.println("Wrong launch folder");
            System.exit(1);
        }

        // 2) вычисляем и сравниваем сырой хэш
        String actual = rawHash();
        if (! expectedHash.equals(actual)) {
            System.err.println("Unauthorized machine");
            System.exit(1);
        }
    }


    public static String rawHash() {
        StringBuilder sb = new StringBuilder()
                .append("disk:").append(getDiskSerial()).append(";")
                .append("mac:").append(String.join(",", getMacs())).append(";")
                .append("install:").append(getInstallTime());
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : h) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** Вызывается при первом запуске: */
    public static void setupPassword() {
        try {
            if (Files.notExists(PASS_FILE)) {
                Files.createDirectories(CONFIG_DIR);
                // генерируем случайный пароль
                String pwd = Long.toHexString(new SecureRandom().nextLong());
                // или попросить пользователя ввести: Console.readPassword()
                String salted = PasswordUtil.generateSaltedHash(pwd);
                Files.writeString(PASS_FILE, salted, StandardOpenOption.CREATE_NEW);
                System.out.println("Your password is: " + pwd);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Проверяет пароль при запуске: */
    public static void checkPassword() {
        try {
            String stored = Files.readString(PASS_FILE).trim();
            Console console = System.console();
            if (console == null) throw new IllegalStateException("No console");
            char[] input = console.readPassword("Enter application password: ");
            if (!PasswordUtil.verify(new String(input), stored)) {
                System.err.println("Wrong password. Exiting.");
                System.exit(1);
            }
        } catch (IOException e) {
            System.err.println("Password file missing. Exiting.");
            System.exit(1);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
