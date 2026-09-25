# Generational Teardown: Pixel 6 (Oriole / Raven / Bluejay / P21)

## Target Profile
* **Generation**: Pixel 6, Pixel 6 Pro, Pixel 6a
* **SoC**: Google Tensor G1 (First-gen Google Silicon)
* **RAM**: 6 GB (Pixel 6a) / 8 GB (Pixel 6) / 12 GB (Pixel 6 Pro)
* **Target Android Version**: Android 13 / 14 / 15
* **Camera HAL Interface**: `com.google.android.camera.experimental2021`

---

## Compatibility Assessment

### 1. Feature Gating & Bypass
* **Device Identification**:
  In `uyv.java`, Pixel 6 devices correspond to flags `a` (Pixel 6), `b` (Pixel 6 Pro), and `d` (Pixel 6a).
  `uyv.l()` returns `false` due to these explicit flags.
* **Required Bypass**:
  Bypassing `uyv.l()` and forcing `klm.q(kjq.bm)` / `klm.q(kjq.bl)` to `true`.
* **UI Behavior**:
  The Camera Looks overlay loads and functions properly. UI navigation and slider adjustments are responsive.

### 2. Camera HAL & Vendor Tag Behavior
* The Pixel 6 Camera HAL implements `experimental2021` (Version 7).
* `tds.c` is `null`; vendor tag passing is skipped.
* No low-level driver failures or sensor timeouts occur.

### 3. Image Processing & Memory Considerations
* `mla.K()` sets `ShotParams_tomte_type_set(shotParams.a, shotParams, lookId)`.
* In `ifj.java`, `zrq` maps to `DEVICE_UNSPECIFIED` (`0`), triggering `looknet_v2.1_float.tflite.uncompressed`.
* **Memory & Processing Latency**:
  * On Tensor G1, Halide tonal processing takes ~2.0s–2.4s per shot.
  * On the 6 GB RAM model (Pixel 6a), shooting rapid consecutive bursts with Camera Looks active can cause memory pressure, potentially triggering background app eviction or delayed post-processing.
  * For single captures and normal photo sessions, processing completes successfully.

### 4. Verdict
**Status: PARTIAL / PASS WITH ADVISORY**
* Single and moderate-paced photography: **PASS** (image processing functions correctly).
* Rapid burst photography on 6 GB Pixel 6a: **ADVISORY** (elevated capture latency).

---

### 5. Action Pan & Motion Blur Unlock (Pixel 6a / Bluejay)

#### Reverse Engineering & Root Observation
* **Empirical Validation**: On rooted Pixel 6a (`bluejay`), altering the system model property `ro.product.model` to `Pixel 6` (`oriole`) followed by a clean install of Google Camera instantly displays the Action Pan and Long Exposure mode selector. Captures process cleanly without optical flow failure or crashes.
* **Artificial Restriction**:
  * Google artificially suppressed `camera.lasagna` flags on Pixel 6a via Phenotype server-side configurations and hardcoded device gating, despite the device featuring the identical Tensor G1 SoC (`gs101`), identical TPU core, and IMX363 sensor architecture known to support multi-frame motion alignment.
* **Smali Gating Structure**:
  * `kkb.smali`: Declares `camera.lasagna` (`f`), `camera.lasagna_action` (`g`), `camera.lasagna_long_exposure` (`h`), `camera.lasagna_bottom_layer` (`i`), and `camera.lasagna.use_darwinn` (`j`).
  * `njn.smali`: Constructor reads `kkb.f`, `kkb.g`, `kkb.h` via `klm.q(Lkiz;)Z` to set fields `a`, `b`, and `c`.
  * `sdo.smali`: Evaluates `njn.a`, `njn.b`, `njn.c` when constructing the mode carousel list (`sdo.N`), adding `sql.p` (Action Pan) and `sql.o` (Long Exposure).
* **Morphe & Build Script Patch**:
  * `ActionPanPatch`: Sets `TomteInitHelper.setActionPanEnabled(true)` in `CameraApp.onCreate` and replaces `njn.<init>` to initialize `a, b, c` to `0x1`.
  * Injects `motion_blur_asset_module_p26.apk` assets (`motion-custom_op-v6.tflite.uncompressed` and `saliency-custom_op-v6.tflite.uncompressed`) into the APK.
  * Allows Pixel 6a users to utilize Action Pan and Long Exposure seamlessly without requiring device model spoofing or root access.

