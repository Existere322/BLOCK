# Prepare JDK 17, Android SDK 36, and the Gradle wrapper inside work/.
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
if (-not (Test-Path (Join-Path $root "settings.gradle.kts"))) {
    $root = "C:\Users\zhangshuai\Projects\shijie"
}
$work = Join-Path $root "work"
$dl = Join-Path $work "downloads"
New-Item -ItemType Directory -Force -Path $dl | Out-Null

function Get-File([string]$url, [string]$dest) {
    Write-Host "GET $url"
    & curl.exe -L --fail --retry 3 --retry-delay 2 -o $dest $url
    if ($LASTEXITCODE -ne 0) { throw "download failed: $url" }
}

$java = Join-Path $work "jdk-17\bin\java.exe"
if (-not (Test-Path $java)) {
    $jdkZip = Join-Path $dl "jdk17.zip"
    Get-File "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk" $jdkZip
    $extract = Join-Path $dl "jdk-extract"
    if (Test-Path $extract) { Remove-Item -Recurse -Force $extract }
    New-Item -ItemType Directory -Force -Path $extract | Out-Null
    tar -xf $jdkZip -C $extract
    $folder = Get-ChildItem $extract -Directory | Select-Object -First 1
    if (Test-Path (Join-Path $work "jdk-17")) { Remove-Item -Recurse -Force (Join-Path $work "jdk-17") }
    Move-Item $folder.FullName (Join-Path $work "jdk-17")
}

$env:JAVA_HOME = Join-Path $work "jdk-17"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
& $java -version

$sdk = Join-Path $work "android-sdk"
$sdkManager = Join-Path $sdk "cmdline-tools\latest\bin\sdkmanager.bat"
if (-not (Test-Path $sdkManager)) {
    $cmdZip = Join-Path $dl "cmdline-tools.zip"
    $candidates = @(
        "https://dl.google.com/android/repository/commandlinetools-win-13114758_latest.zip",
        "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip",
        "https://dl.google.com/android/repository/commandlinetools-win-9477386_latest.zip"
    )
    $downloaded = $false
    foreach ($candidate in $candidates) {
        & curl.exe -L --fail --retry 2 --retry-delay 2 -o $cmdZip $candidate
        if ($LASTEXITCODE -eq 0 -and (Test-Path $cmdZip) -and ((Get-Item $cmdZip).Length -gt 1000000)) {
            $downloaded = $true
            break
        }
    }
    if (-not $downloaded) { throw "could not download Android cmdline-tools" }
    $cmdExtract = Join-Path $dl "cmdline-extract"
    if (Test-Path $cmdExtract) { Remove-Item -Recurse -Force $cmdExtract }
    New-Item -ItemType Directory -Force -Path $cmdExtract | Out-Null
    tar -xf $cmdZip -C $cmdExtract
    $latest = Join-Path $sdk "cmdline-tools\latest"
    New-Item -ItemType Directory -Force -Path (Split-Path $latest) | Out-Null
    if (Test-Path $latest) { Remove-Item -Recurse -Force $latest }
    $unpacked = Join-Path $cmdExtract "cmdline-tools"
    if (-not (Test-Path $unpacked)) { throw "unexpected cmdline-tools archive layout" }
    Move-Item $unpacked $latest
}

$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
$yesFile = Join-Path $dl "yes.txt"
1..200 | ForEach-Object { "y" } | Set-Content $yesFile -Encoding ascii
Get-Content $yesFile | & $sdkManager --sdk_root=$sdk --licenses
& $sdkManager --sdk_root=$sdk "platforms;android-36" "build-tools;36.0.0" "platform-tools"
if ($LASTEXITCODE -ne 0) { throw "sdkmanager failed: $LASTEXITCODE" }

$sdkDir = ($sdk -replace "\\", "/")
Set-Content -Path (Join-Path $root "local.properties") -Value "sdk.dir=$sdkDir" -Encoding ascii

$gradleBat = Join-Path $work "gradle-8.11.1\bin\gradle.bat"
if (-not (Test-Path (Join-Path $root "gradlew.bat"))) {
    if (-not (Test-Path $gradleBat)) {
        $gradleZip = Join-Path $dl "gradle-8.11.1-bin.zip"
        Get-File "https://services.gradle.org/distributions/gradle-8.11.1-bin.zip" $gradleZip
        tar -xf $gradleZip -C $work
    }
    Push-Location $root
    & $gradleBat wrapper --gradle-version 8.11.1
    if ($LASTEXITCODE -ne 0) { Pop-Location; throw "gradle wrapper failed" }
    Pop-Location
}

Write-Host "toolchain ready"
