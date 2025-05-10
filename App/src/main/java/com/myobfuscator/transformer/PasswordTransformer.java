package com.myobfuscator.transformer;

import com.myobfuscator.core.ITransformer;
import com.myobfuscator.core.ObfuscationContext;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.InputStream;

public class PasswordTransformer implements ITransformer {
    private byte[] utilClassBytes;

    @Override
    public void init(ObfuscationContext ctx) throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/templates/PasswordUtil.class")) {
            if (is == null) throw new IllegalStateException("Cannot find PasswordUtil.class");
            utilClassBytes = is.readAllBytes();
        }
    }

    @Override
    public void transform(ClassNode cn) {
        // ничего не делаем — наш класс просто вбрасывается в allClasses
    }

    @Override
    public void finish(ObfuscationContext ctx) { }

    public ClassNode generatePasswordUtilNode() throws IOException {
        ClassReader cr = new ClassReader(utilClassBytes);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);
        return cn;
    }
}
