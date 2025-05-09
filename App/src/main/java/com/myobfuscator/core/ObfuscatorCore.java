package com.myobfuscator.core;

import com.myobfuscator.transformer.BindingTransformer;
import com.myobfuscator.transformer.StringEncryptorTransformer;
import com.myobfuscator.transformer.RenamerTransformer;
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
        // 1) Инициализируем все трансформеры
        for (ITransformer t : ctx.getTransformers()) {
            t.init(ctx);
        }

        // 2) Обеспечиваем порядок: сначала шифрование строк → потом переименование
        ctx.getTransformers().sort((a, b) -> {
            if (a instanceof StringEncryptorTransformer && b instanceof RenamerTransformer) return -1;
            if (a instanceof RenamerTransformer     && b instanceof StringEncryptorTransformer) return 1;
            return 0;
        });

        // 3) Читаем манифест исходного JAR
        Manifest manifest;
        try (JarFile inJar = new JarFile(ctx.getInputJar().toFile())) {
            manifest = inJar.getManifest();
        }

        // 4) Собираем список ClassNode
        List<ClassNode> allClasses = new ArrayList<>();

        // 4.1) Инжектим класс-дешифратор строк (если используется)
        for (ITransformer t : ctx.getTransformers()) {
            if (t instanceof StringEncryptorTransformer s) {
                allClasses.add(s.generateDecryptorNode());
                // без break — даже если вы случайно поставите ещё один шифратор, он добавится
            }
        }

        // 4.2) Инжектим SystemBindingUtil (если используется)
        for (ITransformer t : ctx.getTransformers()) {
            if (t instanceof BindingTransformer bt) {
                ClassReader cr = new ClassReader(bt.getUtilClassBytes());
                ClassNode bindingNode = new ClassNode();
                cr.accept(bindingNode, 0);
                allClasses.add(bindingNode);
            }
        }

        // 4.3) Загружаем все .class из входного JAR
        try (JarFile inJar = new JarFile(ctx.getInputJar().toFile())) {
            Enumeration<JarEntry> ents = inJar.entries();
            while (ents.hasMoreElements()) {
                JarEntry entry = ents.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                try (InputStream is = inJar.getInputStream(entry)) {
                    ClassReader cr = new ClassReader(is);
                    ClassNode node = new ClassNode();
                    cr.accept(node, ClassReader.EXPAND_FRAMES | ClassReader.SKIP_DEBUG);
                    allClasses.add(node);
                }
            }
        }

        // 5) Применяем трансформеры ко всем классам
        for (ClassNode cn : allClasses) {
            for (ITransformer t : ctx.getTransformers()) {
                t.transform(cn);
            }
        }

        // 6) Завершаем трансформеры
        for (ITransformer t : ctx.getTransformers()) {
            t.finish(ctx);
        }

        // 7) Обновляем Main-Class в манифесте, если его переименовал Renamer
        for (ITransformer t : ctx.getTransformers()) {
            if (t instanceof RenamerTransformer ren) {
                String orig = manifest.getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
                if (orig != null) {
                    String oldInternal = orig.replace('.', '/');
                    String newInternal = ren.getClassMap().get(oldInternal);
                    if (newInternal != null) {
                        manifest.getMainAttributes().putValue(
                                Attributes.Name.MAIN_CLASS.toString(),
                                newInternal.replace('/', '.')
                        );
                    }
                }
            }
        }

        // 8) Записываем новый JAR
        Files.createDirectories(ctx.getOutputJar().getParent());
        try (JarOutputStream outJar = new JarOutputStream(
                Files.newOutputStream(ctx.getOutputJar(),
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING),
                manifest)) {

            Set<String> written = new HashSet<>();
            // 8.1) Пишем все классы
            for (ClassNode cn : allClasses) {
                String entryName = cn.name + ".class";
                if (written.add(entryName)) {
                    outJar.putNextEntry(new JarEntry(entryName));
                    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
                    cn.accept(cw);
                    outJar.write(cw.toByteArray());
                    outJar.closeEntry();
                }
            }

            // 8.2) Копируем прочие ресурсы из входного JAR
            try (JarFile inJar = new JarFile(ctx.getInputJar().toFile())) {
                for (Enumeration<JarEntry> e = inJar.entries(); e.hasMoreElements();) {
                    JarEntry entry = e.nextElement();
                    String name = entry.getName();
                    if (name.endsWith(".class") || name.equals(JarFile.MANIFEST_NAME)) continue;
                    if (written.contains(name)) continue;
                    outJar.putNextEntry(new JarEntry(name));
                    outJar.write(inJar.getInputStream(entry).readAllBytes());
                    outJar.closeEntry();
                }
            }
        }
    }
}
