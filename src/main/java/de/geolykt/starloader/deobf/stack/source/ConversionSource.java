package de.geolykt.starloader.deobf.stack.source;

import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.InsnNode;

import de.geolykt.starloader.deobf.StackElement;

public final class ConversionSource extends AbstractInsnSource<InsnNode> {

    public final StackElement sourceElement;

    public ConversionSource(InsnNode insn, StackElement sourceElement) {
        super(insn);
        this.sourceElement = sourceElement;
    }

    @NotNull
    public String getSourceTypeString() {
        switch (this.insn.getOpcode()) {
        case Opcodes.D2F:
        case Opcodes.D2I:
        case Opcodes.D2L:
            return "D";
        case Opcodes.I2B:
        case Opcodes.I2C:
        case Opcodes.I2D:
        case Opcodes.I2F:
        case Opcodes.I2L:
        case Opcodes.I2S:
            return "I";
        case Opcodes.L2D:
        case Opcodes.L2F:
        case Opcodes.L2I:
            return "J";
        case Opcodes.F2D:
        case Opcodes.F2L:
        case Opcodes.F2I:
            return "F";
        default:
            throw new IllegalStateException("Invalid opcode: " + this.insn.getOpcode());
        }
    }

    public int getSourceType() {
        switch (this.insn.getOpcode()) {
        case Opcodes.D2F:
        case Opcodes.D2I:
        case Opcodes.D2L:
            return 'D';
        case Opcodes.I2B:
        case Opcodes.I2C:
        case Opcodes.I2D:
        case Opcodes.I2F:
        case Opcodes.I2L:
        case Opcodes.I2S:
            return 'I';
        case Opcodes.L2D:
        case Opcodes.L2F:
        case Opcodes.L2I:
            return 'J';
        case Opcodes.F2D:
        case Opcodes.F2L:
        case Opcodes.F2I:
            return 'F';
        default:
            throw new IllegalStateException("Invalid opcode: " + this.insn.getOpcode());
        }
    }

    @NotNull
    public String getTargetTypeString() {
        switch (this.insn.getOpcode()) {
        case Opcodes.D2F:
        case Opcodes.I2F:
        case Opcodes.L2F:
            return "F";
        case Opcodes.I2D:
        case Opcodes.F2D:
        case Opcodes.L2D:
            return "D";
        case Opcodes.D2I:
        case Opcodes.L2I:
        case Opcodes.F2I:
            return "I";
        case Opcodes.D2L:
        case Opcodes.I2L:
        case Opcodes.F2L:
            return "J";
        case Opcodes.I2B:
            return "B";
        case Opcodes.I2C:
            return "C";
        case Opcodes.I2S:
            return "S";
        default:
            throw new IllegalStateException("Invalid opcode: " + this.insn.getOpcode());
        }
    }

    public int getTargetType() {
        switch (this.insn.getOpcode()) {
        case Opcodes.D2F:
        case Opcodes.I2F:
        case Opcodes.L2F:
            return 'F';
        case Opcodes.I2D:
        case Opcodes.F2D:
        case Opcodes.L2D:
            return 'D';
        case Opcodes.D2I:
        case Opcodes.L2I:
        case Opcodes.F2I:
            return 'I';
        case Opcodes.D2L:
        case Opcodes.I2L:
        case Opcodes.F2L:
            return 'J';
        case Opcodes.I2B:
            return 'B';
        case Opcodes.I2C:
            return 'C';
        case Opcodes.I2S:
            return 'S';
        default:
            throw new IllegalStateException("Invalid opcode: " + this.insn.getOpcode());
        }
    }
}
