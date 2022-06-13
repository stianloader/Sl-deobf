package de.geolykt.starloader.deobf.stack.source;

import org.objectweb.asm.tree.InsnNode;

import de.geolykt.starloader.deobf.StackElement;

public class ArrayLoadSource extends AbstractInsnSource<InsnNode> {

    public final StackElement index;
    public final StackElement arrayref;

    public ArrayLoadSource(InsnNode insn, StackElement index, StackElement arrayRef) {
        super(insn);
        this.index = index;
        this.arrayref = arrayRef;
    }
}
