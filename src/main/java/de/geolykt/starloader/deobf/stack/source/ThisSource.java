package de.geolykt.starloader.deobf.stack.source;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Reference to "this", usually "ALOAD 0"/"ALOAD THIS".
 * Only applicable to non-static methods.
 */
public class ThisSource extends AbstractInsnSource<VarInsnNode> {

    public static final ThisSource GENERIC_INSTANCE = new ThisSource(new VarInsnNode(Opcodes.AALOAD, 0));

    public ThisSource(VarInsnNode insn) {
        super(insn);
    }
}
