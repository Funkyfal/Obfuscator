package com.myobfuscator.transformer;

import com.myobfuscator.core.ITransformer;
import com.myobfuscator.core.ObfuscationContext;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.jar.JarFile;

public class BindingTransformer implements ITransformer {
    private byte[] utilClassBytes;
    private String mainClassInternal;
    private String expectedHash;

    @Override
    public void init(ObfuscationContext ctx) throws Exception {
        // 1) Считываем SystemBindingUtil.class из ресурсов
        try (InputStream is = getClass().getResourceAsStream("/templates/SystemBindingUtil.class")) {
            utilClassBytes = is.readAllBytes();
        }
        // 2) Узнаём Main-Class
        try (JarFile jar = new JarFile(ctx.getInputJar().toFile())) {
            String mc = jar.getManifest().getMainAttributes().getValue("Main-Class");
            this.mainClassInternal = mc.replace('.', '/');
        }
        // 3) Читаем ожидаемый хеш из ресурса (его заранее положили, например, в /templates/expected_hash.txt)
        try (InputStream ih = getClass().getResourceAsStream("/templates/expected_hash.txt")) {
            expectedHash = new String(ih.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
    }

    @Override
    public void transform(ClassNode cn) {
        // 1) Добавляем сам SystemBindingUtil в выходной JAR
        if (cn.name.equals("SYSTEM_BINDING_PLACEHOLDER")) {
            // placeholder-класс из шаблона — можем его пропустить,
            // реальное добавление делаем в ObfuscatorCore через utilClassBytes
        }

        // 2) Вставляем проверку в main()
        if (cn.name.equals(mainClassInternal)) {
            for (MethodNode mn : cn.methods) {
                if ("main".equals(mn.name) && "([Ljava/lang/String;)V".equals(mn.desc)) {
                    InsnList insn = new InsnList();
                    // загрузка expectedHash
                    insn.add(new LdcInsnNode(expectedHash));
                    // вызов computeSystemHash()
                    insn.add(new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            "com/myobfuscator/security/SystemBindingUtil",
                            "computeSystemHash",
                            "()Ljava/lang/String;",
                            false
                    ));
                    // сравнение
                    insn.add(new MethodInsnNode(
                            Opcodes.INVOKEVIRTUAL,
                            "java/lang/String",
                            "equals",
                            "(Ljava/lang/Object;)Z",
                            false
                    ));
                    LabelNode ok = new LabelNode();
                    insn.add(new JumpInsnNode(Opcodes.IFNE, ok));
                    // не совпало → exit
                    insn.add(new FieldInsnNode(
                            Opcodes.GETSTATIC,
                            "java/lang/System",
                            "err",
                            "Ljava/io/PrintStream;"
                    ));
                    insn.add(new LdcInsnNode("Unauthorized machine. Exiting."));
                    insn.add(new MethodInsnNode(
                            Opcodes.INVOKEVIRTUAL,
                            "java/io/PrintStream",
                            "println",
                            "(Ljava/lang/String;)V",
                            false
                    ));
                    insn.add(new InsnNode(Opcodes.ICONST_1));
                    insn.add(new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            "java/lang/System",
                            "exit",
                            "(I)V",
                            false
                    ));
                    insn.add(ok);
                    // вставляем в самое начало main
                    mn.instructions.insert(insn);
                    break;
                }
            }
        }
    }

    @Override
    public void finish(ObfuscationContext ctx) {
        // ничего
    }

    public byte[] getUtilClassBytes() {
        return utilClassBytes;
    }
}

