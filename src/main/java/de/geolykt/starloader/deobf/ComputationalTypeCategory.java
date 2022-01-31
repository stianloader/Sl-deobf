package de.geolykt.starloader.deobf;

/**
 * An enumeration of the two categories of computational types and helper methods.
 * These categories are defined in §2.11.1 of the JVMS of Java SE 17.
 */
public enum ComputationalTypeCategory {

    /**
     * Computational types of category 1 are int, float, returnAddress and
     * reference. They take up a single word.
     */
    CATEGORY_1,

    /**
     * Computational types of category 2 are long and double.
     * They take up two words.
     */
    CATEGORY_2;

    public static ComputationalTypeCategory parse(int codepoint) {
        if (codepoint == 'D' || codepoint == 'J') {
            return CATEGORY_2;
        } else {
            return CATEGORY_1;
        }
    }

    public static ComputationalTypeCategory parse(char c) {
        if (c == 'D' || c == 'J') {
            return CATEGORY_2;
        } else {
            return CATEGORY_1;
        }
    }

    public static ComputationalTypeCategory parse(String descString) {
        return parse(descString.codePointAt(0));
    }
}
