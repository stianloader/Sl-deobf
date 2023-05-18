package de.geolykt.starloader.deobf.stack.source;

import org.objectweb.asm.tree.InsnNode;

public class DoubleConstantSource extends AbstractConstantSource<InsnNode, Double> {

    private final double value;

    public DoubleConstantSource(InsnNode insn, double value) {
        super(insn, value);
        this.value = value;
    }

    public final double getDoubleConstant() {
        return this.value;
    }
}
