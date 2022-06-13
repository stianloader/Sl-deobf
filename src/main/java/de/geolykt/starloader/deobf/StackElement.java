package de.geolykt.starloader.deobf;

import de.geolykt.starloader.deobf.stack.source.AbstractSource;
import de.geolykt.starloader.deobf.stack.source.UndefinedSource;

public class StackElement {

    @Deprecated
    public static final StackElement INVALID = new StackElement(UndefinedSource.INSTANCE, false);
    @Deprecated
    public static final StackElement INT = new StackElement(UndefinedSource.INSTANCE, "I");
    @Deprecated
    public static final StackElement LONG = new StackElement(UndefinedSource.INSTANCE, "J");
    @Deprecated
    public static final StackElement DOUBLE = new StackElement(UndefinedSource.INSTANCE, "D");
    @Deprecated
    public static final StackElement FLOAT = new StackElement(UndefinedSource.INSTANCE, "F");
    @Deprecated
    public static final StackElement CHAR = new StackElement(UndefinedSource.INSTANCE, "C");
    @Deprecated
    public static final StackElement BYTE = new StackElement(UndefinedSource.INSTANCE, "B");
    @Deprecated
    public static final StackElement SHORT = new StackElement(UndefinedSource.INSTANCE, "S");
    @Deprecated
    public static final StackElement BOOLEAN = new StackElement(UndefinedSource.INSTANCE, "Z");
    @Deprecated
    public static final StackElement OBJECT = new StackElement(UndefinedSource.INSTANCE, "Ljava/lang/Object;");
    @Deprecated
    public static final StackElement CLASS = new StackElement(UndefinedSource.INSTANCE, "Ljava/lang/Class;");
    @Deprecated
    public static final StackElement STRING = new StackElement(UndefinedSource.INSTANCE, "Ljava/lang/String;");
    @Deprecated
    public static final StackElement NULL = new StackElement(UndefinedSource.INSTANCE, "Ljava/lang/Object;", true);

    public final boolean isValid;
    public final boolean isNull;
    public final String type;
    public final AbstractSource source;

    private StackElement(AbstractSource source, boolean valid) {
        this.isValid = valid;
        this.isNull = false;
        this.type = null;
        this.source = source;
    }

    public StackElement(AbstractSource source, String type) {
        this(source, type, false);
    }

    public StackElement(AbstractSource source, String type, boolean isNull) {
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