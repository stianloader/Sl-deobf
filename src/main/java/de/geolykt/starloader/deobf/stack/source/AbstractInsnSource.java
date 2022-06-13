package de.geolykt.starloader.deobf.stack.source;

import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.tree.AbstractInsnNode;

abstract class AbstractInsnSource<T extends AbstractInsnNode> extends AbstractSource {

    public AbstractInsnSource(T insn) {
        super(insn);
    }

    @SuppressWarnings("unchecked")
    @Override
    @NotNull
    public final T getInsn() {
        return (T) insn;
    }
}
