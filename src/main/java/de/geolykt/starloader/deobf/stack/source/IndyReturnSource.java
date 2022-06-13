package de.geolykt.starloader.deobf.stack.source;

import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;

public class IndyReturnSource extends AbstractInsnSource<InvokeDynamicInsnNode> {

    public IndyReturnSource(@NotNull InvokeDynamicInsnNode insn) {
        super(insn);
    }
}
