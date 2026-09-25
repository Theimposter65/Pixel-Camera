# Motion Blur Capture Fix — Losq Readiness Bypass

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix Action Pan / Long Exposure capture on Pixel 6a (bluejay) by forcing the `Losq` readiness state to READY and bypassing the `ojd.n()` readiness gate, so `takePictureNow` proceeds to the actual capture pipeline instead of returning early.

**Architecture:** Two-layer defense-in-depth approach. **Layer 1 (root cause):** Patch the `Losq.<init>(ZLosp;)V` constructor to force `isReady=true` and `status=READY` regardless of what the Motion Blur / Lasagna pipeline tracking passes in. **Layer 2 (defense in depth):** Patch `ojd.n()` to skip the `Losq.a` readiness check entirely, jumping directly to the capture codepath at `:cond_1`. Both patches are integrated into the existing `ActionPanPatch.kt` morphe bytecode patch since they're part of the same Action Pan feature on unsupported devices.

**Tech Stack:** Kotlin (morphe-patches framework), smali bytecode, dexlib2

**Spec:** This plan itself — root cause analysis described in the user's request.

## Global Constraints

- Target APK: `com.google.android.GoogleCamera` version `11.0.073.972752740.32`
- Patch framework: morphe-patches v1.0.6 with `app.morphe.patches` Gradle plugin v1.3.4
- All patches must use `PixelCameraPatchUtils` or inline smali via `toInstructions()`
- Patch files must be mirrored in both `morphe-patches/src/` and `morphe-patches/patches/src/` trees
- No external DEX resources needed — these patches use inline bytecode replacement only
- Existing ActionPanPatch steps (1–7) must remain unmodified

## Review Focus

1. **Losq constructor override must preserve the `Ljava/lang/Object;-><init>` supercall** — omitting it causes a `VerifyError` at runtime. The patched body must call `invoke-direct {p0}, Ljava/lang/Object;-><init>()V` before any field writes.
2. **`sget-object` for `Losp;->a:Losp;` must use the correct static enum field** — `Losp;->a` is ordinal 0 = `READY`. Using `b` (SHUTDOWN) or any other would poison all 20+ Losq consumers downstream with an incorrect status string in logs/analytics.
3. **ojd.n() defense bypass must jump to `:cond_1` (line 1179), not `:cond_0`** — jumping to `:cond_0` re-enters the Losq check. `:cond_1` is the entry point of the actual capture pipeline (`const/4 v2, 0x0` + `invoke-virtual {p0, v2}, Lojd;->c(Z)V`).
4. **nrb.smali `:pswitch_5` (case 14) passes `Losq.a` to `Loih.a(Z)` to enable/disable shutter** — with the Losq constructor forced to `true`, this cascades correctly, making the shutter always enabled. No separate nrb patch is needed, but this must be verified on-device.
5. **Other Losq consumers (20+ classes)** will also receive `isReady=true` globally. For this backport context (unsupported device), this is intentional — these consumers include UI state, analytics, and other feature gates that should all behave as "ready".

---

### Task 1: Patch Losq Constructor to Force isReady=true and status=READY

**Files:**
- Modify: [`ActionPanPatch.kt`](file:///home/kailua/workspace2/Pixel-Camera/morphe-patches/patches/src/main/kotlin/app/morphe/patches/pixelcamera/actionpan/ActionPanPatch.kt) (line ~76)
- Modify: [`ActionPanPatch.kt` mirror](file:///home/kailua/workspace2/Pixel-Camera/morphe-patches/src/main/kotlin/app/morphe/patches/pixelcamera/actionpan/ActionPanPatch.kt) (keep identical)
- Reference: [`osq.smali`](file:///home/kailua/workspace2/Pixel-Camera/apktool_full/smali/osq.smali) (Losq class)
- Reference: [`osp.smali`](file:///home/kailua/workspace2/Pixel-Camera/apktool_full/smali/osp.smali) (Losp enum — field `a` = READY, ordinal 0)

**Interfaces:**
- Consumes: `PixelCameraPatchUtils.replaceMethodBody()` — existing utility
- Produces: All `Losq` instances globally will have `a=true` (isReady) and `b=Losp;->a` (READY)

#### Design Decision: Constructor Replacement vs. Consumer Patching

> [!IMPORTANT]
> We patch the **constructor** (`Losq.<init>(ZLosp;)V`), not the ~20 consumer sites. This is the minimal-diff approach: one 6-instruction method body replaces the original, and every consumer automatically receives `isReady=true`. Patching consumers would require 20+ separate bytecode edits and is fragile to APK version updates.

The `Losq` class has two constructors:
- `<init>()V` — guard constructor that throws null (lines 13-20). **Do not touch this.**
- `<init>(ZLosp;)V` — real constructor (lines 22-34). **Replace this body.**

Original constructor body ([osq.smali:22-34](file:///home/kailua/workspace2/Pixel-Camera/apktool_full/smali/osq.smali#L22-L34)):
```smali
invoke-direct {p0}, Ljava/lang/Object;-><init>()V
iput-boolean p1, p0, Losq;->a:Z           # stores passed-in boolean
invoke-virtual {p2}, Ljava/lang/Object;->getClass()Ljava/lang/Class;  # null-check on p2
iput-object p2, p0, Losq;->b:Losp;        # stores passed-in enum
return-void
```

Patched constructor body:
```smali
invoke-direct {p0}, Ljava/lang/Object;-><init>()V
const/4 p1, 0x1                           # force isReady = true
iput-boolean p1, p0, Losq;->a:Z
sget-object p2, Losp;->a:Losp;            # force status = READY (ordinal 0)
iput-object p2, p0, Losq;->b:Losp;
return-void
```

> [!NOTE]
> The original constructor's `invoke-virtual {p2}, Ljava/lang/Object;->getClass()Ljava/lang/Class;` is a Kotlin-generated null-check (throws NPE if p2 is null). Since we override p2 with a static enum reference, this null-check is unnecessary and safely omitted.

> [!WARNING]
> The `replaceMethodBody` utility selects methods via `firstOrNull` matching name + return type. Since both `<init>()V` and `<init>(ZLosp;)V` share `name="<init>"` and `returnType="V"`, it would match the wrong one (the guard constructor). **Use direct method selection with parameter type matching** as shown below.

- [ ] **Step 1: Add step 8 to ActionPanPatch.kt — Losq constructor override**

In [`ActionPanPatch.kt`](file:///home/kailua/workspace2/Pixel-Camera/morphe-patches/patches/src/main/kotlin/app/morphe/patches/pixelcamera/actionpan/ActionPanPatch.kt), inside the `execute { }` block, after step 7 (line 75) and before the closing `}` of `execute`, add:

```kotlin
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
```

- [ ] **Step 2: Mirror the change to the root-level source tree**

Copy the modified `ActionPanPatch.kt` from `morphe-patches/patches/src/main/kotlin/…/actionpan/` to `morphe-patches/src/main/kotlin/…/actionpan/` — both files must be byte-identical.

- [ ] **Step 3: Verify compilation**

Run: `cd morphe-patches && ./gradlew compileKotlin`  
Expected: BUILD SUCCESSFUL, no errors.

- [ ] **Step 4: Commit**

```bash
git add morphe-patches/patches/src/main/kotlin/app/morphe/patches/pixelcamera/actionpan/ActionPanPatch.kt
git add morphe-patches/src/main/kotlin/app/morphe/patches/pixelcamera/actionpan/ActionPanPatch.kt
git commit -m "fix(actionpan): force Losq readiness to READY in constructor

Override Losq.<init>(ZLosp;)V to always set isReady=true (a=0x1)
and status=READY (b=Losp.a), fixing Action Pan / Long Exposure
capture on Pixel 6a where the Lasagna pipeline tracking evaluates
to not-ready."
```

---

### Task 2: Defense-in-Depth — Bypass Readiness Check in ojd.n()

**Files:**
- Modify: [`ActionPanPatch.kt`](file:///home/kailua/workspace2/Pixel-Camera/morphe-patches/patches/src/main/kotlin/app/morphe/patches/pixelcamera/actionpan/ActionPanPatch.kt) (both trees)
- Reference: [`ojd.smali:1099-1266`](file:///home/kailua/workspace2/Pixel-Camera/apktool_full/smali_classes2/ojd.smali#L1099-L1266) (ojd.n / takePictureNow)

**Interfaces:**
- Consumes: Task 1's Losq override (belt), this is the suspenders
- Produces: `ojd.n()` will unconditionally reach the capture pipeline after the camera-null check

#### Design Decision: NOP the Branch vs. Replace Method Body

> [!IMPORTANT]
> We **replace the conditional branch instruction** (`if-nez v2, :cond_1` → unconditional `goto :cond_1`) rather than replacing the entire 167-line method body. The capture pipeline code after `:cond_1` is complex and must remain unmodified. The camera-null check (first guard) should remain functional.

The critical gate in [`ojd.n()`](file:///home/kailua/workspace2/Pixel-Camera/apktool_full/smali_classes2/ojd.smali#L1141-L1177):

```smali
# Line 1141-1143: Read Losq readiness
check-cast v2, Losq;
iget-boolean v2, v2, Losq;->a:Z

# Line 1145: THE GATE — conditional jump to capture path
if-nez v2, :cond_1          ← when false, falls through to log + return-void

# Lines 1147-1177: "Not taking picture" log and early return
...
return-void

:cond_1                      ← capture pipeline begins here (line 1179)
```

Strategy: Find `iget-boolean ... Losq;->a:Z` followed by `if-nez`, replace `if-nez` with unconditional `goto` to same target.

> [!TIP]
> Use `GOTO_16` (`BuilderInstruction20t`) instead of `GOTO` (`BuilderInstruction10t`). The 8-bit `GOTO` supports ±127 offset; while the jump here is ~34 instructions (within range), `GOTO_16` (±32767) is safer against instruction list mutations by other patches.

- [ ] **Step 1: Add step 9 to ActionPanPatch.kt — ojd.n() readiness bypass**

After the step 8 block, add:

```kotlin
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
                            val fieldRef = (prev as? com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction)
                                ?.reference as? com.android.tools.smali.dexlib2.iface.reference.FieldReference
                            if (fieldRef != null &&
                                fieldRef.definingClass == "Losq;" &&
                                fieldRef.name == "a" &&
                                fieldRef.type == "Z") {
                                val ifNezIns = ins as com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21t
                                val target = ifNezIns.target
                                impl.replaceInstruction(i,
                                    com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction20t(
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
```

- [ ] **Step 2: Ensure the required imports are present at the top of ActionPanPatch.kt**

The file already imports `Opcode` via other patch code. Verify these imports exist (add if missing):

```kotlin
import com.android.tools.smali.dexlib2.Opcode
```

- [ ] **Step 3: Mirror the change to the root-level source tree**

- [ ] **Step 4: Verify compilation**

Run: `cd morphe-patches && ./gradlew compileKotlin`  
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add morphe-patches/patches/src/main/kotlin/app/morphe/patches/pixelcamera/actionpan/ActionPanPatch.kt
git add morphe-patches/src/main/kotlin/app/morphe/patches/pixelcamera/actionpan/ActionPanPatch.kt
git commit -m "fix(actionpan): defense-in-depth bypass of Losq readiness gate in ojd.n()

Replace if-nez (readiness conditional) with unconditional goto_16 to
the capture codepath in MotionBlurModule#takePictureNow."
```

---

### Task 3: Add Verification Tests

**Files:**
- Modify: [`SmokeTest.kt`](file:///home/kailua/workspace2/Pixel-Camera/morphe-patches/patches/src/test/kotlin/SmokeTest.kt) (after line 209)

**Interfaces:**
- Consumes: Task 1 and Task 2's bytecode modifications in the patched DEX output
- Produces: CI-verifiable test coverage for the new patches

- [ ] **Step 1: Add test for Losq constructor patch**

Add after the existing `testVerifyActionPanPatches` method:

```kotlin
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
```

- [ ] **Step 2: Add test for ojd.n() bypass**

```kotlin
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
```

- [ ] **Step 3: Run tests**

Run: `cd morphe-patches && ./gradlew test`  
Expected: All tests PASS.

- [ ] **Step 4: Commit**

```bash
git add morphe-patches/patches/src/test/kotlin/SmokeTest.kt
git commit -m "test(actionpan): verify Losq readiness and ojd.n() bypass patches"
```

---

### Task 4: Update patches-bundle.json Description

**Files:**
- Modify: [`patches-bundle.json`](file:///home/kailua/workspace2/Pixel-Camera/patches-bundle.json) (repo root)

**Interfaces:**
- Consumes: None
- Produces: Updated bundle metadata reflecting the capture fix

- [ ] **Step 1: Update the Action Pan patch description**

Find the `"Action Pan & Motion Blur for Unsupported Pixels"` entry in `patches-bundle.json` and append to its `description` field: `" Includes Losq readiness bypass for capture support on bluejay."` (or similar wording).

- [ ] **Step 2: Commit**

```bash
git add patches-bundle.json
git commit -m "docs(bundle): note Losq readiness capture fix in Action Pan description"
```
