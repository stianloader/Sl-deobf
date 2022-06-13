package de.geolykt.starloader.deobf.stack.source;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.tree.AbstractInsnNode;

@Deprecated
public class UndefinedSource extends AbstractSource {

    @Deprecated
    public static final UndefinedSource INSTANCE = new UndefinedSource();

    @SuppressWarnings("null")
    private UndefinedSource() {
        super((@NotNull AbstractInsnNode) (Object) null);
    }

    @Override
    @NotNull
    @Deprecated
    @Contract(pure = true, value = "-> null")
    public AbstractInsnNode getInsn() {
        return super.getInsn();
    }
}
