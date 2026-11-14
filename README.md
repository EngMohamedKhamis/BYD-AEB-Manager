# 🚗 BYD AEB Manager

[![Platform](https://img.shields.io/badge/Platform-BYD%20DiLink%205.0-blue.svg?style=for-the-badge&logo=android)](https://github.com/EngMohamedKhamis/BYD-AEB-Manager)
[![Requirement](https://img.shields.io/badge/Requirement-ADB%20Enabled-orange.svg?style=for-the-badge&logo=powershell)](https://github.com/EngMohamedKhamis/BYD-AEB-Manager)
[![Latest Release](https://img.shields.io/badge/Download-Latest%20Release%20(APK)-success.svg?style=for-the-badge&logo=android)](https://github.com/EngMohamedKhamis/BYD-AEB-Manager/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/EngMohamedKhamis/BYD-AEB-Manager/total.svg?style=for-the-badge&logo=github&color=blue)](https://github.com/EngMohamedKhamis/BYD-AEB-Manager/releases)
[![License](https://img.shields.io/badge/License-MIT-lightgrey.svg?style=for-the-badge)](LICENSE)

> A lightweight background utility for BYD DiLink infotainment systems that automatically remembers and restores your preferred Automatic Emergency Braking (AEB) setting across driving cycles.

---

## ⚡ Why This Exists
On many BYD vehicles running DiLink 5.0, the infotainment system resets specific safety toggles back to default every time the vehicle powers off. Finding and switching the toggle back requires digging through 3 to 4 nested menu layers on every trip.

**BYD AEB Manager** runs quietly in the background on your DiLink screen, remembers your toggle preference, and automatically applies it when the car boots up—saving you time and menu taps every day.

---

## ✨ Features

- 🔄 **Automatic State Restoration**: Automatically re-applies your preferred AEB toggle state on car power-on.
- 🎛️ **Floating HUD Indicator**: An optional, minimal floating badge on the DiLink display shows current status at a glance.
- 🔒 **100% Offline & Private**: Zero internet permissions, zero analytics, zero telemetry. All communication stays strictly on the vehicle's local loopback.
- ⚡ **Lightweight & Efficient**: Sleeps when idle with virtually zero CPU and battery impact.
- 🛡️ **Safe & Contained**: Designed exclusively to toggle the user-facing AEB switch without modifying system firmware or vehicle files.

---

## 📋 Requirements

| Requirement | Details |
| :--- | :--- |
| **System** | BYD DiLink 5.0 (Android 12) |
| **Tested On** | BYD Song L EV (DiLink 5.0) |
| **Condition** | **ADB Enabled** on DiLink |

---

## 🚀 Quick Start

### 1. Download & Install
Download the latest versioned APK from the [**Latest Release**](https://github.com/EngMohamedKhamis/BYD-AEB-Manager/releases/latest) page (e.g. `AEB_Manager_v2.0.apk`) and install it on your DiLink screen.

### 2. Verify ADB
Ensure **ADB** is enabled on your DiLink head unit (via Developer Options or engineering menu).

### 3. First Launch
1. Open **AEB Manager**.
2. Follow the on-screen prompts to grant **Accessibility** and **Display over other apps** (Overlay) permissions.
3. Select your desired AEB toggle preference.
4. The background service is now active and will manage the setting automatically on every drive.

---

## 🔍 How It Works

- **Self-Generating Local Key**: On first launch, the app creates a secure RSA authentication key stored exclusively on your device to communicate with DiLink's local ADB interface.
- **Boot & Sleep Detection**: The app monitors vehicle ignition/boot events, waits until DiLink's internal vehicle services are fully responsive, and toggles the switch once.
- **Fail-Safe Operation**: If the system is busy or a command fails, it retries safely with back-off delays rather than spamming the bus.

---

## 🛠️ Building from Source

To compile the APK yourself using Gradle:

```bash
# Clone the repository
git clone https://github.com/EngMohamedKhamis/BYD-AEB-Manager.git
cd BYD-AEB-Manager

# Build release APK
./gradlew assembleAggressiveRelease
```

The compiled APK will be output to:
`app/build/outputs/apk/aggressiveRelease/app-aggressiveRelease.apk`

---

## ⚠️ Disclaimer

This is an independent open-source hobbyist project and is **not** affiliated with, endorsed by, or associated with BYD Auto Co., Ltd. 

Modifying driver assistance settings alters vehicle behavior. The driver remains solely responsible for operating the vehicle safely, observing road conditions, and adhering to local traffic laws at all times. Use at your own discretion.

---

## 📄 License

Distributed under the **MIT License**. See [`LICENSE`](LICENSE) for details.
