package zone.rong.loliasm.patches;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.util.Iterator;

/**
 * Fixes LockCode not using its EMPTY variant when loading the EMPTY one from nbt
 */
public class LockCodePatch implements Opcodes {

    public static byte[] patchLockCode(byte[] classBytes) {
        ClassNode classNode = new ClassNode();
        ClassReader cr = new ClassReader(classBytes);
        cr.accept(classNode, 0);

        MethodNode targetMethod = null;
        Iterator<MethodNode> it = classNode.methods.iterator();
        while (it.hasNext()) {
            MethodNode mn = it.next();
            if (mn.name.equals("fromNBT") && mn.desc.equals("(Lnet/minecraft/nbt/NBTTagCompound;)Lnet/minecraft/world/LockCode;")) {
                targetMethod = mn;
                break;
            }
        }

        if (targetMethod == null) {
            throw new RuntimeException("Method fromNBT not found in LockCode class");
        }

        InsnList insns = new InsnList();

        LabelNode labelHasKeyFalse = new LabelNode();
        LabelNode labelAfterCheckEmpty = new LabelNode();

        // if (!nbt.hasKey("Lock", 8)) goto labelHasKeyFalse
        insns.add(new VarInsnNode(ALOAD, 0)); // nbt
        insns.add(new LdcInsnNode("Lock"));
        insns.add(new IntInsnNode(BIPUSH, 8));
        insns.add(new MethodInsnNode(INVOKEVIRTUAL, "net/minecraft/nbt/NBTTagCompound", "hasKey", "(Ljava/lang/String;I)Z", false));
        insns.add(new JumpInsnNode(IFEQ, labelHasKeyFalse));

        // String s = nbt.getString("Lock");
        insns.add(new VarInsnNode(ALOAD, 0));
        insns.add(new LdcInsnNode("Lock"));
        insns.add(new MethodInsnNode(INVOKEVIRTUAL, "net/minecraft/nbt/NBTTagCompound", "getString", "(Ljava/lang/String;)Ljava/lang/String;", false));
        insns.add(new VarInsnNode(ASTORE, 1));

        // if (s.isEmpty()) return EMPTY_CODE;
        insns.add(new VarInsnNode(ALOAD, 1));
        insns.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", "isEmpty", "()Z", false));
        insns.add(new JumpInsnNode(IFEQ, labelAfterCheckEmpty));

        // return EMPTY_CODE;
        insns.add(new FieldInsnNode(GETSTATIC, "net/minecraft/world/LockCode", "EMPTY_CODE", "Lnet/minecraft/world/LockCode;"));
        insns.add(new InsnNode(ARETURN));

        // else return new LockCode(s);
        insns.add(labelAfterCheckEmpty);
        insns.add(new TypeInsnNode(NEW, "net/minecraft/world/LockCode"));
        insns.add(new InsnNode(DUP));
        insns.add(new VarInsnNode(ALOAD, 1));
        insns.add(new MethodInsnNode(INVOKESPECIAL, "net/minecraft/world/LockCode", "<init>", "(Ljava/lang/String;)V", false));
        insns.add(new InsnNode(ARETURN));

        // else return EMPTY_CODE;
        insns.add(labelHasKeyFalse);
        insns.add(new FieldInsnNode(GETSTATIC, "net/minecraft/world/LockCode", "EMPTY_CODE", "Lnet/minecraft/world/LockCode;"));
        insns.add(new InsnNode(ARETURN));

        targetMethod.instructions.clear();
        targetMethod.instructions.add(insns);

        targetMethod.maxStack = 3;
        targetMethod.maxLocals = 2;

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        classNode.accept(cw);
        return cw.toByteArray();
    }
}