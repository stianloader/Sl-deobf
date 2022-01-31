package de.geolykt.starloader.deobf.remapper;

import java.util.HashMap;
import java.util.Map;

import de.geolykt.starloader.deobf.FieldReference;

final class FieldRenameMap {

    private final Map<FieldReference, String> renames = new HashMap<>();

    public FieldRenameMap() {
    }

    public void put(String owner, String descriptor, String name, String newName) {
        renames.put(new FieldReference(owner, descriptor, name), newName);
    }

    public String get(String owner, String descriptor, String oldName) {
        return renames.get(new FieldReference(owner, descriptor, oldName));
    }

    public String getOrDefault(String owner, String descriptor, String oldName, String defaultValue) {
        return renames.getOrDefault(new FieldReference(owner, descriptor, oldName), defaultValue);
    }

    public String optGet(String owner, String descriptor, String oldName) {
        return renames.getOrDefault(new FieldReference(owner, descriptor, oldName), oldName);
    }

    public int size() {
        return renames.size();
    }

    public void clear() {
        renames.clear();
    }
}
