package de.geolykt.starloader.deobf.stack.source;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.tree.AbstractInsnNode;

public class ParameterSource extends AbstractSource {

    public final int parameterIndex;

    public ParameterSource(int index) {
        super(null);
        this.parameterIndex = index;
    }

    @Override
    @NotNull
    @Deprecated
    @Contract(pure = true, value = "-> null")
    public AbstractInsnNode getInsn() {
        return super.getInsn();
    }
}
