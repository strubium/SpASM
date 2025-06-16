package zone.rong.loliasm.patches;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.Method;
import zone.rong.loliasm.LoliLogger;

import java.lang.Thread;
import java.lang.Integer;

public class MinecraftServerThreadPatch {

    // This patches the method visitor for the startServerThread() method
    public static MethodVisitor patchStartServerThread(MethodVisitor mv, int access, String name, String desc) {
        return new AdviceAdapter(Opcodes.ASM5, mv, access, name, desc) {

            private int serverThreadVarIndex = -1;

            @Override
            public void visitMethodInsn(int opcode, String owner, String methodName, String methodDesc, boolean isInterface) {
                if (opcode == INVOKEVIRTUAL
                        && owner.equals("java/lang/Thread")
                        && methodName.equals("start")
                        && methodDesc.equals("()V")) {

                    // store the Thread instance from stack into a local var
                    serverThreadVarIndex = newLocal(Type.getType("Ljava/lang/Thread;"));
                    storeLocal(serverThreadVarIndex);

                    // load thread and set priority to MIN_PRIORITY + 2 (3)
                    loadLocal(serverThreadVarIndex);
                    push(Thread.MIN_PRIORITY + 2);
                    invokeVirtual(Type.getType(Thread.class), new Method("setPriority", "(I)V"));

                    // load thread and start
                    loadLocal(serverThreadVarIndex);
                    invokeVirtual(Type.getType(Thread.class), new Method("start", "()V"));

                    LoliLogger.instance.info("Modifying server thread priority!");
                    visitFieldInsn(GETSTATIC,
                            "net/minecraft/server/MinecraftServer",
                            "LOGGER",
                            "Lorg/apache/logging/log4j/Logger;");
                    visitLdcInsn("LoliASM: Started server thread, with {} priority");
                    visitInsn(ICONST_1);
                    visitTypeInsn(ANEWARRAY, "java/lang/Object");
                    visitInsn(DUP);
                    visitInsn(ICONST_0);
                    loadLocal(serverThreadVarIndex);
                    invokeVirtual(Type.getType(Thread.class), new Method("getPriority", "()I"));
                    invokeStatic(Type.getType(Integer.class), new Method("valueOf", "(I)Ljava/lang/Integer;"));
                    visitInsn(AASTORE);
                    invokeInterface(Type.getType("org/apache/logging/log4j/Logger"),
                            new Method("debug", "(Ljava/lang/String;[Ljava/lang/Object;)V"));

                } else {
                    super.visitMethodInsn(opcode, owner, methodName, methodDesc, isInterface);
                }
            }
        };
    }

    // This is the full class patch method you register with your transformer:
    public static byte[] patchMinecraftServer(byte[] classBytes) {
        ClassReader cr = new ClassReader(classBytes);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

        ClassVisitor cv = new ClassVisitor(Opcodes.ASM5, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (name.equals("startServerThread") && descriptor.equals("()V")) {
                    return patchStartServerThread(mv, access, name, descriptor);
                }
                return mv;
            }
        };

        cr.accept(cv, 0);
        return cw.toByteArray();
    }
}
