package de.geolykt.starloader.deobf.stack.source;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.tree.AbstractInsnNode;

public class InvalidSource extends AbstractSource {

    public static final InvalidSource INSTANCE = new InvalidSource();

    @SuppressWarnings("null")
    private InvalidSource() {
        super((@NotNull AbstractInsnNode) (Object) null);
    }

    @Override
    @NotNull
    @Contract(pure = true, value = "-> null")
    public AbstractInsnNode getInsn() {
        return super.getInsn();
    }
}
