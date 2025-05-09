package com.myobfuscator.transformer;

import com.myobfuscator.core.ITransformer;
import com.myobfuscator.core.ObfuscationContext;
import org.objectweb.asm.ClassReader;
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
    private final String decryptorInternal = "com/myobfuscator/util/StringDecryptor";

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
        if (decryptorInternal.equals(classNode.name)) return;
        // Шифрование литералов (как ранее)
        for (MethodNode mn : classNode.methods) {
            InsnList insns = mn.instructions;
            if (insns == null) continue;

            List<AbstractInsnNode> toReplace = new ArrayList<>();
            for (AbstractInsnNode insn : insns.toArray()) {
                if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof String) {
                    toReplace.add(insn);
                }
            }
            for (AbstractInsnNode insn : toReplace) {
                LdcInsnNode ldc = (LdcInsnNode) insn;
                String original = (String) ldc.cst;
                try {
                    byte[] encrypted = encryptCipher.doFinal(original.getBytes(StandardCharsets.UTF_8));
                    String b64 = Base64.getEncoder().encodeToString(encrypted);

                    InsnList repl = new InsnList();
                    repl.add(new LdcInsnNode(b64));
                    repl.add(new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            "com/myobfuscator/util/StringDecryptor",
                            "decryptBase64",
                            "(Ljava/lang/String;)Ljava/lang/String;",
                            false
                    ));

                    insns.insertBefore(insn, repl);
                    insns.remove(insn);
                } catch (Exception e) {
                    throw new RuntimeException("Encryption failed for: " + original, e);
                }
            }
        }
    }

    @Override
    public void finish(ObfuscationContext context) {
    }

    /**
     * Возвращает ClassNode для StringDecryptor, с патчем ключа и ldc в <clinit>
     */
    public ClassNode generateDecryptorNode() throws IOException {
        try (InputStream template = getClass()
                .getResourceAsStream("/templates/StringDecryptorTemplate.class")) {
            if (template == null) {
                throw new IllegalStateException("Не найден шаблон StringDecryptorTemplate.class в /templates");
            }
            ClassReader cr = new ClassReader(template);
            ClassNode cn = new ClassNode();
            cr.accept(cn, 0);
            patchDecryptor(cn);
            return cn;
        }
    }

    /**
     * Патчит поле BASE64_KEY и LDC-инструкции в методе <clinit>
     */
    private void patchDecryptor(ClassNode cn) {
        // Патчим значение поля BASE64_KEY
        for (FieldNode fn : cn.fields) {
            if ("BASE64_KEY".equals(fn.name)) {
                fn.value = base64Key;
                break;
            }
        }
        // Заменяем LDC-литералы в <clinit>
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
    }

    public String getBase64Key() {
        return base64Key;
    }

    public Cipher getEncryptCipher() { return encryptCipher; }
}
