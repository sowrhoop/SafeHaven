# SafeHaven 🛡️

SafeHaven is an uncompromising, headless Android Device Owner (Device Admin) daemon designed for absolute digital minimalism, deep focus, and dopamine detox. 

Unlike standard digital wellbeing apps that rely on willpower, SafeHaven operates at the OS kernel-policy level. It runs without a persistent UI, enforces a strict application whitelist, hardens network settings, strips away UI dopamine triggers (animations, colors), and self-heals against bypass attempts.

> **⚠️ CRITICAL WARNING:** SafeHaven is designed to be an unbreakable vault. It disables Safe Mode, Factory Resets, and Developer Options. Installing this and setting it as Device Owner **may permanently lock your device** if you do not understand the provisioning flow. Use at your own risk. A factory reset from the bootloader is often the only way to remove it.

## 🧠 Philosophy & Architecture
SafeHaven was built to act as an external pre-frontal cortex for intense academic and professional focus. 

* **Headless Design:** Zero UI. The app hides its own launcher icon upon activation to eliminate attack surfaces and distractions.
* **O(1) Whitelist Engine:** A heavily optimized Coroutine-based engine enforces a continuous strict state. It sweeps the device to suspend *any* unauthorized app currently installed, and intercepts new package broadcasts to neutralize future installations within milliseconds.
* **Open-Source Privacy:** Personal whitelists and settings are stripped from the source code and injected at compile-time via GitHub Actions and Android `BuildConfig`.

## ⚡ Core Features
* **Zero-Latency App Suspension:** Instantly blocks and greys-out any non-whitelisted application. Protects the default system launcher dynamically to prevent UI bricking.
* **Dopamine Detox UI:** Continuously enforces System Grayscale, 0x Window/Transition Animations, and a forced Night Light schedule to make the device unappealing for endless scrolling.
* **Network Vault:** Hardcodes the global device Private DNS (e.g., Mullvad) and injects DNS-over-HTTPS (DoH) templates directly into Chromium-based browsers via Application Restrictions.
* **Ghost Traps (Anti-Bypass):** Utilizes `ContentObserver` to monitor the Android Settings database. If a user attempts to tap the Build Number 7 times to unlock Developer Options, SafeHaven overwrites the database in microseconds, keeping ADB and Dev Options permanently disabled.
* **Terminal Lockdown:** Leverages native Device Owner privileges to disable Safe Boot, Factory Resets, User Switching, and OS-level application control (Clear Data/Force Stop) on the daemon itself.

---

## 🛠️ Deployment & Installation

Because SafeHaven relies on secure variable injection, **do not download the source code and build it locally unless you configure a `local.properties` file**. The intended deployment method is via a GitHub Actions fork.

NOTE: This repository is configured for CI-only builds. The Gradle wrapper and local helper scripts have been removed to prevent local builds. To build locally you must install Gradle on your machine and either re-add the Gradle wrapper (run `gradle wrapper`) or use your system Gradle (e.g., `gradle assembleRelease`).

### Step 1: Fork & Configure Secrets
1. Fork this repository.
2. Go to your repository **Settings** -> **Secrets and variables** -> **Actions**.
3. Add the following **Repository Secrets**:
   * `APP_WHITELIST`: Comma-separated package names (e.g., `app.anonymous.safehaven,com.aurora.store,com.whatsapp`). No spaces.
   * `BATTERY_FLAGS`: Your specific Android battery saver constants.
   * `DNS_SPECIFIER`: Your secure DNS (e.g., `all.dns.mullvad.net`).
   * `KEYSTORE_BASE64`: A base64 encoded PKCS12 `.keystore` file.
   * `KEY_ALIAS`, `KEY_PASSWORD`, `KEYSTORE_PASSWORD`: Your signing credentials.
4. Run the **Production Release** GitHub Action. Download the generated APK to your host machine.

### Step 2: Device Debloat (Pre-Provisioning)
To prevent creating an endlessly updating system blacklist, you must surgically remove OEM bloatware *before* installing SafeHaven. Connect your phone via ADB:
```bash
# Example: Nuke OEM browsers and bloatware for the current user (Non-root)
adb shell pm uninstall -k --user 0 com.android.chrome
adb shell pm uninstall -k --user 0 com.google.android.youtube
```

### Step 3: Install & Grant Permissions

Install your dynamically built APK:

```bash
adb install -r Safehaven-v1.0-Production.apk
```

Before setting it as Device Owner, grant the app WRITE_SECURE_SETTINGS so DNS and Grayscale enforcements can bind to the OS:

```bash
adb shell pm grant app.anonymous.safehaven android.permission.WRITE_SECURE_SETTINGS
```

### Step 4: Promote to Device Owner

> Note: This command usually only works on a freshly factory-reset device before any user accounts (Google accounts) are added.

```bash
adb shell dpm set-device-owner app.anonymous.safehaven/.Receiver
```

### Step 5: The Final Lock

- Disconnect the USB cable.
- Toggle off USB debugging and Wireless debugging in Developer options and revoke any remaining debugging authorizations.
- Optionally turn Developer options off (this hides the Dev options menu on some devices).
- Open your whitelisted installer and install your necessary apps.
- Reboot the device. Upon reboot, SafeHaven's `BootReceiver` will trigger `SP.hasRebootedSinceSetup = true`. The Developer Options trap will instantly activate, and the device will enter its permanent self-healing lockdown state. Every non-whitelisted app currently on the device, as well as any installed in the future, will be suspended instantaneously.

---

## 🛑 Troubleshooting & Removal

* **`dpm set-device-owner` fails:** The device is already provisioned. You must factory reset the device, skip adding a Google/Wi‑Fi account during the setup wizard, and immediately run the ADB commands.
* **Removal:** SafeHaven sets `setUninstallBlocked` to true on itself. It cannot be uninstalled, and Factory Reset from the OS settings is disabled. To remove the app, you must boot your phone into the hardware recovery menu (usually Power + Volume Down) and execute a hard wipe.