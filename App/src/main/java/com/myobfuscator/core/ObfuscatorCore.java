package com.myobfuscator.core;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.*;

public class ObfuscatorCore {
    private final ObfuscationContext ctx;

    public ObfuscatorCore(ObfuscationContext ctx) {
        this.ctx = ctx;
    }

    public void run() throws Exception {
        // 1) Инициализируем трансформеры
        for (ITransformer t : ctx.getTransformers()) {
            t.init(ctx);
        }

        // 2) Читаем входной JAR
        try (JarFile jarIn = new JarFile(ctx.getInputJar().toFile());
             JarOutputStream jarOut = new JarOutputStream(
                     Files.newOutputStream(ctx.getOutputJar(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
             )) {

            Enumeration<JarEntry> entries = jarIn.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                InputStream is = jarIn.getInputStream(entry);
                JarEntry outEntry = new JarEntry(entry.getName());
                jarOut.putNextEntry(outEntry);

                if (entry.getName().endsWith(".class")) {
                    // Обработка байт-кода
                    ClassReader cr = new ClassReader(is);
                    ClassNode node = new ClassNode();
                    cr.accept(node, 0);

                    // Применяем все трансформеры
                    for (ITransformer t : ctx.getTransformers()) {
                        t.transform(node);
                    }

                    // Пишем обратно
                    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
                    node.accept(cw);
                    byte[] bytes = cw.toByteArray();
                    jarOut.write(bytes);
                } else {
                    // Просто копируем ресурс
                    byte[] buffer = is.readAllBytes();
                    jarOut.write(buffer);
                }
                jarOut.closeEntry();
            }
        }

        // 3) Завершаем работу трансформеров
        for (ITransformer t : ctx.getTransformers()) {
            t.finish(ctx);
        }
    }
}
