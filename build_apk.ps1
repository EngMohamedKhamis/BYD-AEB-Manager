$ErrorActionPreference = 'Stop'

$scriptDir = if ($PSScriptRoot) { $PSScriptRoot } else { Split-Path -Parent $MyInvocation.MyCommand.Definition }
$sdkDir = ''
if ($env:ANDROID_HOME -and (Test-Path $env:ANDROID_HOME)) {
    $sdkDir = $env:ANDROID_HOME
} elseif ($env:ANDROID_SDK_ROOT -and (Test-Path $env:ANDROID_SDK_ROOT)) {
    $sdkDir = $env:ANDROID_SDK_ROOT
} else {
    $lp = Join-Path $scriptDir 'local.properties'
    if (Test-Path $lp) {
        $line = Get-Content $lp | Where-Object { $_ -match '^\s*sdk\.dir\s*=' } | Select-Object -First 1
        if ($line) {
            $candidate = (($line -replace '^\s*sdk\.dir\s*=\s*', '') -replace '\\:', ':' -replace '\\\\', '\').Trim()
            if ($candidate -and (Test-Path $candidate)) { $sdkDir = $candidate }
        }
    }
    if (-not $sdkDir -and (Test-Path "E:\AndroidSDK")) {
        $sdkDir = "E:\AndroidSDK"
    } elseif (-not $sdkDir -and (Test-Path "$env:LOCALAPPDATA\Android\Sdk")) {
        $sdkDir = "$env:LOCALAPPDATA\Android\Sdk"
    } elseif (-not $sdkDir -and (Test-Path "$scriptDir\..\..\+Tools\AndroidSDK")) {
        $sdkDir = (Resolve-Path "$scriptDir\..\..\+Tools\AndroidSDK").Path
    }
}
if (-not $sdkDir) {
    throw "Android SDK directory not found. Set ANDROID_HOME or sdk.dir in local.properties."
}

if (-not $env:JAVA_HOME -or !(Test-Path $env:JAVA_HOME)) {
    $jdkPath = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
    if (Test-Path $jdkPath) {
        $env:JAVA_HOME = $jdkPath
        $env:Path = "$jdkPath\bin;" + $env:Path
    }
}

$env:ANDROID_HOME = $sdkDir
$env:ANDROID_SDK_ROOT = $sdkDir

$hasPlatform31 = Test-Path (Join-Path $sdkDir "platforms\android-31")
$hasBuildTools31 = Test-Path (Join-Path $sdkDir "build-tools\31.0.0")

if (-not ($hasPlatform31 -and $hasBuildTools31)) {
    $sdkmanager = "$sdkDir\cmdline-tools\latest\bin\sdkmanager.bat"
    if (!(Test-Path $sdkmanager)) {
        $cmdLineToolsZip = (Resolve-Path "$scriptDir\..\..\+Tools").Path + "\cmdline-tools.zip"
        if (!(Test-Path $cmdLineToolsZip)) {
            Write-Host "Downloading Android command line tools..."
            curl.exe -L -o $cmdLineToolsZip "https://dl.google.com/android/repository/commandlinetools-win-10406996_latest.zip"
        }
        Write-Host "Extracting command line tools..."
        Expand-Archive -Path $cmdLineToolsZip -DestinationPath "$sdkDir\cmdline-tools" -Force
        Rename-Item -Path "$sdkDir\cmdline-tools\cmdline-tools" -NewName "latest" -ErrorAction SilentlyContinue
    }

    Write-Host "Accepting Android SDK licenses..."
    $yes = ("y`n" * 10)
    $yes | & $sdkmanager --licenses

    Write-Host "Downloading SDK platform 31 and build-tools..."
    $yes | & $sdkmanager "platforms;android-31" "build-tools;31.0.0"
    if ($LASTEXITCODE -ne 0) {
        throw "sdkmanager failed with exit code $LASTEXITCODE"
    }
} else {
    Write-Host "SDK platform android-31 and build-tools 31.0.0 already present; skipping sdkmanager."
}

Push-Location $scriptDir
try {
    Write-Host "Building AEB Manager (aggressiveRelease)..."
    cmd.exe /c "gradlew.bat assembleAggressiveRelease"
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle build failed with exit code $LASTEXITCODE"
    }

    $builtApk = "app\build\outputs\apk\aggressiveRelease\app-aggressiveRelease.apk"
    if (!(Test-Path -LiteralPath $builtApk)) {
        throw "Expected APK not found after build: $builtApk"
    }

    Write-Host "Exporting AEB Manager APK..."
    Copy-Item -LiteralPath $builtApk -Destination "AEB_Manager_v2.0.apk" -Force
    Copy-Item -LiteralPath $builtApk -Destination "AEB_Manager.apk" -Force

    Write-Host "Done! AEB_Manager.apk & AEB_Manager_v2.0.apk exported successfully." -ForegroundColor Green
} finally {
    Pop-Location
}
