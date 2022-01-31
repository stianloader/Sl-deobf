package de.geolykt.starloader.deobf.remapper;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import de.geolykt.starloader.deobf.MethodReference;

public class MethodRenameMap {

    private final Map<MethodReference, String> renames = new HashMap<>();

    public MethodRenameMap() {
    }

    public void clear() {
        renames.clear();
    }

    public String get(String owner, String descriptor, String oldName) {
        return renames.get(new MethodReference(owner, descriptor, oldName));
    }

    public String getOrDefault(String owner, String descriptor, String oldName, String defaultValue) {
        return renames.getOrDefault(new MethodReference(owner, descriptor, oldName), defaultValue);
    }

    public String optGet(String owner, String descriptor, String oldName) {
        return renames.getOrDefault(new MethodReference(owner, descriptor, oldName), oldName);
    }

    public void put(String owner, String descriptor, String name, String newName) throws ConflicitingMappingException {
        MethodReference ref = new MethodReference(owner, descriptor, name);
        String oldMapping = renames.get(ref);
        if (oldMapping == null) {
            renames.put(ref, Objects.requireNonNull(newName, "newName cannot be null."));
        } else if (!oldMapping.equals(newName)) {
            throw new ConflicitingMappingException("Overriding method rename for method " + ref.toString());
        }
    }

    /**
     * Removes a method remapping entry from the method remapping list. This method practically undoes {@link MethodRenameMap#put(String, String, String, String)}.
     * Like put remove only affects a SINGLE method in a SINGLE class and it's references.
     * Note that implicitly declared/inherited methods must also be added to the remap list, sperately.
     *
     * @param owner The class of the method that should not be remapped
     * @param desc The descriptor of the method to not remap
     * @param name The name of the method that should not be remapped
     */
    public void remove(String owner, String desc, String name) {
        renames.remove(new MethodReference(owner, desc, name));
    }

    public int size() {
        return renames.size();
    }
}
