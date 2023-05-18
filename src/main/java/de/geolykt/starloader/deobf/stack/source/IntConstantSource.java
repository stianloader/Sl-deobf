package de.geolykt.starloader.deobf.stack.source;

import org.objectweb.asm.tree.InsnNode;

public final class IntConstantSource extends AbstractConstantSource<InsnNode, Integer> {

    private final int value;

    public IntConstantSource(InsnNode insn, int value) {
        super(insn, value);
        this.value = value;
    }

    public final int getIntConstant() {
        return this.value;
    }
}
