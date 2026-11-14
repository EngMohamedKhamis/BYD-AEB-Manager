$ErrorActionPreference = 'Stop'

$scriptDir = $PSScriptRoot
if ($env:ANDROID_HOME -and (Test-Path $env:ANDROID_HOME)) {
    $sdkDir = $env:ANDROID_HOME
} elseif (Test-Path "E:\AndroidSDK") {
    $sdkDir = "E:\AndroidSDK"
} elseif (Test-Path "$env:LOCALAPPDATA\Android\Sdk") {
    $sdkDir = "$env:LOCALAPPDATA\Android\Sdk"
}

if ($sdkDir) {
    $env:ANDROID_HOME = $sdkDir
    $env:ANDROID_SDK_ROOT = $sdkDir
}

if (-not $env:JAVA_HOME -or !(Test-Path $env:JAVA_HOME)) {
    $jdkPath = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
    if (Test-Path $jdkPath) {
        $env:JAVA_HOME = $jdkPath
        $env:Path = "$jdkPath\bin;" + $env:Path
    }
}

Write-Host "Building AEB Manager v2.0 (aggressiveRelease)..."
Set-Location $scriptDir
cmd.exe /c "gradlew.bat assembleAggressiveRelease"

if (Test-Path "app\build\outputs\apk\aggressiveRelease\app-aggressiveRelease.apk") {
    Write-Host "Exporting AEB Manager APK..."
    Copy-Item "app\build\outputs\apk\aggressiveRelease\app-aggressiveRelease.apk" "AEB_Manager_v2.0.apk" -Force
    Copy-Item "app\build\outputs\apk\aggressiveRelease\app-aggressiveRelease.apk" "AEB_Manager.apk" -Force
    Write-Host "Done! AEB_Manager.apk exported successfully." -ForegroundColor Green
}
