package com.myobfuscator.transformer;

import com.myobfuscator.core.ITransformer;
import com.myobfuscator.core.ObfuscationContext;
import com.myobfuscator.security.SystemBindingUtil;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.jar.JarFile;

public class BindingTransformer implements ITransformer {
    private byte[] utilClassBytes;
    private String mainClassInternal;
    private String expectedHash;
    private String expectedPath;

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

        try (InputStream ip = getClass().getResourceAsStream("/templates/expected_path.txt")) {
            expectedPath = new String(ip.readAllBytes(), StandardCharsets.UTF_8).trim();
        }

        SystemBindingUtil.setupPassword();
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
                    // --- Сначала вставляем вызов checkBinding(expectedHash, expectedPath) ---
                    insn.add(new LdcInsnNode(expectedHash));
                    insn.add(new LdcInsnNode(expectedPath));
                    insn.add(new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            "com/myobfuscator/security/SystemBindingUtil",
                            "checkBinding",
                            "(Ljava/lang/String;Ljava/lang/String;)V",
                            false
                    ));
                    insn.add(new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            "com/myobfuscator/security/SystemBindingUtil",
                            "checkPassword",
                            "()V",
                            false
                    ));

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

