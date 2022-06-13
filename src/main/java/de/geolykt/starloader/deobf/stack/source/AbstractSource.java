package de.geolykt.starloader.deobf.stack.source;

import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.tree.AbstractInsnNode;

public abstract class AbstractSource {

    @NotNull
    public final AbstractInsnNode insn;

    @SuppressWarnings("null")
    AbstractSource(AbstractInsnNode insn) {
        this.insn = insn;
    }

    @NotNull
    public AbstractInsnNode getInsn() {
        return insn;
    }
}
