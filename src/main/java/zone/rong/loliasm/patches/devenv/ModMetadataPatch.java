package zone.rong.loliasm.patches.devenv;

import org.objectweb.asm.*;

import java.lang.reflect.Field;
import java.util.List;

public class ModMetadataPatch implements Opcodes {

    private static String requiredModsFieldName = null;
    private static String dependenciesFieldName = null;

    // Fully qualified Forge class names as strings, no direct references!
    private static final String FMLMODCONTAINER = "net/minecraftforge/fml/common/FMLModContainer";
    private static final String MODMETADATA = "net/minecraftforge/fml/common/ModMetadata";
    private static final String METADATA_COLLECTION_DESC = "Lnet/minecraftforge/fml/common/MetadataCollection;";

    private static void detectFieldNames() {
        if (requiredModsFieldName != null && dependenciesFieldName != null) return;

        try {
            Class<?> modMetadataClass = Class.forName("net.minecraftforge.fml.common.ModMetadata");
            for (Field f : modMetadataClass.getDeclaredFields()) {
                if (List.class.isAssignableFrom(f.getType())) {
                    if (requiredModsFieldName == null)
                        requiredModsFieldName = f.getName();
                    else if (dependenciesFieldName == null)
                        dependenciesFieldName = f.getName();
                }
            }
        } catch (Throwable t) {
            // fallback defaults if reflection fails
            requiredModsFieldName = "requiredMods";
            dependenciesFieldName = "dependencies";
        }
    }

    public static byte[] patchBindMetadata(byte[] classBytes) {
        detectFieldNames();

        ClassReader cr = new ClassReader(classBytes);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

        ClassVisitor cv = new ClassVisitor(Opcodes.ASM5, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);

                if ("bindMetadata".equals(name) && ("(" + METADATA_COLLECTION_DESC + ")V").equals(desc)) {
                    return new MethodVisitor(Opcodes.ASM5, mv) {
                        @Override
                        public void visitInsn(int opcode) {
                            if (opcode == Opcodes.RETURN) {
                                // Label to skip null checks
                                Label skipRequired = new Label();

                                // if (this.modMetadata.requiredMods == null) goto skipRequired
                                mv.visitVarInsn(Opcodes.ALOAD, 0);
                                mv.visitFieldInsn(Opcodes.GETFIELD,
                                        FMLMODCONTAINER,
                                        "modMetadata",
                                        "L" + MODMETADATA.replace('.', '/') + ";");
                                mv.visitFieldInsn(Opcodes.GETFIELD,
                                        MODMETADATA.replace('.', '/'),
                                        requiredModsFieldName,
                                        "Ljava/util/List;");
                                mv.visitJumpInsn(Opcodes.IFNULL, skipRequired);

                                // this.modMetadata.requiredMods.removeIf(predicate)
                                mv.visitVarInsn(Opcodes.ALOAD, 0);
                                mv.visitFieldInsn(Opcodes.GETFIELD,
                                        FMLMODCONTAINER,
                                        "modMetadata",
                                        "L" + MODMETADATA.replace('.', '/') + ";");
                                mv.visitFieldInsn(Opcodes.GETFIELD,
                                        MODMETADATA.replace('.', '/'),
                                        requiredModsFieldName,
                                        "Ljava/util/List;");

                                // Use invokedynamic to create Predicate lambda
                                Handle bsm = new Handle(
                                        Opcodes.H_INVOKESTATIC,
                                        "java/lang/invoke/LambdaMetafactory",
                                        "metafactory",
                                        "(Ljava/lang/invoke/MethodHandles$Lookup;" +
                                                "Ljava/lang/String;" +
                                                "Ljava/lang/invoke/MethodType;" +
                                                "Ljava/lang/invoke/MethodType;" +
                                                "Ljava/lang/invoke/MethodHandle;" +
                                                "Ljava/lang/invoke/MethodType;)" +
                                                "Ljava/lang/invoke/CallSite;",
                                        false);

                                mv.visitInvokeDynamicInsn(
                                        "test",
                                        "()Ljava/util/function/Predicate;",
                                        bsm,
                                        Type.getType("(Ljava/lang/Object;)Z"),
                                        new Handle(
                                                Opcodes.H_INVOKESTATIC,
                                                "zone/rong/loliasm/patches/devenv/LambdaHelper",
                                                "predicateTest",
                                                "(Ljava/lang/Object;)Z",
                                                false),
                                        Type.getType("(Ljava/lang/Object;)Z")
                                );

                                mv.visitMethodInsn(Opcodes.INVOKEINTERFACE,
                                        "java/util/List",
                                        "removeIf",
                                        "(Ljava/util/function/Predicate;)Z",
                                        true);
                                mv.visitInsn(Opcodes.POP);
                                mv.visitLabel(skipRequired);

                                // Same for dependencies
                                Label skipDeps = new Label();

                                mv.visitVarInsn(Opcodes.ALOAD, 0);
                                mv.visitFieldInsn(Opcodes.GETFIELD,
                                        FMLMODCONTAINER,
                                        "modMetadata",
                                        "L" + MODMETADATA.replace('.', '/') + ";");
                                mv.visitFieldInsn(Opcodes.GETFIELD,
                                        MODMETADATA.replace('.', '/'),
                                        dependenciesFieldName,
                                        "Ljava/util/List;");
                                mv.visitJumpInsn(Opcodes.IFNULL, skipDeps);

                                mv.visitVarInsn(Opcodes.ALOAD, 0);
                                mv.visitFieldInsn(Opcodes.GETFIELD,
                                        FMLMODCONTAINER,
                                        "modMetadata",
                                        "L" + MODMETADATA.replace('.', '/') + ";");
                                mv.visitFieldInsn(Opcodes.GETFIELD,
                                        MODMETADATA.replace('.', '/'),
                                        dependenciesFieldName,
                                        "Ljava/util/List;");

                                mv.visitInvokeDynamicInsn(
                                        "test",
                                        "()Ljava/util/function/Predicate;",
                                        bsm,
                                        Type.getType("(Ljava/lang/Object;)Z"),
                                        new Handle(
                                                Opcodes.H_INVOKESTATIC,
                                                "zone/rong/loliasm/patches/devenv/LambdaHelper",
                                                "predicateTest",
                                                "(Ljava/lang/Object;)Z",
                                                false),
                                        Type.getType("(Ljava/lang/Object;)Z")
                                );
                                mv.visitMethodInsn(Opcodes.INVOKEINTERFACE,
                                        "java/util/List",
                                        "removeIf",
                                        "(Ljava/util/function/Predicate;)Z",
                                        true);
                                mv.visitInsn(Opcodes.POP);
                                mv.visitLabel(skipDeps);
                            }
                            super.visitInsn(opcode);
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
