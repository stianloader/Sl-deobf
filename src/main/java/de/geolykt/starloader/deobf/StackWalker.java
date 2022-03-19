package de.geolykt.starloader.deobf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import de.geolykt.starloader.deobf.StackElement.ElementSource;

public class StackWalker {

    public interface StackWalkerConsumer {

        /**
         * Method that is invoked before the effect of a given instruction on the stack is calculated.
         * This method is also invoked for pseudo-instructions such as {@link FrameNode},
         * {@link LineNumberNode} or {@link LabelNode}.
         * The "stack" argument is mutable and but should NOT be mutated as otherwise
         * the stack might get invalid as it is backed by the real stack due to performance concerns.
         *
         * @param instruction The current instruction
         * @param stack The stack as it was before the instruction
         */
        public void preCalculation(AbstractInsnNode instruction, LIFOQueue<StackElement> stack);

        /**
         * Method that is invoked after the effect of a given instruction on the stack is calculated.
         * This method is also invoked for pseudo-instructions such as {@link FrameNode},
         * {@link LineNumberNode} or {@link LabelNode}.
         * The "stack" argument is mutable and but should NOT be mutated as otherwise
         * the stack might get invalid as it is backed by the real stack due to performance concerns.
         *
         * @param instruction The current instruction
         * @param stack The stack as it is after the instruction
         */
        public void postCalculation(AbstractInsnNode instruction, LIFOQueue<StackElement> stack);

        /**
         * Method that is invoked when the Stack walker finished walking over all instructions.
         */
        public default void endMethod() {
            // Empty
        }
    }

    public static void walkStack(ClassNode owner, MethodNode method, StackWalkerConsumer consumer) {

        if ((owner.version & 0x00FF) <= Opcodes.V1_5) {
            // In Java 5 and prior stack maps are not always a thing, which bricks the stack walker as
            // I do not want to implement this horrors of ternary statements
            return; // TODO add argument to suppress this behaviour
        }

        AbstractInsnNode insn = method.instructions.getFirst();
        LIFOQueue<StackElement> stack = new LIFOQueue<>(new LinkedList<>());
        List<StackElement> locals = new ArrayList<>(method.maxLocals);
        int asmLocalsFrameSize;

        if ((method.access & Opcodes.ACC_STATIC) == 0) {
            locals.add(new StackElement(ElementSource.thisRef(), 'L' + owner.name + ';'));
        }

        DescString desc = new DescString(method.desc);
        while (desc.hasNext()) {
            String type = desc.nextType();
            locals.add(new StackElement(ElementSource.ofParameter(locals.size()), type));
            if (type.equals("J") || type.equals("D")) {
                // Doubles and Longs occupy 2 stack entries
                locals.add(StackElement.INVALID);
            }
        }
        asmLocalsFrameSize = locals.size(); // Meh, not sure if this is what it should be
        desc = null;

        while (locals.size() < method.maxLocals) {
            locals.add(StackElement.INVALID);
        }

        // Only used in class version 49 (Java 5) or earlier, as they do not have the StackMap attributes by default.
        Map<LabelNode, StackElement> exceptionHandlers = null;
        if ((owner.version & 0x00FF) <= Opcodes.V1_5) {
            exceptionHandlers = new HashMap<>();
            for (TryCatchBlockNode trycatchBlock : method.tryCatchBlocks) {
                exceptionHandlers.put(trycatchBlock.handler, new StackElement(ElementSource.undefined(), 'L' + trycatchBlock.type + ';'));
            }
        }

        while (insn != null) {
            consumer.preCalculation(insn, stack);
            if (insn instanceof VarInsnNode) {
                VarInsnNode varInsn = (VarInsnNode) insn;
                if (OPHelper.isVarLoad(insn.getOpcode())) {
                    // xLoad
                    stack.add(locals.get(varInsn.var));
                } else {
                    // xStore
                    locals.set(varInsn.var, stack.remove());
                }
            } else if (insn instanceof FieldInsnNode) {
                FieldInsnNode field = (FieldInsnNode) insn;
                if (insn.getOpcode() == Opcodes.PUTFIELD || insn.getOpcode() == Opcodes.GETFIELD) {
                    stack.remove(); // remove `this` reference
                }
                if (insn.getOpcode() == Opcodes.GETFIELD || insn.getOpcode() == Opcodes.GETSTATIC) {
                    stack.add(new StackElement(ElementSource.ofField(field), field.desc));
                } else {
                    stack.remove();
                }
            } else if (insn instanceof IntInsnNode) {
                if (insn.getOpcode() == Opcodes.SIPUSH) {
                    stack.add(StackElement.SHORT);
                } else if (insn.getOpcode() == Opcodes.BIPUSH) {
                    stack.add(StackElement.BYTE);
                }  else if (insn.getOpcode() == Opcodes.NEWARRAY) {
                    stack.remove();
                    IntInsnNode intInsn = (IntInsnNode) insn;
                    if (intInsn.operand == Opcodes.T_DOUBLE) {
                        stack.add(new StackElement(ElementSource.ofInstruction(insn), "[D"));
                    } else if (intInsn.operand == Opcodes.T_BOOLEAN || intInsn.operand == 0) {
                        // Latter seems to be nonstandard and would need investigation.
                        // The JVMS does not suggest that 0 is a valid value for the operand.
                        // Recaf, CFR and Procyon seem to accept it however, so could it be
                        // something that have been changed with the years?
                        stack.add(new StackElement(ElementSource.ofInstruction(insn), "[B"));
                    } else if (intInsn.operand == Opcodes.T_BYTE) {
                        stack.add(new StackElement(ElementSource.ofInstruction(insn), "[B")); // Oracle's JVM stores it as 1 boolean per byte
                    } else if (intInsn.operand == Opcodes.T_CHAR) {
                        stack.add(new StackElement(ElementSource.ofInstruction(insn), "[C"));
                    } else if (intInsn.operand == Opcodes.T_FLOAT) {
                        stack.add(new StackElement(ElementSource.ofInstruction(insn), "[F"));
                    } else if (intInsn.operand == Opcodes.T_INT) {
                        stack.add(new StackElement(ElementSource.ofInstruction(insn), "[I"));
                    } else if (intInsn.operand == Opcodes.T_LONG) {
                        stack.add(new StackElement(ElementSource.ofInstruction(insn), "[J"));
                    } else if (intInsn.operand == Opcodes.T_SHORT) {
                        stack.add(new StackElement(ElementSource.ofInstruction(insn), "[S"));
                    } else {
                        throw new UnsupportedOperationException("Unimplemented operand: " + intInsn.operand);
                    }
                } else {
                    throw new UnsupportedOperationException("Unimplemented opcode: " + insn.getOpcode());
                }
            } else if (insn instanceof LdcInsnNode) {
                LdcInsnNode ldc = (LdcInsnNode) insn;
                String typeDescriptor;
                if (ldc.cst instanceof Integer) {
                    typeDescriptor = "I";
                } else if (ldc.cst instanceof Long) {
                    typeDescriptor = "J";
                } else if (ldc.cst instanceof Float) {
                    typeDescriptor = "F";
                } else if (ldc.cst instanceof String) {
                    typeDescriptor = "Ljava/lang/String;";
                } else if (ldc.cst instanceof Double) {
                    typeDescriptor = "D";
                } else if (ldc.cst instanceof Type) {
                    typeDescriptor = "Ljava/lang/Class;";
                } else if (ldc.cst instanceof Handle) {
                    System.err.println(ldc.cst);
                    throw new IllegalStateException();
                } else if (ldc.cst instanceof ConstantDynamic) {
                    System.err.println(ldc.cst);
                    throw new IllegalStateException();
                } else {
                    throw new UnsupportedOperationException("Unimplemented type: " + ldc.cst.getClass().descriptorString());
                }

                stack.add(new StackElement(ElementSource.ofLdc(ldc), typeDescriptor));
            } else if (insn instanceof MethodInsnNode) {
                MethodInsnNode methodInsn = (MethodInsnNode) insn;

                DescString descString = new DescString(methodInsn.desc);
                while (descString.hasNext()) {
                    descString.nextType();
                    stack.remove();
                }

                if (insn.getOpcode() != Opcodes.INVOKESTATIC) {
                    stack.remove();
                }

                String returnType = methodInsn.desc.substring(methodInsn.desc.indexOf(')') + 1);
                if (!returnType.equals("V")) {
                    stack.add(new StackElement(ElementSource.ofMethodReturn(methodInsn), returnType));
                }
            } else if (insn instanceof InsnNode) {
                switch (insn.getOpcode()) {
                case Opcodes.POP2:
                    // operands on computational type category 2 take up two words;
                    // pop2 only removes two words
                    if (stack.remove().getComputationalTypeCategory() == ComputationalTypeCategory.CATEGORY_1) {
                        stack.remove();
                    }
                    break;
                case Opcodes.POP:
                    stack.remove();
                    break;
                case Opcodes.DUP:
                    stack.add(stack.getHead());
                    break;
                case Opcodes.ACONST_NULL:
                    stack.add(StackElement.NULL);
                    break;
                case Opcodes.ICONST_0:
                case Opcodes.ICONST_1:
                case Opcodes.ICONST_2:
                case Opcodes.ICONST_3:
                case Opcodes.ICONST_4:
                case Opcodes.ICONST_5:
                case Opcodes.ICONST_M1:
                    stack.add(StackElement.INT);
                    break;
                case Opcodes.LCONST_0:
                case Opcodes.LCONST_1:
                    stack.add(StackElement.LONG);
                    break;
                case Opcodes.FCONST_0:
                case Opcodes.FCONST_1:
                case Opcodes.FCONST_2:
                    stack.add(StackElement.FLOAT);
                    break;
                case Opcodes.DCONST_0:
                case Opcodes.DCONST_1:
                    stack.add(StackElement.DOUBLE);
                    break;
                case Opcodes.IALOAD:
                    stack.remove();
                    stack.remove();
                    stack.add(StackElement.INT);
                    break;
                case Opcodes.LALOAD:
                    stack.remove();
                    stack.remove();
                    stack.add(StackElement.LONG);
                    break;
                case Opcodes.AALOAD: {
                    // arrayref, index -> value
                    StackElement index = stack.remove();
                    if (!index.isPrimitive() && !index.isNull) {
                        throw new IllegalStateException("Illegal AALOAD statement, index must be a primitive at the very least.");
                    }
                    StackElement arrayref = stack.remove();
                    StackElement value = new StackElement(ElementSource.ofInstruction(insn), arrayref.type.substring(1));
                    if (value.type.isEmpty()) {
                        throw new IllegalStateException("Illegal AALOAD statement, arrayref was a primitive.");
                    }
                    stack.add(value);
                }
                    break;
                case Opcodes.FALOAD:
                    stack.remove();
                    stack.remove();
                    stack.add(StackElement.FLOAT);
                    break;
                case Opcodes.DALOAD:
                    stack.remove();
                    stack.remove();
                    stack.add(StackElement.DOUBLE);
                    break;
                case Opcodes.BALOAD:
                    stack.remove();
                    stack.remove();
                    stack.add(StackElement.BYTE);
                    break;
                case Opcodes.CALOAD:
                    stack.remove();
                    stack.remove();
                    stack.add(StackElement.CHAR);
                    break;
                case Opcodes.SALOAD:
                    stack.remove();
                    stack.remove();
                    stack.add(StackElement.SHORT);
                    break;
                case Opcodes.DASTORE:
                case Opcodes.LASTORE:
                case Opcodes.IASTORE:
                case Opcodes.FASTORE:
                case Opcodes.AASTORE:
                case Opcodes.BASTORE:
                case Opcodes.CASTORE:
                case Opcodes.SASTORE:
                    stack.remove(); // value
                    stack.remove(); // index
                    stack.remove(); // array reference
                    break;
                case Opcodes.IMUL:
                case Opcodes.IDIV:
                case Opcodes.IREM:
                case Opcodes.IADD:
                case Opcodes.ISUB:
                case Opcodes.IAND:
                case Opcodes.IOR:
                case Opcodes.ISHR:
                case Opcodes.ISHL:
                case Opcodes.IUSHR:
                case Opcodes.IXOR:
                    stack.remove();
                    stack.remove();
                    stack.add(StackElement.INT);
                    break;
                case Opcodes.LMUL:
                case Opcodes.LREM:
                case Opcodes.LADD:
                case Opcodes.LSUB:
                case Opcodes.LAND:
                case Opcodes.LOR:
                case Opcodes.LSHR:
                case Opcodes.LSHL:
                case Opcodes.LUSHR:
                case Opcodes.LXOR:
                case Opcodes.LDIV:
                    stack.remove();
                    stack.remove();
                    stack.add(StackElement.LONG);
                    break;
                case Opcodes.DMUL:
                case Opcodes.DREM:
                case Opcodes.DADD:
                case Opcodes.DSUB:
                case Opcodes.DDIV:
                    stack.remove();
                    stack.remove();
                    stack.add(StackElement.DOUBLE);
                    break;
                case Opcodes.FMUL:
                case Opcodes.FREM:
                case Opcodes.FADD:
                case Opcodes.FSUB:
                case Opcodes.FDIV:
                    stack.remove();
                    stack.remove();
                    stack.add(StackElement.FLOAT);
                    break;
                case Opcodes.FCMPG:
                case Opcodes.FCMPL:
                    // FCMPG and FCMPL are mostly identical outside of the treatment of NaN
                    stack.remove();
                    stack.remove();
                    stack.add(StackElement.INT);
                    break;
                case Opcodes.LCMP:
                case Opcodes.DCMPG:
                case Opcodes.DCMPL:
                    stack.remove();
                    stack.remove();
                    stack.add(StackElement.BYTE);
                    break;
                case Opcodes.I2B:
                    stack.remove();
                    stack.add(StackElement.BYTE);
                    break;
                case Opcodes.I2C:
                    stack.remove();
                    stack.add(StackElement.CHAR);
                    break;
                case Opcodes.I2D:
                case Opcodes.L2D:
                case Opcodes.F2D:
                    stack.remove();
                    stack.add(StackElement.DOUBLE);
                    break;
                case Opcodes.I2F:
                case Opcodes.L2F:
                case Opcodes.D2F:
                    stack.remove();
                    stack.add(StackElement.FLOAT);
                    break;
                case Opcodes.I2L:
                case Opcodes.F2L:
                case Opcodes.D2L:
                    stack.remove();
                    stack.add(StackElement.LONG);
                    break;
                case Opcodes.I2S:
                    stack.remove();
                    stack.add(StackElement.SHORT);
                    break;
                case Opcodes.L2I:
                case Opcodes.F2I:
                case Opcodes.D2I:
                    stack.remove();
                    stack.add(StackElement.INT);
                    break;
                case Opcodes.ARRAYLENGTH:
                    stack.remove();
                    stack.add(StackElement.INT);
                    break;
                case Opcodes.IRETURN:
                case Opcodes.ATHROW:
                case Opcodes.DRETURN:
                case Opcodes.FRETURN:
                case Opcodes.LRETURN:
                case Opcodes.ARETURN:
                    stack.remove();
                    break;
                case Opcodes.DUP_X1: {
                    // Duplicate the top operand stack value and insert two values down
                    StackElement value1 = stack.remove();
                    StackElement value2 = stack.remove();
                    stack.add(value1);
                    stack.add(value2);
                    stack.add(value1);
                    break;
                }
                case Opcodes.DUP_X2: {
                    // this opcode is a strange fellow
                    StackElement value1 = stack.remove();
                    StackElement value2 = stack.remove();

                    if (value1.getComputationalTypeCategory() != ComputationalTypeCategory.CATEGORY_1) {
                        throw new IllegalStateException("Illegal bytecode: A DUP_X2 call violates the JVMS. Value1 HAS to be a type of category1, but is not.");
                    }
                    if (value2.getComputationalTypeCategory() == ComputationalTypeCategory.CATEGORY_1) {
                        // Category 1
                        StackElement value3 = stack.remove();
                        if (value3.getComputationalTypeCategory() != ComputationalTypeCategory.CATEGORY_1) {
                            throw new IllegalStateException("Illegal bytecode: A DUP_X2 call violates the JVMS. Value1 and value2 are of computational type category 1, but value3 is not, even though it has to be to honor DUP_X2 form 1.");
                        }
                        // Form 1 (value3, value2, value1 -> value1, value3, value2, value1)
                        // where as the computational types of value1, value2 and value3 are in category 2.
                        stack.add(value1);
                        stack.add(value3);
                        stack.add(value2);
                        stack.add(value1);
                    } else {
                        // Assume category 2 (it could've also been null (i.e. value2 is StackElement.INVALID), but that is unlikely)
                        // Form 2 (value2, value1 -> value1, value2, value1)
                        // where as the computational type of value2 is in category 2 and the computational type of value1
                        // is in category 1.
                        stack.add(value1);
                        stack.add(value2);
                        stack.add(value1);
                    }
                    break;
                }
                case Opcodes.DUP2: {
                    StackElement value1 = stack.remove();

                    if (value1.getComputationalTypeCategory() == ComputationalTypeCategory.CATEGORY_2) {
                        // Form 2 (value -> value, value)
                        // Where as the computational type of value is in category 2.
                        stack.add(value1);
                        stack.add(value1);
                    } else {
                        StackElement value2 = stack.remove();
                        if (value2.getComputationalTypeCategory() != ComputationalTypeCategory.CATEGORY_1) {
                            throw new IllegalStateException("Illegal bytecode: A DUP2 call does not honor the JVMS. While value1 is in category1, value2 is not, which is not allowed for both form 1 and form 2 of DUP_2.");
                        }
                        // Form 1 (value2, value1 -> value2, value1, value2, value1)
                        // Where as the computational types of value1 and value2 are in category 1.
                        stack.add(value2);
                        stack.add(value1);
                        stack.add(value2);
                        stack.add(value1);
                    }
                    break;
                }
                case Opcodes.DUP2_X1: {
                    // I have no idea what the developers were doing while making up these opcodes
                    StackElement value1 = stack.remove();
                    StackElement value2 = stack.remove();

                    if (value2.getComputationalTypeCategory() != ComputationalTypeCategory.CATEGORY_1) {
                        throw new IllegalStateException("Illegal bytecode: DUP2_X1 call does not follow the JVMS. The computational type of value2 must be of category 1, but it is not.");
                    }

                    if (value1.getComputationalTypeCategory() == ComputationalTypeCategory.CATEGORY_2) {
                        // Form 2 (value2, value1 -> value1, value2, value1)
                        // Where as the computational type of value2 is in category 1 and the computational
                        // type of value1 is in category 2.
                        stack.add(value1);
                        stack.add(value2);
                        stack.add(value1);
                    } else {
                        // Assume form 1 (value3, value2, value1 -> value2, value1, value3, value2, value1)
                        // Where as the computational types of value3, value2 and value1 are in category 1.
                        StackElement value3 = stack.remove();
                        if (value3.getComputationalTypeCategory() != ComputationalTypeCategory.CATEGORY_1) {
                            throw new IllegalStateException("Illegal bytecode: DUP2_X1 call does not follow the JVMS. The computational type of value1 and value2 are in category 1, but the computational type of value3 is not, even though it must.");
                        }
                        stack.add(value2);
                        stack.add(value1);
                        stack.add(value3);
                        stack.add(value2);
                        stack.add(value1);
                    }
                    break;
                }
                case Opcodes.DUP2_X2: {
                    // And this should be hopefully the last one...
                    // I'm surprised that I found real-world usages of these opcodes

                    // This will be a fun one: 4 forms! The people behind the JVMS must have been seriously drunk that day
                    // Of course, it makes sense if you look at the fact how computational categories are made up on the operand stack,
                    // but this is absurd.
                    StackElement value1 = stack.remove();
                    StackElement value2 = stack.remove();

                    if (value1.getComputationalTypeCategory() == ComputationalTypeCategory.CATEGORY_2) {
                        if (value2.getComputationalTypeCategory() == ComputationalTypeCategory.CATEGORY_2) {
                            // Form 4 (value2, value1 -> value1, value2, value1)
                            // Where as the computational types of value1 and value2 are in category 2.
                            stack.add(value1);
                            stack.add(value2);
                            stack.add(value1);
                        } else {
                            // Form 2 (value3, value2, value1 -> value1, value3, value2, value1)
                            // Where as the computational types of value3 and value2 are in category 1 and
                            // the computational type of value1 is in category 2.
                            StackElement value3 = stack.remove();
                            stack.add(value1);
                            stack.add(value3);
                            stack.add(value2);
                            stack.add(value1);
                        }
                    } else {
                        if (value2.getComputationalTypeCategory() == ComputationalTypeCategory.CATEGORY_2) {
                            throw new IllegalStateException("Illegal bytecode: DUP2_X2 call does not follow the JVMS. The computational type of value1 is in category 1, but the computational type of value2 is in category 2, which is not allowed.");
                        } else {
                            StackElement value3 = stack.remove();
                            if (value3.getComputationalTypeCategory() == ComputationalTypeCategory.CATEGORY_1) {
                                // Form 1 (value4, value3, value2, value1 -> value2, value1, value4, value3, value2, value1)
                                // Where as the computational types of all the values are in category 1.
                                StackElement value4 = stack.remove();
                                stack.add(value2);
                                stack.add(value1);
                                stack.add(value4);
                                stack.add(value3);
                                stack.add(value2);
                                stack.add(value1);
                            } else {
                                // Form 3 (value3, value2, value1 -> value2, value1, value3, value2, value1)
                                // Where as the computational types of value1 and value2 are in category 1,
                                // but the computational type of value3 is in category 2.
                                stack.add(value2);
                                stack.add(value1);
                                stack.add(value3);
                                stack.add(value2);
                                stack.add(value1);
                            }
                        }
                    }
                    break;
                }
                case Opcodes.RETURN:
                case Opcodes.DNEG:
                case Opcodes.LNEG:
                case Opcodes.FNEG:
                case Opcodes.INEG:
                    // Opcodes that do not affect the operand stack
                    break;
                case Opcodes.MONITORENTER:
                case Opcodes.MONITOREXIT:
                    // objectref -> 
                    stack.remove();
                    break;
                default:
                    throw new UnsupportedOperationException("Unimplemented opcode: " + insn.getOpcode());
                }
            } else if (insn instanceof TypeInsnNode) {
                TypeInsnNode type = (TypeInsnNode) insn;
                if (type.getOpcode() == Opcodes.NEW) {
                    stack.add(new StackElement(ElementSource.ofInstruction(insn), 'L' + type.desc + ';'));
                } else if (type.getOpcode() == Opcodes.ANEWARRAY) {
                    stack.remove(); // Count
                    stack.add(new StackElement(ElementSource.ofInstruction(insn), "[L" + type.desc + ';'));
                } else if (type.getOpcode() == Opcodes.INSTANCEOF) {
                    stack.remove();
                    stack.add(StackElement.BOOLEAN);
                } else if (type.getOpcode() == Opcodes.CHECKCAST) {
                    StackElement beforeCast = stack.remove();
                    stack.add(new StackElement(ElementSource.ofCast(insn, beforeCast), 'L' + type.desc + ';'));
                } else {
                    throw new IllegalStateException("Opcode " + type.getOpcode() + " not known.");
                }
            } else if (insn instanceof JumpInsnNode) {
                switch (insn.getOpcode()) {
                case Opcodes.GOTO:
                case Opcodes.JSR:
                case Opcodes.RET:
                    break; // unconditional branches
                case Opcodes.IFNONNULL:
                case Opcodes.IFNULL:
                    stack.remove();
                    break;
                case Opcodes.IFEQ:
                case Opcodes.IFNE:
                case Opcodes.IFLT:
                case Opcodes.IFGT:
                case Opcodes.IFLE:
                case Opcodes.IFGE:
                    stack.remove(); // compares with 0, so only a single operand must be popped from the operand stack.
                    break;
                case Opcodes.IF_ICMPEQ:
                case Opcodes.IF_ICMPNE:
                case Opcodes.IF_ICMPLT:
                case Opcodes.IF_ICMPGT:
                case Opcodes.IF_ICMPLE:
                case Opcodes.IF_ICMPGE:
                    stack.remove(); // compares two integers with each other, so two operands are removed from the stack
                    stack.remove();
                    break;
                case Opcodes.IF_ACMPEQ:
                case Opcodes.IF_ACMPNE:
                    stack.remove(); // compares two references with each other, so two operands are removed
                    stack.remove();
                    break;
                default:
                    throw new UnsupportedOperationException("Unimplemented jump instruction: " + insn.getOpcode());
                }
            } else if (insn instanceof FrameNode) {
                FrameNode frame = (FrameNode) insn;
                if (frame.local != null) {
                    if (frame.type == Opcodes.F_APPEND) {
                        int i = 0;
                        for (Object local : frame.local) {
                            if (local == Opcodes.TOP) {
                                // ignore
                                asmLocalsFrameSize++;
                            } else {
                                locals.set(asmLocalsFrameSize++, getStackElement(owner, ElementSource.ofFrame(frame, i), local));
                            }
                            i++;
                        }
                    } else if (frame.type == Opcodes.F_CHOP) {
                        for (Object o : frame.local) {
                            if (o != null) {
                                throw new IllegalStateException("Chop frame with non-null local?");
                            }
                            locals.set(--asmLocalsFrameSize, StackElement.INVALID);
                        }
                    } else if (frame.type == Opcodes.F_FULL) {
                        asmLocalsFrameSize = frame.local.size();
                    } else if (frame.type != Opcodes.F_SAME) {
                        throw new UnsupportedOperationException("Unimplemented frame type: " + frame.type);
                    }
                }
                if (frame.stack != null) {

                    if (frame.type == Opcodes.F_APPEND || frame.type == Opcodes.F_CHOP || frame.type == Opcodes.F_SAME) {
                        if (!stack.isEmpty()) {
                            System.out.println(stack);
                            throw new IllegalStateException("Stack must be empty!");
                        }
                    } else if (frame.type == Opcodes.F_FULL) {
                        if (frame.stack.size() > stack.getSize() && !stack.isEmpty()) {
                            throw new IllegalStateException("Attempted to add elements to the stack while it not being empty.");
                        }
                        // Apparently you can remove stuff from the stack without invoking POP, this for example is used after the GOTO statement
                        stack.clear();
                        int i = 0;
                        for (Object o : frame.stack) {
                            stack.add(getStackElement(owner, ElementSource.ofFrame(frame, i++), o));
                        }
                    } else if (frame.type == Opcodes.F_SAME1) {
                        stack.clear();
                        stack.add(getStackElement(owner, ElementSource.ofFrame(frame, 0), frame.stack.get(0)));
                    }
                }
            } else if (insn instanceof LabelNode) {
                if (exceptionHandlers != null) {
                    StackElement e = exceptionHandlers.get(insn);
                    if (e != null) {
                        stack.add(e);
                    }
                }
            } else if (insn instanceof MultiANewArrayInsnNode) {
                MultiANewArrayInsnNode instruction = (MultiANewArrayInsnNode) insn;
                stack.add(new StackElement(ElementSource.ofInstruction(insn), instruction.desc));
            } else if (insn instanceof InvokeDynamicInsnNode) {
                InvokeDynamicInsnNode indy = (InvokeDynamicInsnNode) insn;
                DescString descString = new DescString(indy.desc);
                while (descString.hasNext()) {
                    descString.nextType();
                    stack.remove();
                }

                String returnType = indy.desc.substring(indy.desc.indexOf(')') + 1);
                if (!returnType.equals("V")) {
                    stack.add(new StackElement(ElementSource.ofInvokeDynamic(indy), returnType));
                }
            }
            consumer.postCalculation(insn, stack);
            insn = insn.getNext();
        }
        consumer.endMethod();
    }

    private static StackElement getStackElement(ClassNode ownerClass, ElementSource source, Object frame) {
        if (frame instanceof String) {
           return new StackElement(source, 'L' + frame.toString() + ';');
        } else if (frame instanceof Integer) {
            if (frame == Opcodes.UNINITIALIZED_THIS) {
                return new StackElement(source, 'L' + ownerClass.name + ';');
            } else if (frame == Opcodes.NULL) {
                return StackElement.NULL;
            } else if (frame == Opcodes.DOUBLE) {
                return StackElement.NULL;
            } else if (frame == Opcodes.FLOAT) {
                return StackElement.NULL;
            } else if (frame == Opcodes.LONG) {
                return StackElement.NULL;
            } else if (frame == Opcodes.INTEGER) {
                return StackElement.NULL;
            } else if (frame == Opcodes.TOP) {
                throw new IllegalStateException();
            } else {
                throw new UnsupportedOperationException("Not implemented.");
            }
        } else if (frame instanceof LabelNode) {
            LabelNode label = (LabelNode) frame;
            AbstractInsnNode nextNode = label.getNext();
            while (nextNode instanceof LineNumberNode || nextNode instanceof FrameNode) {
                nextNode = nextNode.getNext();
            }
            TypeInsnNode typeInsn = (TypeInsnNode) nextNode;
            if (typeInsn.getOpcode() != Opcodes.NEW) {
                throw new IllegalStateException();
            }
            if (typeInsn.desc.endsWith(";")) {
                throw new UnsupportedOperationException();
            }
            return new StackElement(source, 'L' + typeInsn.desc + ';');
        } else {
            throw new UnsupportedOperationException("Class " + frame.getClass().toString() + " not implemented.");
        }
    }
}
