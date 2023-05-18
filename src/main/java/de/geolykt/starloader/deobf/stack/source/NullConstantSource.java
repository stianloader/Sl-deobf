package de.geolykt.starloader.deobf.stack.source;

import org.objectweb.asm.tree.InsnNode;

public final class NullConstantSource extends AbstractConstantSource<InsnNode, Void> {
    public NullConstantSource(InsnNode insn) {
        super(insn, null);
    }
}
