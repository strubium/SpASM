package zone.rong.loliasm.patches;

import org.objectweb.asm.*;

public class EntityFallPatch {

    public static byte[] patchFallDistance(byte[] classBytes) {
        ClassReader cr = new ClassReader(classBytes);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

        ClassVisitor cv = new ClassVisitor(Opcodes.ASM5, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);

                // Match updateFallState(double, boolean, IBlockState, BlockPos)
                if (name.equals("updateFallState") && desc.equals("(DZLnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/math/BlockPos;)V")) {
                    return new MethodVisitor(Opcodes.ASM5, mv) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String methodName, String methodDesc, boolean isInterface) {
                            // Before onFallenUpon call
                            if (opcode == Opcodes.INVOKEVIRTUAL &&
                                    owner.equals("net/minecraft/block/Block") &&
                                    methodName.equals("onFallenUpon") &&
                                    methodDesc.equals("(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/entity/Entity;F)V")) {

                                // Inject:
                                // if (y < 0) fallDistance -= y;
                                Label skip = new Label();

                                // Load y (index 1, double)
                                mv.visitVarInsn(Opcodes.DLOAD, 1);
                                mv.visitInsn(Opcodes.DCONST_0);
                                mv.visitInsn(Opcodes.DCMPG);
                                mv.visitJumpInsn(Opcodes.IFGE, skip); // skip if y >= 0

                                // Load 'this'
                                mv.visitVarInsn(Opcodes.ALOAD, 0);
                                // Load 'this.fallDistance'
                                mv.visitVarInsn(Opcodes.ALOAD, 0);
                                mv.visitFieldInsn(Opcodes.GETFIELD, "net/minecraft/entity/Entity", "fallDistance", "F");
                                // Load y and convert to float
                                mv.visitVarInsn(Opcodes.DLOAD, 1);
                                mv.visitInsn(Opcodes.D2F);
                                // Subtract y from fallDistance
                                mv.visitInsn(Opcodes.FSUB);
                                // Store back into fallDistance
                                mv.visitFieldInsn(Opcodes.PUTFIELD, "net/minecraft/entity/Entity", "fallDistance", "F");

                                mv.visitLabel(skip);
                            }

                            super.visitMethodInsn(opcode, owner, methodName, methodDesc, isInterface);
                        }
                    };
                }

                return mv;
            }
        };

        cr.accept(cv, 0);
        return cw.toByteArray();
    }

}
