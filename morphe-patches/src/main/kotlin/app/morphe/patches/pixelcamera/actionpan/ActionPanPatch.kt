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
    }
}
