package zone.rong.loliasm.patches;


import org.objectweb.asm.*;


public class SoundRegistryPatch implements Opcodes {

    private static final String SOUND_REGISTRY_INTERNAL = "net/minecraft/client/audio/SoundRegistry";
    private static final String REGISTRY_SIMPLE_INTERNAL = "net/minecraft/util/registry/RegistrySimple";
    private static final String RESOURCE_LOCATION_INTERNAL = "net/minecraft/util/ResourceLocation";
    private static final String SOUNDEVENTACCESSOR_INTERNAL = "net/minecraft/client/audio/SoundEventAccessor";

    public static byte[] patchSoundRegistry(byte[] classBytes) {
        ClassReader cr = new ClassReader(classBytes);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

        ClassVisitor cv = new ClassVisitor(ASM5, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                // Overwrite createUnderlyingMap() method
                if (name.equals("createUnderlyingMap") && desc.equals("()Ljava/util/Map;")) {
                    // Replace method with call to super.createUnderlyingMap()
                    return new MethodVisitor(ASM5, super.visitMethod(access, name, desc, signature, exceptions)) {
                        @Override
                        public void visitCode() {
                            mv.visitCode();
                            // Load "this"
                            mv.visitVarInsn(ALOAD, 0);
                            // Invoke super.createUnderlyingMap()
                            mv.visitMethodInsn(INVOKESPECIAL,
                                    REGISTRY_SIMPLE_INTERNAL,
                                    "createUnderlyingMap",
                                    "()Ljava/util/Map;",
                                    false);
                            // Return the Map
                            mv.visitInsn(ARETURN);
                            mv.visitMaxs(0, 0); // COMPUTE_MAXS will calculate correct values
                            mv.visitEnd();
                        }
                    };
                }

                // Overwrite clearMap() method
                if (name.equals("clearMap") && desc.equals("()V")) {
                    // Replace method with call to this.clearUnderlyingMap()
                    return new MethodVisitor(ASM5, super.visitMethod(access, name, desc, signature, exceptions)) {
                        @Override
                        public void visitCode() {
                            mv.visitCode();
                            // Load "this"
                            mv.visitVarInsn(ALOAD, 0);
                            // Invoke this.clearUnderlyingMap()
                            mv.visitMethodInsn(INVOKEVIRTUAL,
                                    SOUND_REGISTRY_INTERNAL,
                                    "clearUnderlyingMap",
                                    "()V",
                                    false);
                            // Return void
                            mv.visitInsn(RETURN);
                            mv.visitMaxs(0, 0);
                            mv.visitEnd();
                        }
                    };
                }

                return super.visitMethod(access, name, desc, signature, exceptions);
            }
        };

        cr.accept(cv, 0);
        return cw.toByteArray();
    }
}

