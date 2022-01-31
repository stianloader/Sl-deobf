package de.geolykt.starloader.deobf;

import java.util.HashMap;
import java.util.Map;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

public class ClassWrapperPool {

    protected final ClassLoader loader;
    protected final Map<String, ClassNode> nodes;
    protected final Map<String, ClassWrapper> wrappers;

    public ClassWrapperPool(Map<String, ClassNode> nodes, ClassLoader cl) {
        this.nodes = nodes;
        this.wrappers = new HashMap<>();
        this.loader = cl;
    }

    public void addClassnode(ClassNode node) {
        this.nodes.put(node.name, node);
    }

    public boolean canAssign(ClassWrapper superType, ClassWrapper subType) {
        final String name = superType.getName();
        if (superType.isInterface()) {
            return isImplementingInterface(subType, name);
        } else {
            while (subType != null) {
                if (name.equals(subType.getName()) || name.equals(subType.getSuper())) {
                    return true;
                }
                if (subType.getName().equals("java/lang/Object")) {
                    return false;
                }
                subType = subType.getSuperWrapper();
            }
        }
        return false;
    }


    public ClassWrapper get(String className) {
        ClassWrapper wrapper = wrappers.get(className);
        if (wrapper != null) {
            return wrapper;
        }
        if (className.equals("java/lang/Object")) {
            wrapper = new ClassWrapper("java/lang/Object", null, new String[0], false, this);
            wrappers.put("java/lang/Object", wrapper);
            return wrapper;
        }
        ClassNode asmNode = nodes.get(className);
        if (asmNode == null) {
            Class<?> clazz;
            try {
                clazz = Class.forName(className.replace('/', '.'), false, loader);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Unable to resolve class: " + className, e);
            }
            boolean itf = clazz.isInterface();
            String superName;
            if (itf) {
                superName = "java/lang/Object";
            } else {
                superName = clazz.getSuperclass().getName().replace('.', '/');
            }
            Class<?>[] interfaces = clazz.getInterfaces();
            String[] superInterfaces = new String[interfaces.length];
            for (int i = 0; i < interfaces.length; i++) {
                superInterfaces[i] = interfaces[i].getName().replace('.', '/');
            }
            wrapper = new ClassWrapper(className, superName, superInterfaces, itf, this);
            wrappers.put(className, wrapper);
            return wrapper;
        } else {
            String[] superInterfaces = asmNode.interfaces.toArray(new String[0]);
            boolean itf = (asmNode.access & Opcodes.ACC_INTERFACE) != 0;
            wrapper = new ClassWrapper(className, asmNode.superName, superInterfaces, itf, this);
            wrappers.put(className, wrapper);
            return wrapper;
        }
    }

    public ClassWrapper getCommonSuperClass(ClassWrapper class1, ClassWrapper class2) {
        if (class1.getName().equals("java/lang/Object")) {
            return class1;
        }
        if (class2.getName().equals("java/lang/Object")) {
            return class2;
        }
        // isAssignableFrom = class1 = class2;
        if (canAssign(class1, class2)) {
            return class1;
        }
        if (canAssign(class2, class1)) {
            return class2;
        }
        if (class1.isInterface() || class2.isInterface()) {
            return get("java/lang/Object");
        }
        return getCommonSuperClass(class1, get(class2.getSuper()));
    }

    public boolean isImplementingInterface(ClassWrapper clazz, String interfaceName) {
        if (clazz.getName().equals("java/lang/Object")) {
            return false;
        }
        for (String interfaces : clazz.getSuperInterfacesName()) {
            if (interfaces.equals(interfaceName)) {
                return true;
            } else {
                if (isImplementingInterface(get(interfaces), interfaceName)) {
                    return true;
                }
            }
        }
        if (clazz.isInterface()) {
            return false;
        }
        return isImplementingInterface(clazz.getSuperWrapper(), interfaceName);
    }
}
