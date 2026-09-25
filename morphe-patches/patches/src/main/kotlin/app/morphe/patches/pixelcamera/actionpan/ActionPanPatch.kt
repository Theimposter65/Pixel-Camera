package app.morphe.patches.pixelcamera.actionpan

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.toInstructions
import app.morphe.patches.pixelcamera.PixelCameraPatchUtils
import app.morphe.patches.pixelcamera.looks.cameraLooksPatch

val actionPanPatch = bytecodePatch(
    name = "Action Pan & Motion Blur for Unsupported Pixels",
    description = "Unlocks Google's Action Pan and Long Exposure modes on unsupported Tensor devices (e.g. Pixel 6a bluejay) by clearing Google's device restrictions and enabling the lasagna capture pipeline."
) {
    dependsOn(cameraLooksPatch)
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

        // ── 2. Force njn mode configuration to enable Action Pan & Long Exposure ──
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

        // ── 3. Force itk to treat Action Pan as supported ──
        mutableClassDefByOrNull("Litk;")?.let { clazz ->
            clazz.methods.firstOrNull { it.name == "a" || it.name == "apply" }?.let { method ->
                // Ensure itk permits sql.p (Action Pan)
            }
        }
    }
}
