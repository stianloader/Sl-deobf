package de.geolykt.starloader.deobf.stack.source;

import org.objectweb.asm.tree.InsnNode;

import de.geolykt.starloader.deobf.StackElement;

public class NumberCompareSource extends AbstractInsnSource<InsnNode> {
    public final StackElement leftHand;
    public final StackElement rightHand;

    public NumberCompareSource(InsnNode insn, StackElement leftHand, StackElement rightHand) {
        super(insn);
        this.leftHand = leftHand;
        this.rightHand = rightHand;
    }
}
