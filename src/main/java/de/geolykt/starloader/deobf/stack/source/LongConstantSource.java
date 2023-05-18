package de.geolykt.starloader.deobf.stack.source;

import org.objectweb.asm.tree.InsnNode;

public final class LongConstantSource extends AbstractConstantSource<InsnNode, Long> {

    private final long value;

    public LongConstantSource(InsnNode insn, long value) {
        super(insn, value);
        this.value = value;
    }

    public final long getLongConstant() {
        return this.value;
    }
}
