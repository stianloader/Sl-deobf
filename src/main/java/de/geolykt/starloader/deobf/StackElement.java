package de.geolykt.starloader.deobf;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

public class StackElement {

    public static enum ElementSourceType {
        PARAMETER,
        METHOD_RETURN,
        INVOKE_DYNAMIC_RETURN,
        INSTRUCTION,
        UNDEFINED,
        FRAME,
        FIELD,
        THIS_REFERENCE,
        CASTED;
    }

    public static class ElementSource {
        public final Object data;
        public final ElementSourceType type;

        public ElementSource(ElementSourceType sourceType, Object data) {
            this.type = sourceType;
            this.data = data;
        }

        public static ElementSource ofParameter(int index) {
            return new ElementSource(ElementSourceType.PARAMETER, index);
        }

        public static ElementSource ofMethodReturn(MethodInsnNode method) {
            return new ElementSource(ElementSourceType.METHOD_RETURN, method);
        }

        public static ElementSource undefined() {
            return new ElementSource(ElementSourceType.UNDEFINED, null);
        }

        public static ElementSource ofFrame(FrameNode frame, int index) {
            return new ElementSource(ElementSourceType.FRAME, new Object[] {frame, index});
        }

        public static ElementSource ofInstruction(AbstractInsnNode insn) {
            return new ElementSource(ElementSourceType.INSTRUCTION, insn);
        }

        public static ElementSource ofInvokeDynamic(InvokeDynamicInsnNode insn) {
            return new ElementSource(ElementSourceType.INVOKE_DYNAMIC_RETURN, insn);
        }

        public static ElementSource ofField(FieldInsnNode insn) {
            return new ElementSource(ElementSourceType.FIELD, insn);
        }

        public static ElementSource thisRef() {
            return new ElementSource(ElementSourceType.THIS_REFERENCE, null);
        }

        public static ElementSource ofCast(AbstractInsnNode insn, StackElement beforeCast) {
            return new ElementSource(ElementSourceType.CASTED, new Object[] {insn, beforeCast});
        }

        public int getParameterIndex() {
            if (type != ElementSourceType.PARAMETER) {
                throw new IllegalStateException("Must be of PARAMETER type!");
            }
            return (Integer) data;
        }

        public MethodInsnNode getMethodInvocation() {
            if (type != ElementSourceType.METHOD_RETURN) {
                throw new IllegalStateException("Must be of METHOD_RETURN type!");
            }
            return (MethodInsnNode) data;
        }

        public AbstractInsnNode getMatchingInstruction() {
            if (type == ElementSourceType.FRAME) {
                return getFrameNode();
            }
            if (type == ElementSourceType.CASTED) {
                return (AbstractInsnNode) ((Object[]) data)[0];
            }
            if (type != ElementSourceType.INSTRUCTION
                    && type != ElementSourceType.METHOD_RETURN
                    && type != ElementSourceType.INVOKE_DYNAMIC_RETURN
                    && type != ElementSourceType.FIELD) {
                throw new IllegalStateException("Must be of INSTRUCTION, FRAME, INVOKE_DYNAMIC_RETURN, FIELD, CASTED or METHOD_RETURN type!");
            }
            return (AbstractInsnNode) data;
        }

        public int getFrameStackIndex() {
            if (type != ElementSourceType.FRAME) {
                throw new IllegalStateException("Must be of FRAME type!");
            }
            return (Integer) ((Object[]) data)[1];
        }

        public FrameNode getFrameNode() {
            if (type != ElementSourceType.FRAME) {
                throw new IllegalStateException("Must be of FRAME type!");
            }
            return (FrameNode) ((Object[]) data)[0];
        }

        public FieldInsnNode getFieldInsn() {
            if (type != ElementSourceType.FIELD) {
                throw new IllegalStateException("Must be of FIELD type!");
            }
            return (FieldInsnNode) data;
        }

        public StackElement uncast() {
            if (type != ElementSourceType.CASTED) {
                throw new IllegalStateException("Must be of CASTED type for this operation!");
            }
            StackElement elem = (StackElement) ((Object[]) data)[1];
            if (elem.source.type == ElementSourceType.CASTED) {
                return elem.source.uncast();
            }
            return elem;
        }
    }

    public static final StackElement INVALID = new StackElement(ElementSource.undefined(), false);
    public static final StackElement INT = new StackElement(ElementSource.undefined(), "I");
    public static final StackElement LONG = new StackElement(ElementSource.undefined(), "J");
    public static final StackElement DOUBLE = new StackElement(ElementSource.undefined(), "D");
    public static final StackElement FLOAT = new StackElement(ElementSource.undefined(), "F");
    public static final StackElement CHAR = new StackElement(ElementSource.undefined(), "C");
    public static final StackElement BYTE = new StackElement(ElementSource.undefined(), "B");
    public static final StackElement SHORT = new StackElement(ElementSource.undefined(), "S");
    public static final StackElement BOOLEAN = new StackElement(ElementSource.undefined(), "Z");
    public static final StackElement OBJECT = new StackElement(ElementSource.undefined(), "Ljava/lang/Object;");
    public static final StackElement CLASS = new StackElement(ElementSource.undefined(), "Ljava/lang/Class;");
    public static final StackElement STRING = new StackElement(ElementSource.undefined(), "Ljava/lang/String;");
    public static final StackElement NULL = new StackElement(ElementSource.undefined(), "Ljava/lang/Object;", true);

    public final boolean isValid;
    public final boolean isNull;
    public final String type;
    public final ElementSource source;

    public StackElement(ElementSource source, boolean valid) {
        this.isValid = valid;
        this.isNull = false;
        this.type = null;
        this.source = source;
    }

    public StackElement(ElementSource source, String type) {
        this.isValid = true;
        this.isNull = false;
        this.type = type;
        this.source = source;
    }

    public StackElement(ElementSource source, String type, boolean isNull) {
        this.isValid = true;
        this.type = type;
        this.isNull = isNull;
        this.source = source;
    }

    @Override
    public String toString() {
        if (!isValid) {
            return "invalid";
        } else if (isNull) {
            return "element_null";
        } else {
            return type;
        }
    }

    public ComputationalTypeCategory getComputationalTypeCategory() {
        if (isNull) {
            return ComputationalTypeCategory.CATEGORY_1; // Primitive doubles and longs cannot be null, so category1 is a safe bet.
        }
        if (!isValid) {
            return null;
        }
        return ComputationalTypeCategory.parse(type);
    }

    public boolean isPrimitive() {
        if (isNull || !isValid) {
            return false;
        }
        return type.codePointAt(0) != 'L' && type.codePointAt(0) != '[';
    }

    public boolean isArray() {
        if (!isValid || type == null && isNull) {
            return false;
        }
        return type.codePointAt(0) != '[';
    }
}