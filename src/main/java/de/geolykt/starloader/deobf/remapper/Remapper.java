package de.geolykt.starloader.deobf.remapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.ModuleNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.RecordComponentNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;

/**
 * Simple in-memory remapping engine. Unlike many other remappers it is able to take in already parsed
 * {@link org.objectweb.asm.tree.ClassNode Objectweb ASM Classnodes} as input and output them without having
 * to go through an intermediary store-to-file mode.
 * Additionally, this is a no-bullshit remapper. It will only remap, not change your Access flags,
 * LVT entries or anything like that. If you want a deobfuscator,
 * use {@link de.geolykt.starloader.deobf.Oaktree} after remapping.
 */
public final class Remapper {

    private final FieldRenameMap fieldRenames = new FieldRenameMap();
    private final FieldRenameMap hierarchisedFieldRenames = new FieldRenameMap();
    private final MethodRenameMap methodRenames = new MethodRenameMap();
    private final Map<String, ClassNode> nameToNode = new HashMap<>();
    private final Map<String, String> oldToNewClassName = new HashMap<>();
    private final List<ClassNode> targets = new ArrayList<>();

    private boolean fieldRenameHierarchyOutdated = false;

    /**
     * Adds a single class node to remap
     *
     * @param node The class node
     */
    public void addTarget(ClassNode node) {
        if (nameToNode.put(node.name, node) != null) {
            throw new IllegalStateException("The same class node was registered more than twice.");
        }
        targets.add(node);
    }

    /**
     * Adds multiple class nodes to remap
     *
     * @param nodes The class nodes
     */
    public void addTargets(Collection<ClassNode> nodes) {
        targets.addAll(Objects.requireNonNull(nodes, "Cannot add a null class node list to the target pool."));
        nodes.forEach(node -> {
            if (nameToNode.put(node.name, node) != null) {
                throw new IllegalStateException("The same class node was registered more than twice.");
            }
        });
    }

    public void clearTargets() {
        targets.clear();
        nameToNode.clear();
    }

    private void createFieldHierarchy() {
        hierarchisedFieldRenames.clear();
        Map<String, Set<String>> children = new HashMap<>();
        for (ClassNode node : targets) {
            children.compute(node.superName, (k, v) -> {
                if (v == null) {
                    v = new HashSet<>();
                }
                v.add(node.name);
                return v;
            });
        }
        boolean modified;
        do {
            modified = false;
            for (ClassNode node : targets) {
                Set<String> childNodes = children.get(node.name);
                if (childNodes == null) {
                    continue;
                }
                Set<String> superChildNodes = children.get(node.superName);
                modified |= superChildNodes.addAll(childNodes);
            }
        } while (modified);
        for (ClassNode node : targets) {
            for (FieldNode field : node.fields) {
                String newName = fieldRenames.get(node.name, field.desc, field.name);
                if (newName == null) {
                    continue;
                }
                hierarchisedFieldRenames.put(node.name, field.desc, field.name, newName);
                Set<String> childNodes = children.get(node.name);
                if (childNodes == null) {
                    continue;
                }
                // Apparently ACC_STATIC does not affect the propagation rules for fields
                // I fear that this might lead to a few issues, but what can one do against that?
                if ((field.access & Opcodes.ACC_PROTECTED) != 0 || ((field.access) & Opcodes.ACC_PUBLIC) != 0) {
                    for (String child : childNodes) {
                        hierarchisedFieldRenames.put(child, field.desc, field.name, newName);
                    }
                } else if ((field.access & Opcodes.ACC_PRIVATE) == 0) {
                    // Package-protected
                    final int lastIndexOfSlash = node.name.lastIndexOf('/');
                    String packageName;
                    if (lastIndexOfSlash == -1) {
                        // Node is in the root/default package
                        packageName = null;
                    } else {
                        packageName = node.name.substring(0, lastIndexOfSlash);
                    }
                    for (String child : childNodes) {
                        if (child.length() <= lastIndexOfSlash) {
                            continue;
                        }
                        if (packageName == null) {
                            // Check if the class is in the root/default package.
                            if (child.indexOf('/') != -1) {
                                continue; // And it was not
                            }
                        } else {
                            if (child.codePointAt(lastIndexOfSlash) != '/'
                                    || child.indexOf('/', lastIndexOfSlash + 1) != -1
                                    || !child.startsWith(packageName)) {
                                continue;
                            }
                        }
                        hierarchisedFieldRenames.put(child, field.desc, field.name, newName);
                    }
                }
            }
        }
        hierarchisedFieldRenames.putAllIfAbsent(fieldRenames);
    }

    /**
     * Fixes remapped {@link InnerClassNode} by remapping any child classes alongside their parent class,
     * even if only the parent class was remapped. Due to the potentially destructive properties of this action,
     * this method must be explicitly invoked for it to do anything.
     *
     * <p>More specifically if the class "OuterClass" is remapped to "RootClass", but "OuterClass$Inner" is not remapped,
     * then after {@link #process()} the classes "RootClass" and "OuterClass$Inner" will exist. As the names contradict
     * the relation given by their {@link InnerClassNode}, most decompilers will discard the {@link InnerClassNode}.
     *
     * <p>To fix this issue, this method will look for such changes and renames the inner classes accordingly.
     *
     * <p>This method will only do anything if it is called before {@link #process()} and will modify the internal
     * collection of remapped classes.
     *
     * <p>The more {@link ClassNode ClassNodes} there are, the more efficient this method is at doing what it should do.
     *
     * <p>This method can be destructive as it will not check for collisions.
     *
     * @param sharedBuilder A shared {@link StringBuilder} to reduce memory consumption created by string operations
     * @return A map that contains ALL additional mappings where as the key is the old name and the value the new name.
     * @author Geolykt
     */
    public Map<String, String> fixICNNames(StringBuilder sharedBuilder) {
        Map<String, List<String>> outerToInner = new HashMap<>();
        for (ClassNode node : targets) {
            int lastIndexOfDollar = node.name.lastIndexOf('$');
            if (lastIndexOfDollar != -1) {
                String outerName = node.name.substring(0, lastIndexOfDollar);
                outerToInner.compute(outerName, (key, oldVal) -> {
                    if (oldVal == null) {
                        oldVal = new ArrayList<>();
                    }
                    oldVal.add(node.name);
                    return oldVal;
                });
            }
        }

        // To prevent ConcurrentModificationException
        Map<String, String> additions = new HashMap<>();
        Map<String, String> allAdditions = new HashMap<>();

        while (!outerToInner.isEmpty()) {

            oldToNewClassName.forEach((oldName, newName) -> {
                List<String> innerClasses = outerToInner.remove(oldName);
                if (innerClasses != null) {
                    for (String inner : innerClasses) {
                        if (oldToNewClassName.containsKey(inner)) {
                            continue;
                        }
                        int seperatorPos = inner.lastIndexOf('$');
                        sharedBuilder.setLength(0);
                        sharedBuilder.append(newName);
                        sharedBuilder.append(inner, seperatorPos, inner.length());
                        String newInnerName = sharedBuilder.toString();
                        additions.put(inner, newInnerName);
                    }
                }
            });

            if (additions.isEmpty()) {
                // We are done here
                break;
            }
            oldToNewClassName.putAll(additions);
            allAdditions.putAll(additions);
            additions.clear();
        }

        return allAdditions;
    }

    /**
     * Note: due to the circumstances of how the remapper works, this method call may be not required as the remapper
     * remaps the input ClassNodes without cloning them in any capacity.
     *
     * @return Returns the targets
     */
    public List<ClassNode> getOutput() {
        return targets;
    }

    /**
     * Remaps a class name.
     *
     * @param name The old (unmapped) class name
     * @return The new (remapped) class name
     */
    @NotNull
    public String getRemappedClassName(@NotNull String name) {
        String s = oldToNewClassName.get(name);
        if (s == null) {
            return name;
        }
        return s;
    }

    /**
     * Remaps a field descriptor.
     *
     * @param fieldDesc The old (unmapped) field descriptor
     * @param sharedBuilder A shared cached string builder. The contents of the string builder are wiped and after the invocation the contents are undefined
     * @return The new (remapped) field descriptor. It <b>can</b> be identity identical to the "fieldDesc" if it didn't need to be altered
     */
    @SuppressWarnings("null")
    @NotNull
    public String getRemappedFieldDescriptor(@NotNull String fieldDesc, @NotNull StringBuilder sharedBuilder) {
        sharedBuilder.setLength(0);
        return remapSingleDesc(fieldDesc, sharedBuilder);
    }

    /**
     * Remaps a field name.
     *
     * @param owner The old (unmapped) class name
     * @param name The old (unmapped) field name
     * @param desc The old (unmapped) field descriptor
     * @return The new (remapped) field name
     * @since 0.1.0
     */
    @NotNull
    public String getRemappedFieldName(@NotNull String owner, @NotNull String name, @NotNull String desc) {
        if (this.fieldRenameHierarchyOutdated) {
            createFieldHierarchy();
            this.fieldRenameHierarchyOutdated = false;
        }
        String s = this.hierarchisedFieldRenames.get(owner, name, desc);
        if (s == null) {
            return name;
        }
        return s;
    }

    /**
     * Remaps a method descriptor.
     *
     * @param methodDesc The old (unmapped) method descriptor
     * @param sharedBuilder A shared cached string builder. The contents of the string builder are wiped and after the invocation the contents are undefined
     * @return The new (remapped) method descriptor. It <b>can</b> be identity identical to the "methodDesc" if it didn't need to be altered
     */
    @NotNull
    public String getRemappedMethodDescriptor(@NotNull String methodDesc, @NotNull StringBuilder sharedBuilder) {
        sharedBuilder.setLength(0);
        if (!remapSignature(methodDesc, sharedBuilder)) {
            return methodDesc;
        }
        return sharedBuilder.toString();
    }

    /**
     * Remaps a method name.
     *
     * @param owner The old (unmapped) class name
     * @param name The old (unmapped) method name
     * @param desc The old (unmapped) method descriptor
     * @return The new (remapped) method name
     * @since 0.1.0
     */
    @NotNull
    public String getRemappedMethodName(@NotNull String owner, @NotNull String name, @NotNull String desc) {
        String s = this.methodRenames.get(owner, name, desc);
        if (s == null) {
            return name;
        }
        return s;
    }

    /**
     * Processes all remap orders and clears the remap orders afterwards. The classes that need to be processed remain in the targets
     * list until {@link #clearTargets()} is invoked. This allows for reusability of the same remapper instance.
     * Class names are remapped last.
     */
    public void process() {
        StringBuilder sharedStringBuilder = new StringBuilder();

        if (fieldRenameHierarchyOutdated) {
            createFieldHierarchy();
            fieldRenameHierarchyOutdated = false;
        }

        IdentityHashMap<ModuleNode, Boolean> remappedModules = new IdentityHashMap<>();
        for (ClassNode node : targets) {
            for (FieldNode field : node.fields) {
                remapField(node.name, field, sharedStringBuilder);
            }
            for (InnerClassNode innerClass : node.innerClasses) {
                // TODO: Should we also remap the inner names?
                String newOuterName = oldToNewClassName.get(innerClass.outerName);
                if (newOuterName != null) {
                    innerClass.outerName = newOuterName;
                }
                String newName = oldToNewClassName.get(innerClass.name);
                if (newName != null) {
                    innerClass.name = newName;
                }
            }
            for (int i = 0; i < node.interfaces.size(); i++) {
                String newInterfaceName = oldToNewClassName.get(node.interfaces.get(i));
                if (newInterfaceName != null) {
                    node.interfaces.set(i, newInterfaceName);
                }
            }
            remapAnnotations(node.invisibleTypeAnnotations, sharedStringBuilder);
            remapAnnotations(node.invisibleAnnotations, sharedStringBuilder);
            remapAnnotations(node.visibleTypeAnnotations, sharedStringBuilder);
            remapAnnotations(node.visibleAnnotations, sharedStringBuilder);
            for (MethodNode method : node.methods) {
                remapMethod(node, method, sharedStringBuilder);
            }
            ModuleNode module = node.module;
            if (module != null) {
                Boolean boole = remappedModules.get(module);
                if (boole == null) {
                    remappedModules.put(module, Boolean.TRUE);
                    remapModule(module, sharedStringBuilder);
                }
            }
            if (node.nestHostClass != null) {
                node.nestHostClass = remapInternalName(node.nestHostClass, sharedStringBuilder);
            }
            if (node.nestMembers != null) {
                int size = node.nestMembers.size();
                for (int i = 0; i < size; i++) {
                    String member = node.nestMembers.get(i);
                    String remapped = remapInternalName(member, sharedStringBuilder);
                    if (member != remapped) {
                        node.nestMembers.set(i, remapped);
                    }
                }
            }
            if (node.outerClass != null) {
                if (node.outerMethod != null && node.outerMethodDesc != null) {
                    node.outerMethod = methodRenames.optGet(node.outerClass, node.outerMethodDesc, node.outerMethod);
                }
                node.outerClass = remapInternalName(node.outerClass, sharedStringBuilder);
            }
            if (node.outerMethodDesc != null) {
                sharedStringBuilder.setLength(0);
                if (remapSignature(node.outerMethodDesc, sharedStringBuilder)) {
                    node.outerMethodDesc = sharedStringBuilder.toString();
                }
            }
            if (node.permittedSubclasses != null) {
                int size = node.permittedSubclasses.size();
                for (int i = 0; i < size; i++) {
                    String member = node.permittedSubclasses.get(i);
                    String remapped = remapInternalName(member, sharedStringBuilder);
                    if (member != remapped) {
                        node.permittedSubclasses.set(i, remapped);
                    }
                }
            }
            if (node.recordComponents != null) {
                // This requires eventual testing as I do not make use of codesets with Java9+ features.
                for (RecordComponentNode record : node.recordComponents) {
                    sharedStringBuilder.setLength(0);
                    if (remapSignature(record.descriptor, sharedStringBuilder)) {
                        record.descriptor = sharedStringBuilder.toString();
                    }
                    remapAnnotations(record.invisibleAnnotations, sharedStringBuilder);
                    remapAnnotations(record.invisibleTypeAnnotations, sharedStringBuilder);
                    remapAnnotations(record.visibleAnnotations, sharedStringBuilder);
                    remapAnnotations(record.visibleTypeAnnotations, sharedStringBuilder);
                    if (record.signature != null) {
                        sharedStringBuilder.setLength(0);
                        if (remapSignature(record.signature, sharedStringBuilder)) {
                            record.signature = sharedStringBuilder.toString();
                        }
                    }
                }
            }
            if (node.signature != null) {
                sharedStringBuilder.setLength(0);
                // Class signatures are formatted differently than method or field signatures, but we can just ignore this
                // caveat here as the method will consider the invalid tokens are primitive objects. (sometimes laziness pays off)
                if (remapSignature(node.signature, sharedStringBuilder)) {
                    node.signature = sharedStringBuilder.toString();
                }
            }
            if (node.superName != null) {
                node.superName = remapInternalName(node.superName, sharedStringBuilder);
            }
            // remap the node's name if required
            String newName = oldToNewClassName.get(node.name);
            if (newName == null) {
                continue;
            }
            nameToNode.remove(node.name);
            node.name = newName;
            nameToNode.put(node.name, node);
        }
        oldToNewClassName.clear();
    }

    private void remapAnnotation(AnnotationNode annotation, StringBuilder sharedStringBuilder) {
        String internalName = annotation.desc.substring(1, annotation.desc.length() - 1);
        String newInternalName = oldToNewClassName.get(internalName);
        if (newInternalName != null) {
            annotation.desc = 'L' + newInternalName + ';';
        }
        if (annotation.values != null) {
            int size = annotation.values.size();
            for (int i = 0; i < size; i++) {
                @SuppressWarnings("unused") // We are using the cast as a kind of built-in automatic unit test
                String bitvoid = (String) annotation.values.get(i++);
                remapAnnotationValue(annotation.values.get(i), i, annotation.values, sharedStringBuilder);
            }
        }
    }

    private void remapAnnotations(List<? extends AnnotationNode> annotations, StringBuilder sharedStringBuilder) {
        if (annotations == null) {
            return;
        }
        for (AnnotationNode annotation : annotations) {
            remapAnnotation(annotation, sharedStringBuilder);
        }
    }

    private void remapAnnotationValue(Object value, int index, List<Object> values, StringBuilder sharedStringBuilder) {
        if (value instanceof Type) {
            String type = ((Type) value).getDescriptor();
            sharedStringBuilder.setLength(0);
            if (remapSignature(type, sharedStringBuilder)) {
                values.set(index, Type.getType(sharedStringBuilder.toString()));
            }
        } else if (value instanceof String[]) {
            String[] enumvals = (String[]) value;
            String internalName = enumvals[0].substring(1, enumvals[0].length() - 1);
            enumvals[1] = hierarchisedFieldRenames.optGet(internalName, enumvals[0], enumvals[1]);
            String newInternalName = oldToNewClassName.get(internalName);
            if (newInternalName != null) {
                enumvals[0] = 'L' + newInternalName + ';';
            }
        } else if (value instanceof AnnotationNode) {
            remapAnnotation((AnnotationNode) value, sharedStringBuilder);
        } else if (value instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> valueList = (List<Object>) value;
            int size = valueList.size();
            for (int i = 0; i < size; i++) {
                remapAnnotationValue(valueList.get(i), i, valueList, sharedStringBuilder);
            }
        } else {
            // Irrelevant
        }
    }

    private void remapBSMArg(final Object[] bsmArgs, final int index, final StringBuilder sharedStringBuilder) {
        Object bsmArg = bsmArgs[index];
        if (bsmArg instanceof Type) {
            Type type = (Type) bsmArg;
            sharedStringBuilder.setLength(0);

            if (type.getSort() == Type.METHOD) {
                if (remapSignature(type.getDescriptor(), sharedStringBuilder)) {
                    bsmArgs[index] = Type.getMethodType(sharedStringBuilder.toString());
                }
            } else if (type.getSort() == Type.OBJECT) {
                String oldVal = type.getInternalName();
                String remappedVal = remapInternalName(oldVal, sharedStringBuilder);
                if (oldVal != remappedVal) { // Instance comparison intended
                    bsmArgs[index] = Type.getObjectType(remappedVal);
                }
            } else {
                throw new IllegalArgumentException("Unexpected bsm arg Type sort. Sort = " + type.getSort() + "; type = " + type);
            }
        } else if (bsmArg instanceof Handle) {
            Handle handle = (Handle) bsmArg;
            String oldName = handle.getName();
            String hOwner = handle.getOwner();
            String newName = methodRenames.optGet(hOwner, handle.getDesc(), oldName);
            String newOwner = oldToNewClassName.get(hOwner);
            boolean modified = oldName != newName;
            if (newOwner != null) {
                hOwner = newOwner;
                modified = true;
            }
            String desc = handle.getDesc();
            sharedStringBuilder.setLength(0);
            if (remapSignature(desc, sharedStringBuilder)) {
                desc = sharedStringBuilder.toString();
                modified = true;
            }
            if (modified) {
                bsmArgs[index] = new Handle(handle.getTag(), hOwner, newName, desc, handle.isInterface());
            }
        } else if (bsmArg instanceof String) {
            // Do nothing. I'm kind of surprised that I built this method modular enough that this was a straightforward fix
        } else {
            throw new IllegalArgumentException("Unexpected bsm arg class at index " + index + " for " + Arrays.toString(bsmArgs) + ". Class is " + bsmArg.getClass().getName());
        }
    }

    /**
     * Remaps the name of all target class nodes once {@link #process()} is called.
     * Class names are remapped alongside method / fields however technically they are remapped last.
     *
     * @param oldName The old internal class name of the class to remap
     * @param newName The new internal class name of the class to remap
     */
    public void remapClassName(String oldName, String newName) {
        oldToNewClassName.put(oldName, newName);
    }

    /**
     * Remaps the name of all target class nodes once {@link #process()} is called.
     * Class names are remapped alongside method / fields however technically they are remapped last.
     * This is a bulk method. And is similar to calling {@link #remapClassName(String, String)}.
     *
     * @param mappings A map that represents all the class remaps. Keys are old names, values are new names.
     */
    public void remapClassNames(Map<String, String> mappings) {
        oldToNewClassName.putAll(mappings);
    }

    private void remapField(String owner, FieldNode field, StringBuilder sharedStringBuilder) {
        field.name = hierarchisedFieldRenames.optGet(owner, field.desc, field.name);

        int typeType = field.desc.charAt(0);
        if (typeType == '[' || typeType == 'L') {
            // Remap descriptor
            sharedStringBuilder.setLength(0);
            field.desc = remapSingleDesc(field.desc, sharedStringBuilder);
            // Remap signature
            if (field.signature != null) {
                sharedStringBuilder.setLength(0);
                if (remapSignature(field.signature, sharedStringBuilder)) {
                    field.signature = sharedStringBuilder.toString();
                }
            }
        }
    }

    /**
     * Inserts a field renaming entry to the remapping list.
     * The owner and desc strings must be valid for the current class names, i. e. without {@link #remapClassName(String, String)}
     * and {@link #process()} applied. The fields are not actually renamed until {@link #process()} is invoked.
     * These tasks are however removed as soon as {@link #process()} is invoked.
     * Unlike {@link #remapMethod(String, String, String, String)}, this method will do it's best to
     * propagate field renames to child classes. However it may miss a few if not all classes are declared as targets.
     * However one would still be able to explicitly add a remap via this method.
     *
     * @param owner The internal name of the current owner of the field
     * @param desc The descriptor string of the field entry
     * @param oldName The old name of the field
     * @param newName The new name of the field
     * @see Type#getInternalName()
     */
    public void remapField(String owner, String desc, String oldName, String newName) {
        fieldRenames.put(owner, desc, oldName, newName);
        fieldRenameHierarchyOutdated = true;
    }

    private void remapFrameNode(FrameNode frameNode, StringBuilder sharedStringBuilder) {
        if (frameNode.stack != null) {
            int size = frameNode.stack.size();
            for (int i = 0; i < size; i++) {
                Object o = frameNode.stack.get(i);
                if (o instanceof String) {
                    String oldName = (String) o;
                    String newName = remapInternalName(oldName, sharedStringBuilder);
                    if (oldName != newName) { // instance comparision intended
                        frameNode.stack.set(i, newName);
                    }
                }
            }
        }
        if (frameNode.local != null) {
            int size = frameNode.local.size();
            for (int i = 0; i < size; i++) {
                Object o = frameNode.local.get(i);
                if (o instanceof String) {
                    String oldName = (String) o;
                    String newName = remapInternalName(oldName, sharedStringBuilder);
                    if (oldName != newName) { // instance comparision intended
                        frameNode.local.set(i, newName);
                    }
                }
            }
        }
    }

    private String remapInternalName(String internalName, StringBuilder sharedStringBuilder) {
        if (internalName.codePointAt(0) == '[') {
            return remapSingleDesc(internalName, sharedStringBuilder);
        } else {
            String remapped = oldToNewClassName.get(internalName);
            if (remapped != null) {
                return remapped;
            }
            return internalName;
        }
    }

    private void remapMethod(ClassNode owner, MethodNode method, StringBuilder sharedStringBuilder) {
        method.name = methodRenames.optGet(owner.name, method.desc, method.name);
        for (int i = 0; i < method.exceptions.size(); i++) {
            String newExceptionName = oldToNewClassName.get(method.exceptions.get(i));
            if (newExceptionName != null) {
                method.exceptions.set(i, newExceptionName);
            }
        }
        remapAnnotations(method.invisibleTypeAnnotations, sharedStringBuilder);
        remapAnnotations(method.invisibleLocalVariableAnnotations, sharedStringBuilder);
        remapAnnotations(method.invisibleAnnotations, sharedStringBuilder);
        remapAnnotations(method.visibleAnnotations, sharedStringBuilder);
        remapAnnotations(method.visibleTypeAnnotations, sharedStringBuilder);
        remapAnnotations(method.visibleLocalVariableAnnotations, sharedStringBuilder);
        if (method.invisibleParameterAnnotations != null) {
            for (List<AnnotationNode> annotations : method.invisibleParameterAnnotations) {
                remapAnnotations(annotations, sharedStringBuilder);
            }
        }
        if (method.localVariables != null) {
            for (LocalVariableNode lvn : method.localVariables) {
                int typeType = lvn.desc.charAt(0);
                boolean isObjectArray = typeType == '[';
                int arrayDimension = 0;
                if (isObjectArray) {
                    if (lvn.desc.codePointBefore(lvn.desc.length()) == ';') {
                        // calculate depth
                        int arrayType;
                        do {
                            arrayType = lvn.desc.charAt(++arrayDimension);
                        } while (arrayType == '[');
                    } else {
                        isObjectArray = false;
                    }
                }
                if (isObjectArray || typeType == 'L') {
                    // Remap descriptor
                    Type type = Type.getType(lvn.desc);
                    String internalName = type.getInternalName();
                    String newInternalName = oldToNewClassName.get(internalName);
                    if (newInternalName != null) {
                        if (isObjectArray) {
                            sharedStringBuilder.setLength(arrayDimension);
                            for (int i = 0; i < arrayDimension; i++) {
                                sharedStringBuilder.setCharAt(i, '[');
                            }
                            sharedStringBuilder.append(newInternalName);
                            sharedStringBuilder.append(';');
                            lvn.desc = sharedStringBuilder.toString();
                        } else {
                            lvn.desc = 'L' + newInternalName + ';';
                        }
                    }
                    if (lvn.signature != null) {
                        sharedStringBuilder.setLength(0);
                        if (remapSignature(lvn.signature, sharedStringBuilder)) {
                            lvn.signature = sharedStringBuilder.toString();
                        }
                    }
                }
            }
        }
        for (TryCatchBlockNode catchBlock : method.tryCatchBlocks) {
            if (catchBlock.type != null) {
                String newName = oldToNewClassName.get(catchBlock.type);
                if (newName != null) {
                    catchBlock.type = newName;
                }
            }
            remapAnnotations(catchBlock.visibleTypeAnnotations, sharedStringBuilder);
            remapAnnotations(catchBlock.invisibleTypeAnnotations, sharedStringBuilder);
        }
        sharedStringBuilder.setLength(0);
        if (remapSignature(method.desc, sharedStringBuilder)) {
            // The field signature and method desc system are similar enough that this works;
            method.desc = sharedStringBuilder.toString();
        }
        if (method.signature != null) {
            sharedStringBuilder.setLength(0);
            if (remapSignature(method.signature, sharedStringBuilder)) {
                // Method signature and field signature are also similar enough
                method.signature = sharedStringBuilder.toString();
            }
        }
        if (method.annotationDefault != null && !(method.annotationDefault instanceof Number)) {
            // Little cheat to avoid writing the same code twice :)
            List<Object> annotationList = Arrays.asList(method.annotationDefault);
            remapAnnotationValue(method.annotationDefault, 0, annotationList, sharedStringBuilder);
            method.annotationDefault = annotationList.get(0);
        }
        InsnList instructions = method.instructions;
        if (instructions != null && instructions.size() != 0) {
            AbstractInsnNode insn = instructions.getFirst();
            while (insn != null) {
                if (insn instanceof FieldInsnNode) {
                    FieldInsnNode instruction = (FieldInsnNode) insn;
                    String fieldName = hierarchisedFieldRenames.get(instruction.owner, instruction.desc, instruction.name);
                    if (fieldName != null) {
                        instruction.name = fieldName;
                    }
                    instruction.desc = remapSingleDesc(instruction.desc, sharedStringBuilder);
                    instruction.owner = remapInternalName(instruction.owner, sharedStringBuilder);
                } else if (insn instanceof FrameNode) {
                    remapFrameNode((FrameNode) insn, sharedStringBuilder);
                } else if (insn instanceof InvokeDynamicInsnNode) {
                    InvokeDynamicInsnNode specialisedInsn = (InvokeDynamicInsnNode) insn;
                    Object[] bsmArgs = specialisedInsn.bsmArgs;
                    int arglen = bsmArgs.length;
                    for (int i = 0; i < arglen; i++) {
                        remapBSMArg(bsmArgs, i, sharedStringBuilder);
                    }
                    sharedStringBuilder.setLength(0);
                    if (remapSignature(specialisedInsn.desc, sharedStringBuilder)) {
                        specialisedInsn.desc = sharedStringBuilder.toString();
                    }
                } else if (insn instanceof LdcInsnNode) {
                    LdcInsnNode specialisedInsn = (LdcInsnNode) insn;
                    if (specialisedInsn.cst instanceof Type) {
                        String descString = ((Type) specialisedInsn.cst).getDescriptor();
                        String newDescString = remapSingleDesc(descString, sharedStringBuilder);
                        if (descString != newDescString) {
                            specialisedInsn.cst = Type.getType(newDescString);
                        }
                    }
                } else if (insn instanceof MethodInsnNode) {
                    MethodInsnNode instruction = (MethodInsnNode) insn;
                    boolean isArray = instruction.owner.codePointAt(0) == '[';
                    if (!isArray) { // Javac sometimes invokes methods on array objects
                        instruction.name = methodRenames.optGet(instruction.owner, instruction.desc, instruction.name);
                        String newOwner = oldToNewClassName.get(instruction.owner);
                        if (newOwner != null) {
                            instruction.owner = newOwner;
                        }
                    } else {
                        sharedStringBuilder.setLength(0);
                        instruction.owner = remapSingleDesc(instruction.owner, sharedStringBuilder);
                    }
                    sharedStringBuilder.setLength(0);
                    if (remapSignature(instruction.desc, sharedStringBuilder)) {
                        instruction.desc = sharedStringBuilder.toString();
                    }
                } else if (insn instanceof MultiANewArrayInsnNode) {
                    MultiANewArrayInsnNode instruction = (MultiANewArrayInsnNode) insn;
                    instruction.desc = remapSingleDesc(instruction.desc, sharedStringBuilder);
                } else if (insn instanceof TypeInsnNode) {
                    TypeInsnNode instruction = (TypeInsnNode) insn;
                    instruction.desc = remapInternalName(instruction.desc, sharedStringBuilder);
                }
                insn = insn.getNext();
            }
        }
    }

    /**
     * Inserts a method renaming entry to the remapping list.
     * The owner and desc strings must be valid for the current class names, i. e. without {@link #remapClassName(String, String)}
     * and {@link #process()} applied. The method are not actually renamed until {@link #process()} is invoked.
     *<p>
     * <b>WARNING: if the method is non-static and non-private and the owning class non-final then it is recommended that the change is
     * propagated through the entire tree. Renaming a method will only affect one class, not multiple - which may void
     * overrides or other similar behaviours.</b>
     *
     * @param owner The internal name of the current owner of the method
     * @param desc The descriptor string of the method entry
     * @param oldName The old name of the method
     * @param newName The new name of the method
     * @see Type#getInternalName()
     * @throws ConflicitingMappingException If a mapping error occurs.
     */
    public void remapMethod(String owner, String desc, String oldName, String newName) throws ConflicitingMappingException {
        methodRenames.put(owner, desc, oldName, newName);
    }

    private void remapModule(ModuleNode module, StringBuilder sharedStringBuilder) {
        // This is really stupid design
        if (module.mainClass != null) {
            String newMainClass = oldToNewClassName.get(module.mainClass);
            if (newMainClass != null) {
                module.mainClass = newMainClass;
            }
        }
        if (module.uses != null) {
            int size = module.uses.size();
            for (int i = 0; i < size; i++) {
                String service = module.uses.get(i);
                String remapped = remapInternalName(service, sharedStringBuilder);
                if (remapped != service) {
                    module.uses.set(i, remapped);
                }
            }
        }
    }

    private boolean remapSignature(String signature, StringBuilder out) {
        return remapSignature(out, signature, 0, signature.length());
    }

    private boolean remapSignature(StringBuilder signatureOut, String signature, int start, int end) {
        if (start == end) {
            return false;
        }
        int type = signature.codePointAt(start++);
        switch (type) {
        case 'T':
            // generics type parameter
            // fall-through intended as they are similar enough in format compared to objects
        case 'L':
            // object
            // find the end of the internal name of the object
            int endObject = start;
            while(true) {
                // this will skip a character, but this is not interesting as class names have to be at least 1 character long
                int codepoint = signature.codePointAt(++endObject);
                if (codepoint == ';') {
                    String name = signature.substring(start, endObject);
                    String newName = oldToNewClassName.get(name);
                    boolean modified = false;
                    if (newName != null) {
                        name = newName;
                        modified = true;
                    }
                    signatureOut.appendCodePoint(type);
                    signatureOut.append(name);
                    signatureOut.append(';');
                    modified |= remapSignature(signatureOut, signature, ++endObject, end);
                    return modified;
                } else if (codepoint == '<') {
                    // generics - please no
                    // post scriptum: well, that was a bit easier than expected
                    int openingBrackets = 1;
                    int endGenerics = endObject;
                    while(true) {
                        codepoint = signature.codePointAt(++endGenerics);
                        if (codepoint == '>' ) {
                            if (--openingBrackets == 0) {
                                break;
                            }
                        } else if (codepoint == '<') {
                            openingBrackets++;
                        }
                    }
                    String name = signature.substring(start, endObject);
                    String newName = oldToNewClassName.get(name);
                    boolean modified = false;
                    if (newName != null) {
                        name = newName;
                        modified = true;
                    }
                    signatureOut.append('L');
                    signatureOut.append(name);
                    signatureOut.append('<');
                    modified |= remapSignature(signatureOut, signature, endObject + 1, endGenerics++);
                    signatureOut.append('>');
                    // apparently that can be rarely be a '.', don't ask when or why exactly this occours
                    signatureOut.appendCodePoint(signature.codePointAt(endGenerics));
                    modified |= remapSignature(signatureOut, signature, ++endGenerics, end);
                    return modified;
                }
            }
        case '+':
            // idk what this one does - but it appears that it works good just like it does right now
        case '*':
            // wildcard - this can also be read like a regular primitive
            // fall-through intended
        case '(':
        case ')':
            // apparently our method does not break even in these cases, so we will consider them raw primitives
        case '[':
            // array - fall through intended as in this case they behave the same
        default:
            // primitive
            signatureOut.appendCodePoint(type);
            return remapSignature(signatureOut, signature, start, end); // Did not modify the signature - but following operations could
        }
    }

    private String remapSingleDesc(String input, StringBuilder sharedBuilder) {
        int indexofL = input.indexOf('L');
        if (indexofL == -1) {
            return input;
        }
        int length = input.length();
        String internalName = input.substring(indexofL + 1, length - 1);
        String newInternalName = oldToNewClassName.get(internalName);
        if (newInternalName == null) {
            return input;
        }
        sharedBuilder.setLength(indexofL + 1);
        sharedBuilder.setCharAt(indexofL, 'L');
        while(indexofL != 0) {
            sharedBuilder.setCharAt(--indexofL, '[');
        }
        sharedBuilder.append(newInternalName);
        sharedBuilder.append(';');
        return sharedBuilder.toString();
    }

    /**
     * Removes a method remapping entry from the method remapping list. This method practically undoes {@link #remapMethod(String, String, String, String)}.
     * Like it it only affects a SINGLE method in a SINGLE class and it's references. Note that implicitly declared/inherited methods must also be added to the remap list.
     *
     * @param owner The class of the method that should not be remapped
     * @param desc The descriptor of the method to not remap
     * @param name The name of the method that should not be remapped
     */
    public void removeMethodRemap(String owner, String desc, String name) {
        methodRenames.remove(owner, desc, name);
    }
}
