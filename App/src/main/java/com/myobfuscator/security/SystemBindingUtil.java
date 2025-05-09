package com.myobfuscator.security;

import java.io.*;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

public class SystemBindingUtil {
    private static final Path CONFIG_DIR  = Paths.get(System.getProperty("user.home"), ".myapp");
    private static final Path TIME_FILE   = CONFIG_DIR.resolve("install_time.txt");

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
}
