package de.geolykt.starloader.deobf;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ClassWrapper {

    private final boolean itf;
    private final String name;
    private final ClassWrapperPool pool;
    private final String[] superInterfaces;
    private final String superName;

    private Set<String> allInterfacesCache;

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

    /**
     * Obtains all interfaces this class implements.
     * This includes interfaces superclasses have implemented or super-interfaces of interfaces.
     *
     * <p>If this class is an interface, it also includes this class.
     *
     * @return A set of all interfaces implemented by this class or it's supers
     */
    public Set<String> getAllImplementatingInterfaces() {
        if (allInterfacesCache == null) {
            if (superName == null) {
                // Probably java/lang/Object
                allInterfacesCache = Collections.emptySet();
                return allInterfacesCache;
            }

            allInterfacesCache = new HashSet<>();
            for (String interfaceName : getSuperInterfacesName()) {
                allInterfacesCache.addAll(pool.get(interfaceName).getAllImplementatingInterfaces());
            }

            if (itf) {
                allInterfacesCache.add(name);
            } else {
                allInterfacesCache.addAll(pool.get(superName).getAllImplementatingInterfaces());
            }
        }
        return allInterfacesCache;
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
