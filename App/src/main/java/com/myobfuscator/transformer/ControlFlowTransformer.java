// src/main/java/com/myobfuscator/transformer/ControlFlowTransformer.java
package com.myobfuscator.transformer;

import com.myobfuscator.core.ITransformer;
import com.myobfuscator.core.ObfuscationContext;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class ControlFlowTransformer implements ITransformer {
    private ObfuscationContext context;

    @Override
    public void init(ObfuscationContext ctx) {
        this.context = ctx;
    }

    @Override
    public void transform(ClassNode classNode) {
        for (MethodNode method : classNode.methods) {
            // пропускаем «пустые», abstract и native
            if (method.instructions == null || method.instructions.size() == 0) continue;
            int acc = method.access;
            if ((acc & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) continue;

            InsnList insns = method.instructions;
            int deadBranches = context.getDeadBranchCount();
            Random rnd = context.getRandom();

            // Сохраняем статический снимок списка узлов
            List<AbstractInsnNode> snapshot = Arrays.asList(insns.toArray());

            // Фильтруем только «безопасные» позиции для вставки
            List<AbstractInsnNode> candidates = snapshot.stream()
                    .filter(in -> !(in instanceof LabelNode))
                    .filter(in -> !(in instanceof FrameNode))
                    .filter(in -> !(in instanceof LineNumberNode))
                    .filter(in -> {
                        int op = in.getOpcode();
                        // не перед return/goto/switch
                        return op != Opcodes.RETURN
                                && op != Opcodes.GOTO
                                && !(in instanceof JumpInsnNode)
                                && !(in instanceof LookupSwitchInsnNode)
                                && !(in instanceof TableSwitchInsnNode);
                    })
                    .collect(Collectors.toList());

            // Перемешиваем кандидатов и берём первые deadBranches (или меньше)
            Collections.shuffle(candidates, rnd);
            List<AbstractInsnNode> picks = candidates.stream()
                    .limit(deadBranches)
                    .toList();

            // Вставляем «мёртвые» ветвления перед каждым выбранным узлом
            for (AbstractInsnNode anchor : picks) {
                insns.insertBefore(anchor, buildDeadBranch());
            }
        }
    }

    @Override
    public void finish(ObfuscationContext ctx) {
        // no-op
    }

    /**
     * Построить InsnList с «мёртвым» ветвлением:
     *    ICONST_0
     *    IFNE end
     *    ICONST_1
     *    POP
     *  end:
     */
    private InsnList buildDeadBranch() {
        LabelNode end = new LabelNode();
        InsnList dead = new InsnList();
        dead.add(new InsnNode(Opcodes.ICONST_0));
        dead.add(new JumpInsnNode(Opcodes.IFNE, end));
        dead.add(new InsnNode(Opcodes.ICONST_1));
        dead.add(new InsnNode(Opcodes.POP));
        dead.add(end);
        return dead;
    }
}
