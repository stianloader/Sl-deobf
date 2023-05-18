package de.geolykt.starloader.deobf.stack.source;

import org.objectweb.asm.tree.InsnNode;

import de.geolykt.starloader.deobf.StackElement;

public class ArraylengthSource extends AbstractInsnSource<InsnNode> {

    public final StackElement array;

    public ArraylengthSource(InsnNode insn, StackElement array) {
        super(insn);
        this.array = array;
    }
}
