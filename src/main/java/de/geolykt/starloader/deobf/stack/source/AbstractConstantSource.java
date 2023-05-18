package de.geolykt.starloader.deobf.stack.source;

import org.objectweb.asm.tree.AbstractInsnNode;

public abstract class AbstractConstantSource<T extends AbstractInsnNode, C> extends AbstractInsnSource<T> {

    private final C constant;

    public AbstractConstantSource(T insn, C constant) {
        super(insn);
        this.constant = constant;
    }

    public final C getConstant() {
        return this.constant;
    }
}
