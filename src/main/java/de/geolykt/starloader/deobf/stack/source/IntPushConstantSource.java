package de.geolykt.starloader.deobf.stack.source;

import org.objectweb.asm.tree.IntInsnNode;

public class IntPushConstantSource extends AbstractConstantSource<IntInsnNode, Integer> {

    public IntPushConstantSource(IntInsnNode insn) {
        super(insn, insn.operand);
    }

    public final int getIntConstant() {
        return super.getInsn().operand;
    }
}
