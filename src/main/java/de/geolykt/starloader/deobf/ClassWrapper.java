package de.geolykt.starloader.deobf;

import java.util.Arrays;

public class ClassWrapper {

    private final boolean itf;
    private final String name;
    private final ClassWrapperPool pool;
    private final String[] superInterfaces;
    private final String superName;

    protected ClassWrapper(String name, String superName, String[] superInterfaces, boolean isInterface, ClassWrapperPool pool) {
        this.name = name;
        this.pool = pool;
        if (name.equals("java/lang/Object")) {
            this.itf = false;
            this.superInterfaces = new String[0];
            this.superName = null;
        } else {
            this.superName = superName;
            this.superInterfaces = superInterfaces;
            this.itf = isInterface;
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ClassWrapper) {
            return ((ClassWrapper) obj).getName().equals(this.getName());
        }
        return false;
    }

    public String getName() {
        return name;
    }

    public String getSuper() {
        return superName;
    }

    public String[] getSuperInterfacesName() {
        return superInterfaces;
    }

    public ClassWrapper getSuperWrapper() {
        String superName = getSuper();
        if (superName == null) {
            throw new IllegalStateException(name + " does not have a super type.");
        }
        return pool.get(superName);
    }

    @Override
    public int hashCode() {
        return getName().hashCode();
    }

    public boolean isInterface() {
        return itf;
    }

    @Override
    public String toString() {
        return String.format("ClassWrapper[name=%s, itf=%b, extends=%s, implements=%s]", name, itf, superName, Arrays.toString(superInterfaces));
    }
}
