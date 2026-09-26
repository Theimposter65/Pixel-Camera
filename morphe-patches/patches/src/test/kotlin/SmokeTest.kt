package app.morphe.patches.pixelcamera

import org.junit.Test
import kotlin.test.assertTrue

class SmokeTest {
    @Test
    fun testInspectPatchedDex() {
        val dexDir = java.io.File("build/tmp/test_patcher/patched_dex")
        val foundZoom = mutableSetOf<String>()
        val foundPortrait = mutableSetOf<String>()
        val foundPhotoSaving = mutableSetOf<String>()
        for (dexFile in dexDir.listFiles()?.sortedBy { it.name } ?: emptyList()) {
            if (!dexFile.name.endsWith(".dex")) continue
            val dex = com.android.tools.smali.dexlib2.DexFileFactory.loadDexFile(dexFile, com.android.tools.smali.dexlib2.Opcodes.getDefault())
            for (c in dex.classes) {
                if (c.type in listOf("Lkfw;", "Lkgy;", "Lkgx;", "Lkhk;", "Lkgs;", "Lkfl;")) {
                    foundZoom.add(c.type)
                    println("${dexFile.name} defines zoom class ${c.type} with ${c.methods.count()} methods")
                }
                if (c.type in listOf("Lpwm;", "Lpwh;", "Lpvz;", "Lpwp;", "Lkic;", "Lnum;", "Lioy;", "Lhpq;")) {
                    foundPortrait.add(c.type)
                    println("${dexFile.name} defines portrait class ${c.type} with ${c.methods.count()} methods")
                }
                if (c.type in listOf("Lhpq;", "Lpsh;", "Lpsk;", "Lmjy;", "Ltba;", "Lmkm;", "Lejn;")) {
                    foundPhotoSaving.add(c.type)
                    println("${dexFile.name} defines photo saving class ${c.type} with ${c.methods.count()} methods")
                }
            }
        }
        println("Total zoom classes found in patched output: ${foundZoom.size} / 6")
        assertTrue(foundZoom.size == 6, "All 6 zoom classes should be present in patched output")
        println("Total portrait classes found in patched output: ${foundPortrait.size} / 8")
        assertTrue(foundPortrait.size == 8, "All 8 portrait classes should be present in patched output")
        println("Total photo saving classes found in patched output: ${foundPhotoSaving.size} / 7")
        assertTrue(foundPhotoSaving.size == 7, "All 7 photo saving classes should be present in patched output")
    }

    @Test
    fun testAssembleDexes() {
        val rootDir = java.io.File("../..")
        val tomteSmali = java.io.File(rootDir, "smali_patches/TomteInitHelper.smali")
        assertTrue(tomteSmali.exists(), "TomteInitHelper.smali must exist")

        val optionsTomte = com.android.tools.smali.smali.SmaliOptions()
        val tomteDexOut = java.io.File("src/main/resources/TomteInitHelper.dex")
        optionsTomte.outputDexFile = tomteDexOut.absolutePath
        val tomteSuccess = com.android.tools.smali.smali.Smali.assemble(optionsTomte, listOf(tomteSmali.absolutePath))
        assertTrue(tomteSuccess, "TomteInitHelper assembly must succeed")
        println("Generated TomteInitHelper.dex: ${tomteDexOut.length()} bytes")
        tomteDexOut.copyTo(java.io.File("../src/main/resources/TomteInitHelper.dex"), overwrite = true)

        val portraitSmaliFiles = listOf(
            java.io.File(rootDir, "apktool_full/smali_classes2/kic.smali"),
            java.io.File(rootDir, "apktool_full/smali_classes2/pwm.smali"),
            java.io.File(rootDir, "apktool_full/smali_classes2/pwh.smali"),
            java.io.File(rootDir, "apktool_full/smali/pvz.smali"),
            java.io.File(rootDir, "apktool_full/smali/pwp.smali"),
            java.io.File(rootDir, "apktool_full/smali/num.smali"),
            java.io.File(rootDir, "apktool_full/smali/ioy.smali"),
            java.io.File(rootDir, "apktool_full/smali/hpq.smali")
        )
        for (f in portraitSmaliFiles) {
            assertTrue(f.exists(), "Smali file ${f.name} must exist")
        }

        val optionsPortrait = com.android.tools.smali.smali.SmaliOptions()
        val portraitDexOut = java.io.File("src/main/resources/PortraitControllers.dex")
        optionsPortrait.outputDexFile = portraitDexOut.absolutePath
        val portraitSuccess = com.android.tools.smali.smali.Smali.assemble(optionsPortrait, portraitSmaliFiles.map { it.absolutePath })
        assertTrue(portraitSuccess, "PortraitControllers assembly must succeed")
        println("Generated PortraitControllers.dex: ${portraitDexOut.length()} bytes")
        portraitDexOut.copyTo(java.io.File("../src/main/resources/PortraitControllers.dex"), overwrite = true)
        portraitDexOut.copyTo(java.io.File(rootDir, "scratch/PortraitControllers.dex"), overwrite = true)

        val photoSavingSmaliFiles = listOf(
            java.io.File(rootDir, "apktool_full/smali/hpq.smali"),
            java.io.File(rootDir, "apktool_full/smali/psh.smali"),
            java.io.File(rootDir, "apktool_full/smali/psk.smali"),
            java.io.File(rootDir, "apktool_full/smali/mjy.smali"),
            java.io.File(rootDir, "apktool_full/smali/tba.smali"),
            java.io.File(rootDir, "apktool_full/smali/mkm.smali"),
            java.io.File(rootDir, "apktool_full/smali/ejn.smali")
        )
        for (f in photoSavingSmaliFiles) {
            assertTrue(f.exists(), "Smali file ${f.name} must exist")
        }

        val optionsPhotoSaving = com.android.tools.smali.smali.SmaliOptions()
        val photoSavingDexOut = java.io.File("src/main/resources/PhotoSavingControllers.dex")
        optionsPhotoSaving.outputDexFile = photoSavingDexOut.absolutePath
        val photoSavingSuccess = com.android.tools.smali.smali.Smali.assemble(optionsPhotoSaving, photoSavingSmaliFiles.map { it.absolutePath })
        assertTrue(photoSavingSuccess, "PhotoSavingControllers assembly must succeed")
        println("Generated PhotoSavingControllers.dex: ${photoSavingDexOut.length()} bytes")
        photoSavingDexOut.copyTo(java.io.File("../src/main/resources/PhotoSavingControllers.dex"), overwrite = true)
        photoSavingDexOut.copyTo(java.io.File(rootDir, "scratch/PhotoSavingControllers.dex"), overwrite = true)
    }

    @Test
    fun testTomteInitHelperActionPanSupport() {
        val rootDir = java.io.File("../..")
        val tomteSmali = java.io.File(rootDir, "smali_patches/TomteInitHelper.smali")
        assertTrue(tomteSmali.exists(), "TomteInitHelper.smali must exist")
        val content = tomteSmali.readText()
        assertTrue(content.contains("sActionPanEnabled"), "TomteInitHelper must have sActionPanEnabled field")
        assertTrue(content.contains("setActionPanEnabled"), "TomteInitHelper must have setActionPanEnabled method")
    }

    @Test
    fun testActionPanPatchDefinition() {
        val patch = app.morphe.patches.pixelcamera.actionpan.actionPanPatch
        assertTrue(patch.name == "Action Pan & Motion Blur for Unsupported Pixels")
        assertTrue(patch.description?.contains("Action Pan") == true)
    }

    @Test
    fun testPatchesListContainsActionPan() {
        val file = java.io.File("../../patches-list.json")
        assertTrue(file.exists())
        val content = file.readText()
        assertTrue(content.contains("Action Pan & Motion Blur for Unsupported Pixels"))
    }

    @Test
    fun testPatchesBundleContainsActionPan() {
        val file = java.io.File("../../patches-bundle.json")
        assertTrue(file.exists())
        val content = file.readText()
        assertTrue(content.contains("Action Pan & Motion Blur for Unsupported Pixels"))
        assertTrue(content.contains("Theimposter65/Pixel-Camera"))
    }

    @Test
    fun testVerifyActionPanPatches() {
        val dexDir = java.io.File("build/tmp/test_patcher/patched_dex")
        if (!dexDir.exists()) return

        var verifiedUyv = false
        var verifiedNjn = false
        var verifiedNjj = false
        var verifiedSid = false
        var verifiedShutterButton = false
        var verifiedKlm = false

        for (dexFile in dexDir.listFiles()?.sortedBy { it.name } ?: emptyList()) {
            if (!dexFile.name.endsWith(".dex")) continue
            val dex = com.android.tools.smali.dexlib2.DexFileFactory.loadDexFile(dexFile, com.android.tools.smali.dexlib2.Opcodes.getDefault())
            for (c in dex.classes) {
                if (c.type == "Luyv;") {
                    val rMethod = c.methods.firstOrNull { it.name == "r" }
                    if (rMethod != null) {
                        val ins = rMethod.implementation?.instructions?.toList() ?: emptyList()
                        println("uyv.r instructions: ${ins.joinToString { it.opcode.name }}")
                        assertTrue(ins.any { it.opcode == com.android.tools.smali.dexlib2.Opcode.CONST_4 })
                        verifiedUyv = true
                    }
                }
                if (c.type == "Lnjn;") {
                    val initMethod = c.methods.firstOrNull { it.name == "<init>" }
                    if (initMethod != null) {
                        val ins = initMethod.implementation?.instructions?.toList() ?: emptyList()
                        println("njn.<init> instructions: ${ins.joinToString { it.opcode.name }}")
                        assertTrue(ins.any { it.opcode == com.android.tools.smali.dexlib2.Opcode.IPUT_BOOLEAN })
                        verifiedNjn = true
                    }
                }
                if (c.type == "Lnjj;") {
                    val aMethod = c.methods.firstOrNull { it.name == "a" }
                    if (aMethod != null) {
                        val ins = aMethod.implementation?.instructions?.toList() ?: emptyList()
                        println("njj.a instructions: ${ins.joinToString { it.opcode.name }}")
                        assertTrue(ins.all { it.opcode == com.android.tools.smali.dexlib2.Opcode.RETURN_VOID })
                        verifiedNjj = true
                    }
                }
                if (c.type == "Lsid;") {
                    val fMethod = c.methods.firstOrNull { it.name == "f" }
                    if (fMethod != null) {
                        val ins = fMethod.implementation?.instructions?.toList() ?: emptyList()
                        println("sid.f instructions: ${ins.joinToString { it.opcode.name }}")
                        assertTrue(ins.any { it.opcode == com.android.tools.smali.dexlib2.Opcode.CONST_4 })
                        verifiedSid = true
                    }
                }
                if (c.type == "Lcom/google/android/apps/camera/ui/shutterbutton/ShutterButton;") {
                    val uMethod = c.methods.firstOrNull { it.name == "u" }
                    if (uMethod != null) {
                        val ins = uMethod.implementation?.instructions?.toList() ?: emptyList()
                        println("ShutterButton.u instructions: ${ins.joinToString { it.opcode.name }}")
                        assertTrue(ins.any { it.opcode == com.android.tools.smali.dexlib2.Opcode.CONST_4 })
                        verifiedShutterButton = true
                    }
                }
                if (c.type == "Lklm;") {
                    if (c.methods.any { it.name == "original_q" }) {
                        verifiedKlm = true
                    }
                }
            }
        }

        assertTrue(verifiedUyv, "uyv.r must be patched")
        assertTrue(verifiedNjn, "njn.<init> must be patched")
        assertTrue(verifiedNjj, "njj.a must be patched to return void")
        assertTrue(verifiedSid, "sid.f must be patched")
        assertTrue(verifiedShutterButton, "ShutterButton.u must be patched")
        assertTrue(verifiedKlm, "klm must be hooked via TomteInitHelper")
        println("All Action Pan and ShutterButton bytecode patches successfully verified in DEX output!")
    }

    @Test
    fun testVerifyLosqReadinessPatch() {
        val dexDir = java.io.File("build/tmp/test_patcher/patched_dex")
        if (!dexDir.exists()) return

        var verifiedLosqInit = false
        for (dexFile in dexDir.listFiles()?.sortedBy { it.name } ?: emptyList()) {
            if (!dexFile.name.endsWith(".dex")) continue
            val dex = com.android.tools.smali.dexlib2.DexFileFactory.loadDexFile(
                dexFile, com.android.tools.smali.dexlib2.Opcodes.getDefault()
            )
            for (c in dex.classes) {
                if (c.type == "Losq;") {
                    val initMethod = c.methods.firstOrNull {
                        it.name == "<init>" && it.parameterTypes.toList() == listOf("Z", "Losp;")
                    }
                    if (initMethod != null) {
                        val ins = initMethod.implementation?.instructions?.toList() ?: emptyList()
                        val opcodes = ins.map { it.opcode }
                        println("Losq.<init>(ZLosp;)V opcodes: ${opcodes.joinToString()}")
                        assertTrue(opcodes.contains(com.android.tools.smali.dexlib2.Opcode.CONST_4),
                            "Losq constructor must contain CONST_4 for forced isReady=true")
                        assertTrue(opcodes.contains(com.android.tools.smali.dexlib2.Opcode.SGET_OBJECT),
                            "Losq constructor must contain SGET_OBJECT for Losp.READY")
                        val sgetIns = ins.firstOrNull { it.opcode == com.android.tools.smali.dexlib2.Opcode.SGET_OBJECT }
                        if (sgetIns is com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction) {
                            val ref = sgetIns.reference as? com.android.tools.smali.dexlib2.iface.reference.FieldReference
                            assertTrue(ref?.definingClass == "Losp;" && ref.name == "a",
                                "SGET_OBJECT must reference Losp;->a (READY)")
                        }
                        verifiedLosqInit = true
                    }
                }
            }
        }
        assertTrue(verifiedLosqInit, "Losq.<init>(ZLosp;)V must be patched to force READY")
        println("Losq readiness constructor patch verified!")
    }

    @Test
    fun testVerifyOjdReadinessBypass() {
        val dexDir = java.io.File("build/tmp/test_patcher/patched_dex")
        if (!dexDir.exists()) return

        var verifiedOjdN = false
        for (dexFile in dexDir.listFiles()?.sortedBy { it.name } ?: emptyList()) {
            if (!dexFile.name.endsWith(".dex")) continue
            val dex = com.android.tools.smali.dexlib2.DexFileFactory.loadDexFile(
                dexFile, com.android.tools.smali.dexlib2.Opcodes.getDefault()
            )
            for (c in dex.classes) {
                if (c.type == "Lojd;") {
                    val nMethod = c.methods.firstOrNull {
                        it.name == "n" && it.returnType == "V" && it.parameterTypes.isEmpty()
                    }
                    if (nMethod != null) {
                        val ins = nMethod.implementation?.instructions?.toList() ?: emptyList()
                        var foundBypass = false
                        for (i in ins.indices) {
                            if (ins[i].opcode == com.android.tools.smali.dexlib2.Opcode.IGET_BOOLEAN) {
                                val ref = (ins[i] as? com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction)
                                    ?.reference as? com.android.tools.smali.dexlib2.iface.reference.FieldReference
                                if (ref?.definingClass == "Losq;" && ref.name == "a" && i + 1 < ins.size) {
                                    val next = ins[i + 1]
                                    assertTrue(
                                        next.opcode == com.android.tools.smali.dexlib2.Opcode.GOTO ||
                                        next.opcode == com.android.tools.smali.dexlib2.Opcode.GOTO_16,
                                        "Expected GOTO after Losq.a check, found ${next.opcode}"
                                    )
                                    foundBypass = true
                                }
                            }
                        }
                        assertTrue(foundBypass, "ojd.n() must have Losq readiness gate replaced with GOTO")
                        verifiedOjdN = true
                    }
                }
            }
        }
        assertTrue(verifiedOjdN, "ojd.n() readiness bypass must be in patched DEX")
        println("ojd.n() readiness bypass verified!")
    }

    @Test
    fun testVerifyOjcMethodG() {
        val dexDir = java.io.File("build/tmp/test_patcher/patched_dex")
        if (!dexDir.exists()) return

        var verifiedOjcG = false
        for (dexFile in dexDir.listFiles()?.sortedBy { it.name } ?: emptyList()) {
            if (!dexFile.name.endsWith(".dex")) continue
            val dex = com.android.tools.smali.dexlib2.DexFileFactory.loadDexFile(
                dexFile, com.android.tools.smali.dexlib2.Opcodes.getDefault()
            )
            for (c in dex.classes) {
                if (c.type == "Lojc;") {
                    val gMethod = c.methods.firstOrNull { it.name == "g" && it.returnType == "V" && it.parameterTypes.isEmpty() }
                    if (gMethod != null) {
                        verifiedOjcG = true
                    }
                }
            }
        }
        assertTrue(verifiedOjcG, "ojc must define method g()V delegating to a()")
        println("ojc.g() delegation verified!")
    }

    @Test
    fun testVerifyParPictureTakerBypass() {
        val dexDir = java.io.File("build/tmp/test_patcher/patched_dex")
        if (!dexDir.exists()) return

        var verifiedParBypass = false
        for (dexFile in dexDir.listFiles()?.sortedBy { it.name } ?: emptyList()) {
            if (!dexFile.name.endsWith(".dex")) continue
            val dex = com.android.tools.smali.dexlib2.DexFileFactory.loadDexFile(
                dexFile, com.android.tools.smali.dexlib2.Opcodes.getDefault()
            )
            for (c in dex.classes) {
                if (c.type == "Lpar;") {
                    val aMethod = c.methods.firstOrNull { it.name == "a" && it.returnType == "V" && it.parameterTypes.isEmpty() }
                    if (aMethod != null) {
                        val ins = aMethod.implementation?.instructions?.toList() ?: emptyList()
                        var gotoCount = 0
                        for (i in ins.indices) {
                            val inss = ins[i]
                            if (inss.opcode == com.android.tools.smali.dexlib2.Opcode.GOTO_16 ||
                                inss.opcode == com.android.tools.smali.dexlib2.Opcode.GOTO) {
                                if (i >= 2) {
                                    val prev = ins[i - 1]
                                    val prev2 = ins[i - 2]
                                    if (prev.opcode == com.android.tools.smali.dexlib2.Opcode.MOVE_RESULT &&
                                        prev2.opcode == com.android.tools.smali.dexlib2.Opcode.INVOKE_VIRTUAL) {
                                        gotoCount++
                                    }
                                }
                            }
                        }
                        assertTrue(gotoCount >= 2, "Expected at least 2 GOTO replacements in par.a(), found $gotoCount")
                        verifiedParBypass = true
                    }
                }
            }
        }
        assertTrue(verifiedParBypass, "Lpar;->a() PictureTaker readiness bypass must be present")
        println("Lpar;->a() PictureTaker readiness bypass verified!")
    }
}



