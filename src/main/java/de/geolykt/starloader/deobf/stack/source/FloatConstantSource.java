package de.geolykt.starloader.deobf.stack.source;

import org.objectweb.asm.tree.InsnNode;

public final class FloatConstantSource extends AbstractConstantSource<InsnNode, Float> {

    private final float value;

    public FloatConstantSource(InsnNode insn, float value) {
        super(insn, value);
        this.value = value;
    }

    public final float getFloatConstant() {
        return this.value;
    }
}
