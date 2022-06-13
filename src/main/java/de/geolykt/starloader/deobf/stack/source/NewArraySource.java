package de.geolykt.starloader.deobf.stack.source;

import org.objectweb.asm.tree.AbstractInsnNode;

import de.geolykt.starloader.deobf.StackElement;

public class NewArraySource extends AbstractSource {

    public final StackElement length;

    public NewArraySource(AbstractInsnNode insn, StackElement length) {
        super(insn);
        this.length = length;
    }
}
