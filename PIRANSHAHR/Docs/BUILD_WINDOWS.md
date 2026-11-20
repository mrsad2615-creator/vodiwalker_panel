# PIRANSHAHR — Windows Build Guide (UE5.4)

> This project was authored autonomously in a Linux container (§14 honesty: compile is *Simulated Static Validation* until this guide is executed on Windows).

## Prerequisites

- Windows 10/11, 16GB RAM, SSD
- Unreal Engine 5.4.4 (Epic Launcher → Library)
- Visual Studio 2022 (MSVC v143, Windows SDK 10.0.22621)
- Android Studio Ladybug+ (SDK 34, NDK 25.1.8937393, CMake 3.22)
- Java 17

## Steps

```powershell
# 1. Clone
git clone https://github.com/mrsad2615-creator/vodiwalker_panel.git
cd vodiwalker_panel/PIRANSHAHR

# 2. Generate solution
"C:\Program Files\Epic Games\UE_5.4\Engine\Binaries\DotNET\UnrealBuildTool\UnrealBuildTool.exe" Piranshahr Development Win64 -project="Piranshahr.uproject" -WaitMutex -FromMsBuild

# 3. Open .sln in VS2022, build Development Editor → should succeed with no errors

# 4. Android packaging (Vulkan ASTC OBB)
# Edit → Project Settings → Platforms → Android (SDK/NDK paths)
# File → Package Project → Build Configuration: Shipping → Android (ASTC)
# Output: Binaries/Android/Piranshahr-Android-Shipping.apk + .obb

# 5. Install on POCO X3 Pro (USB debugging)
adb install Piranshahr-Android-Shipping.apk
adb push main.1.com.PiranshahrStudio.Piranshahr.obb /sdcard/Android/obb/com.PiranshahrStudio.Piranshahr/
```

## Validation Checklist

- [ ] `Piranshahr_Center.umap` opens, World Partition shows 16 cells, HLOD valid
- [ ] Peykan vehicle drives, Chaos debug wheels visible
- [ ] Persian HUD renders RTL correctly (Vazirmatn)
- [ ] 30 FPS on POCO X3 Pro (stat fps)
- [ ] Save/load round-trip
- [ ] Offline launch (airplane mode)

## CI

GitHub Actions runner `windows-2022` + UE5.4 installed on self-hosted (future).
