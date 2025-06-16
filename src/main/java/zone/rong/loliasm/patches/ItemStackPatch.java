package zone.rong.loliasm.patches;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.util.ListIterator;

public class ItemStackPatch implements Opcodes {

    public static byte[] patchItemStacks(byte[] classBytes) {
        ClassReader cr = new ClassReader(classBytes);
        ClassNode classNode = new ClassNode();
        cr.accept(classNode, 0);

        patchItemStacks(classNode);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        classNode.accept(cw);
        return cw.toByteArray();
    }

    private static void patchItemStacks(ClassNode classNode) {
        // Add private boolean field initializedCapabilities;
        boolean fieldExists = false;
        for (FieldNode f : classNode.fields) {
            if (f.name.equals("initializedCapabilities")) {
                fieldExists = true;
                break;
            }
        }
        if (!fieldExists) {
            FieldNode initializedCapabilitiesField = new FieldNode(ACC_PRIVATE, "initializedCapabilities", "Z", null, false);
            classNode.fields.add(initializedCapabilitiesField);
        }

        // Remove existing methods that will be overwritten
        String[] methodsToRemove = new String[]{
                "hasInitializedCapabilities",
                "initializeCapabilities",
                "copy",
                "hasCapability",
                "getCapability",
                "areCapsCompatible",
                "forgeInit"
        };

        ListIterator<MethodNode> iter = classNode.methods.listIterator();
        while (iter.hasNext()) {
            MethodNode m = iter.next();
            for (String name : methodsToRemove) {
                if (m.name.equals(name)) {
                    iter.remove();
                    break;
                }
            }
        }

        // Add hasInitializedCapabilities method
        MethodNode hasInitializedCapabilities = new MethodNode(
                ACC_PUBLIC,
                "hasInitializedCapabilities",
                "()Z",
                null,
                null);
        InsnList hasInitInsn = hasInitializedCapabilities.instructions;
        hasInitInsn.add(new VarInsnNode(ALOAD, 0));
        hasInitInsn.add(new FieldInsnNode(GETFIELD, classNode.name, "initializedCapabilities", "Z"));
        hasInitInsn.add(new InsnNode(IRETURN));
        classNode.methods.add(hasInitializedCapabilities);

        // Add initializeCapabilities method
        MethodNode initializeCapabilities = new MethodNode(
                ACC_PUBLIC,
                "initializeCapabilities",
                "()V",
                null,
                null);
        InsnList initCaps = initializeCapabilities.instructions;

        LabelNode returnLabel = new LabelNode();

        // if (initializedCapabilities) return;
        initCaps.add(new VarInsnNode(ALOAD, 0));
        initCaps.add(new FieldInsnNode(GETFIELD, classNode.name, "initializedCapabilities", "Z"));
        initCaps.add(new JumpInsnNode(IFNE, returnLabel));

        // initializedCapabilities = true;
        initCaps.add(new VarInsnNode(ALOAD, 0));
        initCaps.add(new InsnNode(ICONST_1));
        initCaps.add(new FieldInsnNode(PUTFIELD, classNode.name, "initializedCapabilities", "Z"));

        // Item item = getItemRaw();
        // Note: getItemRaw() is protected abstract in mixin, but in vanilla is protected, so just call it.
        initCaps.add(new VarInsnNode(ALOAD, 0));
        initCaps.add(new MethodInsnNode(INVOKESPECIAL, classNode.name, "getItemRaw", "()Lnet/minecraft/item/Item;", false));
        initCaps.add(new VarInsnNode(ASTORE, 1)); // local var 1 = item

        // if (item != null) {
        LabelNode afterIfLabel = new LabelNode();
        initCaps.add(new VarInsnNode(ALOAD, 1));
        initCaps.add(new JumpInsnNode(IFNULL, afterIfLabel));

        // this.capabilities = ForgeEventFactory.gatherCapabilities(this, item.initCapabilities(this, this.capNBT));
        initCaps.add(new VarInsnNode(ALOAD, 1)); // item
        initCaps.add(new VarInsnNode(ALOAD, 0)); // this
        initCaps.add(new VarInsnNode(ALOAD, 0)); // this
        initCaps.add(new FieldInsnNode(GETFIELD, classNode.name, "capNBT", "Lnet/minecraft/nbt/NBTTagCompound;"));
        initCaps.add(new MethodInsnNode(INVOKEVIRTUAL, "net/minecraft/item/Item", "initCapabilities", "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/nbt/NBTTagCompound;)Lnet/minecraftforge/common/capabilities/CapabilityDispatcher;", false));
        initCaps.add(new MethodInsnNode(INVOKESTATIC, "net/minecraftforge/event/ForgeEventFactory", "gatherCapabilities",
                "(Lnet/minecraft/item/ItemStack;Lnet/minecraftforge/common/capabilities/CapabilityDispatcher;)Lnet/minecraftforge/common/capabilities/CapabilityDispatcher;", false));
        initCaps.add(new VarInsnNode(ALOAD, 0)); // this
        initCaps.add(new InsnNode(SWAP)); // swap top two stack elements so 'this' is under capabilities
        initCaps.add(new FieldInsnNode(PUTFIELD, classNode.name, "capabilities", "Lnet/minecraftforge/common/capabilities/CapabilityDispatcher;"));

        // if (this.capNBT != null && this.capabilities != null) { this.capabilities.deserializeNBT(this.capNBT); }
        LabelNode skipDeserialize = new LabelNode();

        initCaps.add(new VarInsnNode(ALOAD, 0));
        initCaps.add(new FieldInsnNode(GETFIELD, classNode.name, "capNBT", "Lnet/minecraft/nbt/NBTTagCompound;"));
        initCaps.add(new JumpInsnNode(IFNULL, skipDeserialize));

        initCaps.add(new VarInsnNode(ALOAD, 0));
        initCaps.add(new FieldInsnNode(GETFIELD, classNode.name, "capabilities", "Lnet/minecraftforge/common/capabilities/CapabilityDispatcher;"));
        initCaps.add(new JumpInsnNode(IFNULL, skipDeserialize));

        initCaps.add(new VarInsnNode(ALOAD, 0));
        initCaps.add(new FieldInsnNode(GETFIELD, classNode.name, "capabilities", "Lnet/minecraftforge/common/capabilities/CapabilityDispatcher;"));
        initCaps.add(new VarInsnNode(ALOAD, 0));
        initCaps.add(new FieldInsnNode(GETFIELD, classNode.name, "capNBT", "Lnet/minecraft/nbt/NBTTagCompound;"));
        initCaps.add(new MethodInsnNode(INVOKEVIRTUAL, "net/minecraftforge/common/capabilities/CapabilityDispatcher", "deserializeNBT", "(Lnet/minecraft/nbt/NBTTagCompound;)V", false));

        initCaps.add(skipDeserialize);

        initCaps.add(afterIfLabel);
        initCaps.add(returnLabel);
        initCaps.add(new InsnNode(RETURN));

        classNode.methods.add(initializeCapabilities);

        // Add copy() method
        MethodNode copyMethod = new MethodNode(ACC_PUBLIC, "copy", "()Lnet/minecraft/item/ItemStack;", null, null);
        InsnList copyInsns = copyMethod.instructions;

        // new ItemStack(this.getItem(), this.stackSize, this.itemDamage, this.capabilities != null ? this.capabilities.serializeNBT() : this.capNBT);
        copyInsns.add(new VarInsnNode(ALOAD, 0));
        copyInsns.add(new MethodInsnNode(INVOKEVIRTUAL, classNode.name, "getItem", "()Lnet/minecraft/item/Item;", false));

        copyInsns.add(new VarInsnNode(ALOAD, 0));
        copyInsns.add(new FieldInsnNode(GETFIELD, classNode.name, "stackSize", "I"));

        copyInsns.add(new VarInsnNode(ALOAD, 0));
        copyInsns.add(new FieldInsnNode(GETFIELD, classNode.name, "itemDamage", "I"));

        copyInsns.add(new VarInsnNode(ALOAD, 0));
        copyInsns.add(new FieldInsnNode(GETFIELD, classNode.name, "capabilities", "Lnet/minecraftforge/common/capabilities/CapabilityDispatcher;"));

        LabelNode copyElseLabel = new LabelNode();
        LabelNode copyEndIfLabel = new LabelNode();

        copyInsns.add(new JumpInsnNode(IFNULL, copyElseLabel));

        copyInsns.add(new VarInsnNode(ALOAD, 0));
        copyInsns.add(new FieldInsnNode(GETFIELD, classNode.name, "capabilities", "Lnet/minecraftforge/common/capabilities/CapabilityDispatcher;"));
        copyInsns.add(new MethodInsnNode(INVOKEVIRTUAL, "net/minecraftforge/common/capabilities/CapabilityDispatcher", "serializeNBT", "()Lnet/minecraft/nbt/NBTTagCompound;", false));
        copyInsns.add(new JumpInsnNode(GOTO, copyEndIfLabel));

        copyInsns.add(copyElseLabel);
        copyInsns.add(new VarInsnNode(ALOAD, 0));
        copyInsns.add(new FieldInsnNode(GETFIELD, classNode.name, "capNBT", "Lnet/minecraft/nbt/NBTTagCompound;"));

        copyInsns.add(copyEndIfLabel);

        copyInsns.add(new MethodInsnNode(INVOKESPECIAL, classNode.name, "<init>", "(Lnet/minecraft/item/Item;IILnet/minecraft/nbt/NBTTagCompound;)V", false));

        copyInsns.add(new VarInsnNode(ASTORE, 1));

        // stack.setAnimationsToGo(this.getAnimationsToGo());
        copyInsns.add(new VarInsnNode(ALOAD, 1));
        copyInsns.add(new VarInsnNode(ALOAD, 0));
        copyInsns.add(new MethodInsnNode(INVOKEVIRTUAL, classNode.name, "getAnimationsToGo", "()I", false));
        copyInsns.add(new MethodInsnNode(INVOKEVIRTUAL, classNode.name, "setAnimationsToGo", "(I)V", false));

        // if (this.stackTagCompound != null) { ((ItemStackMixin)(Object)stack).stackTagCompound = this.stackTagCompound.copy(); }
        LabelNode copySkipTag = new LabelNode();

        copyInsns.add(new VarInsnNode(ALOAD, 0));
        copyInsns.add(new FieldInsnNode(GETFIELD, classNode.name, "stackTagCompound", "Lnet/minecraft/nbt/NBTTagCompound;"));
        copyInsns.add(new JumpInsnNode(IFNULL, copySkipTag));

        copyInsns.add(new VarInsnNode(ALOAD, 1));
        copyInsns.add(new TypeInsnNode(CHECKCAST, "zone/rong/loliasm/common/capability/mixins/ItemStackMixin"));

        copyInsns.add(new VarInsnNode(ALOAD, 0));
        copyInsns.add(new FieldInsnNode(GETFIELD, classNode.name, "stackTagCompound", "Lnet/minecraft/nbt/NBTTagCompound;"));
        copyInsns.add(new MethodInsnNode(INVOKEVIRTUAL, "net/minecraft/nbt/NBTTagCompound", "copy", "()Lnet/minecraft/nbt/NBTTagCompound;", false));
        copyInsns.add(new FieldInsnNode(PUTFIELD, "zone/rong/loliasm/common/capability/mixins/ItemStackMixin", "stackTagCompound", "Lnet/minecraft/nbt/NBTTagCompound;"));

        copyInsns.add(copySkipTag);

        copyInsns.add(new VarInsnNode(ALOAD, 1));
        copyInsns.add(new InsnNode(ARETURN));

        classNode.methods.add(copyMethod);

        // Add hasCapability method (remap = false)
        MethodNode hasCapability = new MethodNode(ACC_PUBLIC, "hasCapability", "(Lnet/minecraftforge/common/capabilities/Capability;Lnet/minecraft/util/EnumFacing;)Z", null, null);
        InsnList hasCapInsns = hasCapability.instructions;

        LabelNode hasCapFalseLabel = new LabelNode();
        LabelNode hasCapAfterInitLabel = new LabelNode();

        // if (this.isEmpty) return false;
        hasCapInsns.add(new VarInsnNode(ALOAD, 0));
        hasCapInsns.add(new FieldInsnNode(GETFIELD, classNode.name, "isEmpty", "Z"));
        hasCapInsns.add(new JumpInsnNode(IFNE, hasCapFalseLabel));

        // if (!initializedCapabilities) initializeCapabilities();
        hasCapInsns.add(new VarInsnNode(ALOAD, 0));
        hasCapInsns.add(new FieldInsnNode(GETFIELD, classNode.name, "initializedCapabilities", "Z"));
        hasCapInsns.add(new JumpInsnNode(IFNE, hasCapAfterInitLabel));
        hasCapInsns.add(new VarInsnNode(ALOAD, 0));
        hasCapInsns.add(new MethodInsnNode(INVOKEVIRTUAL, classNode.name, "initializeCapabilities", "()V", false));

        hasCapInsns.add(hasCapAfterInitLabel);

        // return capabilities != null && capabilities.hasCapability(capability, facing);
        hasCapInsns.add(new VarInsnNode(ALOAD, 0));
        hasCapInsns.add(new FieldInsnNode(GETFIELD, classNode.name, "capabilities", "Lnet/minecraftforge/common/capabilities/CapabilityDispatcher;"));
        LabelNode hasCapReturnFalse = new LabelNode();
        hasCapInsns.add(new JumpInsnNode(IFNULL, hasCapReturnFalse));

        hasCapInsns.add(new VarInsnNode(ALOAD, 0));
        hasCapInsns.add(new FieldInsnNode(GETFIELD, classNode.name, "capabilities", "Lnet/minecraftforge/common/capabilities/CapabilityDispatcher;"));
        hasCapInsns.add(new VarInsnNode(ALOAD, 1)); // Capability parameter
        hasCapInsns.add(new VarInsnNode(ALOAD, 2)); // EnumFacing parameter
        hasCapInsns.add(new MethodInsnNode(INVOKEVIRTUAL, "net/minecraftforge/common/capabilities/CapabilityDispatcher", "hasCapability", "(Lnet/minecraftforge/common/capabilities/Capability;Lnet/minecraft/util/EnumFacing;)Z", false));
        hasCapInsns.add(new InsnNode(IRETURN));

        hasCapInsns.add(hasCapReturnFalse);
        hasCapInsns.add(new InsnNode(ICONST_0));
        hasCapInsns.add(new InsnNode(IRETURN));

        hasCapInsns.add(hasCapFalseLabel);
        hasCapInsns.add(new InsnNode(ICONST_0));
        hasCapInsns.add(new InsnNode(IRETURN));

        classNode.methods.add(hasCapability);

        // Add getCapability method (remap = false)
        MethodNode getCapability = new MethodNode(ACC_PUBLIC, "getCapability", "(Lnet/minecraftforge/common/capabilities/Capability;Lnet/minecraft/util/EnumFacing;)Ljava/lang/Object;", null, null);
        InsnList getCapInsns = getCapability.instructions;

        LabelNode getCapReturnNullLabel = new LabelNode();
        LabelNode getCapAfterInitLabel = new LabelNode();

        // if (this.isEmpty) return null;
        getCapInsns.add(new VarInsnNode(ALOAD, 0));
        getCapInsns.add(new FieldInsnNode(GETFIELD, classNode.name, "isEmpty", "Z"));
        getCapInsns.add(new JumpInsnNode(IFNE, getCapReturnNullLabel));

        // if (!initializedCapabilities) initializeCapabilities();
        getCapInsns.add(new VarInsnNode(ALOAD, 0));
        getCapInsns.add(new FieldInsnNode(GETFIELD, classNode.name, "initializedCapabilities", "Z"));
        getCapInsns.add(new JumpInsnNode(IFNE, getCapAfterInitLabel));
        getCapInsns.add(new VarInsnNode(ALOAD, 0));
        getCapInsns.add(new MethodInsnNode(INVOKEVIRTUAL, classNode.name, "initializeCapabilities", "()V", false));

        getCapInsns.add(getCapAfterInitLabel);

        // return capabilities == null ? null : capabilities.getCapability(capability, facing);
        getCapInsns.add(new VarInsnNode(ALOAD, 0));
        getCapInsns.add(new FieldInsnNode(GETFIELD, classNode.name, "capabilities", "Lnet/minecraftforge/common/capabilities/CapabilityDispatcher;"));
        LabelNode getCapCallLabel = new LabelNode();
        getCapInsns.add(new JumpInsnNode(IFNONNULL, getCapCallLabel));

        // return null;
        getCapInsns.add(new InsnNode(ACONST_NULL));
        getCapInsns.add(new InsnNode(ARETURN));

        // call capabilities.getCapability(capability, facing);
        getCapInsns.add(getCapCallLabel);
        getCapInsns.add(new VarInsnNode(ALOAD, 0));
        getCapInsns.add(new FieldInsnNode(GETFIELD, classNode.name, "capabilities", "Lnet/minecraftforge/common/capabilities/CapabilityDispatcher;"));
        getCapInsns.add(new VarInsnNode(ALOAD, 1)); // Capability parameter
        getCapInsns.add(new VarInsnNode(ALOAD, 2)); // EnumFacing parameter
        getCapInsns.add(new MethodInsnNode(INVOKEVIRTUAL, "net/minecraftforge/common/capabilities/CapabilityDispatcher", "getCapability", "(Lnet/minecraftforge/common/capabilities/Capability;Lnet/minecraft/util/EnumFacing;)Ljava/lang/Object;", false));
        getCapInsns.add(new InsnNode(ARETURN));

        getCapInsns.add(getCapReturnNullLabel);
        getCapInsns.add(new InsnNode(ACONST_NULL));
        getCapInsns.add(new InsnNode(ARETURN));

        classNode.methods.add(getCapability);

        // Add areCapsCompatible method
        MethodNode areCapsCompatible = new MethodNode(ACC_PUBLIC, "areCapsCompatible", "(Lnet/minecraft/item/ItemStack;)Z", null, null);
        InsnList areCapsInsns = areCapsCompatible.instructions;

        LabelNode afterThisInit = new LabelNode();
        LabelNode afterOtherInit = new LabelNode();
        LabelNode capsNullLabel = new LabelNode();

        // if (!initializedCapabilities) initializeCapabilities();
        areCapsInsns.add(new VarInsnNode(ALOAD, 0));
        areCapsInsns.add(new FieldInsnNode(GETFIELD, classNode.name, "initializedCapabilities", "Z"));
        areCapsInsns.add(new JumpInsnNode(IFNE, afterThisInit));
        areCapsInsns.add(new VarInsnNode(ALOAD, 0));
        areCapsInsns.add(new MethodInsnNode(INVOKEVIRTUAL, classNode.name, "initializeCapabilities", "()V", false));
        areCapsInsns.add(afterThisInit);

        // if (!other.hasInitializedCapabilities()) other.initializeCapabilities();
        areCapsInsns.add(new VarInsnNode(ALOAD, 1));
        areCapsInsns.add(new MethodInsnNode(INVOKEVIRTUAL, classNode.name, "hasInitializedCapabilities", "()Z", false));
        LabelNode skipOtherInit = new LabelNode();
        areCapsInsns.add(new JumpInsnNode(IFNE, skipOtherInit));
        areCapsInsns.add(new VarInsnNode(ALOAD, 1));
        areCapsInsns.add(new MethodInsnNode(INVOKEVIRTUAL, classNode.name, "initializeCapabilities", "()V", false));
        areCapsInsns.add(skipOtherInit);

        // if (this.capabilities == null && other.capabilities == null) return true;
        areCapsInsns.add(new VarInsnNode(ALOAD, 0));
        areCapsInsns.add(new FieldInsnNode(GETFIELD, classNode.name, "capabilities", "Lnet/minecraftforge/common/capabilities/CapabilityDispatcher;"));
        areCapsInsns.add(new JumpInsnNode(IFNONNULL, capsNullLabel));

        areCapsInsns.add(new VarInsnNode(ALOAD, 1));
        areCapsInsns.add(new FieldInsnNode(GETFIELD, classNode.name, "capabilities", "Lnet/minecraftforge/common/capabilities/CapabilityDispatcher;"));
        LabelNode retTrueLabel = new LabelNode();
        areCapsInsns.add(new JumpInsnNode(IFNULL, retTrueLabel));

        areCapsInsns.add(capsNullLabel);

        // if (this.capabilities == null || other.capabilities == null) return false;
        areCapsInsns.add(new VarInsnNode(ALOAD, 0));
        areCapsInsns.add(new FieldInsnNode(GETFIELD, classNode.name, "capabilities", "Lnet/minecraftforge/common/capabilities/CapabilityDispatcher;"));
        LabelNode retFalseLabel = new LabelNode();
        areCapsInsns.add(new JumpInsnNode(IFNULL, retFalseLabel));

        areCapsInsns.add(new VarInsnNode(ALOAD, 1));
        areCapsInsns.add(new FieldInsnNode(GETFIELD, classNode.name, "capabilities", "Lnet/minecraftforge/common/capabilities/CapabilityDispatcher;"));
        areCapsInsns.add(new JumpInsnNode(IFNULL, retFalseLabel));

        // return this.capabilities.equals(other.capabilities);
        areCapsInsns.add(new VarInsnNode(ALOAD, 0));
        areCapsInsns.add(new FieldInsnNode(GETFIELD, classNode.name, "capabilities", "Lnet/minecraftforge/common/capabilities/CapabilityDispatcher;"));
        areCapsInsns.add(new VarInsnNode(ALOAD, 1));
        areCapsInsns.add(new FieldInsnNode(GETFIELD, classNode.name, "capabilities", "Lnet/minecraftforge/common/capabilities/CapabilityDispatcher;"));
        areCapsInsns.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Object", "equals", "(Ljava/lang/Object;)Z", false));
        areCapsInsns.add(new InsnNode(IRETURN));

        areCapsInsns.add(retTrueLabel);
        areCapsInsns.add(new InsnNode(ICONST_1));
        areCapsInsns.add(new InsnNode(IRETURN));

        areCapsInsns.add(retFalseLabel);
        areCapsInsns.add(new InsnNode(ICONST_0));
        areCapsInsns.add(new InsnNode(IRETURN));

        classNode.methods.add(areCapsCompatible);

        // Add forgeInit method
        MethodNode forgeInit = new MethodNode(ACC_PUBLIC, "forgeInit", "()V", null, null);
        InsnList forgeInitInsns = forgeInit.instructions;

        forgeInitInsns.add(new VarInsnNode(ALOAD, 0));
        forgeInitInsns.add(new MethodInsnNode(INVOKEVIRTUAL, classNode.name, "initializeCapabilities", "()V", false));
        forgeInitInsns.add(new InsnNode(RETURN));

        classNode.methods.add(forgeInit);
    }
}

