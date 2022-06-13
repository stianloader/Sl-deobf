package de.geolykt.starloader.deobf.stack.source;

import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.tree.FrameNode;

public class FrameSource extends AbstractInsnSource<FrameNode> {

    public final int index;

    public FrameSource(@NotNull FrameNode insn, int index) {
        super(insn);
        this.index = index;
    }
}
