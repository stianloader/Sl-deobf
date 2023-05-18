package de.geolykt.starloader.deobf;

import de.geolykt.starloader.deobf.stack.source.AbstractSource;
import de.geolykt.starloader.deobf.stack.source.InvalidSource;

public class StackElement {

    public static final StackElement INVALID_ELEMENT = new StackElement(InvalidSource.INSTANCE, false);

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