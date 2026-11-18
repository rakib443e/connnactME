# Android Build & Test Guide

## Prerequisites
- Java 21 (`Temurin-21`)
- Gradle 9.x (installed at `/opt/gradle/gradle-9.3.1/bin/gradle`)
- Android SDK (`ANDROID_SDK_ROOT` configured; `compileSdk 36`, `minSdk 24`, `targetSdk 36`)
- Package Name: `com.connectme.messaging`

## Commands
Run unit & Robolectric integration tests:
```bash
gradle :app:testDebugUnitTest
```

Build debug APK:
```bash
gradle :app:assembleDebug
```
Output path: `app/build/outputs/apk/debug/app-debug.apk`

Build release APK:
```bash
gradle :app:assembleRelease
```
Output path: `app/build/outputs/apk/release/app-release.apk`
