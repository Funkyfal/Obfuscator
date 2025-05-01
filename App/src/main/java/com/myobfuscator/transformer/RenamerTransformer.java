package com.myobfuscator.transformer;

import com.myobfuscator.core.ITransformer;
import com.myobfuscator.core.ObfuscationContext;
import org.objectweb.asm.tree.ClassNode;

import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class RenamerTransformer implements ITransformer {

    private final Map<String, String> classMap = new HashMap<>();
    private int classCounter = 0;

    @Override
    public void init(ObfuscationContext ctx) throws Exception {
        // 1) Открыть входной JAR
        try (JarFile jar = new JarFile(ctx.getInputJar().toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                // 2) Внутреннее имя класса (слеши), без .class
                String internalName = entry.getName().replaceAll("\\.class$", "");
                // 3) Сгенерировать новое короткое имя: C0, C1, C2, ...
                String newName = "C" + (classCounter++);
                classMap.put(internalName, newName);
            }
        }
        System.out.println("[Renamer] mapped " + classMap.size() + " classes");
    }

    @Override
    public void transform(ClassNode classNode) {
        if (classNode.signature != null) {
            classNode.signature = remapSignature(classNode.signature);
        }
        // 1) Переименовать сам класс
        String oldName = classNode.name;
        String newName = classMap.get(oldName);
        if (newName != null) {
            classNode.name = newName;
        }

        // 2) Поля
        classNode.fields.forEach(fn -> {
            if (fn.signature != null) {
                fn.signature = remapSignature(fn.signature);
            }
        });

        // 3) Методы
        classNode.methods.forEach(mn -> {
            if (mn.signature != null) {
                mn.signature = remapSignature(mn.signature);
            }
            if (mn.exceptions != null) {
                mn.exceptions.replaceAll(ex -> classMap.getOrDefault(ex, ex));
            }
        });

        // 4) Переименовать суперкласс
        if (classMap.containsKey(classNode.superName)) {
            classNode.superName = classMap.get(classNode.superName);
        }
        // 5) Переименовать интерфейсы
        for (int i = 0; i < classNode.interfaces.size(); i++) {
            String iface = classNode.interfaces.get(i);
            if (classMap.containsKey(iface)) {
                classNode.interfaces.set(i, classMap.get(iface));
            }
        }

        // 6) Переименовать все ссылочные инструкции
        classNode.methods.forEach(mn -> {
            mn.instructions.iterator().forEachRemaining(insn -> {
                // Тип инструкций: FieldInsnNode, MethodInsnNode, TypeInsnNode, InvokeDynamicInsnNode, LdcInsnNode с Type
                if (insn instanceof org.objectweb.asm.tree.FieldInsnNode fin) {
                    if (classMap.containsKey(fin.owner)) {
                        fin.owner = classMap.get(fin.owner);
                    }
                } else if (insn instanceof org.objectweb.asm.tree.MethodInsnNode min) {
                    if (classMap.containsKey(min.owner)) {
                        min.owner = classMap.get(min.owner);
                    }
                } else if (insn instanceof org.objectweb.asm.tree.TypeInsnNode tin) {
                    if (classMap.containsKey(tin.desc)) {
                        tin.desc = classMap.get(tin.desc);
                    }
                } else if (insn instanceof org.objectweb.asm.tree.InvokeDynamicInsnNode indy) {
                    // handle indy.bsmArgs if they contain Type or Handle (omitted for brevity)
                }
                // примеры других нод: MultiANewArrayInsnNode, LdcInsnNode
            });
        });
    }

    @Override
    public void finish(ObfuscationContext ctx) {
        // Ничего не делаем
        System.out.println("[Renamer] done");
    }

    public Map<String, String> getClassMap() {
        return classMap;
    }

    private String remapSignature(String sig) {
        for (Map.Entry<String, String> e : classMap.entrySet()) {
            sig = sig.replace(e.getKey(), e.getValue());
        }
        return sig;
    }
}
