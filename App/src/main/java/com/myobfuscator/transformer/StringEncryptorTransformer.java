package com.myobfuscator.transformer;

import com.myobfuscator.core.ITransformer;
import com.myobfuscator.core.ObfuscationContext;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class StringEncryptorTransformer implements ITransformer {

    private SecretKey aesKey;
    private Cipher encryptCipher;
    private String base64Key;

    @Override
    public void init(ObfuscationContext context) throws Exception {
        // Генерация AES-ключа
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(128);
        aesKey = keyGen.generateKey();

        // Инициализация шифратора
        encryptCipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        encryptCipher.init(Cipher.ENCRYPT_MODE, aesKey);

        // Base64 ключ для StringDecryptor
        base64Key = Base64.getEncoder().encodeToString(aesKey.getEncoded());
        System.out.println("[StringEncryptor] Generated AES key: " + base64Key);
    }

    @Override
    public void transform(ClassNode classNode) {
        for (MethodNode mn : classNode.methods) {
            InsnList insns = mn.instructions;
            if (insns == null) continue;

            // Сначала накопим все LDC-инструкции со String
            List<AbstractInsnNode> toReplace = new ArrayList<>();
            for (AbstractInsnNode insn : insns.toArray()) {
                if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof String) {
                    toReplace.add(insn);
                }
            }

            // А теперь для каждой — сделать замену
            for (AbstractInsnNode insn : toReplace) {
                LdcInsnNode ldc = (LdcInsnNode) insn;
                String original = (String) ldc.cst;
                try {
                    // 1) Шифруем
                    byte[] encrypted = encryptCipher.doFinal(
                            original.getBytes(StandardCharsets.UTF_8)
                    );
                    String b64 = Base64.getEncoder().encodeToString(encrypted);

                    // 2) Готовим новую инструкцию: LDC "base64" + INVOKESTATIC decryptBase64
                    InsnList repl = new InsnList();
                    repl.add(new LdcInsnNode(b64));
                    repl.add(new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            "com/myobfuscator/util/StringDecryptor",
                            "decryptBase64",
                            "(Ljava/lang/String;)Ljava/lang/String;",
                            false
                    ));

                    // 3) Вставляем _перед_ старым ldc, чтобы не сбить порядок
                    insns.insertBefore(insn, repl);
                    // 4) Удаляем только сам старый ldc
                    insns.remove(insn);
                } catch (Exception e) {
                    throw new RuntimeException("Encryption failed for: " + original, e);
                }
            }
        }
    }


    @Override
    public void finish(ObfuscationContext context) {
        // при необходимости: вывести/сохранить ключ
    }

    public byte[] generateDecryptorClass() throws IOException {
        try (InputStream template =
                     getClass().getResourceAsStream("/templates/StringDecryptorTemplate.class")) {

            ClassReader cr = new ClassReader(template);
            ClassNode cn = new ClassNode();
            cr.accept(cn, 0);

            // 1) Патчим ConstantValue у поля BASE64_KEY
            for (FieldNode fn : cn.fields) {
                if (fn.name.equals("BASE64_KEY")) {
                    fn.value = base64Key;
                    break;
                }
            }

            // 2) Патчим LdcInsnNode внутри <clinit>, заменяя literal "{{BASE64_KEY}}"
            for (MethodNode mn : cn.methods) {
                if ("<clinit>".equals(mn.name)) {
                    for (AbstractInsnNode insn : mn.instructions.toArray()) {
                        if (insn instanceof LdcInsnNode ldc
                                && "{{BASE64_KEY}}".equals(ldc.cst)) {
                            ldc.cst = base64Key;
                        }
                    }
                }
            }

            // 3) Пишем класс обратно
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            cn.accept(cw);
            return cw.toByteArray();
        }
    }


    // Геттер для base64Key, если понадобится в GUI
    public String getBase64Key() {
        return base64Key;
    }
}
