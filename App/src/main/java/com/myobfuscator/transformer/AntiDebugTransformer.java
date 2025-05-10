package com.myobfuscator.transformer;

import com.myobfuscator.core.ITransformer;
import com.myobfuscator.core.ObfuscationContext;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class AntiDebugTransformer implements ITransformer {

    private Cipher encryptCipher;      // будет null, если StringEncryptor не включён
    private String encryptedArg;       // Base64(AES(literal)) или null
    private final String plainArg = "-agentlib:jdwp";  // запасная константа
    private boolean useDecryption;     // true, только если encryptCipher != null
    private String decryptorOwner;

    @Override
    public void init(ObfuscationContext ctx) throws Exception {
        // Пытаемся найти StringEncryptorTransformer
        for (ITransformer t : ctx.getTransformers()) {
            if (t instanceof StringEncryptorTransformer s) {
                this.encryptCipher = s.getEncryptCipher();
                break;
            }
        }
        // Если нашли шифратор — шифруем и включаем decryption
        if (encryptCipher != null) {
            byte[] raw = plainArg.getBytes(StandardCharsets.UTF_8);
            byte[] enc = encryptCipher.doFinal(raw);
            this.encryptedArg   = Base64.getEncoder().encodeToString(enc);
            this.useDecryption  = true;
        } else {
            // Без шифрования — будем вставлять plainArg
            this.useDecryption  = false;
        }

        // 2) Вычислить, под каким именем теперь будет StringDecryptor
        String original = "com/myobfuscator/util/StringDecryptor";
        decryptorOwner = original;
        for (ITransformer t : ctx.getTransformers()) {
            if (t instanceof RenamerTransformer ren) {
                String mapped = ren.getClassMap().get(original);
                if (mapped != null) decryptorOwner = mapped;
                break;
            }
        }
    }

    @Override
    public void transform(ClassNode classNode) {
        if (classNode.name.equals(decryptorOwner)
                || classNode.name.equals("com/myobfuscator/security/SystemBindingUtil")) {
            return;
        }

        for (MethodNode m : classNode.methods) {
            if (m.instructions == null || m.instructions.size() == 0) continue;
            if ((m.access & (Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE)) != 0) continue;

            InsnList check = buildAntiDebugBlock();
            m.instructions.insert(m.instructions.getFirst(), check);
        }
    }

    @Override
    public void finish(ObfuscationContext ctx) { /* no-op */ }

    private InsnList buildAntiDebugBlock() {
        InsnList list = new InsnList();
        LabelNode ok = new LabelNode();

        // --- общий код проверки JVM-флагов ---
        list.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "java/lang/management/ManagementFactory",
                "getRuntimeMXBean",
                "()Ljava/lang/management/RuntimeMXBean;",
                false));
        list.add(new MethodInsnNode(
                Opcodes.INVOKEINTERFACE,
                "java/lang/management/RuntimeMXBean",
                "getInputArguments",
                "()Ljava/util/List;",
                true));
        list.add(new MethodInsnNode(
                Opcodes.INVOKEINTERFACE,
                "java/util/List",
                "toString",
                "()Ljava/lang/String;",
                true));

        // --- Ветвление вставки литерала ---
        if (useDecryption) {
            // зашифрованный Base64 + дешифровка
            list.add(new LdcInsnNode(encryptedArg));
            list.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    decryptorOwner,
                    "decryptBase64",
                    "(Ljava/lang/String;)Ljava/lang/String;",
                    false
            ));
        } else {
            // простой литерал
            list.add(new LdcInsnNode(plainArg));
        }

        // дальше стандартная проверка contains...
        list.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "java/lang/String",
                "contains",
                "(Ljava/lang/CharSequence;)Z",
                false));
        list.add(new JumpInsnNode(Opcodes.IFEQ, ok));

        // бросаем исключение
        list.add(new TypeInsnNode(Opcodes.NEW, "java/lang/RuntimeException"));
        list.add(new InsnNode(Opcodes.DUP));
        list.add(new LdcInsnNode("Debug detected"));
        list.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL,
                "java/lang/RuntimeException",
                "<init>",
                "(Ljava/lang/String;)V",
                false));
        list.add(new InsnNode(Opcodes.ATHROW));

        // метка продолжения
        list.add(ok);
        return list;
    }

}
