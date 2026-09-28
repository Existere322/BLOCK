# BLOCK

[中文](README.md)

<p align="center"><img src="docs/screenshots/icon.jpg" alt="BLOCK" width="220"></p>

BLOCK (时界) is a fully offline Android app. When a restricted app comes to the foreground during a blocked period, or after its daily allowance runs out, BLOCK sends it back to the home screen and keeps it from being used.

The install package does not request the internet permission. There is no account, no advertising, and no cloud sync. Rules, usage time, and emergency releases stay on this phone, and are kept for 30 days by default.

Current version **2.1.3** (versionCode 17). The package is [`dist/时界-release.apk`](dist/时界-release.apk).

```text
SHA-256 ae4d4ac9658432b879533ac66d513dcb04d28559d9aad328721a777fc77f4203
```

This project is published under the [noncommercial license](LICENSE). You may use it for non-commercial purposes. You may not sell it or use it for a business.

## How to use

1. Copy `dist/时界-release.apk` to the phone and install it from a file manager. If the system blocks unknown sources, allow that source app to install unknown apps.
2. Open BLOCK and follow the onboarding pages: data stays on the phone, usage access, accessibility, optional notifications, and keeping the app running in the background.
3. On the last page, check “limits stop working if accessibility is turned off, the app is force-stopped, or it is uninstalled” before the home page opens.
4. In Groups, put the apps you want to slow down into one group, then set blocked periods and a daily allowance.
5. Open a restricted app. During a blocked period, or after that group’s allowance is used up, BLOCK returns to the home screen and shows the block page.

Accessibility reads only the package name of the foreground app when the window changes. It does not read screen text, what you type, or the view tree. Usage access adds up foreground time. It does not read notification contents.

After accessibility is turned off, or the app is force-stopped or uninstalled, limits stop immediately. BLOCK cannot forbid an app from launching at the system level, and it cannot stop you from turning these permissions off.

On ColorOS 16, also allow unrestricted background running, turn on autostart, and lock the app in the recent-tasks list. The step-by-step path is in [docs/安装与ColorOS配置.md](docs/安装与ColorOS配置.md) (Chinese).

### How the allowance is counted

Time comes from foreground events recorded by the system: counting starts when an app comes to the foreground and ends when it leaves. Turning the screen off ends the current span. System apps are excluded. Totals are split at local midnight and written to a database on the phone. The next time you open BLOCK, it only fills in records since the last sync.

Apps in one group share a daily allowance. It resets at local midnight and does not carry over. An app may belong to several groups at once; if any one of those groups is in effect, the app is blocked. Phone, launcher, system UI, Settings, the installer, and BLOCK itself cannot be added to a group.

The checks run in this order: safe app, emergency release, blocked period, daily allowance, allow.

### Emergency release

On the block page, each group can be released once per day. You write a reason, wait 30 seconds, and then that one app is released for 5 minutes. Time during the release still counts toward the allowance. Restarting the phone cancels the release at once.

After a restart, opening BLOCK rebinds the accessibility service if its switch is still on, so blocked periods keep intercepting the apps in those groups. If ColorOS removed the service, turn it on again. An optional one-time `adb shell pm grant app.shijie android.permission.WRITE_SECURE_SETTINGS` lets BLOCK put that entry back by itself.

### Lock

In Settings, existing groups can be locked until midnight tomorrow, for 24 hours, or for 7 days. While the lock lasts, you cannot change those groups’ schedules, allowances, or app lists, and you cannot turn them off or delete them. Names and icons can still be changed, and new groups can still be created. There is no unlock button in the app before the lock ends.

## Pages

The bottom bar has four entries. From left to right: Today, Groups, Stats, and Settings.

![Ocean: Today, Groups, Stats, Settings](docs/screenshots/ocean.jpg)

Ocean

![Coffee: Today, Groups, Stats, Settings](docs/screenshots/coffee.jpg)

Coffee

### Today

The home page. The top shows the protection status and today’s total foreground time. Below that, each group shows time used, allowance remaining, and today’s blocked periods. If a permission is missing, this page says whether accessibility or usage access is still off.

### Groups

The group list, plus creating and editing a group. Each group can set a name, color, icon, enabled state, an effective date, a schedule of every day / statutory workdays / custom weekdays, several blocked periods, and a daily allowance in minutes. A period may cross midnight. Overlapping periods are merged. The editor shows how much system foreground time the group has already used today.

![Edit group](docs/screenshots/edit.jpg)

### Stats

Today is a 24-hour bar chart and an app ranking. Last 7 days is a daily total and a ranking. Durations are shown in minutes. System apps do not appear here.

### Settings

- **Appearance**: Ocean (blue) and Coffee (coffee-shop palette: 01 `#C67C4E`, 02 `#EDD6C8`, 03 `#313131`, 04 `#E3E3E3`, 05 `#F9F2ED`).
- **Lock**: freeze the rules of groups that already exist.
- **Permission check**: accessibility, usage access, notifications, and the latest foreground event.
- **ColorOS / background guide**: opens the matching system settings page.
- **Statutory workday corrections**: mainland China holidays and makeup workdays for 2025 and 2026 are built in, and any day can be marked as a workday or a rest day. Years that are not covered are treated as Monday through Friday.
- **Privacy note**, plus clearing every group and record and returning to the first-run guide.

### First-run guide

Five steps after install: fully offline, usage access, accessibility, optional notifications, and staying alive in the background. The four pages above stay closed until this is finished.

### Block page

Shown when a restricted app comes to the foreground. It names the group and the reason (blocked period or allowance used up). You can dismiss it and return home. When the conditions are met, you can also request that group’s one emergency release for the day. The page follows the current Ocean or Coffee theme.

## Build

The running app does not use the network. Dependencies are downloaded only when you compile on a computer.

```text
powershell -ExecutionPolicy Bypass -File .\scripts\bootstrap.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\build-release.ps1
```

`scripts/bootstrap.ps1` prepares JDK 17 and Android SDK 36 under `work/`. `minSdk` is 29. `compileSdk` and `targetSdk` are 36. The signing key lives in `keystore/` on this machine; the key and passwords in that directory are not committed. Later upgrades must use the same key, or the system will refuse to install over the existing app.

## License

Copyright (c) 2026 Existere

This repository is licensed for non-commercial use. You may use, copy, and modify it for non-commercial purposes if you keep the copyright notice and the full license text. You may not sell it, include it in a paid product, or use it for a business. The full text is in [LICENSE](LICENSE). The software is provided as is, without warranty.
