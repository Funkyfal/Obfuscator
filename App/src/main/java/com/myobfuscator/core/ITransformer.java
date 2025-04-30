package com.myobfuscator.core;

import org.objectweb.asm.tree.ClassNode;

public interface ITransformer {
    void init(ObfuscationContext context) throws Exception;

    void transform(ClassNode classNode);

    void finish(ObfuscationContext context);
}
