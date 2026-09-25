package app.morphe.patches.pixelcamera.actionpan

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.toInstructions
import app.morphe.patches.pixelcamera.PixelCameraPatchUtils

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

        // ── 2. Device classification: treat Pixel 6a (bluejay) as Pixel 6 (oriole) ──
        mutableClassDefByOrNull("Luyv;")?.let { clazz ->
            // Unconditionally treat as Tensor G1 flagship (r() -> true)
            PixelCameraPatchUtils.forceReturnTrue(clazz, "r")

            // In constructor, if this.N (bluejay) is detected, set this.M (oriole) = true
            clazz.methods.firstOrNull { it.name == "<init>" }?.let { method ->
                val impl = method.implementation ?: return@let
                val hookSmali = """
                    if-eqz v0, :cond_not_bluejay
                    const/4 v0, 0x1
                    iput-boolean v0, p0, Luyv;->M:Z
                    :cond_not_bluejay
                """.trimIndent()
                try {
                    val instructions = hookSmali.toInstructions(method)
                    val idx = impl.instructions.indexOfFirst {
                        it.opcode == com.android.tools.smali.dexlib2.Opcode.IPUT_BOOLEAN &&
                        it.toString().contains("->N:Z")
                    }
                    if (idx != -1) {
                        var pos = idx + 1
                        for (ins in instructions) {
                            impl.addInstruction(pos++, ins)
                        }
                    }
                } catch (_: Throwable) {}
            }
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

        // ── 5. Force itk to treat Action Pan and Long Exposure as supported for ShutterButton ──
        mutableClassDefByOrNull("Litk;")?.let { clazz ->
            clazz.methods.firstOrNull { it.name == "apply" || it.name == "a" }?.let { method ->
                val impl = method.implementation ?: return@let
                val hookSmali = """
                    iget v0, p0, Litk;->b:I
                    const/16 v1, 0x8
                    if-ne v0, v1, :cond_orig_itk
                    instance-of v0, p1, Lsql;
                    if-eqz v0, :cond_orig_itk
                    sget-object v0, Lsql;->p:Lsql;
                    if-eq p1, v0, :cond_itk_ret_true
                    sget-object v0, Lsql;->o:Lsql;
                    if-eq p1, v0, :cond_orig_itk
                    :cond_itk_ret_true
                    sget-object p0, Ljava/lang/Boolean;->TRUE:Ljava/lang/Boolean;
                    return-object p0
                    :cond_orig_itk
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

        // ── 6. Neutralize shutter disabling in Motion Blur state machine (njj.a) ──
        mutableClassDefByOrNull("Lnjj;")?.let { clazz ->
            PixelCameraPatchUtils.forceReturnVoid(clazz, "a")
        }

        // ── 7. Protect ShutterButton listener validation (sid.f()Z -> true) ──
        mutableClassDefByOrNull("Lsid;")?.let { clazz ->
            PixelCameraPatchUtils.forceReturnTrue(clazz, "f")
        }

        // ── 8. Ensure ShutterButton clickability validation always passes (ShutterButton.u()Z -> true) ──
        mutableClassDefByOrNull("Lcom/google/android/apps/camera/ui/shutterbutton/ShutterButton;")?.let { clazz ->
            PixelCameraPatchUtils.forceReturnTrue(clazz, "u")
        }

        // ── 9. Re-enable shutter immediately upon Motion Blur module start (ojd.l) ──
        mutableClassDefByOrNull("Lojd;")?.let { clazz ->
            clazz.methods.firstOrNull { it.name == "l" && it.returnType == "V" }?.let { method ->
                val impl = method.implementation ?: return@let
                val hookSmali = """
                    iget-object v0, p0, Lojd;->g:Lsia;
                    if-eqz v0, :cond_skip_sia
                    const/4 v1, 0x1
                    sget-object v2, Lshz;->a:Lshz;
                    invoke-interface {v0, v1, v2}, Lsia;->Z(ZLshz;)V
                    :cond_skip_sia
                """.trimIndent()
                try {
                    val instructions = hookSmali.toInstructions(method)
                    val returnIdx = impl.instructions.indexOfLast {
                        it.opcode == com.android.tools.smali.dexlib2.Opcode.RETURN_VOID
                    }
                    if (returnIdx != -1) {
                        var pos = returnIdx
                        for (ins in instructions) {
                            impl.addInstruction(pos++, ins)
                        }
                    }
                } catch (_: Throwable) {}
            }
        }

        // ── 10. Force CPU inference fallback for Lasagna / Motion Blur in mwg.a() ──
        mutableClassDefByOrNull("Lmwg;")?.let { clazz ->
            clazz.methods.firstOrNull { it.name == "a" && it.returnType == "V" }?.let { method ->
                val impl = method.implementation ?: return@let
                val hookSmali = """
                    const/16 v19, 0x0
                """.trimIndent()
                try {
                    val instructions = hookSmali.toInstructions(method)
                    val idx = impl.instructions.indexOfFirst {
                        it.opcode == com.android.tools.smali.dexlib2.Opcode.INVOKE_VIRTUAL &&
                        it.toString().contains("->q(Lkiz;)Z")
                    }
                    if (idx != -1) {
                        var pos = idx + 2
                        for (ins in instructions) {
                            impl.addInstruction(pos++, ins)
                        }
                    }
                } catch (_: Throwable) {}
            }
        }
    }
}
