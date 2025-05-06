package com.myobfuscator.core;

import com.myobfuscator.transformer.RenamerTransformer;
import com.myobfuscator.transformer.StringEncryptorTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.InputStream;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;

//TODO: УБРАТЬ КОММЕНТАРИИ ПРИ ДИЗАССЕМБЛИРОВАНИИ
public class ObfuscatorCore {
    private final ObfuscationContext ctx;

    public ObfuscatorCore(ObfuscationContext ctx) {
        this.ctx = ctx;
    }

    public void run() throws Exception {
        // 1) Инициализация трансформеров
        for (ITransformer t : ctx.getTransformers()) {
            t.init(ctx);
        }

        // --- новая вставка: переставляем StringEncryptor перед Renamer ---
        ctx.getTransformers().sort((a, b) -> {
            if (a instanceof StringEncryptorTransformer && b instanceof RenamerTransformer) return -1;
            if (a instanceof RenamerTransformer     && b instanceof StringEncryptorTransformer) return 1;
            return 0;
        });

        // 2) Читаем входной JAR и его манифест
        Manifest manifest;
        try (JarFile jarIn = new JarFile(ctx.getInputJar().toFile())) {
            manifest = jarIn.getManifest();
        }

        // 3) Собираем все ClassNode
        List<ClassNode> allClasses = new ArrayList<>();

        // 3.1) Добавляем StringDecryptor, если нужен
        for (ITransformer t : ctx.getTransformers()) {
            if (t instanceof StringEncryptorTransformer s) {
                ClassNode decryptorNode = s.generateDecryptorNode();
                allClasses.add(decryptorNode);
                System.out.println("[Core] Injected raw StringDecryptor");
            }
        }

        // 3.2) Добавляем остальные классы из входного JAR
        try (JarFile jarIn = new JarFile(ctx.getInputJar().toFile())) {
            Enumeration<JarEntry> entries = jarIn.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                InputStream is = jarIn.getInputStream(entry);
                ClassReader cr = new ClassReader(is);
                ClassNode node = new ClassNode();
                cr.accept(node, 0);
                allClasses.add(node);
            }
        }

        // 4) Применяем трансформеры
        for (ClassNode cn : allClasses) {
            for (ITransformer t : ctx.getTransformers()) {
                t.transform(cn);
            }
        }

        // 5) Завершаем работу трансформеров
        for (ITransformer t : ctx.getTransformers()) {
            t.finish(ctx);
        }

        // 6) Обновляем Main-Class, если есть Renamer
        for (ITransformer t : ctx.getTransformers()) {
            if (t instanceof RenamerTransformer ren) {
                String original = manifest.getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
                if (original != null) {
                    String internalOld = original.replace('.', '/');
                    String internalNew = ren.getClassMap().get(internalOld);
                    if (internalNew != null) {
                        manifest.getMainAttributes().put(
                                Attributes.Name.MAIN_CLASS,
                                internalNew.replace('/', '.')
                        );
                        System.out.println("[Core] Updated Main-Class to " +
                                manifest.getMainAttributes().getValue(Attributes.Name.MAIN_CLASS));
                    }
                }
            }
        }

        // 7) Пишем результат в JAR
        Files.createDirectories(ctx.getOutputJar().getParent());
        try (JarOutputStream jarOut = new JarOutputStream(
                Files.newOutputStream(ctx.getOutputJar(),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING),
                manifest
        )) {
            Set<String> written = new HashSet<>();

            for (ClassNode cn : allClasses) {
                String name = cn.name + ".class";
                if (written.contains(name)) continue;
                written.add(name);

                JarEntry entry = new JarEntry(name);
                jarOut.putNextEntry(entry);

                ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
                cn.accept(cw);
                jarOut.write(cw.toByteArray());
                jarOut.closeEntry();
            }

            // 7.1) Копируем ресурсы из оригинального JAR
            try (JarFile jarIn = new JarFile(ctx.getInputJar().toFile())) {
                Enumeration<JarEntry> entries = jarIn.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (entry.getName().endsWith(".class") || entry.getName().equals(JarFile.MANIFEST_NAME))
                        continue;
                    if (written.contains(entry.getName())) continue;

                    InputStream is = jarIn.getInputStream(entry);
                    jarOut.putNextEntry(new JarEntry(entry.getName()));
                    jarOut.write(is.readAllBytes());
                    jarOut.closeEntry();
                }
            }
        }
    }
}
