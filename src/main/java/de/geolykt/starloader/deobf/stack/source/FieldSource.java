package de.geolykt.starloader.deobf.stack.source;

import org.objectweb.asm.tree.FieldInsnNode;

public class FieldSource extends AbstractInsnSource<FieldInsnNode> {

    public FieldSource(FieldInsnNode insn) {
        super(insn);
    }
}
