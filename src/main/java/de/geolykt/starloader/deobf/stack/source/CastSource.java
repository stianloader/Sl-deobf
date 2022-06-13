package de.geolykt.starloader.deobf.stack.source;

import org.objectweb.asm.tree.TypeInsnNode;

import de.geolykt.starloader.deobf.StackElement;

public class CastSource extends AbstractInsnSource<TypeInsnNode> {

    public final StackElement uncastElement;

    public CastSource(TypeInsnNode insn, StackElement oldElement) {
        super(insn);
        this.uncastElement = oldElement;
    }
}
