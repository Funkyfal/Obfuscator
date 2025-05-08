// src/main/java/com/myobfuscator/core/ObfuscatorCore.java
package com.myobfuscator.core;

import com.myobfuscator.transformer.ControlFlowTransformer;
import com.myobfuscator.transformer.RenamerTransformer;
import com.myobfuscator.transformer.StringEncryptorTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.InputStream;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;

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

        // Обеспечиваем порядок StringEncryptor → Renamer
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

        // 3.1) Inject StringDecryptor if needed
        for (ITransformer t : ctx.getTransformers()) {
            if (t instanceof StringEncryptorTransformer s) {
                allClasses.add(s.generateDecryptorNode());
            }
        }

        // 3.2) Load classes from JAR
        try (JarFile jarIn = new JarFile(ctx.getInputJar().toFile())) {
            Enumeration<JarEntry> entries = jarIn.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                try (InputStream is = jarIn.getInputStream(entry)) {
                    ClassReader cr = new ClassReader(is);
                    ClassNode node = new ClassNode();
                    // expand frames so we can insert instructions safely
                    cr.accept(node, ClassReader.EXPAND_FRAMES | ClassReader.SKIP_DEBUG);
                    allClasses.add(node);
                }
            }
        }

        // 4) Apply transformers
        for (ClassNode cn : allClasses) {
            for (ITransformer t : ctx.getTransformers()) {
                t.transform(cn);
            }
        }

        // 5) Finish
        for (ITransformer t : ctx.getTransformers()) {
            t.finish(ctx);
        }

        // 6) Update Main-Class if renamed
        for (ITransformer t : ctx.getTransformers()) {
            if (t instanceof RenamerTransformer ren) {
                String orig = manifest.getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
                if (orig != null) {
                    String inOld = orig.replace('.', '/');
                    String inNew = ren.getClassMap().get(inOld);
                    if (inNew != null) {
                        manifest.getMainAttributes().putValue(
                                Attributes.Name.MAIN_CLASS.toString(),
                                inNew.replace('/', '.')
                        );
                    }
                }
            }
        }

        // 7) Write out JAR
        Files.createDirectories(ctx.getOutputJar().getParent());
        try (JarOutputStream jarOut = new JarOutputStream(
                Files.newOutputStream(ctx.getOutputJar(),
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING),
                manifest)) {

            Set<String> written = new HashSet<>();
            for (ClassNode cn : allClasses) {
                String name = cn.name + ".class";
                if (written.add(name)) {
                    jarOut.putNextEntry(new JarEntry(name));
                    ClassWriter cw = new ClassWriter(
                            ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS
                    );
                    cn.accept(cw);
                    jarOut.write(cw.toByteArray());
                    jarOut.closeEntry();
                }
            }
            // copy resources
            try (JarFile jarIn = new JarFile(ctx.getInputJar().toFile())) {
                for (Enumeration<JarEntry> e = jarIn.entries(); e.hasMoreElements();) {
                    JarEntry entry = e.nextElement();
                    if (entry.getName().endsWith(".class") ||
                            entry.getName().equals(JarFile.MANIFEST_NAME)) continue;
                    if (written.contains(entry.getName())) continue;
                    jarOut.putNextEntry(new JarEntry(entry.getName()));
                    jarOut.write(jarIn.getInputStream(entry).readAllBytes());
                    jarOut.closeEntry();
                }
            }
        }
    }
}
