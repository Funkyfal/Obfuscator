package com.myobfuscator.core;

import com.myobfuscator.transformer.RenamerTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.InputStream;
import java.nio.file.*;
import java.util.Enumeration;
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

        // 2) Читаем входной JAR и его манифест
        Manifest manifest;
        try (JarFile jarIn = new JarFile(ctx.getInputJar().toFile())) {
            manifest = jarIn.getManifest();
        }

        // 2.1) Если есть RenamerTransformer, правим Main-Class
        for (ITransformer t : ctx.getTransformers()) {
            if (t instanceof com.myobfuscator.transformer.RenamerTransformer ren) {
                String original = manifest.getMainAttributes()
                        .getValue(Attributes.Name.MAIN_CLASS);
                // точечное имя -> внутреннее
                String internalOld = original.replace('.', '/');
                String internalNew = ren.getClassMap().get(internalOld);
                if (internalNew != null) {
                    // ставим обратно в точечном формате
                    manifest.getMainAttributes().put(
                            Attributes.Name.MAIN_CLASS,
                            internalNew.replace('/', '.')
                    );
                    System.out.println("[Core] Updated Main-Class to " +
                            manifest.getMainAttributes().getValue(Attributes.Name.MAIN_CLASS));
                }
            }
        }

        // 3) Открываем выходной JAR и сразу записываем исправленный манифест
        Files.createDirectories(ctx.getOutputJar().getParent());
        try (JarOutputStream jarOut = new JarOutputStream(
                Files.newOutputStream(ctx.getOutputJar(),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING))
        ) {
            // 3.1) Записываем новый манифест первым
            JarEntry mfEntry = new JarEntry(JarFile.MANIFEST_NAME);
            jarOut.putNextEntry(mfEntry);
            manifest.write(jarOut);
            jarOut.closeEntry();

            // 3.2) Копируем и обрабатываем остальные записи, пропуская старый манифест
            try (JarFile jarIn = new JarFile(ctx.getInputJar().toFile())) {
                Enumeration<JarEntry> entries = jarIn.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (JarFile.MANIFEST_NAME.equals(entry.getName())) {
                        continue; // пропускаем, уже записали
                    }
                    InputStream is = jarIn.getInputStream(entry);

                    if (entry.getName().endsWith(".class")) {
                        // 1) прочитать ClassNode
                        ClassReader cr = new ClassReader(is);
                        ClassNode node = new ClassNode();
                        cr.accept(node, 0);

                        // 2) применить трансформеры (переименуют node.name и т.п.)
                        for (ITransformer t : ctx.getTransformers()) {
                            t.transform(node);
                        }

                        // 3) определить новое имя файла
                        String oldEntryName = entry.getName();                       // "HelloWorld.class"
                        String oldInternal = oldEntryName.replaceAll("\\.class$", ""); // "HelloWorld"
                        String newInternal = null;
                        for (ITransformer t : ctx.getTransformers()) {
                            if (t instanceof RenamerTransformer ren) {
                                newInternal = ren.getClassMap().get(oldInternal);
                                break;
                            }
                        }
                        String newEntryName = (newInternal != null ? newInternal + ".class" : oldEntryName);

                        // 4) записать в JAR под новым именем
                        JarEntry outEntry = new JarEntry(newEntryName);
                        jarOut.putNextEntry(outEntry);

                        // 5) написать байты
                        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
                        node.accept(cw);
                        jarOut.write(cw.toByteArray());

                        jarOut.closeEntry();
                    } else {
                        // ресурсы копируем напрямую
                        jarOut.write(is.readAllBytes());
                    }
                    jarOut.closeEntry();
                }
            }
        }

        // 4) Завершаем работу трансформеров
        for (ITransformer t : ctx.getTransformers()) {
            t.finish(ctx);
        }
    }
}
