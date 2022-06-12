package de.geolykt.starloader.deobf;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.ParameterNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import de.geolykt.starloader.deobf.StackElement.ElementSourceType;
import de.geolykt.starloader.deobf.StackWalker.StackWalkerConsumer;

/**
 * Primitive class metadata recovery tool.
 * Originally intended for SML0 (a patch-based modding framework intended for larger tasks like multiplayer)
 * but got recycled to fix the mess produced by the remapping software and to make it ready for decompilation.
 *
 * @author Geolykt
 */
public class Oaktree {
    // TODO: lambda handle name recovery (Does this fall under Oaktree? I would assume that that is for SlIntermediary)
    // TODO: lambda-stream based generic signature guessing - if applicable

    /**
     * A hardcoded set of implementations of the {@link Collection} interface that apply for
     * generics checking later on.
     */
    public static final Set<String> COLLECTIONS = new HashSet<>() {
        private static final long serialVersionUID = -3779578266088390366L;

        {
            add("Ljava/util/Vector;");
            add("Ljava/util/List;");
            add("Ljava/util/ArrayList;");
            add("Ljava/util/Collection;");
            add("Ljava/util/AbstractCollection;");
            add("Ljava/util/AbstractList;");
            add("Ljava/util/AbstractSet;");
            add("Ljava/util/AbstractQueue;");
            add("Ljava/util/HashSet;");
            add("Ljava/util/Set;");
            add("Ljava/util/Queue;");
            add("Ljava/util/concurrent/ArrayBlockingQueue;");
            add("Ljava/util/concurrent/ConcurrentLinkedQueue;");
            add("Ljava/util/concurrent/ConcurrentLinkedQueue;");
            add("Ljava/util/concurrent/DelayQueue;");
            add("Ljava/util/concurrent/LinkedBlockingQueue;");
            add("Ljava/util/concurrent/SynchronousQueue;");
            add("Ljava/util/concurrent/BlockingQueue;");
            add("Ljava/util/concurrent/BlockingDeque;");
            add("Ljava/util/concurrent/LinkedBlockingDeque;");
            add("Ljava/util/concurrent/ConcurrentLinkedDeque;");
            add("Ljava/util/Deque;");
            add("Ljava/util/ArrayDeque;");
        }
    };

    /**
     * A hardcoded set of implementations of the {@link Iterable} interface that apply for
     * generics checking later on.
     */
    public static final Set<String> ITERABLES = new HashSet<>() {
        private static final long serialVersionUID = -3779578266088390365L;

        {
            add("Ljava/util/Vector;");
            add("Ljava/util/List;");
            add("Ljava/util/ArrayList;");
            add("Ljava/util/Collection;");
            add("Ljava/util/AbstractCollection;");
            add("Ljava/util/AbstractList;");
            add("Ljava/util/AbstractSet;");
            add("Ljava/util/AbstractQueue;");
            add("Ljava/util/HashSet;");
            add("Ljava/util/Set;");
            add("Ljava/util/Queue;");
            add("Ljava/util/concurrent/ArrayBlockingQueue;");
            add("Ljava/util/concurrent/ConcurrentLinkedQueue;");
            add("Ljava/util/concurrent/ConcurrentLinkedQueue;");
            add("Ljava/util/concurrent/DelayQueue;");
            add("Ljava/util/concurrent/LinkedBlockingQueue;");
            add("Ljava/util/concurrent/SynchronousQueue;");
            add("Ljava/util/concurrent/BlockingQueue;");
            add("Ljava/util/concurrent/BlockingDeque;");
            add("Ljava/util/concurrent/LinkedBlockingDeque;");
            add("Ljava/util/concurrent/ConcurrentLinkedDeque;");
            add("Ljava/util/Deque;");
            add("Ljava/util/ArrayDeque;");
            add("Ljava/lang/Iterable;");
        }
    };

    public static final int VISIBILITY_MODIFIERS = Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED | Opcodes.ACC_PUBLIC;

    /**
     * Obtains the internal name of the class that is returned by a given method descriptor.
     * If not an object (or array) is returned as according to the method descriptor, null
     * is returned by this method.
     * For arrays, the leading "[" are removed - so it is not possible to differ between array and
     * "normal" object.
     *
     * <p>This method was created in anticipation of L-World.
     *
     * @param methodDesc The method descriptor
     * @return The internal name of the object or array returned by the method.
     */
    @Nullable
    @Contract(pure = true)
    public static final String getReturnedClass(String methodDesc) {
        if (methodDesc.codePointBefore(methodDesc.length()) != ';') {
            return null;
        }
        int closingBracket = methodDesc.indexOf(')');
        if (closingBracket == -1) {
            throw new IllegalArgumentException("The descriptor: \"" + methodDesc + "\" is probably not a method descriptor.");
        }
        int indexOfL = methodDesc.indexOf('L', closingBracket);
        if (indexOfL != -1) {
            return methodDesc.substring(indexOfL + 1, methodDesc.length() - 1);
        }
        return null;
    }

    /**
     * Obtains the internal name of the class or array that is described within the descriptor.
     * If no object is embedded in there (e.g. primitives), null is returned.
     *
     * @param fieldDesc The field descriptor
     * @return The internal name of the parsed class
     */
    @Nullable
    @Contract(pure = true)
    private static final String getClassName(String fieldDesc) {
        int indexOfL = fieldDesc.indexOf('L');
        if (++indexOfL == 0) {
            return null;
        }
        return fieldDesc.substring(indexOfL, fieldDesc.length() - 1);
    }

    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        if (args.length < 2) {
            System.err.println("Not enough arguments. The first argument is the source jar, the second one the target jar.");
            return;
        }
        try {
            Oaktree oakTree = new Oaktree();
            JarFile file = new JarFile(args[0]);
            oakTree.index(file);
            file.close();
            oakTree.definalizeAnonymousClasses();
            oakTree.fixInnerClasses();
            oakTree.fixParameterLVT();
            oakTree.guessFieldGenerics();
            oakTree.guessMethodGenerics();
            oakTree.inferMethodGenerics();
            oakTree.inferConstructorGenerics();
            oakTree.fixSwitchMaps();
            oakTree.fixForeachOnArray();
            oakTree.fixComparators(true);
            oakTree.guessAnonymousClasses();
            long startStep = System.currentTimeMillis();
            oakTree.applyInnerclasses();
            System.out.println("Applied inner class nodes to referencing classes. (" + (System.currentTimeMillis() - startStep) + " ms)");
            if (args.length == 3 && Boolean.valueOf(args[2]) == true) {
                // remapper activate!
                IntermediaryGenerator gen = new IntermediaryGenerator(new File("map.tiny"), new File(args[1]), oakTree.nodes);
                gen.addResources(new File(args[0]));
                gen.useAlternateClassNaming(Boolean.getBoolean("oaktree.cli.alternateClassNaming"));
                gen.remapClassesV2();
                gen.doProposeEnumFieldsV2();
                long startGetters = System.currentTimeMillis();
                gen.remapGetters();
                System.out.println("Getters remapped in " + (System.currentTimeMillis() - startGetters) + " ms");
                gen.deobfuscate();
            } else {
                FileOutputStream os = new FileOutputStream(args[1]);
                oakTree.write(os);
                os.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.printf("Finished processing in record pace: Only %d ms!\n", System.currentTimeMillis() - start);
    }

    private final Map<String, ClassNode> nameToNode = new HashMap<>();
    private final List<ClassNode> nodes = new ArrayList<>();
    private final ClassWrapperPool wrapperPool;

    public Oaktree() {
        this(new URLClassLoader("Oaktree ClassWrapper Pool Classloader", new URL[0], Oaktree.class.getClassLoader()));
    }

    public Oaktree(ClassLoader classWrapperClassloader) {
        wrapperPool = new ClassWrapperPool(nameToNode, classWrapperClassloader);
    }

    /**
     * Applies the inner class nodes to any encountered classes.
     */
    public void applyInnerclasses() {
        // Index inner class nodes
        Map<String, InnerClassNode> innerClassNodes = new HashMap<>();
        for (ClassNode node : nodes) {
            for (InnerClassNode icn : node.innerClasses) {
                if (icn.name.equals(node.name)) {
                    innerClassNodes.put(node.name, icn);
                    break;
                }
            }
        }

        // Find references to these classes
        Set<String> encounteredClasses = new HashSet<>();
        for (ClassNode node : nodes) {
            encounteredClasses.clear();
            for (InnerClassNode icn : node.innerClasses) {
                encounteredClasses.add(icn.name);
            }
            for (MethodNode method : node.methods) {
                if (method.instructions == null) {
                    continue;
                }
                for (AbstractInsnNode insn : method.instructions) {
                    if (insn instanceof MethodInsnNode) {
                        MethodInsnNode methodRef = (MethodInsnNode) insn;
                        if (encounteredClasses.add(methodRef.owner)) {
                            InnerClassNode icn = innerClassNodes.get(methodRef.owner);
                            if (icn != null) {
                                node.innerClasses.add(icn);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Removes the final access modifier from non-obfuscated anonymous classes.
     * The reason this is done is because for recompiled galimulator (using Java 17 to compile and target 1.8),
     * the access modifiers differ in this instance. Why exactly this is the case is unknown to me.
     */
    public void definalizeAnonymousClasses() {
        for (ClassNode node : nodes) {
            int dollarIndex = node.name.indexOf('$');
            if (dollarIndex == -1) {
                continue;
            }
            if (Character.isDigit(node.name.codePointAt(dollarIndex + 1))) {
                // Highly likely an anonymous class, so we remove the anonymous access flag
                node.access &= ~Opcodes.ACC_FINAL;
            }
        }
    }

    /**
     * Add the signature of obvious bridge methods (i. e. comparators).
     *
     * @param resolveTRArtifact Whether to resolve an artifact left over by tiny remapper.
     */
    public void fixComparators(boolean resolveTRArtifact) {
        for (ClassNode node : nodes) {
            if (node.signature != null || node.interfaces.size() != 1) {
                continue;
            }
            if (!node.interfaces.get(0).equals("java/util/Comparator")) {
                continue;
            }
            // Ljava/lang/Object;Ljava/util/Comparator<Lorg/junit/runner/Description;>;
            for (MethodNode method : node.methods) {
                if ((method.access & Opcodes.ACC_SYNTHETIC) == 0) {
                    continue;
                }
                if (method.name.equals("compare") && method.desc.equals("(Ljava/lang/Object;Ljava/lang/Object;)I")) {
                    AbstractInsnNode insn = method.instructions.getFirst();
                    while (insn instanceof LabelNode || insn instanceof LineNumberNode) {
                        insn = insn.getNext();
                    }
                    if (insn.getOpcode() != Opcodes.ALOAD) {
                        throw new IllegalStateException("invalid bridge method: unexpected opcode");
                    }
                    VarInsnNode aloadThis = (VarInsnNode) insn;
                    if (aloadThis.var != 0) {
                        throw new IllegalStateException("invalid bridge method: unexpected variable loaded");
                    }
                    insn = insn.getNext();
                    if (insn.getOpcode() != Opcodes.ALOAD) {
                        throw new IllegalStateException("invalid bridge method: unexpected opcode");
                    }
                    insn = insn.getNext();
                    if (insn.getOpcode() != Opcodes.CHECKCAST) {
                        throw new IllegalStateException("invalid bridge method: unexpected opcode");
                    }
                    insn = insn.getNext();
                    if (insn.getOpcode() != Opcodes.ALOAD) {
                        throw new IllegalStateException("invalid bridge method: unexpected opcode");
                    }
                    insn = insn.getNext();
                    if (insn.getOpcode() != Opcodes.CHECKCAST) {
                        throw new IllegalStateException("invalid bridge method: unexpected opcode");
                    }
                    insn = insn.getNext();
                    if (insn.getOpcode() != Opcodes.INVOKEVIRTUAL) {
                        throw new IllegalStateException("invalid bridge method: unexpected opcode");
                    }
                    MethodInsnNode invokevirtual = (MethodInsnNode) insn;
                    insn = insn.getNext();
                    if (insn.getOpcode() != Opcodes.IRETURN) {
                        throw new IllegalStateException("invalid bridge method: unexpected opcode");
                    }
                    boolean methodCallIsInvalid = true;
                    for (MethodNode m : node.methods) {
                        if (m.name.equals(invokevirtual.name) && m.desc.equals(invokevirtual.desc)) {
                            methodCallIsInvalid = false;
                            break;
                        }
                    }
                    if (methodCallIsInvalid) {
                        if (resolveTRArtifact) {
                            // Tiny remapper artifact
                            invokevirtual.name = "compare";
                        } else {
                            throw new IllegalStateException("invalid bridge method: method does not exist (consider setting resolveTRArtifact to true)");
                        }
                    }
                    String generics = invokevirtual.desc.substring(1, invokevirtual.desc.indexOf(';'));
                    node.signature = "Ljava/lang/Object;Ljava/util/Comparator<" + generics + ";>;";
                    method.access |= Opcodes.ACC_BRIDGE;
                    break;
                }
            }
        }
    }

    /**
     * Resolve useless &lt;unknown&gt; mentions when quiltflower decompiles enhanced for loops that
     * loop on arrays by adding their respective LVT entries via guessing.
     * However since this requires the knowledge of the array type, this may not always be successful.
     *<br/>
     * As this modifies the LVT entries, it should be called AFTER {@link #fixParameterLVT()}.
     *
     * @return The amount of added LVTs
     */
    public int fixForeachOnArray() {
        int addedLVTs = 0;

        for (ClassNode node : nodes) {
            for (MethodNode method : node.methods) {
                AbstractInsnNode instruction = method.instructions.getFirst();
                while (instruction != null) {
                    if (instruction instanceof VarInsnNode && OPHelper.isVarStore(instruction.getOpcode())) {
                        VarInsnNode arrayStore = (VarInsnNode) instruction;
                        AbstractInsnNode next = arrayStore.getNext();
                        // Ensure that the variable that was just stored is reloaded again
                        if (!(next instanceof VarInsnNode && OPHelper.isVarLoad(next.getOpcode())
                                && ((VarInsnNode) next).var == arrayStore.var)) {
                            instruction = next;
                            continue;
                        }
                        // the array length needs to be obtained & stored
                        next = next.getNext();
                        if (!(next instanceof InsnNode && next.getOpcode() == Opcodes.ARRAYLENGTH)) {
                            instruction = next;
                            continue;
                        }
                        next = next.getNext();
                        if (!(next instanceof VarInsnNode && next.getOpcode() == Opcodes.ISTORE)) {
                            instruction = next;
                            continue;
                        }
                        VarInsnNode arrayLengthStore = (VarInsnNode) next;
                        next = next.getNext();
                        // the array index needs to be initialized and stored
                        if (!(next instanceof InsnNode && next.getOpcode() == Opcodes.ICONST_0)) {
                            // is not the init process
                            instruction = next;
                            continue;
                        }
                        next = next.getNext();
                        if (!(next instanceof VarInsnNode && next.getOpcode() == Opcodes.ISTORE)) {
                            // does not store the loop index
                            instruction = next;
                            continue;
                        }
                        VarInsnNode indexStore = (VarInsnNode) next;
                        next = next.getNext();
                        // This is the loop starting point
                        while (next instanceof FrameNode || next instanceof LabelNode) {
                            next = next.getNext();
                        }
                        // The index needs to be loaded and compared do the array length
                        if (!(next instanceof VarInsnNode && next.getOpcode() == Opcodes.ILOAD
                                && ((VarInsnNode)next).var == indexStore.var)) {
                            instruction = next;
                            continue;
                        }
                        next = next.getNext();
                        if (!(next instanceof VarInsnNode && next.getOpcode() == Opcodes.ILOAD
                                && ((VarInsnNode)next).var == arrayLengthStore.var)) {
                            instruction = next;
                            continue;
                        }
                        next = next.getNext();
                        // The end of the loop statement
                        if (!(next instanceof JumpInsnNode && next.getOpcode() == Opcodes.IF_ICMPGE)) {
                            instruction = next;
                            continue;
                        }
                        JumpInsnNode jumpToEnd = (JumpInsnNode) next;
                        next = next.getNext();
                        // obtain array & loop index
                        if (!(next instanceof VarInsnNode && OPHelper.isVarLoad(next.getOpcode())
                                && ((VarInsnNode)next).var == arrayStore.var)) {
                            instruction = next;
                            continue;
                        }
                        VarInsnNode arrayLoad = (VarInsnNode) next;
                        next = next.getNext();
                        if (!(next instanceof VarInsnNode && next.getOpcode() == Opcodes.ILOAD
                                && ((VarInsnNode)next).var == indexStore.var)) {
                            instruction = next;
                            continue;
                        }
                        next = next.getNext();
                        // it should now proceed to actually obtain the referenced object
                        if (!(next instanceof InsnNode && OPHelper.isArrayLoad(next.getOpcode())
                                && OPHelper.isVarSimilarType(next.getOpcode(), arrayLoad.getOpcode()))) {
                            instruction = next;
                            continue;
                        }
                        next = next.getNext();
                        if (!(next instanceof VarInsnNode && OPHelper.isVarStore(next.getOpcode())
                                && OPHelper.isVarSimilarType(next.getOpcode(), arrayStore.getOpcode()))) {
                            instruction = next;
                            continue;
                        }
                        VarInsnNode objectStore = (VarInsnNode) next;
                        next = next.getNext();
                        instruction = next; // There may be nested loops - we want to take a lookout for them
                        // This is now defenitely a for loop on an array. This does not mean however
                        // that it is a foreach loop, which is the kind of loop we were searching for.
                        // There is at least one operation that invalidate the use of a foreach loop:
                        // - obtaining the loop index
                        // Obtaining the array contents might be another issue, but I don't think it qualifies
                        // as it could also be that the array was declared earlier
                        boolean validForEachLoop = true;
                        while (true) { // dangerous while (true) loop; but do not despair, it isn't as dangerous as you may believe
                            if (next == null) {
                                System.err.println("Method " + node.name + "." + method.name + method.desc + " has a cursed for loop.");
                                break;
                            }
                            if (next instanceof VarInsnNode && ((VarInsnNode)next).var == indexStore.var) {
                                validForEachLoop = false;
                                break;
                            }
                            if (next instanceof LabelNode && jumpToEnd.label.equals(next)) {
                                break;
                            }
                            next = next.getNext();
                        }
                        if (validForEachLoop) {
                            // So this is a valid foreach loop on an array!
                            // Grats, but now we need to determine the correct type for LVT.
                            // Since I did a mistake while designing this method, we already know
                            // where the loop came from, so that thankfully is not an issue (yay)
                            AbstractInsnNode previous = arrayStore.getPrevious();
                            if (previous == null) {
                                System.err.println("Method " + node.name + "." + method.name + method.desc + " has invalid bytecode.");
                                continue;
                            }
                            String arrayDesc = null;
                            if (previous instanceof MethodInsnNode) {
                                MethodInsnNode methodInvocation = (MethodInsnNode) previous;
                                arrayDesc = methodInvocation.desc.substring(methodInvocation.desc.lastIndexOf(')') + 1);
                            } else if (previous instanceof FieldInsnNode) {
                                arrayDesc = ((FieldInsnNode)previous).desc;
                            } else if (previous instanceof TypeInsnNode) {
                                if (previous.getOpcode() == Opcodes.ANEWARRAY) {
                                    arrayDesc = "[L" + ((TypeInsnNode)previous).desc + ";";
                                } else {
                                    arrayDesc = ((TypeInsnNode)previous).desc;
                                }
                            } else if (previous instanceof VarInsnNode) {
                                if (OPHelper.isVarLoad(previous.getOpcode())) {
                                    VarInsnNode otherArrayInstance = (VarInsnNode) previous;
                                    while (previous != null) {
                                        if (previous instanceof VarInsnNode
                                                && ((VarInsnNode) previous).var == otherArrayInstance.var
                                                && OPHelper.isVarStore(previous.getOpcode())) {
                                            AbstractInsnNode origin = previous.getPrevious();
                                            if (origin instanceof VarInsnNode && OPHelper.isVarLoad(origin.getOpcode())) {
                                                // Ugh...
                                                otherArrayInstance = (VarInsnNode) origin;
                                                continue;
                                            } else if (origin instanceof MethodInsnNode) {
                                                MethodInsnNode methodInvocation = (MethodInsnNode) origin;
                                                arrayDesc = methodInvocation.desc.substring(methodInvocation.desc.lastIndexOf(')') + 1);
                                                break;
                                            } else if (origin instanceof FieldInsnNode) {
                                                arrayDesc = ((FieldInsnNode)origin).desc;
                                                break;
                                            } else if (origin instanceof TypeInsnNode) {
                                                if (origin.getOpcode() == Opcodes.ANEWARRAY) {
                                                    arrayDesc = "[L" + ((TypeInsnNode)origin).desc + ";";
                                                } else {
                                                    arrayDesc = ((TypeInsnNode)origin).desc;
                                                }
                                                break;
                                            } else {
                                                // I have come to the conclusion that it isn't worth the effort to attempt to recover the
                                                // type of the variable here
                                                // This is as it is likely that the array is hidden deep in the stack before it was stored
                                                break;
                                            }
                                        }
                                        previous = previous.getPrevious();
                                    }
                                }
                            }
                            if (arrayDesc != null) {
                                if (arrayDesc.charAt(0) != '[') {
                                    System.err.println("Method " + node.name + "." + method.name + method.desc + " has invalid bytecode.");
                                    System.err.println("Guessed type: " + arrayDesc + ", but expected an array. Array found at index " + arrayStore.var);
                                    continue;
                                }
                                // Copy my Quiltflower rant from the other genericsfixing method
                                // Actually - it might be for the better as otherwise I would have to spend my time checking if the LVT entry already exists
                                LabelNode startObjectStoreLabel = new LabelNode();
                                method.instructions.insertBefore(objectStore, startObjectStoreLabel);
                                LocalVariableNode localVar = new LocalVariableNode("var" + objectStore.var,
                                        arrayDesc.substring(1), null, startObjectStoreLabel, jumpToEnd.label, objectStore.var);
                                method.localVariables.add(localVar);
                                addedLVTs++;
                            }
                        }
                        continue;
                    }
                    instruction = instruction.getNext();
                }
            }
        }

        return addedLVTs;
    }

    /**
     * Guesses the inner classes from class nodes
     */
    public void fixInnerClasses() {
        Map<String, InnerClassNode> splitInner = new HashMap<>();
        Set<String> enums = new HashSet<>();
        Map<String, List<InnerClassNode>> parents = new HashMap<>();

        // Initial indexing sweep
        for (ClassNode node : nodes) {
            parents.put(node.name, new ArrayList<>());
            if (node.superName.equals("java/lang/Enum")) {
                enums.add(node.name); // Register enum
            }
        }
        // Second sweep
        for (ClassNode node : nodes) {
            // Sweep enum members
            if (enums.contains(node.superName)) {
                // Child of (abstract) enum
                boolean skip = false;
                for (InnerClassNode innerNode : node.innerClasses) {
                    if (node.name.equals(innerNode.name)) {
                        skip = true;
                        break;
                    }
                }
                if (!skip) {
                    // Apply fixup
                    // We are using 16400 for access, but are there times where this is not wanted?
                    // 16400 = ACC_FINAL | ACC_ENUM
                    InnerClassNode innerNode = new InnerClassNode(node.name, null, null, 16400);
                    parents.get(node.superName).add(innerNode);
                    node.outerClass = node.superName;
                    node.innerClasses.add(innerNode);
                }
            } else if (node.name.contains("$")) {
                // Partially unobfuscated inner class.

                // This operation cannot be performed during the first sweep
                boolean skip = false;
                for (InnerClassNode innernode : node.innerClasses) {
                    if (innernode.name.equals(node.name)) {
                        skip = true;
                        break;
                    }
                }
                if (!skip) {
                    int lastSeperator = node.name.lastIndexOf('$');
                    String outerNode = node.name.substring(0, lastSeperator++);
                    String innerMost = node.name.substring(lastSeperator);
                    InnerClassNode innerClassNode;
                    if (innerMost.matches("^[0-9]+$")) {
                        // Anonymous class
                        // We know that ACC_SUPER is invalid for inner classes, so we remove that flag
                        innerClassNode = new InnerClassNode(node.name, null, null, node.access & ~Opcodes.ACC_SUPER);
                        node.outerClass = outerNode;
                    } else {
                        // We need to check for static inner classes.
                        // We already know that anonymous classes can never be static classes by definition,
                        // So we can skip that step for anonymous classes
                        boolean staticInnerClass = false;
                        boolean implicitStatic = false;
                        // Interfaces, Enums and Records are implicitly static
                        if (!staticInnerClass) {
                            staticInnerClass = (node.access & Opcodes.ACC_INTERFACE) != 0
                                    || (node.access & Opcodes.ACC_RECORD) != 0
                                    || ((node.access & Opcodes.ACC_ENUM) != 0 && node.superName.equals("java/lang/Enum"));
                            implicitStatic = staticInnerClass;
                        }
                        // Member classes of interfaces are implicitly static
                        if (!staticInnerClass) {
                            ClassNode outerClassNode = nameToNode.get(outerNode);
                            staticInnerClass = outerClassNode != null && (outerClassNode.access & Opcodes.ACC_INTERFACE) != 0;
                            implicitStatic = staticInnerClass;
                        }
                        // The constructor of non-static inner classes must take in an instance of the outer class an
                        // argument
                        if (!staticInnerClass && outerNode != null) {
                            boolean staticConstructor = false;
                            for (MethodNode method : node.methods) {
                                if (method.name.equals("<init>")) {
                                    int outernodeLen = outerNode.length();
                                    if (outernodeLen + 2 > method.desc.length()) {
                                        // The reference to the outer class cannot be passed in via a parameter as there
                                        // i no space for it in the descriptor, so the class has to be static
                                        staticConstructor = true;
                                        break;
                                    }
                                    String arg = method.desc.substring(2, outernodeLen + 2);
                                    if (!arg.equals(outerNode)) {
                                        // Has to be static. The other parameters are irrelevant as the outer class
                                        // reference is always at first place.
                                        staticConstructor = true;
                                        break;
                                    }
                                }
                            }
                            if (staticConstructor) {
                                staticInnerClass = true;
                                implicitStatic = false;
                            }
                        }
                        if (staticInnerClass && !implicitStatic) {
                            for (FieldNode field : node.fields) {
                                if ((field.access & Opcodes.ACC_FINAL) != 0 && field.name.startsWith("this$")) {
                                    System.err.println("Falsely identified " + node.name + " as static inner class.");
                                    staticInnerClass = false;
                                }
                            }
                        }

                        int innerClassAccess = node.access & ~Opcodes.ACC_SUPER; // Super is not allowed for inner class nodes

                        // Don't fall to the temptation of adding ACC_STATIC to the class node.
                        // According the the ASM verifier it is not legal to do so. However the JVM does not seem care
                        // Nonetheless, we are not adding it the access flags of the class, though we will add it in the inner
                        // class node
                        if (!staticInnerClass) {
                            // Beware of https://docs.oracle.com/javase/specs/jls/se16/html/jls-8.html#jls-8.1.3
                            node.outerClass = outerNode;
                        } else {
                            innerClassAccess |= Opcodes.ACC_STATIC;
                        }
                        innerClassNode = new InnerClassNode(node.name, outerNode, innerMost, innerClassAccess);
                    }
                    parents.get(outerNode).add(innerClassNode);
                    splitInner.put(node.name, innerClassNode);
                    node.innerClasses.add(innerClassNode);
                }
            }
        }
        for (ClassNode node : nodes) {
            // General sweep
            Collection<InnerClassNode> innerNodesToAdd = new ArrayList<>();
            for (FieldNode field : node.fields) {
                String descriptor = field.desc;
                if (descriptor.length() < 4) {
                    continue; // Most likely a primitive
                }
                if (descriptor.charAt(0) == '[') {
                    // Array
                    descriptor = descriptor.substring(2, descriptor.length() - 1);
                } else {
                    // Non-array
                    descriptor = descriptor.substring(1, descriptor.length() - 1);
                }
                InnerClassNode innerNode = splitInner.get(descriptor);
                if (innerNode != null) {
                    if (innerNode.innerName == null && !field.name.startsWith("this$")) {
                        // Not fatal, but worrying
                        System.err.println(String.format("Unlikely field descriptor for field \"%s\" with descriptor %s in class %s", field.name, field.desc, node.name));
                    }
                    innerNodesToAdd.add(innerNode);
                }
            }
            // Apply inner nodes
            HashSet<String> entryNames = new HashSet<>();
            for (InnerClassNode inner : innerNodesToAdd) {
                if (entryNames.add(inner.name)) {
                    node.innerClasses.add(inner);
                }
            }
        }
        // Add inner classes to the parent of the anonymous classes
        for (Entry<String, List<InnerClassNode>> entry : parents.entrySet()) {
            // Remove duplicates
            HashSet<String> entryNames = new HashSet<>();
            ArrayList<InnerClassNode> toRemove = new ArrayList<>();
            for (InnerClassNode inner : entry.getValue()) {
                if (!entryNames.add(inner.name)) {
                    toRemove.add(inner);
                }
            }
            toRemove.forEach(entry.getValue()::remove);
            ClassNode node = nameToNode.get(entry.getKey());
            for (InnerClassNode innerEntry : entry.getValue()) {
                boolean skip = false;
                for (InnerClassNode inner : node.innerClasses) {
                    if (inner.name.equals(innerEntry.name)) {
                        skip = true;
                        break;
                    }
                }
                if (!skip) {
                    node.innerClasses.add(innerEntry);
                }
            }
        }
    }

    /**
     * Method that tries to put the Local Variable Table (LVT) in a acceptable state
     * by synchronising parameter declarations with lvt declarations. Does not do
     * anything to the LVT is the LVT is declared but empty, which is a sign of the
     * usage of obfuscation tools.
     * It is intended to be used in combination with decompilers such as quiltflower
     * but might not be useful for less naive decompilers such as procyon, which do not decompile
     * into incoherent java code if the LVT is damaged.
     */
    public void fixParameterLVT() {
        for (ClassNode node : nodes) {
            for (MethodNode method : node.methods) {
                List<LocalVariableNode> locals = method.localVariables;
                List<ParameterNode> params = method.parameters;
                if (method.desc.indexOf(')') == 1 && params == null) {
                    // since the description starts with a '(' we don't need to check that one
                    // a closing parenthesis after the opening one suggests that there are no input parameters.
                    continue;
                }
                if ((method.access & Opcodes.ACC_ABSTRACT) != 0) {
                    // abstract methods do not have any local variables apparently.
                    // It makes sense however given that abstract methods do not have a method body
                    // where local variables could be declared
                    continue;
                }
                if (!Objects.requireNonNull(locals).isEmpty()) {
                    // LVTs that have been left alone by the obfuscator will have at least one declared local
                    continue;
                }

                if (params == null) {
                    method.parameters = new ArrayList<>();
                    params = method.parameters;
                    // Generate method parameter array
                    DescString description = new DescString(method.desc);
                    List<String> types = new ArrayList<>();
                    while (description.hasNext()) {
                        types.add(description.nextType());
                    }
                    Set<String> existingTypes = new HashSet<>();
                    Set<String> duplicateTypes = new HashSet<>();
                    duplicateTypes.add("Ljava/lang/Class;"); // class is a keyword
                    boolean oneArray = false;
                    boolean multipleArrays = false;
                    for (String type : types) {
                        if (type.charAt(0) == '[') {
                            if (oneArray) {
                                multipleArrays = true;
                            } else {
                                oneArray = true;
                            }
                        } else {
                            if (!existingTypes.add(type)) {
                                duplicateTypes.add(type);
                            }
                        }
                    }
                    for (int i = 0; i < types.size(); i++) {
                        String type = types.get(i);
                        String name = null;
                        switch (type.charAt(0)) {
                        case 'L':
                            int cutOffIndex = Math.max(type.lastIndexOf('/'), type.lastIndexOf('$')) + 1;
                            name = Character.toString(Character.toLowerCase(type.codePointAt(cutOffIndex))) + type.substring(cutOffIndex + 1, type.length() - 1);
                            if (name.length() < 3) {
                                name = "argument"; // This reduces the volatility of obfuscated code
                            }
                            if (duplicateTypes.contains(type)) {
                                name += i;
                            }
                            break;
                        case '[':
                            if (multipleArrays) {
                                name = "arr" + i;
                            } else {
                                name = "arr";
                            }
                            break;
                        case 'F': // float
                            name = "float" + i;
                            break;
                        case 'D': // double
                            name = "double" + i;
                            break;
                        case 'Z': // boolean
                            name = "boolean" + i;
                            break;
                        case 'B': // byte
                            name = "byte" + i;
                            break;
                        case 'C': // char
                            if (duplicateTypes.contains(type)) {
                                name = "character" + i;
                            } else {
                                name = "character";
                            }
                            break;
                        case 'S': // short
                            name = "short" + i;
                            break;
                        case 'I': // integer
                            if (duplicateTypes.contains(type)) {
                                name = "integer" + i;
                            } else {
                                name = "integer";
                            }
                            break;
                        case 'J': // long
                            name = "long" + i;
                            break;
                        default:
                            throw new IllegalStateException("Unknown type: " + type);
                        }
                        params.add(new ParameterNode(Objects.requireNonNull(name), 0));
                    }
                }

                int localVariableIndex = 0;
                if ((method.access & Opcodes.ACC_STATIC) == 0) {
                    localVariableIndex++;
                }
                DescString description = new DescString(method.desc);

                // since we can only guess when the parameters are used and when they are not
                // it only makes sense that we are cheating here and declaring empty label nodes.
                // Apparently both ASM and quiltflower accept this, so /shrug
                LabelNode start = new LabelNode();
                LabelNode end = new LabelNode();
                for (int i = 0; i < params.size(); i++) {
                    String type = description.nextType();
                    LocalVariableNode a = new LocalVariableNode(params.get(i).name,
                            type,
                            null, // we can only guess about the signature, so it'll be null
                            start,
                            end,
                            localVariableIndex);
                    char c = type.charAt(0);
                    if (c == 'D' || c == 'J') {
                        // doubles and longs take two frames on the stack. Makes sense, I know
                        localVariableIndex += 2;
                    } else {
                        localVariableIndex++;
                    }
                    locals.add(a);
                }
            }
        }
    }

    /**
     * Method that tries to restore the SwitchMaps to how they should be.
     * This includes marking the switchmap classes as anonymous classes, so they may not be referenceable
     * afterwards.
     *
     * @return The amount of classes who were identified as switch maps.
     */
    public int fixSwitchMaps() {
        Map<FieldReference, String> deobfNames = new HashMap<>(); // The deobf name will be something like $SwitchMap$org$bukkit$Material

        // index switch map classes - or at least their candidates
        for (ClassNode node : nodes) {
            if (node.superName != null && node.superName.equals("java/lang/Object") && node.interfaces.isEmpty()) {
                if (node.fields.size() == 1 && node.methods.size() == 1) {
                    MethodNode method = node.methods.get(0);
                    FieldNode field = node.fields.get(0);
                    if (method.name.equals("<clinit>") && method.desc.equals("()V")
                            && field.desc.equals("[I")
                            && (field.access & Opcodes.ACC_STATIC) != 0) {
                        FieldReference fieldRef = new FieldReference(node.name, field);
                        String enumName = null;
                        AbstractInsnNode instruction = method.instructions.getFirst();
                        while (instruction != null) {
                            if (instruction instanceof FieldInsnNode && instruction.getOpcode() == Opcodes.GETSTATIC) {
                                FieldInsnNode fieldInstruction = (FieldInsnNode) instruction;
                                if (fieldRef.equals(new FieldReference(fieldInstruction))) {
                                    AbstractInsnNode next = instruction.getNext();
                                    while (next instanceof FrameNode || next instanceof LabelNode) {
                                        // ASM is sometimes not so nice
                                        next = next.getNext();
                                    }
                                    if (next instanceof FieldInsnNode && next.getOpcode() == Opcodes.GETSTATIC) {
                                        if (enumName == null) {
                                            enumName = ((FieldInsnNode) next).owner;
                                        } else if (!enumName.equals(((FieldInsnNode) next).owner)) {
                                            enumName = null;
                                            break; // It may not be a switchmap field
                                        }
                                    }
                                }
                            }
                            instruction = instruction.getNext();
                        }
                        if (enumName != null) {
                            if (fieldRef.getName().indexOf('$') == -1) {
                                // The deobf name will be something like $SwitchMap$org$bukkit$Material
                                String newName = "$SwitchMap$" + enumName.replace('/', '$');
                                deobfNames.put(fieldRef, newName);
                                instruction = method.instructions.getFirst();
                                // Remap references within this class
                                while (instruction != null) {
                                    if (instruction instanceof FieldInsnNode) {
                                        FieldInsnNode fieldInsn = (FieldInsnNode) instruction;
                                        if ((fieldInsn.getOpcode() == Opcodes.GETSTATIC || fieldInsn.getOpcode() == Opcodes.PUTSTATIC)
                                                && fieldInsn.owner.equals(node.name)
                                                && fieldRef.equals(new FieldReference(fieldInsn))) {
                                            fieldInsn.name = newName;
                                        }
                                    }
                                    instruction = instruction.getNext();
                                }
                                // Remap the actual field declaration
                                // Switch maps can only contain a single field and we have already obtained said field, so it isn't much of a deal here
                                field.name = newName;
                            }
                        }
                    }
                }
            }
        }

        // Rename references to the field
        for (ClassNode node : nodes) {
            Set<String> addedInnerClassNodes = new HashSet<>();
            for (MethodNode method : node.methods) {
                AbstractInsnNode instruction = method.instructions.getFirst();
                while (instruction != null) {
                    if (instruction instanceof FieldInsnNode && instruction.getOpcode() == Opcodes.GETSTATIC) {
                        FieldInsnNode fieldInstruction = (FieldInsnNode) instruction;
                        if (fieldInstruction.owner.equals(node.name)) { // I have no real idea what I was doing here
                            instruction = instruction.getNext();
                            continue;
                        }
                        FieldReference fRef = new FieldReference(fieldInstruction);
                        String newName = deobfNames.get(fRef);
                        if (newName != null) {
                            fieldInstruction.name = newName;
                            if (!addedInnerClassNodes.contains(fRef.getOwner())) {
                                InnerClassNode innerClassNode = new InnerClassNode(fRef.getOwner(), node.name, null, Opcodes.ACC_STATIC ^ Opcodes.ACC_SYNTHETIC ^ Opcodes.ACC_FINAL);
                                ClassNode outerNode = nameToNode.get(fRef.getOwner());
                                if (outerNode != null) {
                                    outerNode.innerClasses.add(innerClassNode);
                                }
                                ClassNode outermostClassnode = null;
                                if (node.outerClass != null) {
                                    outermostClassnode = nameToNode.get(node.outerClass);
                                }
                                if (outermostClassnode == null) {
                                    for (InnerClassNode inner : node.innerClasses) {
                                        if (inner.name.equals(node.name) && inner.outerName != null) {
                                            outermostClassnode = nameToNode.get(inner.outerName);
                                            break;
                                        }
                                    }
                                }
                                if (outermostClassnode != null) {
                                    outermostClassnode.innerClasses.add(innerClassNode);
                                }
                                node.innerClasses.add(innerClassNode);
                            }
                        }
                    }
                    instruction = instruction.getNext();
                }
            }
        }

        return deobfNames.size();
    }

    public List<ClassNode> getClassNodesDirectly() {
        return nodes;
    }

    /**
     * Guesses the should-be inner classes of classes based on the usages of the class.
     * This only guesses anonymous classes based on the code, but not based on the name.
     * It also does not require the synthetic fields for the anonymous classes, so it may
     * do mismatches from time to time.
     *
     * <p>It will not overwrite already existing relations and will not perform any changes
     * to the class nodes.
     * Inner class nodes must therefore be applied manually. It is advisable to run
     * {@link Oaktree#applyInnerclasses()} afterwards.
     *
     * @author Geolykt
     * @return The returned map will have the outer class as the key and the outer method as the desc.
     */
    @Contract(value = "-> new", pure = true)
    public Map<String, MethodReference> guessAnonymousClasses() {
        Map<String, MethodReference> anonymousClasses = new HashMap<>();

        Set<String> potentialAnonymousClasses = new HashSet<>();
        Set<FieldReference> syntheticFields = new HashSet<>();

        nodeLoop:
        for (ClassNode node : nodes) {
            if ((node.access & VISIBILITY_MODIFIERS) != 0) {
                continue;
            }
            if (node.innerClasses != null) {
                for (InnerClassNode icn : node.innerClasses) {
                    if (icn.name.equals(node.name)) {
                        continue nodeLoop;
                    }
                }
            }
            potentialAnonymousClasses.add(node.name);
            for (FieldNode field : node.fields) {
                if ((field.access & Opcodes.ACC_SYNTHETIC) != 0) {
                    syntheticFields.add(new FieldReference(node.name, field));
                }
            }
        }

        for (ClassNode node : nodes) {
            for (FieldNode field : node.fields) {
                if ((field.access & Opcodes.ACC_SYNTHETIC) != 0) {
                    continue;
                }
                String className = getClassName(field.desc);
                if (className == null) {
                    continue;
                }
                potentialAnonymousClasses.remove(className);
                anonymousClasses.remove(className);
            }

            for (MethodNode method : node.methods) {
                DescString descString = new DescString(method.desc);
                while (descString.hasNext()) {
                    String className = getClassName(descString.nextType());
                    if (className != null && !className.equals(node.name)) {
                        potentialAnonymousClasses.remove(className);
                        anonymousClasses.remove(className);
                    }
                }

                if (method.instructions != null) {
                    AbstractInsnNode insn = method.instructions.getFirst();
                    while (insn != null) {
                        if (insn instanceof FieldInsnNode) {
                            FieldInsnNode fieldInsn = (FieldInsnNode) insn;
                            if (!syntheticFields.contains(new FieldReference(fieldInsn))) {
                                String className = getClassName(fieldInsn.desc);
                                if (className != null && !className.equals(node.name)) {
                                    potentialAnonymousClasses.remove(className);
                                    anonymousClasses.remove(className);
                                }
                                className = fieldInsn.owner;
                                if (className != null && !className.equals(node.name)) {
                                    potentialAnonymousClasses.remove(className);
                                    anonymousClasses.remove(className);
                                }
                            }
                        } else if (insn instanceof MethodInsnNode) {
                            MethodInsnNode methodInsn = (MethodInsnNode) insn;
                            if (methodInsn.name.equals("<init>")) {
                                if (anonymousClasses.containsKey(methodInsn.owner)) {
                                    potentialAnonymousClasses.remove(methodInsn.owner);
                                    anonymousClasses.remove(methodInsn.owner);
                                } else if (potentialAnonymousClasses.contains(methodInsn.owner)) {
                                    if (methodInsn.desc.startsWith(node.name, 2)) {
                                        anonymousClasses.put(methodInsn.owner, new MethodReference(node.name, method));
                                    } else {
                                        anonymousClasses.remove(methodInsn.owner);
                                        potentialAnonymousClasses.remove(methodInsn.owner);
                                    }
                                }
                            } else {
                                String returnClass = getReturnedClass(methodInsn.desc);
                                if (returnClass != null) {
                                    potentialAnonymousClasses.remove(returnClass);
                                    anonymousClasses.remove(returnClass);
                                }
                                potentialAnonymousClasses.remove(methodInsn.owner);
                                anonymousClasses.remove(methodInsn.owner);
                            }
                        }
                        insn = insn.getNext();
                    }
                }
            }
        }
        return anonymousClasses;
    }

    /**
     * Analyses the likely generic type of the return value of methods. The return value may only be an instance
     * of {@link Collection} or an implementation of it (as defined through
     * {@link ClassWrapperPool#isImplementingInterface(ClassWrapper, String)}).
     *
     * <p>To reduce the imprecision of the used algorithm following filters are put in place:
     * <ul>
     *   <li>The method must be static</li>
     *   <li>The method must not already have a signature</li>
     *   <li>The method must not have arguments</li>
     * </ul>
     * Due to these constraints only few methods will match. Furthermore this method internally uses rather expensive
     * tools such as the {@link StackWalker} and the {@link ClassWrapperPool}, which means using this method
     * may not always be beneficial.
     *
     * <p>This method does not modify the signatures of methods directly. This operation
     * would need to be done by an external method.
     *
     * @return A map that stores the location of the method as the key and the signature type as the value.
     * @author Geolykt
     */
    public Map<MethodReference, ClassWrapper> analyseLikelyMethodReturnCollectionGenerics() {
        Map<MethodReference, ClassWrapper> signatures = new HashMap<>();
        Map<StackElement, ClassWrapper> stackSignatureTypes = new HashMap<>();

        for (ClassNode node : nodes) {
            for (MethodNode method : node.methods) {
                if ((method.access & Opcodes.ACC_STATIC) == 0 || method.signature != null) {
                    continue;
                }
                String returnedClass = getReturnedClass(method.desc);
                if (returnedClass == null) {
                    continue;
                }
                ClassWrapper returnType = wrapperPool.optGet(returnedClass);
                if (returnType == null || !returnType.getAllImplementatingInterfaces().contains("java/util/Collection")) {
                    continue;
                }

                stackSignatureTypes.clear();
                MethodReference methodRef = new MethodReference(node.name, method);
                StackWalker.walkStack(node, method, new StackWalkerConsumer() {

                    @Override
                    public void preCalculation(AbstractInsnNode instruction, LIFOQueue<StackElement> stack) {
                        if (instruction instanceof MethodInsnNode) {
                            MethodInsnNode methodInsn = (MethodInsnNode) instruction;
                            if (methodInsn.name.equals("add")) {
                                ClassWrapper methodOwner = wrapperPool.get(methodInsn.owner);
                                if (methodOwner.getAllImplementatingInterfaces().contains("java/util/Collection")) {
                                    StackElement collection = stack.getDelegateList().get(1);
                                    StackElement insertedElement = stack.getHead();
                                    ClassWrapper oldSignature = stackSignatureTypes.get(collection);
                                    // TODO this does not treat arrays well
                                    ClassWrapper insertedElementWrapper = wrapperPool.get(insertedElement.type.substring(1, insertedElement.type.length() - 1));
                                    ClassWrapper wrapper;
                                    if (oldSignature != null) {
                                        wrapper = wrapperPool.getCommonSuperClass(insertedElementWrapper, oldSignature);
                                    } else {
                                        wrapper = insertedElementWrapper;
                                    }
                                    stackSignatureTypes.put(collection, wrapper);
                                }
                            }
                        } else if (instruction.getOpcode() == Opcodes.ARETURN) {
                            // TODO if possible, calculate the LVT for the collections
                            ClassWrapper mergeSignature = stackSignatureTypes.get(stack.getHead());
                            if (mergeSignature != null) {
                                ClassWrapper oldSignature = signatures.get(methodRef);
                                if (oldSignature == null) {
                                    signatures.put(methodRef, mergeSignature);
                                } else {
                                    signatures.put(methodRef, wrapperPool.getCommonSuperClass(oldSignature, mergeSignature));
                                }
                            }
                        }
                    }

                    @Override
                    public void postCalculation(AbstractInsnNode instruction, LIFOQueue<StackElement> stack) {
                        // Not needed
                    }
                });
            }
        }

        signatures.values().removeIf(wrapper -> wrapper.getSuper() == null);
        return signatures;
    }

    /**
     * Guesses which classes are a local class within a certain class.
     * Other than anonymous classes, local classes can be references within the entire block.
     * This method only supports guessing local classes within classes (may they be nested or not),
     * however can also lead to it marking anonymous classes as local classes.
     *
     * <p>The names of the local classes are inferred from outer class, the given prefix and their name compared
     * to other local classes.
     *
     * <p>This method does not check for collision
     *
     * <p>This method will internally allow for large amounts of nesting - at performance cost,
     * however should a recursive nest be assumed, the nest (and all nests depending on the nest) will be
     * <b>silently discarded</b>.
     *
     * @param localClassNamePrefix The prefix to use for naming local classes
     * @param condition            A predicate that is called for each proposed mapping.
     *                             First argument is the outer class, second argument the inner class.
     *                             Should the predicate yield false, the relation is discarded.
     * @param applyInnerClassNodes Whether to apply {@link InnerClassNode} to the inner AND outer class nodes.
     *                             Making it false may require {@link #fixInnerClasses()} to be applied.
     * @return A map storing proposed renames of the inner classes.
     * @author Geolykt
     */
    public Map<String, String> getProposedLocalClassNames(final String localClassNamePrefix, BiPredicate<String, String> condition, boolean applyInnerClassNodes) {
        Map<String, List<String>> mappings = new HashMap<>();
        Set<String> unmappedInnerClasses = new HashSet<>();
        guessLocalClasses().forEach((inner, outer) -> {
            if (!condition.test(outer, inner)) {
                return;
            }
            mappings.compute(outer, (key, list) -> {
                if (list == null) {
                    list = new ArrayList<>();
                }
                list.add(inner);
                return list;
            });
            unmappedInnerClasses.add(inner);
        });
        Map<String, String> mappedNames = new HashMap<>();
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
                    String innerName = localClassNamePrefix + counter++;
                    if (applyInnerClassNodes) {
                        InnerClassNode icn = new InnerClassNode(inner, outer, innerName, innerNode.access);
                        outerNode.innerClasses.add(icn);
                        innerNode.innerClasses.add(icn);
                    }
                    mappedNames.put(inner, mappedNames.getOrDefault(outer, outer) + '$' + innerName);
                    unmappedInnerClasses.remove(inner);
                }
            });
            if (unmappedInnerClasses.size() == oldSize) {
                break; // Only nested pairs remaining - silently discard all
            }
        }
        return mappedNames;
    }

    /**
     * Guesses anonymous inner classes by checking whether they have a synthetic field and if they
     * do whether they are referenced only by a single "parent" class.
     * Note: this method is VERY aggressive when it comes to adding inner classes, sometimes it adds
     * inner classes on stuff where it wouldn't belong. This means that usage of this method should
     * be done wisely. This method will do some damage even if it does no good.
     *
     * @return The amount of guessed anonymous inner classes
     */
    public int guessAnonymousInnerClasses() {
        // FIXME while this code does an excellent job at what it should do, it does a terrible job
        // at what it should not do (removing the classes as root classes)

        // Class name -> referenced class, method
        // I am well aware that we are using method node, but given that there can be multiple methods with the same
        // name it is better to use MethodNode instead of String to reduce object allocation overhead.
        // Should we use triple instead? Perhaps.
        HashMap<String, Map.Entry<String, MethodNode>> candidates = new LinkedHashMap<>();
        for (ClassNode node : nodes) {
            if ((node.access & VISIBILITY_MODIFIERS) != 0) {
                continue; // Anonymous inner classes are always package-private
            }
            boolean skipClass = false;
            FieldNode outerClassReference = null;
            for (FieldNode field : node.fields) {
                if ((field.access & (Opcodes.ACC_SYNTHETIC | Opcodes.ACC_FINAL)) == (Opcodes.ACC_SYNTHETIC | Opcodes.ACC_FINAL)
                        && (field.access & VISIBILITY_MODIFIERS) == 0) {
                    if (outerClassReference != null) {
                        skipClass = true;
                        break; // short-circuit
                    }
                    outerClassReference = field;
                }
            }
            if (skipClass || outerClassReference == null) {
                continue;
            }
            // anonymous classes can only have a single constructor since they are only created at a single spot
            // However they also have to have a constructor so they can pass the outer class reference
            MethodNode constructor = null;
            for (MethodNode method : node.methods) {
                if (method.name.equals("<init>")) {
                    if (constructor != null) {
                        // cannot have multiple constructors
                        skipClass = true;
                        break; // short-circuit
                    }
                    if ((method.access & VISIBILITY_MODIFIERS) != 0) {
                        // The constructor should be package - protected
                        skipClass = true;
                        break;
                    }
                    constructor = method;
                }
            }
            if (skipClass || constructor == null) { // require a single constructor, not more, not less
                continue;
            }
            // since we have the potential reference to the outer class and we know that it has to be set
            // via the constructor's parameter, we can check whether this is the case here
            DescString desc = new DescString(constructor.desc);
            skipClass = true;
            while (desc.hasNext()) {
                String type = desc.nextType();
                if (type.equals(outerClassReference.desc)) {
                    skipClass = false;
                    break;
                }
            }
            if (skipClass) {
                continue;
            }
            int dollarIndex = node.name.indexOf('$');
            if (dollarIndex != -1 && !Character.isDigit(node.name.codePointAt(dollarIndex + 1))) {
                // Unobfuscated class that is 100% not anonymous
                continue;
            }
            candidates.put(node.name, null);
        }

        // Make sure that the constructor is only invoked in a single class, which should be the outer class
        for (ClassNode node : nodes) {
            for (MethodNode method : node.methods) {
                AbstractInsnNode instruction = method.instructions.getFirst();
                while (instruction != null) {
                    if (instruction instanceof MethodInsnNode && ((MethodInsnNode)instruction).name.equals("<init>")) {
                        MethodInsnNode methodInvocation = (MethodInsnNode) instruction;
                        String owner = methodInvocation.owner;
                        if (candidates.containsKey(owner)) {
                            if (owner.equals(node.name)) {
                                // this is no really valid anonymous class
                                candidates.remove(owner);
                            } else {
                                Map.Entry<String, MethodNode> invoker = candidates.get(owner);
                                if (invoker == null) {
                                    candidates.put(owner, Map.entry(node.name, method));
                                } else if (!invoker.getKey().equals(node.name)
                                        || !invoker.getValue().name.equals(method.name)
                                        || !invoker.getValue().desc.equals(method.desc)) {
                                    // constructor referenced by multiple classes, cannot be valid
                                    // However apparently these classes could be extended? I am not entirely sure how that is possible, but it is.
                                    // That being said, we are going to ignore that this is possible and just consider them invalid
                                    // as everytime this happens the decompiler is able to decompile the class without any issues.
                                    candidates.remove(owner);
                                }
                            }
                        }
                    }
                    instruction = instruction.getNext();
                }
            }
        }

        // If another class has a field reference to the potential anonymous class, and that field is not
        // synthetic, then the class is likely not anonymous.
        // In the future I could settle with not checking for the anonymous access flag, but this would
        // be quite the effort to get around nonetheless since previous steps of this method utilise
        // this access flag
        for (ClassNode node : nodes) {
            for (FieldNode field : node.fields) {
                if (field.desc.length() == 1 || (field.access & Opcodes.ACC_SYNTHETIC) != 0) {
                    continue;
                }
                if (field.desc.codePointAt(field.desc.lastIndexOf('[') + 1) != 'L') {
                    continue;
                }
                // Now technically, they are still inner classes. Just regular ones and they are not static ones
                // however not adding them as a inner class has no effect in recomplieabillity so we will not really care about it just yet.
                String className = field.desc.substring(field.desc.lastIndexOf('[') + 2, field.desc.length() - 1);
                candidates.remove(className);
            }
        }

        int addedInners = 0;
        for (Map.Entry<String, Map.Entry<String, MethodNode>> candidate : candidates.entrySet()) {
            String inner = candidate.getKey();
            Map.Entry<String, MethodNode> outer = candidate.getValue();
            if (outer == null) {
                continue;
            }
            ClassNode innerNode = nameToNode.get(inner);
            if (innerNode == null) {
                throw new IllegalStateException("Unable to find class: " + inner);
            }
            ClassNode outernode = nameToNode.get(outer.getKey());

            MethodNode outerMethod = outer.getValue();
            if (outernode == null) {
                continue;
            }
            boolean hasInnerClassInfoInner = false;
            boolean hasInnerClassInfoOuter = false;
            for (InnerClassNode icn : innerNode.innerClasses) {
                if (icn.name.equals(inner)) {
                    hasInnerClassInfoInner = true;
                    break;
                }
            }
            for (InnerClassNode icn : outernode.innerClasses) {
                if (icn.name.equals(inner)) {
                    hasInnerClassInfoOuter = true;
                    break;
                }
            }
            if (hasInnerClassInfoInner && hasInnerClassInfoOuter) {
                continue;
            }
            if (hasInnerClassInfoInner || hasInnerClassInfoOuter) {
                throw new IllegalStateException("Partially applied inner classes found");
            }
            // Used to be (16400 = ACC_FINAL | ACC_ENUM), but we ended up going with
            // just ACC_SUPER (0x20) instead as only that one really makes sense and is the only access
            // used for anonymous class (see https://gist.github.com/Geolykt/52a7917c279f90695f5afbe10105399a)
            InnerClassNode newInnerClassNode = new InnerClassNode(inner, outernode.name, null, Opcodes.ACC_SUPER);
            if (!hasInnerClassInfoInner) {
                innerNode.outerMethod = outerMethod.name;
                innerNode.outerMethodDesc = outerMethod.desc;
                innerNode.outerClass = outernode.name;
                innerNode.innerClasses.add(newInnerClassNode);
            }
            if (!hasInnerClassInfoOuter) {
                outernode.innerClasses.add(newInnerClassNode);
            }
            addedInners++;
        }

        return addedInners;
    }

    /**
     * Guesses the generic signatures of fields based on their usage. This might be inaccurate under
     * some circumstances, however it tries to play it as safe as possible.
     * The main algorithm in this method checks for generics via foreach iteration over the field
     * and searches for the CHECKCAST to determine the signature.
     * Note: this method should be invoked AFTER {@link #fixParameterLVT()}, invoking this method before it
     * would lead to LVT fixing not working properly
     *
     * @return The amount of added field signatures
     */
    public int guessFieldGenerics() {
        Map<FieldReference, SignatureNode> newFieldSignatures = new HashMap<>();

        int addedFieldSignatures = 0;
        // index signatureless fields
        for (ClassNode node : nodes) {
            for (FieldNode field : node.fields) {
                if (field.signature == null && ITERABLES.contains(field.desc)) {
                    newFieldSignatures.put(new FieldReference(node.name, field), null);
                }
            }
        }

        // guess signatures based on iterators
        for (ClassNode node : nodes) {
            for (MethodNode method : node.methods) {
                AbstractInsnNode instruction = method.instructions.getFirst();
                while (instruction != null) {
                    if (instruction instanceof FieldInsnNode) {
                        FieldInsnNode fieldNode = (FieldInsnNode) instruction;
                        FieldReference key = new FieldReference(fieldNode);
                        AbstractInsnNode next = instruction.getNext();
                        if (!newFieldSignatures.containsKey(key) // The field doesn't actively search for a new signature
                                || !(next instanceof MethodInsnNode)) { // We cannot work with this instruction
                            instruction = next;
                            continue;
                        }
                        MethodInsnNode iteratorMethod = (MethodInsnNode) next;
                        next = next.getNext();
                        // check whether the called method is Iterable#iterator
                        if (iteratorMethod.itf // definitely not it // FIXME huh?
                                || !iteratorMethod.name.equals("iterator")
                                || !iteratorMethod.desc.equals("()Ljava/util/Iterator;")
                                || !(next instanceof VarInsnNode)) { // We cannot work with this instruction
                            instruction = next;
                            continue;
                        }
                        // cache instruction for later. This instruction should store the iterator that was just obtained
                        VarInsnNode storeInstruction = (VarInsnNode) next;
                        next = next.getNext();
                        if (!(next instanceof LabelNode)) { // this is the label that marks the beginning of the loop
                            instruction = next;
                            continue;
                        }
                        // I *might* use this later, but right now we do not
                        // LabelNode loopStartLabel = (LabelNode) next;
                        next = next.getNext();
                        while ((next instanceof FrameNode) || (next instanceof LineNumberNode)) {
                            // filter out pseudo-instructions
                            next = next.getNext();
                        }
                        if (!(next instanceof VarInsnNode)) { // require the load instruction where the iterator will be obtained again
                            instruction = next;
                            continue;
                        }
                        VarInsnNode loadInstruction = (VarInsnNode) next;
                        next = next.getNext();
                        if (loadInstruction.var != storeInstruction.var // both instruction should load/save the same local
                                || loadInstruction.getOpcode() != Opcodes.ALOAD // the load instruction should actually load
                                || storeInstruction.getOpcode() != Opcodes.ASTORE // and the store instruction should actually store
                                || !(next instanceof MethodInsnNode)) { // we cannot work with this instruction
                            instruction = next;
                            continue;
                        }
                        MethodInsnNode hasNextInstruction = (MethodInsnNode) next;
                        next = next.getNext();
                        if (!hasNextInstruction.itf // iterator is an interface
                                || !hasNextInstruction.owner.equals("java/util/Iterator") // check whether this is the right method
                                || !hasNextInstruction.name.equals("hasNext")
                                || !hasNextInstruction.desc.equals("()Z")
                                || !(next instanceof JumpInsnNode)) { // it is pretty clear that this is a while loop now, but we have this for redundancy anyways
                            instruction = next;
                            continue;
                        }
                        JumpInsnNode loopEndJump = (JumpInsnNode) next;
                        LabelNode loopEndLabel = loopEndJump.label;
                        next = next.getNext();
                        if (!(next instanceof VarInsnNode)) { // require the load instruction where the iterator will be obtained again
                            instruction = next;
                            continue;
                        }
                        // redo the load instruction check
                        loadInstruction = (VarInsnNode) next;
                        next = next.getNext();
                        if (loadInstruction.var != storeInstruction.var // both instruction should load/save the same local
                                || loadInstruction.getOpcode() != Opcodes.ALOAD // the load instruction should actually load
                                || storeInstruction.getOpcode() != Opcodes.ASTORE // and the store instruction should actually store
                                || !(next instanceof MethodInsnNode)) { // we cannot work with this instruction
                            instruction = next;
                            continue;
                        }
                        MethodInsnNode getNextInstruction = (MethodInsnNode) next;
                        next = next.getNext();
                        if (!getNextInstruction.itf // iterator is an interface
                                || !getNextInstruction.owner.equals("java/util/Iterator") // check whether this is the right method
                                || !getNextInstruction.name.equals("next")
                                || !getNextInstruction.desc.equals("()Ljava/lang/Object;")
                                || !(next instanceof TypeInsnNode)) { // this instruction is the core of our check, and the holy grail - sadly it wasn't here. Hopefully we have better luck next time
                            instruction = next;
                            continue;
                        }
                        TypeInsnNode checkCastInstruction = (TypeInsnNode) next;
                        next = next.getNext();
                        if (checkCastInstruction.getOpcode() != Opcodes.CHECKCAST) {
                            // so close!
                            instruction = next;
                            continue;
                        }
                        String suggestion = "L" + checkCastInstruction.desc + ";";
                        SignatureNode suggestedSignature = new SignatureNode(fieldNode.desc, suggestion);
                        SignatureNode currentlySuggested = newFieldSignatures.get(key);
                        instruction = next;
                        if (currentlySuggested != null) {
                            if (!suggestedSignature.equals(currentlySuggested)) {
                                addedFieldSignatures--;
                                System.out.println("Contested signatures for " + key);
                                newFieldSignatures.remove(key);
                                continue;
                            }
                        } else {
                            addedFieldSignatures++;
                            newFieldSignatures.put(key, suggestedSignature);
                        }

                        // Add arbitrary LVT entries to reduce the amount of <unknown>
                        if (!(next instanceof VarInsnNode) || next.getOpcode() != Opcodes.ASTORE) {
                            // We don't have a variable to attach anything to (???) - not critical, so shrug
                            continue;
                        }
                        VarInsnNode iteratedObject = (VarInsnNode) next;
                        List<LocalVariableNode> localVars = method.localVariables;
                        boolean alreadyDeclaredLVT = false;
                        for (LocalVariableNode var0 : localVars) {
                            if (var0.index == iteratedObject.var && var0.desc.equals(suggestion)) {
                                alreadyDeclaredLVT = true;
                                break;
                            }
                        }
                        if (!alreadyDeclaredLVT) {
                            // Quiltflower has a bug where it does not correctly identify LVT entries
                            // and acts as if they weren't there. This precisely occurs as the decompiler
                            // expects that the start label provided by of the LVT entry is equal to the first declaration of the
                            // entry. While I have already brought forward a fix for this, unfortunately this results in a few other
                            // (more serious) issues that result in formerly broken but technically correct and compilable code
                            // being no longer compilable. This makes it unlikely that the fix would be pushed anytime soon.
                            // My assumption is that this has something to do with another bug in the decompiler,
                            // but in the meantime I guess that we will have to work around this bug by adding a LabelNode
                            // just before the first astore operation.
                            // Developers have to make sacrifices to attain perfection after all
                            LabelNode firstDeclaration = new LabelNode();
                            method.instructions.insertBefore(iteratedObject, firstDeclaration);
                            // add LVT entry for the iterator
                            LocalVariableNode lvtNode = new LocalVariableNode(
                                    "var" + iteratedObject.var, suggestion,
                                    null,
                                    firstDeclaration, loopEndLabel, iteratedObject.var);
                            localVars.add(lvtNode);
                        }
                        continue;
                    }
                    instruction = instruction.getNext();
                }
            }
        }

        // guess signatures based on Collection#add
        Map<FieldReference, Map.Entry<ClassWrapper, String>> collectionSignatures = new HashMap<>();

        for (ClassNode node : nodes) {
            for (MethodNode method : node.methods) {
                AbstractInsnNode insn = method.instructions.getFirst();
                while (insn != null) {
                    if (insn instanceof FieldInsnNode) {
                        AbstractInsnNode next = insn.getNext();
                        if (insn.getOpcode() != Opcodes.GETFIELD && insn.getOpcode() != Opcodes.GETSTATIC) {
                            insn = next;
                            continue;
                        }
                        FieldInsnNode fieldInsn = (FieldInsnNode) insn;
                        FieldReference fref = new FieldReference(fieldInsn);
                        if (newFieldSignatures.get(fref) != null) {
                            // Already mapped via iteration, which is deemed more safe than checking through .add
                            insn = next;
                            continue;
                        }
                        if (collectionSignatures.containsKey(fref) && collectionSignatures.get(fref) == null) {
                            // Inconclusive type
                            insn = next;
                            continue;
                        }
                        if (next == null || next.getOpcode() != Opcodes.NEW) {
                            insn = next;
                            continue;
                        }
                        TypeInsnNode newInsn = (TypeInsnNode) next;
                        next = next.getNext();
                        // FIXME this is a terrible and potentially dangerous solution
                        while (next != null) {
                            // FIXME arrays are not initialised that way
                            if (next.getOpcode() == Opcodes.INVOKESPECIAL && ((MethodInsnNode) next).name.equals("<init>")
                                    && ((MethodInsnNode) next).owner.equals(newInsn.desc)) {
                                break;
                            }
                            next = next.getNext();
                        }
                        if (next == null) {
                            insn = newInsn.getNext();
                            continue;
                        }
                        next = next.getNext();
                        if (next == null || !(next instanceof MethodInsnNode)) {
                            insn = insn.getNext();
                            continue;
                        }
                        MethodInsnNode collectionAdd = (MethodInsnNode) next;
                        if (!collectionAdd.name.equals("add") || !COLLECTIONS.contains("L" + collectionAdd.owner + ";")) {
                            insn = next;
                            continue;
                        }
                        Type type = Type.getObjectType(newInsn.desc);
                        String internalClassName;
                        if (type.getSort() == Type.ARRAY) {
                            internalClassName = type.getElementType().getInternalName();
                        } else {
                            internalClassName = type.getInternalName();
                        }
                        ClassWrapper wrapper = wrapperPool.get(internalClassName);
                        String signatureDesc;
                        Map.Entry<ClassWrapper, String> oldEntry = collectionSignatures.get(fref);
                        if (oldEntry != null) {
                            // FIXME does not verify compatitibllity with different array sizes
                            ClassWrapper common = wrapperPool.getCommonSuperClass(wrapper, oldEntry.getKey());
                            if (common != wrapper) {
                                if (common == oldEntry.getKey()) {
                                    signatureDesc = oldEntry.getValue();
                                } else {
                                    StringBuilder b = new StringBuilder();
                                    for (int i = 0; i < newInsn.desc.length(); i++) {
                                        if (newInsn.desc.codePointAt(i) == '[') {
                                            b.append('[');
                                        } else {
                                            break;
                                        }
                                    }
                                    b.append('L');
                                    b.append(common.getName());
                                    b.append(';');
                                    signatureDesc = b.toString();
                                }
                                wrapper = common;
                            } else {
                                signatureDesc = type.getDescriptor();
                            }
                            collectionSignatures.put(fref, Map.entry(common, signatureDesc));
                        } else {
                            signatureDesc = type.getDescriptor();
                            collectionSignatures.put(fref, Map.entry(wrapper, signatureDesc));
                        }
                    }
                    insn = insn.getNext();
                }
            }
        }

        for (Entry<FieldReference, Entry<ClassWrapper, String>> collectionEntry : collectionSignatures.entrySet()) {
            addedFieldSignatures++;
            newFieldSignatures.put(collectionEntry.getKey(), new SignatureNode(collectionEntry.getKey().getDesc(), collectionEntry.getValue().getValue()));
        }

        for (ClassNode node : nodes) {
            for (FieldNode field : node.fields) {
                if (field.signature == null && ITERABLES.contains(field.desc)) {
                    SignatureNode result = newFieldSignatures.get(new FieldReference(node.name, field));
                    if (result == null) {
                        // System.out.println("Unable to find signature for: " + node.name + "." + field.name);
                    } else {
                        // System.out.println("Signature for " + node.name + "." + field.name + " is " + result.toString());
                        field.signature = result.toString();
                    }
                }
            }
        }

        return addedFieldSignatures;
    }

    /**
     * Guesses which classes are local classes within another class.
     * Local classes are non-static classes that are nested within another class
     * or method.
     *
     * <p>This method is rather aggressive and may recommend pairing that do not make
     * sense. It is up to the API consumer to sort out nonsensical pairings from
     * the ones that make sense.
     *
     * <p>Invoking this method does not have any effect in itself.
     * The returned map should be used to create pairings.
     *
     * @return A map that has the inner classes as the key and their outer class as it's value.
     */
    public Map<String, String> guessLocalClasses() {
        Map<String, String> localClasses = new HashMap<>();
        classLoop:
        for (ClassNode node : nodes) {
            for (InnerClassNode icn : node.innerClasses) {
                if (icn.name.equals(node.name)) {
                    continue classLoop;
                }
            }

            String this0FieldDesc = null;
            String this0FieldName = null;
            for (MethodNode method : node.methods) {
                if (method.name.equals("<init>")) {
                    if (method.desc.codePointAt(1) != 'L') {
                        continue classLoop;
                    }
                    String outerClassDesc = method.desc.substring(1, method.desc.indexOf(';', 3) + 1);
                    if (this0FieldDesc != null && !outerClassDesc.equals(this0FieldDesc)) {
                        continue classLoop;
                    }
                    this0FieldDesc = outerClassDesc;
                    AbstractInsnNode insn = method.instructions.getFirst();
                    while (insn.getOpcode() == -1) {
                        insn = insn.getNext();
                    }
                    if (insn.getOpcode() != Opcodes.ALOAD || ((VarInsnNode)insn).var != 0) {
                        continue classLoop;
                    }
                    insn = insn.getNext();
                    if (insn.getOpcode() != Opcodes.ALOAD || ((VarInsnNode)insn).var != 1) {
                        continue classLoop;
                    }
                    insn = insn.getNext();
                    if (insn.getOpcode() != Opcodes.PUTFIELD) {
                        continue classLoop;
                    }
                    FieldInsnNode putFieldInsn = (FieldInsnNode) insn;
                    if (!this0FieldDesc.equals(putFieldInsn.desc)) {
                        continue classLoop;
                    }
                    if (this0FieldName != null && !this0FieldName.equals(putFieldInsn.name)) {
                        continue classLoop;
                    }
                    this0FieldName = putFieldInsn.name;
                }
            }

            if (this0FieldDesc == null || this0FieldName == null) {
                continue;
            }

            boolean resolvedField = false;
            for (FieldNode field : node.fields) {
                if ((field.access & Opcodes.ACC_SYNTHETIC) == 0) {
                    continue;
                }
                if (field.name.equals(this0FieldName) && field.desc.equals(this0FieldDesc)) {
                    resolvedField = true;
                    break;
                }
            }
            if (!resolvedField) {
                continue;
            }

            // Ensure that the two classes are in the same package
            int lastIndexOfSlash = node.name.lastIndexOf('/');
            if (this0FieldDesc.length() <= (lastIndexOfSlash + 1) || this0FieldDesc.codePointAt(lastIndexOfSlash + 1) != '/') {
                continue;
            }
            if (!this0FieldDesc.startsWith(node.name.substring(0, lastIndexOfSlash), 1)) {
                continue;
            }
            localClasses.put(node.name, this0FieldDesc.substring(1, this0FieldDesc.length() - 1));
        }

        return localClasses;
    }

    /**
     * Guesses the generic signatures of methods based on several factors, such as loops or {@link Collection#add(Object)}.
     *
     * @return The amount of guessed method generics
     */
    @SuppressWarnings("unused")
    public int guessMethodGenerics() {
        if (true) {
            return 0;
        }
        int guessedMethodGenerics = 0;

        // Guess generic signatures of method parameter based on Collection#add
        List<Boolean> localsValidity = new ArrayList<>(); // Stores whether a given local is relevant for us
        List<String> assumedSignatures = new ArrayList<>(); // Stores the current assumed signature of a local

        final Map<MethodReference, String> assumedSignature = new HashMap<>();

        Map<MethodReference, String> methodSignatures = new HashMap<>();
        Map<FieldReference, String> fieldSignatures = new HashMap<>();
        Map<MethodReference, List<StackElement>> methodReturnedElements = new HashMap<>();

        for (ClassNode node : nodes) {
            for (FieldNode field : node.fields) {
                fieldSignatures.put(new FieldReference(node.name, field), field.signature);
            }
            for (MethodNode method : node.methods) {
                methodSignatures.put(new MethodReference(node.name, method), method.signature);
                if (method.signature != null) {
                    continue;
                }
                String returnType = method.desc.substring(method.desc.indexOf(')') + 1);
                if (returnType.codePointAt(0) != 'L') {
                    continue;
                }
                ClassWrapper returnTypeWrapper;
                try {
                    returnTypeWrapper = wrapperPool.get(returnType.substring(1, returnType.length() - 1));
                    if (!wrapperPool.isImplementingInterface(returnTypeWrapper, "java/util/Collection")) {
                        continue; // Not a collection, assumptions useless
                    }
                } catch (Exception e) {
                    continue; // Discard exception
                }

                int paramCount = 0;
                for (DescString desc = new DescString(method.desc); desc.hasNext();) {
                    String type = desc.nextType();
                    if (type.equals("J") || type.equals("D")) {
                        paramCount += 2;
                    } else {
                        paramCount++;
                    }
                }

                ClassWrapper[] paramSignatures = new ClassWrapper[paramCount];

                StackWalker.walkStack(node, method, new StackWalkerConsumer() {

                    @Override
                    public void preCalculation(AbstractInsnNode instruction, LIFOQueue<StackElement> stack) {
                        if (instruction instanceof MethodInsnNode) {
                            MethodInsnNode minsn = (MethodInsnNode) instruction;
                            if (minsn.name.equals("<init>")) {
                                return; // Irrelevant
                            }
                            ClassWrapper owner = wrapperPool.get(minsn.owner);
                            if (wrapperPool.isImplementingInterface(owner, "java/util/Collection")) {
                                if (minsn.name.equals("add") && minsn.desc.equals("(Ljava/lang/Object;)Z")) {
                                    Iterator<StackElement> stackIterator = stack.iterator();
                                    StackElement arg = stackIterator.next();
                                    StackElement ref = stackIterator.next();
                                    if (ref.source.type == ElementSourceType.INSTRUCTION) {
                                        System.out.println(ref.source.getMatchingInstruction().getOpcode());;
                                    }
                                }
                            }
                        }
                    }

                    @Override
                    public void postCalculation(AbstractInsnNode instruction, LIFOQueue<StackElement> stack) {
                        // Unused
                    }
                });
            }
        }

        for (ClassNode node : nodes) {
            for (MethodNode method : node.methods) {
                
            }
        }
        localsValidity = null;
        assumedSignatures = null;

        return guessedMethodGenerics;
    }

    public void index(JarFile file) {
        file.entries().asIterator().forEachRemaining(entry -> {
            if (entry.getName().endsWith(".class")) {
                ClassReader reader;
                try {
                    InputStream is = file.getInputStream(entry);
                    reader = new ClassReader(is);
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    return;
                }
                ClassNode node = new ClassNode();
                reader.accept(node, 0);
                nodes.add(node);
//                if (node.name.endsWith("or/class_u")) {
//                    org.objectweb.asm.util.ASMifier asmifier = new org.objectweb.asm.util.ASMifier();
//                    org.objectweb.asm.util.TraceClassVisitor tcv =
//                            new org.objectweb.asm.util.TraceClassVisitor(null, asmifier, new java.io.PrintWriter(System.out));
//                    node.accept(tcv);
//                }
                nameToNode.put(node.name, node);
            }
        });
    }

    /**
     * Infers the generics of constructors based on the calls to the constructor.
     */
    public void inferConstructorGenerics() {

        // Index constructors
        Map<MethodReference, List<String>> constructors = new HashMap<>();
        for (ClassNode node : nodes) {
            for (MethodNode method : node.methods) {
                if (method.signature != null) {
                    continue; // No point in guessing the signature if we already know it
                }
                if (!method.name.equals("<init>")) {
                    continue; // Not a constructor
                }
                if (method.desc.codePointAt(1) == ')') {
                    continue; // No arguments to infer stuff from
                }
                DescString descString = new DescString(method.desc);
                while (descString.hasNext()) {
                    if (ITERABLES.contains(descString.nextType())) {
                        // The constructor has at least 1 generic-able argument
                        constructors.put(new MethodReference(node.name, method), null);
                        break;
                    }
                }
            }
        }

        // Index references to constructors
        for (ClassNode node : nodes) {
            for (MethodNode method : node.methods) {
                if (method.instructions == null) {
                    continue; // Abstract method with no body
                }
                AbstractInsnNode insn = method.instructions.getFirst();
                while (insn != null) {
                    if (insn.getOpcode() == Opcodes.INVOKESPECIAL) {
                        MethodInsnNode ctorCall = (MethodInsnNode) insn;
                        if (!ctorCall.name.equals("<init>")) {
                            insn = insn.getNext();
                            continue;
                        }
                        MethodReference ctorReference = new MethodReference(ctorCall);
                        if (!constructors.containsKey(ctorReference)) {
                            // Constructor not indexed, likely because it does not need a signature,
                            // but it can also be that the constructor is not known because it is not a class that should be deobfuscated
                            insn = insn.getNext();
                            continue;
                        }

                        TypeInsnNode newCall = null;
                        AbstractInsnNode insn2 = insn.getPrevious();

                        while (insn2 != null) {
                            if (insn2.getOpcode() == Opcodes.DUP) {
                                if (insn2.getPrevious().getOpcode() != Opcodes.NEW) {
                                    break; // While technically not strictly breaking, I'd want to save some time calculating all the stack deltas
                                }
                                TypeInsnNode new2 = (TypeInsnNode) insn2.getPrevious();
                                if (new2.desc.equals(ctorReference.getOwner())) { // Given the other checks nothing else is possible, but we'll have it here anyways for "unit testing"
                                    newCall = new2;
                                }
                                break;
                            }
                            if (insn2.getOpcode() != Opcodes.INVOKESTATIC && insn2.getOpcode() != Opcodes.GETSTATIC) {
                                break; // Technically we could allow non-static variants, but they are a bit harder to compute
                            }
                            insn2 = insn2.getPrevious();
                        }

                        if (newCall == null || insn2 == null) {
                            insn = insn.getNext();
                            continue;
                        }

                        List<String> ourArgs = new ArrayList<>();
                        insn2 = insn2.getNext();

                        boolean invalidate = false;
                        while (insn2 != ctorCall) {
                            if (insn2.getOpcode() == Opcodes.INVOKESTATIC) {
                                MethodInsnNode invokestaticInsn = (MethodInsnNode) insn2;
                                if (invokestaticInsn.desc.codePointAt(1) != ')') {
                                    invalidate = true; // Not a getter-like method, however the method MUST be a getter-like method
                                    break;
                                }
                                if (!ITERABLES.contains(invokestaticInsn.desc.substring(2))) {
                                    ourArgs.add(null);
                                    insn2 = insn2.getNext();
                                    continue;
                                }
                                ourArgs.add(""); // I'm too lazy to fetch the generic signature of the method, so we'll leave this blank
                            } else if (insn2.getOpcode() == Opcodes.GETSTATIC) {
                                FieldInsnNode getstaticInsn = (FieldInsnNode) insn2;
                                if (!ITERABLES.contains(getstaticInsn.desc)) {
                                    ourArgs.add(null);
                                    insn2 = insn2.getNext();
                                    continue;
                                }

                                // Fetch generic signature of the field
                                ClassNode ownerNode = nameToNode.get(getstaticInsn.owner);
                                if (ownerNode == null) {
                                    // Class does not exist for some reason
                                    ourArgs.add("");
                                    insn2 = insn2.getNext();
                                    continue;
                                }

                                String fetchedSignature = null;
                                for (FieldNode ownerField : ownerNode.fields) {
                                    if (ownerField.name.equals(getstaticInsn.name) && ownerField.desc.equals(getstaticInsn.desc)) {
                                        fetchedSignature = ownerField.signature;
                                        break;
                                    }
                                }
                                if (fetchedSignature == null) {
                                    // Unable to fetch signature
                                    ourArgs.add("");
                                    insn2 = insn2.getNext();
                                    continue;
                                }

                                int startSign = fetchedSignature.indexOf('<');
                                int endSign = fetchedSignature.indexOf('>');
                                ourArgs.add(fetchedSignature.substring(startSign, endSign + 1));
                            }
                            insn2 = insn2.getNext();
                        }

                        if (!invalidate) {
                            List<String> old = constructors.get(ctorReference);
                            if (old != null) {
                                // Merge the two lists
                                if (old.size() != ourArgs.size()) {
                                    throw new IllegalStateException("Argument sizes do not match.");
                                }
                                for (int i = 0; i < old.size(); i++) {
                                    String oldElement = old.get(i);
                                    String newElement = ourArgs.get(i);
                                    if (oldElement == null || newElement == null) {
                                        ourArgs.set(i, null);
                                    } else if (newElement.isEmpty()) {
                                        ourArgs.set(i, oldElement);
                                    } else if (oldElement.isEmpty()) {
                                        // Don't do anything
                                    } else if (!oldElement.equals(newElement)) {
                                        ourArgs.set(i, null);
                                    }
                                }
                            }
                            constructors.put(ctorReference, ourArgs);
                        }
                    }
                    insn = insn.getNext();
                }
            }
        }

        Map<FieldReference, String> fieldSignatures = new HashMap<>();
        // Apply generic signatures on the constructor
        StringBuilder signatureAssembler = new StringBuilder();
        for (ClassNode node : nodes) {
            for (MethodNode method : node.methods) {
                if (method.signature != null) {
                    continue; // reduce memory allocation
                }
                List<String> argumentSignatures = constructors.get(new MethodReference(node.name, method));
                if (argumentSignatures == null) {
                    continue;
                }
                // TODO test whether this code really deals with the long/double quirk correctly
                int[] parameterIndices = new int[argumentSignatures.size() + 1];
                DescString plainDescriptor = new DescString(method.desc);
                signatureAssembler.setLength(0);
                signatureAssembler.append('(');
                int paramIndex = 1;
                for (int i = 0; i < argumentSignatures.size(); i++) {
                    String type = plainDescriptor.nextType();
                    if (type.codePointAt(0) == 'L') {
                        parameterIndices[i + 1] = paramIndex++;
                        signatureAssembler.append(type.substring(0, type.length() - 1));
                        String argSignature = argumentSignatures.get(i);
                        if (argSignature != null) {
                            signatureAssembler.append(argSignature);
                        }
                        signatureAssembler.append(';');
                    } else {
                        if (type.codePointAt(0) == 'D' || type.codePointAt(0) == 'J') {
                            parameterIndices[i + 1] = paramIndex;
                            paramIndex += 2;
                        } else {
                            parameterIndices[i + 1] = paramIndex++;
                        }
                        signatureAssembler.append(type);
                    }
                }
                if (plainDescriptor.hasNext()) {
                    System.err.println("Signature for method " + node.name + "." + method.name + method.desc + " could not be completed fully because some parameters are missing.");
                    continue;
                }
                signatureAssembler.append(')');
                signatureAssembler.append('V');
                method.signature = signatureAssembler.toString();

                boolean[] damagedParams = new boolean[argumentSignatures.size() + 1];
                int[] localToParam = new int[paramIndex];
                for (int i = 0; i < parameterIndices.length; i++) {
                    localToParam[parameterIndices[i]] = i;
                }
                // The constructor is never static and the `this` local variable is not capable of generics
                // Not marking it as "damaged" may create issues for us
                damagedParams[0] = true;
                AbstractInsnNode insn = method.instructions.getFirst();
                int loadedParameter = -1;

                // Infer field signatures too
                while (insn != null) {
                    if (insn instanceof VarInsnNode) {
                        VarInsnNode varInsn = (VarInsnNode) insn;
                        if (OPHelper.isVarLoad(varInsn.getOpcode())) {
                            // xLoad
                            if (varInsn.var < localToParam.length) {
                                loadedParameter = localToParam[varInsn.var];
                                if (loadedParameter >= damagedParams.length) {
                                    loadedParameter = -1;
                                }
                            } else {
                                loadedParameter = -1;
                            }
                        } else {
                            // xStore
                            if (varInsn.var < localToParam.length && localToParam[varInsn.var] < damagedParams.length) {
                                damagedParams[localToParam[varInsn.var]] = true;
                            }
                        }
                    } else if (insn instanceof FieldInsnNode) {
                        if (loadedParameter < damagedParams.length && loadedParameter != -1 && !damagedParams[loadedParameter]) {
                            FieldInsnNode fieldInsn = (FieldInsnNode) insn;
                            if (fieldInsn.getOpcode() == Opcodes.PUTFIELD || fieldInsn.getOpcode() == Opcodes.PUTSTATIC) {
                                FieldReference fref = new FieldReference(fieldInsn);
                                if (fieldSignatures.containsKey(fref)) {
                                    String oldProposal = fieldSignatures.get(fref);
                                    String suggested = argumentSignatures.get(loadedParameter - 1);
                                    if (oldProposal != null && suggested != null && !suggested.isEmpty()) {
                                        if (!oldProposal.equals(suggested)) {
                                            fieldSignatures.put(fref, null);
                                        }
                                    }
                                } else {
                                    fieldSignatures.put(fref, argumentSignatures.get(loadedParameter - 1));
                                }
                            }
                        }
                    } else {
                        loadedParameter = -1;
                    }
                    insn = insn.getNext();
                }
            }
        }

        for (ClassNode node : nodes) {
            for (FieldNode field : node.fields) {
                if (field.signature != null) {
                    continue;
                }
                FieldReference fref = new FieldReference(node.name, field);
                String suggested = fieldSignatures.get(fref);
                if (suggested != null) {
                    signatureAssembler.setLength(0);
                    signatureAssembler.append(field.desc.substring(0, field.desc.length() - 1)).append(suggested).append(';');
                    field.signature = signatureAssembler.toString();
                }
            }
        }
    }

    /**
     * Infers the generic signatures of methods based on the contents of the method.
     *
     * @return The amount of guessed signatures
     */
    public int inferMethodGenerics() {
        int addedMethodSignatures = 0;

        // Infer generics of getters
        Map<FieldReference, List<MethodNode>> getterRefs = new HashMap<>();
        for (ClassNode classNode : nodes) {
            for (MethodNode method : classNode.methods) {
                if (method.signature != null) {
                    continue; // We already know the signature
                }
                if (method.instructions.size() == 0) {
                    // Abstract method (can also be a method within an interface)
                    continue;
                }
                if (method.desc.codePointAt(1) != ')') {
                    continue; // not a getter
                }
                String returnValue = method.desc.substring(2);
                int indexOfL = returnValue.indexOf('L');
                if (indexOfL == -1) {
                    // We cannot add generics to primitives
                    continue;
                }
                String rawObject = returnValue.substring(indexOfL);
                if (!ITERABLES.contains(rawObject)) {
                    continue; // Not something we know can be a generic
                }
                AbstractInsnNode insn = method.instructions.getLast().getPrevious();
                while (insn != null && insn.getOpcode() != Opcodes.ARETURN) {
                    insn = insn.getPrevious();
                }
                if (insn != null) {
                    continue; // not a straightforward getter
                }
                insn = method.instructions.getLast().getPrevious();
                if (!(insn instanceof FieldInsnNode)) {
                    continue; // We only accept getters that directly return a field
                }
                List<MethodNode> old = getterRefs.get(new FieldReference((FieldInsnNode) insn));
                if (old == null) {
                    old = new ArrayList<>();
                    getterRefs.put(new FieldReference((FieldInsnNode) insn), old);
                }
                old.add(method);
            }
        }

        // Set the signatures
        for (ClassNode node : nodes) {
            for (FieldNode field : node.fields) {
                if (field.signature != null && ITERABLES.contains(field.desc)) {
                    List<MethodNode> references = getterRefs.get(new FieldReference(node.name, field));
                    if (references != null) {
                        for (MethodNode reference : references) {
                            // FIXME Casts?
                            reference.signature = "()" + field.signature;
                            addedMethodSignatures++;
                        }
                    }
                }
            }
        }

        return addedMethodSignatures;
    }

    /**
     * Invalidate internal {@link ClassNode} {@link ClassNode#name name} caches.
     * Should be invoked when for example class nodes are remapped, at which point
     * internal caches are no longer valid.
     */
    public void invalidateNameCaches() {
        nameToNode.clear();
        for (ClassNode node : nodes) {
            nameToNode.put(node.name, node);
        }
        wrapperPool.invalidateNameCaches();
    }

    public void write(OutputStream out) throws IOException {
        JarOutputStream jarOut = new JarOutputStream(out);
        for (ClassNode node : nodes) {
            ClassWriter writer = new ClassWriter(0);
            node.accept(writer);
            jarOut.putNextEntry(new ZipEntry(node.name + ".class"));
            jarOut.write(writer.toByteArray());
            jarOut.closeEntry();
        }
        jarOut.close();
    }

    /**
     * Write all class nodes and copy over all resources from a given jar.
     * This method throws an IOException if there is no file at the path "resources"
     * or if it is not a zip (and by extension jar) file.
     * If no resources need to be copied over, {@link Oaktree#write(OutputStream)} should be used instead.
     *
     * @param out The stream to write the nodes and resources to as a jar
     * @param resources The path to obtain resources from
     * @throws IOException If something went wrong while writing to the stream or reading the resources jar.
     */
    public void write(@NotNull OutputStream out, @NotNull Path resources) throws IOException {
        if (Files.notExists(resources)) {
            throw new IOException("The path (" + resources.toString() + ") specified by \"resources\" does not exist.");
        }
        JarOutputStream jarOut = new JarOutputStream(out);
        for (ClassNode node : nodes) {
            ClassWriter writer = new ClassWriter(0);
            node.accept(writer);
            jarOut.putNextEntry(new ZipEntry(node.name + ".class"));
            jarOut.write(writer.toByteArray());
            jarOut.closeEntry();
        }
        try (ZipInputStream zipIn = new ZipInputStream(Files.newInputStream(resources))) {
            for (ZipEntry entry = zipIn.getNextEntry(); entry != null; entry = zipIn.getNextEntry()) {
                if (entry.getName().endsWith(".class")) {
                    int ch1 = zipIn.read();
                    int ch2 = zipIn.read();
                    int ch3 = zipIn.read();
                    int ch4 = zipIn.read();
                    if ((ch1 | ch2 | ch3 | ch4) < 0) {
                        // This might lead to duplicate .class files, but the chance of this happening is relatively low
                        // so this behaviour will be ignored
                        jarOut.putNextEntry(entry);
                        if (ch1 == -1) {
                            continue;
                        }
                        jarOut.write(ch1);
                        if (ch2 == -1) {
                            continue;
                        }
                        jarOut.write(ch2);
                        if (ch3 == -1) {
                            continue;
                        }
                        jarOut.write(ch3);
                        if (ch4 == -1) {
                            continue;
                        } else {
                            throw new IOException(String.format("Unexpected header: [%d, %d, %d, %d]", ch1, ch2, ch3, ch4));
                        }
                    }
                    if (ch1 == 0xCA && ch2 == 0xFE && ch3 == 0xBA && ch4 == 0xBE) {
                        // every valid class file must begin with CAFEBABE
                        continue;
                    }
                }
                jarOut.putNextEntry(entry);
                zipIn.transferTo(jarOut);
            }
        }
        jarOut.close();
    }
}
