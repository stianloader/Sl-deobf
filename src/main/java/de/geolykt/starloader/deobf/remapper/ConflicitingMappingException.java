package de.geolykt.starloader.deobf.remapper;

/**
 * Exception that is thrown if {@link MethodRenameMap#put(String, String, String, String)} is fed an already
 * existing mapping for the method and the old new name is unequal to the new method.
 */
public class ConflicitingMappingException extends Exception {

    ConflicitingMappingException(String string) {
        super(string);
    }

    /**
     * serialVersionUID.
     */
    private static final long serialVersionUID = 526200690433380219L;
}
