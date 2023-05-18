package de.geolykt.starloader.deobf.stack.source;

import org.objectweb.asm.tree.TypeInsnNode;

import de.geolykt.starloader.deobf.StackElement;

public final class InstanceofSource extends AbstractInsnSource<TypeInsnNode> {

    public final StackElement checkedElement;

    public InstanceofSource(TypeInsnNode insn, StackElement checkedElement) {
        super(insn);
        this.checkedElement = checkedElement;
    }
}
