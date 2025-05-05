package com.myobfuscator.transformer;

import com.myobfuscator.core.ITransformer;
import org.objectweb.asm.tree.ClassNode;
import com.myobfuscator.core.ObfuscationContext;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

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
        // 2.4.1) Class-level annotations
        remapAnnotations(classNode.visibleAnnotations);
        remapAnnotations(classNode.invisibleAnnotations);

        if (classNode.signature != null) {
            classNode.signature = remapSignature(classNode.signature);
        }
        // 1) Переименовать сам класс
        String oldName = classNode.name;
        String newName = classMap.get(oldName);
        if (newName != null) {
            classNode.name = newName;
        }

        for (MethodNode mn : classNode.methods) {
            // 0) Переименовать сигнатуру (generic) — у тебя уже есть
            if (mn.signature != null) {
                mn.signature = remapSignature(mn.signature);
            }
            // 1) Переименовать DESCRIPTOR метода/конструктора:
            //    например "(Ldemo/TestAll;)V" → "(LC0;)V"
            String oldDesc = mn.desc;
            String newDesc = remapDesc(oldDesc);
            if (!newDesc.equals(oldDesc)) {
                mn.desc = newDesc;
            }
        }

        for (MethodNode mn : classNode.methods) {
            // — уже обновили mn.desc и mn.signature выше
            InsnList insns = mn.instructions;
            // Перебираем всевозможные узлы один раз
            for (AbstractInsnNode insn = insns.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof TypeInsnNode tin) {
                    // NEW, CHECKCAST, ANEWARRAY
                    String rn = classMap.get(tin.desc);
                    if (rn != null) tin.desc = rn;
                }
                else if (insn instanceof FieldInsnNode fin) {
                    String rn = classMap.get(fin.owner);
                    if (rn != null) fin.owner = rn;
                }
                else if (insn instanceof MethodInsnNode min) {
                    // INVOKESPECIAL, INVOKEVIRTUAL и т.д.
                    String rn = classMap.get(min.owner);
                    if (rn != null) {
                        min.owner = rn;
                    }
                    // и дескриптор вызова (конструктора) тоже правим:
                    String nd = remapDesc(min.desc);
                    if (!nd.equals(min.desc)) min.desc = nd;
                }
                else if (insn instanceof InvokeDynamicInsnNode indy) {
                    // bootstrap args: Type и Handle
                    for (int i = 0; i < indy.bsmArgs.length; i++) {
                        Object arg = indy.bsmArgs[i];
                        if (arg instanceof Type t && t.getSort() == Type.OBJECT) {
                            String in = t.getInternalName();
                            String rn = classMap.get(in);
                            if (rn != null) indy.bsmArgs[i] = Type.getObjectType(rn);
                        } else if (arg instanceof Handle h) {
                            String o = h.getOwner();
                            String rn = classMap.get(o);
                            if (rn != null) {
                                indy.bsmArgs[i] = new Handle(
                                        h.getTag(), rn, h.getName(), h.getDesc(), h.isInterface()
                                );
                            }
                        }
                    }
                }
            }
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
            InsnList insns = mn.instructions;
            for (AbstractInsnNode insn : insns.toArray()) {
                if (insn instanceof TypeInsnNode tin) {
                    // NEW, CHECKCAST, ANEWARRAY — tin.desc это INTERNAL_NAME
                    String renamed = classMap.get(tin.desc);
                    if (renamed != null) {
                        tin.desc = renamed;
                    }
                }
                else if (insn instanceof FieldInsnNode fin) {
                    // доступ к полю
                    String ro = fin.owner;
                    String r = classMap.get(ro);
                    if (r != null) fin.owner = r;
                }
                else if (insn instanceof MethodInsnNode min) {
                    // вызов метода или конструктора
                    String ro = min.owner;
                    String r = classMap.get(ro);
                    if (r != null) min.owner = r;
                    // и дескриптор: (Ldemo/TestAll;)V → (LC0;)V
                    String oldDesc = min.desc;
                    String nd = remapDesc(oldDesc);
                    if (!nd.equals(oldDesc)) min.desc = nd;
                }
                else if (insn instanceof InvokeDynamicInsnNode indy) {
                    // твой код для bootstrap args
                    for (int i = 0; i < indy.bsmArgs.length; i++) {
                        Object arg = indy.bsmArgs[i];
                        if (arg instanceof Type t && t.getSort() == Type.OBJECT) {
                            String in = t.getInternalName();
                            String rn = classMap.get(in);
                            if (rn != null) indy.bsmArgs[i] = Type.getObjectType(rn);
                        } else if (arg instanceof Handle h) {
                            String o = h.getOwner();
                            String rn = classMap.get(o);
                            if (rn != null) {
                                indy.bsmArgs[i] = new Handle(
                                        h.getTag(), rn, h.getName(), h.getDesc(), h.isInterface()
                                );
                            }
                        }
                    }
                }
            }
        });


        //Локальные переменные
        classNode.methods.forEach(method -> {
            if (method.localVariables != null) {
                for (LocalVariableNode lv : method.localVariables) {
                    // lv.name — это имя переменной, его обычно не обфусцируем
                    // Но вот lv.desc и lv.signature могут содержать внутренние имена классов
                    // 1) Переименовать тип переменной
                    String oldDesc = lv.desc;              // например "Ldemo/TestAll;"
                    String newDesc = remapDesc(oldDesc);
                    if (!newDesc.equals(oldDesc)) {
                        lv.desc = newDesc;
                    }
                    // 2) Переименовать generic-подпись
                    if (lv.signature != null) {
                        String oldSig = lv.signature;
                        String newSig = remapSignature(oldSig);
                        if (!newSig.equals(oldSig)) {
                            lv.signature = newSig;
                        }
                    }
                }
            }
        });

        // 2.4.2) Field-level annotations
        classNode.fields.forEach(fn -> {
            remapAnnotations(fn.visibleAnnotations);
            remapAnnotations(fn.invisibleAnnotations);
        });

        // 2.4.3) Method-level annotations (and parameter annotations)
        classNode.methods.forEach(mn -> {
            remapAnnotations(mn.visibleAnnotations);
            remapAnnotations(mn.invisibleAnnotations);
            if (mn.visibleParameterAnnotations != null) {
                for (List<AnnotationNode> paramAnns : mn.visibleParameterAnnotations) {
                    remapAnnotations(paramAnns);
                }
            }
            if (mn.invisibleParameterAnnotations != null) {
                for (List<AnnotationNode> paramAnns : mn.invisibleParameterAnnotations) {
                    remapAnnotations(paramAnns);
                }
            }
        });

        //2.4.4 Inner classes
        if (classNode.innerClasses != null) {
            for (InnerClassNode icn : classNode.innerClasses) {
                // 1) вложенный класс
                if (classMap.containsKey(icn.name)) {
                    icn.name = classMap.get(icn.name);
                }
                // 2) внешний класс
                if (icn.outerName != null && classMap.containsKey(icn.outerName)) {
                    icn.outerName = classMap.get(icn.outerName);
                }
                // 3) оставляем icn.innerName (простой) без изменений
            }
        }

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

    private String remapDesc(String desc) {
        // пример: desc = "Ljava/util/List<Ldemo/TestAll;>;"
        // нужно пройтись по classMap, заменяя "/demo/TestAll" на "/C0" и т.п.
        for (Map.Entry<String, String> e : classMap.entrySet()) {
            String key = e.getKey();      // "demo/TestAll"
            String val = e.getValue();    // "C0"
            // в desc внутренние имена всегда идут с ведущим 'L' и завершающим ';'
            desc = desc.replace(key, val);
        }
        return desc;
    }

    private void remapAnnotations(List<AnnotationNode> list) {
        if (list == null) return;
        for (AnnotationNode an : list) {
            // 1) переименовать desc
            an.desc = remapDesc(an.desc);
            // 2) в values: ключи/значения
            if (an.values != null) {
                for (int i = 0; i < an.values.size(); i++) {
                    Object v = an.values.get(i);
                    if (v instanceof String s) {
                        // строки оставляем как есть
                    } else if (v instanceof Type t && t.getSort() == Type.OBJECT) {
                        String in = t.getInternalName();
                        if (classMap.containsKey(in)) {
                            an.values.set(i, Type.getObjectType(classMap.get(in)));
                        }
                    } else if (v instanceof AnnotationNode nested) {
                        // рекурсивно обрабатываем вложенную аннотацию
                        remapAnnotations(List.of(nested));
                    }
                    // остальные типы (Integer, etc.) не трогаем
                }
            }
        }
    }

}
