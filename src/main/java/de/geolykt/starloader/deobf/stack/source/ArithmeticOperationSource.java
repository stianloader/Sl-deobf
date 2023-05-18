package de.geolykt.starloader.deobf.stack.source;

import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.InsnNode;

import de.geolykt.starloader.deobf.StackElement;

public final class ArithmeticOperationSource extends AbstractInsnSource<InsnNode> {

    public final StackElement leftHand;
    public final StackElement rightHand;

    public ArithmeticOperationSource(InsnNode insn, StackElement leftHand, StackElement rightHand) {
        super(insn);
        this.leftHand = leftHand;
        this.rightHand = rightHand;
    }

    @NotNull
    public String getOperandTypeString() {
        switch (super.insn.getOpcode()) {
        case Opcodes.FMUL:
        case Opcodes.FREM:
        case Opcodes.FADD:
        case Opcodes.FSUB:
        case Opcodes.FDIV:
            return "F";
        case Opcodes.DMUL:
        case Opcodes.DREM:
        case Opcodes.DADD:
        case Opcodes.DSUB:
        case Opcodes.DDIV:
            return "D";
        case Opcodes.LMUL:
        case Opcodes.LREM:
        case Opcodes.LADD:
        case Opcodes.LSUB:
        case Opcodes.LAND:
        case Opcodes.LOR:
        case Opcodes.LSHR:
        case Opcodes.LSHL:
        case Opcodes.LUSHR:
        case Opcodes.LXOR:
        case Opcodes.LDIV:
            return "L";
        case Opcodes.IMUL:
        case Opcodes.IDIV:
        case Opcodes.IREM:
        case Opcodes.IADD:
        case Opcodes.ISUB:
        case Opcodes.IAND:
        case Opcodes.IOR:
        case Opcodes.ISHR:
        case Opcodes.ISHL:
        case Opcodes.IUSHR:
        case Opcodes.IXOR:
            return "I";
        default:
            throw new IllegalStateException("Invalid opcode: " + this.insn.getOpcode());
        }
    }

    public int getOperandType() {
        switch (super.insn.getOpcode()) {
        case Opcodes.FMUL:
        case Opcodes.FREM:
        case Opcodes.FADD:
        case Opcodes.FSUB:
        case Opcodes.FDIV:
            return 'F';
        case Opcodes.DMUL:
        case Opcodes.DREM:
        case Opcodes.DADD:
        case Opcodes.DSUB:
        case Opcodes.DDIV:
            return 'D';
        case Opcodes.LMUL:
        case Opcodes.LREM:
        case Opcodes.LADD:
        case Opcodes.LSUB:
        case Opcodes.LAND:
        case Opcodes.LOR:
        case Opcodes.LSHR:
        case Opcodes.LSHL:
        case Opcodes.LUSHR:
        case Opcodes.LXOR:
        case Opcodes.LDIV:
            return 'L';
        case Opcodes.IMUL:
        case Opcodes.IDIV:
        case Opcodes.IREM:
        case Opcodes.IADD:
        case Opcodes.ISUB:
        case Opcodes.IAND:
        case Opcodes.IOR:
        case Opcodes.ISHR:
        case Opcodes.ISHL:
        case Opcodes.IUSHR:
        case Opcodes.IXOR:
            return 'I';
        default:
            throw new IllegalStateException("Invalid opcode: " + this.insn.getOpcode());
        }
    }
}
