package de.geolykt.starloader.deobf.stack.source;

import org.objectweb.asm.tree.AbstractInsnNode;

public class GenericInsnSource extends AbstractInsnSource<AbstractInsnNode> {

    public GenericInsnSource(AbstractInsnNode insn) {
        super(insn);
    }
}
