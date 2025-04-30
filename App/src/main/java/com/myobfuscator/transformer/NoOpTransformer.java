package com.myobfuscator.transformer;

import com.myobfuscator.core.ITransformer;
import com.myobfuscator.core.ObfuscationContext;
import org.objectweb.asm.tree.ClassNode;

public class NoOpTransformer implements ITransformer {
    @Override
    public void init(ObfuscationContext ctx) {
        System.out.println("[NoOp] init");
    }
    @Override
    public void transform(ClassNode classNode) {
        System.out.println("[NoOp] transform: " + classNode.name);
    }
    @Override
    public void finish(ObfuscationContext ctx) {
        System.out.println("[NoOp] finish");
    }
}
