# Create the release keystore when missing, run unit tests, and assemble the signed APK.
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
if (-not (Test-Path (Join-Path $root "settings.gradle.kts"))) {
    $root = "C:\Users\zhangshuai\Projects\shijie"
}
& (Join-Path $root "scripts\bootstrap.ps1")

$env:JAVA_HOME = Join-Path $root "work\jdk-17"
$env:ANDROID_HOME = Join-Path $root "work\android-sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;" + $env:Path

$keystoreDir = Join-Path $root "keystore"
New-Item -ItemType Directory -Force -Path $keystoreDir | Out-Null
$props = Join-Path $keystoreDir "keystore.properties"
$jks = Join-Path $keystoreDir "shijie-release.jks"
if (-not (Test-Path $jks)) {
    $alphabet = (48..57) + (65..90) + (97..122)
    $password = -join ($alphabet | Get-Random -Count 24 | ForEach-Object { [char]$_ })
    & "$env:JAVA_HOME\bin\keytool.exe" -genkeypair -keystore $jks -storepass $password -keypass $password -alias shijie -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Shijie, OU=Personal, O=Existere, C=CN"
    if ($LASTEXITCODE -ne 0) { throw "keytool failed" }
    @(
        "storeFile=keystore/shijie-release.jks",
        "storePassword=$password",
        "keyAlias=shijie",
        "keyPassword=$password"
    ) | Set-Content -Path $props -Encoding ascii
    @(
        "Release signing key for Shijie. Keep this file for upgrades.",
        "file=$jks",
        "alias=shijie",
        "password=$password"
    ) | Set-Content -Path (Join-Path $keystoreDir "credentials.local.txt") -Encoding ascii
}

Push-Location $root
& .\gradlew.bat :app:testDebugUnitTest :app:assembleRelease
$code = $LASTEXITCODE
Pop-Location
if ($code -ne 0) { throw "Gradle build failed: $code" }

$apk = Join-Path $root "app\build\outputs\apk\release\app-release.apk"
if (-not (Test-Path $apk)) { throw "release apk missing" }
$dist = Join-Path $root "dist"
New-Item -ItemType Directory -Force -Path $dist | Out-Null
$name = "$([char]0x65F6)$([char]0x754C)-release.apk"
$out = Join-Path $dist $name
Copy-Item $apk $out -Force
$hash = (Get-FileHash $out -Algorithm SHA256).Hash.ToLower()
Set-Content -Path (Join-Path $dist "$name.sha256") -Value "$hash  $name" -Encoding utf8

$buildTools = Get-ChildItem (Join-Path $env:ANDROID_HOME "build-tools") -Directory | Sort-Object Name -Descending | Select-Object -First 1
$aapt = Join-Path $buildTools.FullName "aapt.exe"
$apksigner = Join-Path $buildTools.FullName "apksigner.bat"
Write-Host "----- aapt permissions -----"
& $aapt dump permissions $out
Write-Host "----- apksigner -----"
& $apksigner verify --verbose --print-certs $out
if ($LASTEXITCODE -ne 0) { throw "apksigner verify failed" }
Write-Host "SHA-256 $hash"
Write-Host "APK $out"
