package de.geolykt.starloader.deobf.stack.source;

import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.tree.MethodInsnNode;

public class MethodReturnSource extends AbstractInsnSource<MethodInsnNode> {

    public MethodReturnSource(@NotNull MethodInsnNode insn) {
        super(insn);
    }
}
