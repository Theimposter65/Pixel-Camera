package app.morphe.patches.pixelcamera.actionpan

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.toInstructions
import app.morphe.patches.pixelcamera.PixelCameraPatchUtils
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction20t
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21t
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod

val actionPanPatch = bytecodePatch(
    name = "Action Pan & Motion Blur for Unsupported Pixels",
    description = "Unlocks Google's Action Pan and Long Exposure modes on unsupported Tensor devices (e.g. Pixel 6a bluejay) by clearing Google's device restrictions and enabling the lasagna capture pipeline."
) {
    extendWith("TomteInitHelper.dex")

    compatibleWith(
        "com.google.android.GoogleCamera" to setOf("11.0.073.972752740.32")
    )

    execute {
        // ── 1. Enable Action Pan flag resolution in TomteInitHelper on CameraApp start ──
        mutableClassDefByOrNull("Lcom/google/android/apps/camera/app/CameraApp;")?.let { clazz ->
            clazz.methods.firstOrNull { it.name == "onCreate" }?.let { method ->
                val impl = method.implementation ?: return@let
                val hookSmali = """
                    const/4 v0, 0x1
                    invoke-static {v0}, Lcom/google/android/patch/cameralooks/TomteInitHelper;->setActionPanEnabled(Z)V
                    const/4 v0, 0x0
                    invoke-static {v0}, Lcom/google/android/patch/cameralooks/TomteInitHelper;->setActionPanUseDarwinn(Z)V
                """.trimIndent()
                try {
                    val instructions = hookSmali.toInstructions(method)
                    var insertPos = 0
                    for (ins in instructions) {
                        impl.addInstruction(insertPos++, ins)
                    }
                } catch (_: Throwable) {}
            }
        }

        // ── 2. Device classification: treat as Tensor G1 flagship (r() -> true) ──
        mutableClassDefByOrNull("Luyv;")?.let { clazz ->
            PixelCameraPatchUtils.forceReturnTrue(clazz, "r")
        }

        // ── 3. Force njn mode configuration to enable Action Pan & Long Exposure ──
        mutableClassDefByOrNull("Lnjn;")?.let { clazz ->
            val initSmali = """
                invoke-direct {p0}, Ljava/lang/Object;-><init>()V
                const/4 v0, 0x1
                iput-boolean v0, p0, Lnjn;->a:Z
                iput-boolean v0, p0, Lnjn;->b:Z
                iput-boolean v0, p0, Lnjn;->c:Z
                return-void
            """.trimIndent()
            PixelCameraPatchUtils.replaceMethodBody(clazz, "<init>", "V", initSmali)
        }

        // ── 4. Hook klm feature flags & model routing via TomteInitHelper ──
        mutableClassDefByOrNull("Lklm;")?.let { clazz ->
            PixelCameraPatchUtils.hookKlmFlags(clazz)
            PixelCameraPatchUtils.hookKlmFlagH(clazz)
        }

        // ── 5. Neutralize shutter disabling in Motion Blur state machine (njj.a) ──
        mutableClassDefByOrNull("Lnjj;")?.let { clazz ->
            PixelCameraPatchUtils.forceReturnVoid(clazz, "a")
        }

        // ── 6. Protect ShutterButton listener validation (sid.f()Z -> true) ──
        mutableClassDefByOrNull("Lsid;")?.let { clazz ->
            PixelCameraPatchUtils.forceReturnTrue(clazz, "f")
        }

        // ── 7. Ensure ShutterButton clickability validation always passes (ShutterButton.u()Z -> true) ──
        mutableClassDefByOrNull("Lcom/google/android/apps/camera/ui/shutterbutton/ShutterButton;")?.let { clazz ->
            PixelCameraPatchUtils.forceReturnTrue(clazz, "u")
        }

        // ── 8. Force Losq readiness: override constructor to always set isReady=true, status=READY ──
        mutableClassDefByOrNull("Losq;")?.let { clazz ->
            clazz.methods.firstOrNull {
                it.name == "<init>" && it.parameterTypes == listOf("Z", "Losp;")
            }?.let { method ->
                val impl = method.implementation ?: return@let
                val initSmali = """
                    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
                    const/4 p1, 0x1
                    iput-boolean p1, p0, Losq;->a:Z
                    sget-object p2, Losp;->a:Losp;
                    iput-object p2, p0, Losq;->b:Losp;
                    return-void
                """.trimIndent()
                val newInstructions = initSmali.toInstructions(method)
                try {
                    val field = impl.javaClass.getDeclaredField("tryBlocks")
                    field.isAccessible = true
                    (field.get(impl) as? MutableList<*>)?.clear()
                } catch (_: Throwable) {}
                while (impl.instructions.isNotEmpty()) {
                    impl.removeInstruction(0)
                }
                for (ins in newInstructions) {
                    impl.addInstruction(ins)
                }
            }
        }

        // ── 9. Defense-in-depth: bypass Losq readiness check in ojd.n() (takePictureNow) ──
        mutableClassDefByOrNull("Lojd;")?.let { clazz ->
            clazz.methods.firstOrNull {
                it.name == "n" && it.returnType == "V" && it.parameterTypes.isEmpty()
            }?.let { method ->
                val impl = method.implementation ?: return@let
                val instructions = impl.instructions.toList()
                for (i in instructions.indices) {
                    val ins = instructions[i]
                    if (ins.opcode == Opcode.IF_NEZ && i > 0) {
                        val prev = instructions[i - 1]
                        if (prev.opcode == Opcode.IGET_BOOLEAN) {
                            val fieldRef = (prev as? ReferenceInstruction)?.reference as? FieldReference
                            if (fieldRef != null &&
                                fieldRef.definingClass == "Losq;" &&
                                fieldRef.name == "a" &&
                                fieldRef.type == "Z") {
                                val ifNezIns = ins as BuilderInstruction21t
                                val target = ifNezIns.target
                                impl.replaceInstruction(i,
                                    BuilderInstruction20t(
                                        Opcode.GOTO_16, target
                                    )
                                )
                                break
                            }
                        }
                    }
                }
            }
        }

        // ── 10. Shutter listener fallback: ensure ojc implements g() delegating to a() ──
        mutableClassDefByOrNull("Lojc;")?.let { clazz ->
            if (clazz.methods.none { it.name == "g" }) {
                val aMethod = clazz.methods.firstOrNull {
                    it.name == "a" && it.returnType == "V" && it.parameterTypes.isEmpty()
                }
                if (aMethod != null) {
                    val newG = MutableMethod(aMethod)
                    newG.name = "g"
                    val smaliG = """
                        invoke-virtual {p0}, Lojc;->a()V
                        return-void
                    """.trimIndent()
                    val gInstructions = smaliG.toInstructions(newG)
                    val gImpl = newG.implementation
                    if (gImpl != null) {
                        try {
                            val field = gImpl.javaClass.getDeclaredField("tryBlocks")
                            field.isAccessible = true
                            (field.get(gImpl) as? MutableList<*>)?.clear()
                        } catch (_: Throwable) {}
                        while (gImpl.instructions.isNotEmpty()) {
                            gImpl.removeInstruction(0)
                        }
                        for (ins in gInstructions) {
                            gImpl.addInstruction(ins)
                        }
                        clazz.methods.add(newG)
                    }
                }
            }
        }

        // ── 11. PictureTaker availability bypass: ensure captureImage runs unconditionally in par.a() ──
        mutableClassDefByOrNull("Lpar;")?.let { clazz ->
            clazz.methods.firstOrNull {
                it.name == "a" && it.returnType == "V" && it.parameterTypes.isEmpty()
            }?.let { method ->
                val impl = method.implementation ?: return@let
                val instructions = impl.instructions.toList()

                val ifNezIndices = mutableListOf<Int>()
                for (i in instructions.indices) {
                    val ins = instructions[i]
                    if (ins.opcode == Opcode.IF_NEZ && i >= 2) {
                        val prev = instructions[i - 1]
                        val prev2 = instructions[i - 2]
                        if (prev.opcode == Opcode.MOVE_RESULT && prev2.opcode == Opcode.INVOKE_VIRTUAL) {
                            val methodRef = (prev2 as? ReferenceInstruction)?.reference as? MethodReference
                            if (methodRef?.name == "booleanValue" && methodRef.definingClass == "Ljava/lang/Boolean;") {
                                ifNezIndices.add(i)
                            }
                        }
                    }
                }

                if (ifNezIndices.size >= 2) {
                    val secondIfNez = instructions[ifNezIndices[1]] as? BuilderInstruction21t
                    val captureTarget = secondIfNez?.target
                    if (captureTarget != null) {
                        impl.replaceInstruction(
                            ifNezIndices[1],
                            BuilderInstruction20t(Opcode.GOTO_16, captureTarget)
                        )
                        impl.replaceInstruction(
                            ifNezIndices[0],
                            BuilderInstruction20t(Opcode.GOTO_16, captureTarget)
                        )
                    }
                }
            }
        }
    }
}
