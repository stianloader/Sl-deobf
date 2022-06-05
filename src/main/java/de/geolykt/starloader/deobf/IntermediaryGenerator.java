package de.geolykt.starloader.deobf;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import de.geolykt.starloader.deobf.remapper.ConflicitingMappingException;
import de.geolykt.starloader.deobf.remapper.Remapper;

class ClassNodeNameComparator implements Comparator<ClassNode> {

    public static final ClassNodeNameComparator INSTANCE = new ClassNodeNameComparator();

    @Override
    public int compare(ClassNode o1, ClassNode o2) {
        int len1 = o1.name.length();
        int len2 = o2.name.length();
        if (len1 == len2) {
            return o1.name.compareTo(o2.name);
        } else {
            return len1 - len2;
        }
    }
} public class IntermediaryGenerator {

    private boolean alternateClassNaming;
    private final File map;
    private final List<ClassNode> nodes = new ArrayList<>();
    private final Map<String, ClassNode> nameToNode = new HashMap<>();

    private final File output;
    private final Remapper remapper = new Remapper();
    private final List<Map.Entry<String, byte[]>> resources = new ArrayList<>();

    public IntermediaryGenerator(@Nullable File map, File output, @Nullable Collection<ClassNode> nodes) {
        this.map = map;
        this.output = output;
        if (nodes != null) {
            this.nodes.addAll(nodes);
            this.nodes.forEach(node -> nameToNode.put(node.name, node));
            this.remapper.addTargets(nodes);
        }
    }

    public IntermediaryGenerator(File input, File map, File output) {
        this(map, output, (Collection<ClassNode>) null);
        try {
            JarFile inJar = new JarFile(input);
            Enumeration<JarEntry> entries = inJar.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                InputStream is = inJar.getInputStream(entry);
                if (!entry.getName().endsWith(".class")) {
                    resources.add(Map.entry(entry.getName(), is.readAllBytes()));
                    is.close();
                    continue;
                }
                ClassNode node = new ClassNode(Opcodes.ASM9);
                ClassReader reader = new ClassReader(is);
                reader.accept(node, 0);
                nodes.add(node);
                is.close();
            }
            inJar.close();
            remapper.addTargets(nodes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Adds resources from a jar file at a given location.
     * This is used for the {@link #deobfuscate()} operation if and only if an output folder was chosen.
     *
     * @param input The file to scan for resources
     * @throws IOException if an IO issue occurred
     */
    public void addResources(@NotNull File input) throws IOException {
        try (JarFile inJar = new JarFile(input)) {
            Enumeration<JarEntry> entries = inJar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) {
                    InputStream is = inJar.getInputStream(entry);
                    resources.add(Map.entry(entry.getName(), is.readAllBytes()));
                    is.close();
                    continue;
                }
            }
            inJar.close();
        }
    }

    protected Map<String, List<String>> computeFullHierarchy(Map<String, List<String>> nearbyHierarchy) {
        Map<String, List<String>> allSubtypes = new HashMap<>();
        for (Map.Entry<String, List<String>> clazz : nearbyHierarchy.entrySet()) {
            String name = clazz.getKey();
            List<String> subtypes = new ArrayList<>();
            for (String subtype : clazz.getValue()) {
                computeFullHierarchy0(subtypes, nearbyHierarchy, subtype);
            }
            allSubtypes.put(name, subtypes);
        }
        return allSubtypes;
    }


    private void computeFullHierarchy0(List<String> out, Map<String, List<String>> nearbyHierarchy, String current) {
        out.add(current);
        List<String> l = nearbyHierarchy.get(current);
        if (l == null) {
            return;
        }
        for (String subtype : l) {
            computeFullHierarchy0(out, nearbyHierarchy, subtype);
        }
    }

    private String createString(int num) {
        if (alternateClassNaming) {
            return Integer.toString(num);
        } else {
            // Create base 26 string with the characters a-z.
            num++;
            int len = 16;
            byte[] characters = new byte[len];
            int i;
            for (i = len - 1; num != 0; i--) {
                characters[i] = (byte) ((--num % 26) + 'a');
                num /= 26;
            }
            return new String(characters, ++i, len - i, StandardCharsets.US_ASCII);
        }
    }

    public void deobfuscate() {
        remapper.process();
        if (output != null) {
            try (JarOutputStream jarOut = new JarOutputStream(new FileOutputStream(output))) {
                for (ClassNode node : nodes) {
                    ClassWriter writer = new ClassWriter(0);
                    node.accept(writer);
                    jarOut.putNextEntry(new ZipEntry(node.name + ".class"));
                    jarOut.write(writer.toByteArray());
                    jarOut.closeEntry();
                }
                for (Map.Entry<String, byte[]> resource : resources) {
                    jarOut.putNextEntry(new ZipEntry(resource.getKey()));
                    jarOut.write(resource.getValue());
                    jarOut.closeEntry();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Proposes new field names within enum class that can be easily guessed by the computer.
     */
    public void doProposeEnumFieldsV2() {
        BufferedWriter bw = null;
        if (map != null) {
            try {
                @SuppressWarnings("resource")
                BufferedWriter dontcomplain = new BufferedWriter(new FileWriter(map, StandardCharsets.UTF_8, true));
                bw = dontcomplain;
                bw.write("# begin enum field remapping");
                bw.newLine();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        // We share this map instance for performance reasons (TM)
        Map<String, FieldNode> memberNames = new HashMap<>();
        for (ClassNode node : nodes) {
            if (node.superName.equals("java/lang/Enum")) {
                memberNames.clear();
                String expectedDesc = 'L' + node.name + ';';
                for (FieldNode field : node.fields) {
                    if (!field.desc.equals(expectedDesc)) {
                        continue;
                    }
                    memberNames.put(field.name, field);
                }
                for (MethodNode method : node.methods) {
                    if (method.name.equals("<clinit>")) {
                        AbstractInsnNode instruction = method.instructions.getFirst();
                        while (instruction != null) {
                            if (instruction.getOpcode() == Opcodes.NEW) {
                                TypeInsnNode newCall = (TypeInsnNode) instruction;
                                instruction = newCall.getNext();
                                if (instruction == null || instruction.getOpcode() != Opcodes.DUP) {
                                    break;
                                }
                                instruction = instruction.getNext();
                                if (instruction == null || instruction.getOpcode() != Opcodes.LDC) {
                                    break;
                                }
                                LdcInsnNode enumName = (LdcInsnNode) instruction;
                                if (!(enumName.cst instanceof String)) {
                                    continue;
                                }
                                instruction = instruction.getNext();
                                if (instruction == null) {
                                    break;
                                }
                                // SIPUSH or whatever, not relevant
                                instruction = instruction.getNext();
                                if (instruction == null) {
                                    break;
                                }
                                // other args for the constructor
                                AbstractInsnNode formerInsn = instruction;
                                while (instruction != null && (instruction.getOpcode() != Opcodes.INVOKESPECIAL || !((MethodInsnNode) instruction).owner.equals(newCall.desc))) {
                                    instruction = instruction.getNext();
                                }
                                if (instruction == null) {
                                    instruction = formerInsn;
                                    continue;
                                }
                                if (!((MethodInsnNode) instruction).name.equals("<init>")) {
                                    instruction = formerInsn;
                                    continue;
                                }
                                instruction = instruction.getNext();
                                if (instruction.getOpcode() != Opcodes.PUTSTATIC) {
                                    instruction = formerInsn;
                                    continue;
                                }
                                FieldInsnNode field = (FieldInsnNode) instruction;
                                if (!field.owner.equals(node.name) || !field.desc.equals(expectedDesc) || !memberNames.containsKey(field.name)) {
                                    instruction = formerInsn;
                                    continue;
                                }
                                if (field.name.equals(enumName.cst)) {
                                    continue;
                                }
                                if (bw != null) {
                                    try {
                                        // Comment from Nov 21 2021:
                                        // Yes, this sounds incredibly wrong (right now at least), but apparently is right.
                                        // For whatever reason
                                        bw.write("FIELD\t");
                                        bw.write(node.name);
                                        bw.write('\t');
                                        bw.write(expectedDesc);
                                        bw.write('\t');
                                        bw.write(field.name);
                                        bw.write('\t');
                                        bw.write(enumName.cst.toString());
                                        bw.write('\n');
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                                remapper.remapField(node.name, expectedDesc, field.name, enumName.cst.toString());
                                continue;
                            }
                            instruction = instruction.getNext();
                        }
                    }
                }
            }
        }
        if (bw != null) {
            try {
                bw.flush();
                bw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public List<ClassNode> getAsClassNodes() {
        return Collections.unmodifiableList(this.nodes);
    }

    private Map<String, List<String>> invertHierarchy(Map<String, List<String>> allSubtypes) {
        Map<String, List<String>> allSupertypes = new HashMap<>();
        allSubtypes.forEach((superType, inSubtypes) -> {
            for (String subtype : inSubtypes) {
                List<String> outSupertypes = allSupertypes.get(subtype);
                if (outSupertypes == null) {
                    outSupertypes = new ArrayList<>();
                    allSupertypes.put(subtype, outSupertypes);
                }
                outSupertypes.add(superType);
            }
        });
        return allSupertypes;
    }

    private void propagateDownwards(Map<String, List<String>> directChildren, Collection<MethodReference> output,
            ClassNode currentNode, MethodReference declaringRef, Map<String, ClassNode> name2Node, OverrideScope currentScope) {

        if (currentScope == OverrideScope.NEVER) {
            // This is futile
            return;
        }

        List<String> children = directChildren.get(currentNode.name);
        for (String childName : children) {
            ClassNode childNode = name2Node.get(childName);
            boolean canOverride = currentScope == OverrideScope.ALWAYS;
            if (!canOverride) {
                // scope is OverrideScope.PACKAGE
                String superMethodPackage = declaringRef.getOwner().substring(0, declaringRef.getOwner().lastIndexOf('/'));
                String overrdingMethodPackage = childName.substring(0, childName.lastIndexOf('/'));
                if (superMethodPackage.equals(overrdingMethodPackage)) {
                    canOverride = true;
                }
            }
            // This also accounts for implicit inheritance
            output.add(new MethodReference(childName, declaringRef.getDesc(), declaringRef.getName()));
            boolean found = false;
            for (MethodNode childMethod : childNode.methods) {
                if (childMethod.name.equals(declaringRef.getName())
                        && childMethod.desc.equals(declaringRef.getDesc())
                        && (childMethod.access & Opcodes.ACC_STATIC) != 0) {
                    found = true;
                    int flagWithoutFinal = childMethod.access & ~Opcodes.ACC_FINAL; // Better be safe than sorry
                    propagateDownwards(directChildren, output, childNode, declaringRef, name2Node, OverrideScope.fromFlags(flagWithoutFinal));
                    break;
                }
            }
            if (!found) {
                propagateDownwards(directChildren, output, childNode, declaringRef, name2Node, currentScope);
            }
        }
    }

    private void remapClass(String oldName, String newName, BufferedWriter bw) {
        remapper.remapClassName(oldName, newName);
        if (bw != null) {
            try {
                bw.write("CLASS\t");
                bw.write(oldName);
                bw.write('\t');
                bw.write(newName);
                bw.write('\n'); // The tiny format does not make use of system-dependent newlines
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void remapClassesV2() {
        remapClassesV2(false);
    }

    public void remapClassesV2(boolean findLocalClasses) {
        BufferedWriter bw;
        if (map != null) {
            try {
                @SuppressWarnings("resource")
                BufferedWriter dontcomplain = new BufferedWriter(new FileWriter(map, StandardCharsets.UTF_8, false));
                bw = dontcomplain;
                bw.write("v1\tofficial\tintermediary\n");
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        } else {
            bw = null;
        }

        Map<String, String> localClasses;
        if (findLocalClasses) {
            Oaktree oaktree = new Oaktree();
            oaktree.getClassNodesDirectly().addAll(nodes);
            localClasses = oaktree.guessLocalClasses();
        } else {
            localClasses = Collections.emptyMap();
        }

        Map<String, TreeSet<ClassNode>> remappedEnums = new HashMap<>();
        Map<String, TreeSet<ClassNode>> remappedInterfaces = new HashMap<>();
        Map<String, TreeSet<ClassNode>> remappedInners = new HashMap<>();
        Map<String, TreeSet<ClassNode>> remappedLocals = new HashMap<>();
        Map<String, TreeSet<ClassNode>> remappedPrivateClasses = new HashMap<>();
        Map<String, TreeSet<ClassNode>> remappedProtectedClasses = new HashMap<>();
        Map<String, TreeSet<ClassNode>> remappedPublicClasses = new HashMap<>();

        for (ClassNode node : nodes) {
            if (localClasses.containsKey(node.name)) {
                continue; // No need to rename it
            }
            int lastSlash = node.name.lastIndexOf('/');
            String className = node.name.substring(lastSlash + 1);
            String packageName = node.name.substring(0, lastSlash);
            if (packageName.startsWith("org/hamcrest") || packageName.startsWith("org/lwjgl")) {
                // the three (?) packages contain classes that should not be remapped
                continue;
            }
            if (className.length() < 3) {
                if ("java/lang/Enum".equals(node.superName)) {
                    TreeSet<ClassNode> remapSet = remappedEnums.get(packageName);
                    if (remapSet == null) {
                        remapSet = new TreeSet<>(ClassNodeNameComparator.INSTANCE);
                        remappedEnums.put(packageName, remapSet);
                    }
                    remapSet.add(node);
                } else if (node.outerClass != null) {
                    if (node.outerMethod == null) {
                        TreeSet<ClassNode> remapSet = remappedInners.get(packageName);
                        if (remapSet == null) {
                            remapSet = new TreeSet<>(ClassNodeNameComparator.INSTANCE);
                            remappedInners.put(packageName, remapSet);
                        }
                        remapSet.add(node);
                    } else {
                        TreeSet<ClassNode> remapSet = remappedLocals.get(packageName);
                        if (remapSet == null) {
                            remapSet = new TreeSet<>(ClassNodeNameComparator.INSTANCE);
                            remappedLocals.put(packageName, remapSet);
                        }
                        remapSet.add(node);
                    }
                } else if ((node.access & Opcodes.ACC_INTERFACE) != 0) {
                    TreeSet<ClassNode> remapSet = remappedInterfaces.get(packageName);
                    if (remapSet == null) {
                        remapSet = new TreeSet<>(ClassNodeNameComparator.INSTANCE);
                        remappedInterfaces.put(packageName, remapSet);
                    }
                    remapSet.add(node);
                } else if ((node.access & Opcodes.ACC_PUBLIC) != 0) {
                    TreeSet<ClassNode> remapSet = remappedPublicClasses.get(packageName);
                    if (remapSet == null) {
                        remapSet = new TreeSet<>(ClassNodeNameComparator.INSTANCE);
                        remappedPublicClasses.put(packageName, remapSet);
                    }
                    remapSet.add(node);
                } else if ((node.access & Opcodes.ACC_PROTECTED) != 0) {
                    TreeSet<ClassNode> remapSet = remappedProtectedClasses.get(packageName);
                    if (remapSet == null) {
                        remapSet = new TreeSet<>(ClassNodeNameComparator.INSTANCE);
                        remappedProtectedClasses.put(packageName, remapSet);
                    }
                    remapSet.add(node);
                } else {
                    TreeSet<ClassNode> remapSet = remappedPrivateClasses.get(packageName);
                    if (remapSet == null) {
                        remapSet = new TreeSet<>(ClassNodeNameComparator.INSTANCE);
                        remappedPrivateClasses.put(packageName, remapSet);
                    }
                    remapSet.add(node);
                }
            }
        }

        Map<String, String> remapMap = new HashMap<>();
        remapSet(remappedEnums, bw, "enum_", remapMap);
        remapSet(remappedInterfaces, bw, "interface_", remapMap);
        remapSet(remappedInners, bw, "innerclass_", remapMap);
        remapSet(remappedLocals, bw, "localclass_", remapMap);
        remapSet(remappedPublicClasses, bw, "class_", remapMap);
        remapSet(remappedProtectedClasses, bw, "pclass_",remapMap); // protected class
        remapSet(remappedPrivateClasses, bw, "ppclass_", remapMap); // package-private class

        Map<String, List<String>> mappings = new HashMap<>();
        Set<String> unmappedInnerClasses = new HashSet<>();

        localClasses.forEach((inner, outer) -> {
            mappings.compute(outer, (key, list) -> {
                if (list == null) {
                    list = new ArrayList<>();
                }
                list.add(inner);
                return list;
            });
            unmappedInnerClasses.add(inner);
        });

        while (unmappedInnerClasses.size() != 0) {
            int oldSize = unmappedInnerClasses.size();
            mappings.forEach((outer, inners) -> {
                if (unmappedInnerClasses.contains(outer)) {
                    return;
                }
                inners.sort(String::compareTo);
                int counter = 0;
                ClassNode outerNode = nameToNode.get(outer);
                for (String inner : inners) {
                    ClassNode innerNode = nameToNode.get(inner);
                    String innerName = "Local" + counter++;
                    InnerClassNode icn = new InnerClassNode(inner, outer, innerName, innerNode.access);
                    outerNode.innerClasses.add(icn);
                    innerNode.innerClasses.add(icn);
                    String newName = remapMap.getOrDefault(outer, outer) + '$' + innerName;
                    remapMap.put(inner, newName);
                    remapClass(inner, newName, bw);
                    unmappedInnerClasses.remove(inner);
                }
            });
            if (unmappedInnerClasses.size() == oldSize) {
                for (String s : unmappedInnerClasses) {
                    System.out.println("IntermediaryGenerator: " + s + " is part of a nested pair. Discarded from intermediary");
                }
                break; // Only nested pairs remaining - Discard all
            }
        }

        if (bw != null) {
            try {
                bw.flush();
                bw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void remapGetters() {
        BufferedWriter bw = null;
        if (map != null) {
            try {
                @SuppressWarnings("resource")
                BufferedWriter dontcomplain = new BufferedWriter(new FileWriter(map, StandardCharsets.UTF_8, true));
                bw = dontcomplain;
                bw.write("# begin getter remapping");
                bw.newLine();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        Map<String, ClassNode> name2Node = new HashMap<>(nodes.size());
        List<Map.Entry<MethodReference, FieldReference>> getterCandidates = new ArrayList<>();
        Map<String, List<String>> directSubtypes = new HashMap<>(nodes.size());
        Map<String, List<MethodReference>> declaredMethods = new HashMap<>();

        for (ClassNode node : nodes) {
            name2Node.put(node.name, node);
            if ((node.access & Opcodes.ACC_FINAL) != 0) {
                directSubtypes.put(node.name, Collections.emptyList());
            } else {
                directSubtypes.put(node.name, new ArrayList<>());
            }
            List<MethodReference> methods = new ArrayList<>();
            declaredMethods.put(node.name, methods);
            for (MethodNode method : node.methods) {
                MethodReference mref = new MethodReference(node.name, method);
                methods.add(mref);
                if (method.name.length() > 2) {
                    // unlikely to be obfuscated
                    continue;
                }
                if (method.desc.codePointAt(1) != ')') {
                    // getter methods must be no-args methods
                    continue;
                }
                AbstractInsnNode insn = method.instructions.getFirst();
                if (insn == null) {
                    // Abstract method? In any case it can never be a getter method
                    continue;
                }
                while ((insn instanceof FrameNode || insn instanceof LineNumberNode || insn instanceof LabelNode)) {
                    insn = insn.getNext();
                }
                if ((method.access & Opcodes.ACC_STATIC) == 0 && insn instanceof VarInsnNode && ((VarInsnNode) insn).var == 0) {
                    insn = insn.getNext();
                }
                if (insn.getOpcode() == Opcodes.GETSTATIC || insn.getOpcode() == Opcodes.GETFIELD) {
                    FieldInsnNode getField = (FieldInsnNode) insn;
                    insn = insn.getNext();
                    while ((insn instanceof FrameNode || insn instanceof LineNumberNode)) {
                        insn = insn.getNext();
                    }
                    if (!(insn instanceof InsnNode)) {
                        continue;
                    }
                    if (insn.getOpcode() != Opcodes.ARETURN
                            && insn.getOpcode() != Opcodes.IRETURN
                            && insn.getOpcode() != Opcodes.DRETURN
                            && insn.getOpcode() != Opcodes.FRETURN
                            && insn.getOpcode() != Opcodes.LRETURN) {
                        continue;
                    }
                    if (!getField.owner.equals(node.name)) {
                        continue;
                    }
                    FieldReference fref = new FieldReference(getField);
                    getterCandidates.add(Map.entry(mref, fref));
                }
            }
        }

        // Calculate nearby hierarchy
        for (ClassNode node : nodes) {
            // As of now this has to be on another loop and cannot be merged easily into the loop above, albeit this is theoretically possible
            List<String> a = directSubtypes.get(node.superName);
            if (a != null) {
                a.add(node.name);
            }
            for (String interfaceName : node.interfaces) {
                a = directSubtypes.get(interfaceName);
                if (a != null) {
                    a.add(node.name);
                }
            }
        }

        // Filter out conflicting proposals
        Set<MethodReference> conflictingMappings = new HashSet<>();
        Map<MethodReference, FieldReference> existingMappings = new HashMap<>();
        for (Map.Entry<MethodReference, FieldReference> proposedMapping : getterCandidates) {
            MethodReference mref = proposedMapping.getKey();
            FieldReference fref = proposedMapping.getValue();

            if (conflictingMappings.contains(mref)) {
                continue;
            }

            FieldReference oldFieldReference = existingMappings.getOrDefault(existingMappings, fref);
            if (!oldFieldReference.getName().equals(fref.getName())) {
                // FIXME this method never gets called
                existingMappings.remove(mref);
                conflictingMappings.add(mref);
            } else {
                existingMappings.putIfAbsent(mref, oldFieldReference);
            }
        }

        // calculate the full hierarchy based on the nearby hierarchy
        Map<String, List<String>> allSubtypes = computeFullHierarchy(directSubtypes); // super -> children map
        Map<String, List<String>> allSupertypes = invertHierarchy(allSubtypes); // child -> super

        // Based on the hierarchy and the declared method's access flags, we can try to identify the methods that are the same.
        // In our case, we will call these connected methods a "method group".
        Map<MethodReference, Set<MethodReference>> methodGroups = new HashMap<>();

        declaredMethods.forEach((declarerName, declaredMethodRefs) -> {
            ClassNode declarerNode = name2Node.get(declarerName);

            List<String> supers = allSupertypes.get(declarerName);
            if (supers == null) {
                // This can likely happen if a class is a superclass of a class that does not exit or is java-specific. However given the circumstances it shouldn't be too much of an issue (heh)
                // System.err.println(declarerName + " extends " + declarerNode.superName + " and implements " + Arrays.toString(declarerNode.interfaces.toArray()));
                supers = Collections.emptyList();
            }
            List<ClassNode> superNodes = new ArrayList<>();
            supers.forEach(name -> superNodes.add(name2Node.get(name)));
            List<MethodNode> declaredMethodNodes = new ArrayList<>(); // This is required in order to obtain the access flags of the method
            for (MethodReference mref : declaredMethodRefs) {
                for (MethodNode method : declarerNode.methods) {
                    if (method.name.equals(mref.getName()) && method.desc.equals(mref.getDesc())) {
                        declaredMethodNodes.add(method);
                        break;
                    }
                }
            }

            declaredMethodNodes.removeIf(method -> {
                boolean isStatic = (method.access & Opcodes.ACC_STATIC) != 0;
                if (isStatic) {
                    MethodReference ref = new MethodReference(declarerName, method);
                    methodGroups.put(ref, Collections.singleton(ref)); // A static method is more or less a standalone method
                    return true; // as such they are not relevant after that and are removed from the methodNodes list.
                } else {
                    return false;
                }
            });

            for (MethodNode method : declaredMethodNodes) {
                Set<MethodReference> group = new HashSet<>();
                MethodReference declaredMethodRef = new MethodReference(declarerName, method);
                methodGroups.put(declaredMethodRef, group);
                group.add(declaredMethodRef);
                for (ClassNode node : superNodes) {
                    for (MethodNode superMethod : node.methods) {
                        if (method.name.equals(superMethod.name)
                                && method.desc.equals(superMethod.desc)
                                && (superMethod.access & Opcodes.ACC_STATIC) == 0) {
                            // Check whether 'method' can possibly override 'superMethod'
                            OverrideScope superMethodScope = OverrideScope.fromFlags(superMethod.access);
                            if (superMethodScope == OverrideScope.ALWAYS) {
                                group.add(new MethodReference(node.name, superMethod));
                                propagateDownwards(directSubtypes, group, node, declaredMethodRef, name2Node, superMethodScope);
                            } else if (superMethodScope == OverrideScope.PACKAGE) {
                                String superMethodPackage = node.name.substring(0, node.name.lastIndexOf('/'));
                                String overrdingMethodPackage = declarerName.substring(0, declarerName.lastIndexOf('/'));
                                if (superMethodPackage.equals(overrdingMethodPackage)) {
                                    group.add(new MethodReference(node.name, superMethod));
                                }
                                // TODO this is terribly inefficient as we are propagating the same set of classes multiple times.
                                // A solution to this issue would be to have a stop condition of some sorts, either when the reference
                                // was already added or when the same class is scanned twice
                                // however it is also required as we have implicit inheritance, so a removal is not the solution
                                propagateDownwards(directSubtypes, group, node, declaredMethodRef, name2Node, superMethodScope);
                            } else {
                                // private (or final) method. Not it
                            }
                            break; // We found our method declaration - or at least think we have
                        }
                    }
                }
                propagateDownwards(directSubtypes, group, declarerNode, declaredMethodRef, name2Node, OverrideScope.fromFlags(method.access));
            }
        });

        // prevent renaming two methods to the same name
        // (does not fully work)
        Map<FieldReference, MethodReference> refeers = new HashMap<>();
        existingMappings.forEach((mref, fref)-> {
            MethodReference oldReference = refeers.put(fref, mref);
            if (oldReference != null && !oldReference.equals(mref)) {
                if (oldReference.getOwner().equals(mref.getOwner()) && fref.getOwner().equals(oldReference.getOwner())) {
                    // One of the two methods is likely a synthetic method. We will try to only invalidate that synthetic method
                    ClassNode node = name2Node.get(oldReference.getOwner());
                    boolean oldRefSynthetic = false;
                    boolean newRefSynthetic = false;
                    for (MethodNode method : node.methods) {
                        if (method.name.equals(oldReference.getName()) && (method.desc.equals(oldReference.getDesc()))) {
                            oldRefSynthetic = (method.access & Opcodes.ACC_SYNTHETIC) != 0;
                        }
                        if (method.name.equals(mref.getName()) && (method.desc.equals(mref.getDesc()))) {
                            newRefSynthetic = (method.access & Opcodes.ACC_SYNTHETIC) != 0;
                        }
                    }
                    if (oldRefSynthetic == newRefSynthetic) {
                        // Either both are synthetic or both are not synthetic. A preference thus cannot be established
                        conflictingMappings.add(mref);
                        conflictingMappings.add(oldReference);
                    } else if (oldRefSynthetic) {
                        // old reference was synthetic, but the new one is not
                        conflictingMappings.add(oldReference);
                    } else {
                        // new reference is synthetic, but the old one was not
                        refeers.put(fref, oldReference);
                        conflictingMappings.add(mref);
                    }
                } else {
                    // While we could only rename one method, due to the way HashMap ordering works, this is not easily doable
                    // while keeping a predictable output.
                    conflictingMappings.add(mref);
                    conflictingMappings.add(oldReference);
                }
            }
        });

        StringBuilder sharedBuilder = new StringBuilder();

        // Filter out conflicts within the group
        // Also filter out conflicts which occur due to the method name being already present in the class.
        Map<MethodReference, String> crudeNames = new HashMap<>();
        existingMappings.forEach((mref, fref) -> {
            sharedBuilder.setLength(0);
            if (fref.getName().length() > 2) {
                sharedBuilder.append("get");
                sharedBuilder.appendCodePoint(Character.toUpperCase(fref.getName().codePointAt(0)));
                sharedBuilder.append(fref.getName().substring(1));
            } else {
                sharedBuilder.append("get_");
                sharedBuilder.append(fref.getName());
            }
            String newName = sharedBuilder.toString();
            Set<MethodReference> group = methodGroups.get(mref);
            boolean invalid = false;
            for (MethodReference groupRef : group) {
                if (conflictingMappings.contains(groupRef)) {
                    invalid = true;
                    break;
                }
                if (!crudeNames.getOrDefault(groupRef, newName).equals(newName)) {
                    invalid = true;
                    // What to do with the old mapping? (especially those that are connected to this one)
                    break;
                }
                ClassNode node = name2Node.get(groupRef.getOwner());
                for (MethodNode method : node.methods) {
                    if (method.name.equals(newName) && method.desc.startsWith("()")) {
                        invalid = true; // Method name already present. (Could we use another name?)
                        break;
                    }
                }
                if (invalid) {
                    break;
                }
            }
            if (invalid) {
                for (MethodReference groupRef : group) {
                    conflictingMappings.add(groupRef);
                    crudeNames.remove(groupRef);
                }
            } else {
                for (MethodReference groupRef : group) {
                    crudeNames.put(groupRef, newName);
                }
            }
        });
        crudeNames.clear();

        // Guard against remapping two methods within the same class to the same name
        // This is a far more brute-force approach and takes in account of "method groups"
        // Apparently the following block is useless, but I'll still leave this here in case I need it
        Map<MethodReference, MethodReference> potentialRemaps = new HashMap<>(); // future -> current
        existingMappings.forEach((mref, fref) -> {
            if (conflictingMappings.contains(mref)) {
                return;
            }
            sharedBuilder.setLength(0);
            if (fref.getName().length() > 2) {
                sharedBuilder.append("get");
                sharedBuilder.appendCodePoint(Character.toUpperCase(fref.getName().codePointAt(0)));
                sharedBuilder.append(fref.getName().substring(1));
            } else {
                sharedBuilder.append("get_");
                sharedBuilder.append(fref.getName());
            }
            String newName = sharedBuilder.toString();
            MethodReference future = new MethodReference(mref.getOwner(), mref.getDesc(), newName);
            MethodReference current = potentialRemaps.getOrDefault(future, mref);
            if (!current.equals(mref) && !methodGroups.get(mref).contains(current)) {
                // Bigger group "wins", to avoid nullifying large method groups
                int groupSizeContender = methodGroups.get(mref).size();
                int groupSizeCurrent = methodGroups.get(current).size();
                if (groupSizeContender == groupSizeCurrent) {
                    // Both loose, so build results are reliable
                    conflictingMappings.add(mref);
                    conflictingMappings.add(current);
                } else if (groupSizeContender > groupSizeCurrent) {
                    // Contender wins
                    conflictingMappings.add(current);
                    potentialRemaps.put(future, current);
                } else {
                    // Contender looses
                    conflictingMappings.add(mref);
                }
                return;
            }
            potentialRemaps.put(future, mref);
            for (MethodReference groupRef : methodGroups.get(mref)) {
                if (conflictingMappings.contains(groupRef)) {
                    continue;
                }
                future = new MethodReference(groupRef.getOwner(), groupRef.getDesc(), newName);
                current = potentialRemaps.getOrDefault(future, mref);
                if (!current.equals(groupRef) && !methodGroups.get(mref).contains(current)) {
                    // Bigger group "wins", to avoid nullifying large method groups
                    int groupSizeContender = methodGroups.get(mref).size();
                    int groupSizeCurrent = methodGroups.get(current).size();
                    if (groupSizeContender == groupSizeCurrent) {
                        // Both loose, so build results are reliable
                        conflictingMappings.add(mref);
                        conflictingMappings.add(current);
                    } else if (groupSizeContender > groupSizeCurrent) {
                        // Contender wins
                        conflictingMappings.add(current);
                        potentialRemaps.put(future, current);
                    } else {
                        // Contender looses
                        conflictingMappings.add(mref);
                    }
                    continue;
                }
                potentialRemaps.put(future, groupRef);
            }
        });

        // Filter out group conflicts, again
        Map<MethodReference, String> proposedNames = new HashMap<>();
        existingMappings.forEach((mref, fref) -> {
            sharedBuilder.setLength(0);
            if (fref.getName().length() > 2) {
                sharedBuilder.append("get");
                sharedBuilder.appendCodePoint(Character.toUpperCase(fref.getName().codePointAt(0)));
                sharedBuilder.append(fref.getName().substring(1));
            } else {
                sharedBuilder.append("get_");
                sharedBuilder.append(fref.getName());
            }
            String newName = sharedBuilder.toString();
            Set<MethodReference> group = methodGroups.get(mref);
            boolean invalid = false;
            for (MethodReference groupRef : group) {
                if (conflictingMappings.contains(groupRef)) {
                    invalid = true;
                    break;
                }
                if (!proposedNames.getOrDefault(groupRef, newName).equals(newName)) {
                    invalid = true;
                    // What to do with the old mapping? (especially those that are connected to this one)
                    break;
                }
                if (invalid) {
                    break;
                }
            }
            if (invalid) {
                for (MethodReference groupRef : group) {
                    conflictingMappings.add(groupRef);
                    proposedNames.remove(groupRef);
                }
            } else {
                for (MethodReference groupRef : group) {
                    proposedNames.put(groupRef, newName);
                }
            }
        });

        for (Map.Entry<MethodReference, String> entry : proposedNames.entrySet()) {
            MethodReference method = entry.getKey();
            String newName = entry.getValue();

            try {
                remapper.remapMethod(method.getOwner(), method.getDesc(), method.getName(), newName);
            } catch (ConflicitingMappingException e1) {
                throw new IllegalStateException("Conflict filtering was not done throughout enough.", e1);
            }
            if (bw != null) {
                try {
                    bw.write("METHOD\t");
                    bw.write(method.getOwner());
                    bw.write('\t');
                    bw.write(method.getName());
                    bw.write('\t');
                    bw.write(method.getDesc());
                    bw.write('\t');
                    bw.write(newName);
                    bw.write('\n');
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        if (bw != null) {
            try {
                bw.flush();
                bw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void remapSet(Map<String, TreeSet<ClassNode>> set, BufferedWriter writer, String prefix, Map<String, String> mappingsOut) {
        prefix = '/' + prefix;
        for (Map.Entry<String, TreeSet<ClassNode>> packageNode : set.entrySet()) {
            String packageName = packageNode.getKey();
            int counter = 0;
            for (ClassNode node : packageNode.getValue()) {
                String newName = packageName + prefix + createString(counter++);
                remapClass(node.name, newName, writer);
                mappingsOut.put(node.name, newName);
            }
        }
    }

    /**
     * Sets whether alternate class naming should be employed. This is very useful if obftools has to be updated or
     * when the application to link to has changed. Making use of this feature eliminates large portions of issues
     * where the classes have changed their name, but using the old name is still fully valid. This can cause subtle
     * bugs that are very hard to trace.
     * If the alternate naming scheme is on, classes are enumerated with the characters [0-9],
     * where as they are enumerated with the characters [a-z] when the alternate naming scheme is off.
     *
     * @param toggle Whether to use the alternate class naming scheme.
     */
    public void useAlternateClassNaming(boolean toggle) {
        alternateClassNaming = toggle;
    }

} enum OverrideScope {
    ALWAYS,
    NEVER,
    PACKAGE;

    public static OverrideScope fromFlags(int accessFlags) {
        if ((accessFlags & Opcodes.ACC_STATIC) != 0
                || (accessFlags & Opcodes.ACC_FINAL) != 0
                || (accessFlags & Opcodes.ACC_PRIVATE) != 0) {
            return OverrideScope.NEVER;
        }
        if ((accessFlags & Opcodes.ACC_PROTECTED) != 0
                || (accessFlags & Opcodes.ACC_PUBLIC) != 0) {
            return OverrideScope.ALWAYS;
        }
        return OverrideScope.PACKAGE;
    }
}
