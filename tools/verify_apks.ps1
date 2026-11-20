<#
.SYNOPSIS
  Inspects built APK files and proves that Stage 8 (pure DEX, per-install key, hardened markers) is inside them.

.DESCRIPTION
  For every APK it checks:
    1. apksigner: signature verifies, and the signer certificate SHA-256 matches a known project keystore.
    2. Architecture: pure DEX, no embedded native .so libraries, and no hardcoded private keys.
    3. DEX: contains the marker strings of the refactor fixes (H02, M01 fault pause, H07, S8 adb_key.pk8).
    4. Manifest: versionCode / versionName (when expected values are given).
  Prints [PASS] / [FAIL] per check, then OVERALL. Exit code 0 = everything passed, 1 = something failed.
  Prints no key material.

.EXAMPLE
  powershell -NoProfile -ExecutionPolicy Bypass -File tools\verify_apks.ps1 `
     -Apk AEB_Manager_v2.0.apk `
     -ExpectedVersionCode 19000 -ExpectedVersionName 2.0
#>
param(
    [Parameter(Mandatory = $true)][string[]]$Apk,
    [string]$ProjectRoot = '',
    [string]$SdkDir = '',
    [string]$ExpectedVersionCode = '',
    [string]$ExpectedVersionName = ''
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

$scriptDir = if ($PSScriptRoot) { $PSScriptRoot } else { Split-Path -Parent $MyInvocation.MyCommand.Definition }
if (-not $ProjectRoot) {
    $ProjectRoot = Split-Path -Parent $scriptDir
}

# "powershell -File ... -Apk a.apk,b.apk" delivers ONE string "a.apk,b.apk"; split it back into a list.
$Apk = @($Apk | ForEach-Object { $_ -split ',' } | ForEach-Object { $_.Trim() } | Where-Object { $_ })

# ---- tool locations ----
if (-not $SdkDir) {
    $lp = Join-Path $ProjectRoot 'local.properties'
    if (Test-Path $lp) {
        $line = Get-Content $lp | Where-Object { $_ -match '^\s*sdk\.dir\s*=' } | Select-Object -First 1
        if ($line) { $SdkDir = ($line -replace '^\s*sdk\.dir\s*=\s*', '') -replace '\\:', ':' -replace '\\\\', '\' }
    }
    if (-not $SdkDir -and $env:ANDROID_HOME -and (Test-Path $env:ANDROID_HOME)) {
        $SdkDir = $env:ANDROID_HOME
    }
    if (-not $SdkDir -and $env:ANDROID_SDK_ROOT -and (Test-Path $env:ANDROID_SDK_ROOT)) {
        $SdkDir = $env:ANDROID_SDK_ROOT
    }
    if (-not $SdkDir -and (Test-Path "E:\AndroidSDK")) {
        $SdkDir = "E:\AndroidSDK"
    }
    if (-not $SdkDir -and (Test-Path "$env:LOCALAPPDATA\Android\Sdk")) {
        $SdkDir = "$env:LOCALAPPDATA\Android\Sdk"
    }
}
if (-not $SdkDir -or -not (Test-Path $SdkDir)) { throw "Android SDK folder not found (sdk.dir). Pass -SdkDir." }
$bt = Get-ChildItem (Join-Path $SdkDir 'build-tools') -Directory | Sort-Object { [version]($_.Name -replace '[^0-9.]', '') } | Select-Object -Last 1
$apksigner = Join-Path $bt.FullName 'apksigner.bat'
$aapt2 = Join-Path $bt.FullName 'aapt2.exe'
if (-not (Test-Path $apksigner)) { throw "apksigner not found: $apksigner" }
if (-not $env:JAVA_HOME -or -not (Test-Path $env:JAVA_HOME)) {
    $jdk = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'
    if (Test-Path $jdk) { $env:JAVA_HOME = $jdk; $env:Path = "$jdk\bin;" + $env:Path }
}

# Authorized signing certificate fingerprint (SHA-256) for AEB Manager release builds
$KNOWN_SIGNERS = @(
    "39:AC:E4:5E:26:C9:C0:10:FF:56:50:D3:1C:90:4B:6D:E2:9A:B1:8B:5B:AA:1B:D4:21:55:D7:6F:C9:7F:B1:E8"  # community-vault.jks
)

$script:failures = 0
function Check([bool]$ok, [string]$text) {
    if ($ok) { Write-Host ("  [PASS] " + $text) -ForegroundColor Green }
    else { Write-Host ("  [FAIL] " + $text) -ForegroundColor Red; $script:failures++ }
}
function Read-Entry($entry) {
    $ms = New-Object IO.MemoryStream
    $s = $entry.Open(); $s.CopyTo($ms); $s.Close()
    return $ms.ToArray()
}

foreach ($a in $Apk) {
    Write-Host ""
    Write-Host "=== $a"
    if (-not (Test-Path $a)) { Check $false "file exists"; continue }
    $item = Get-Item $a
    Write-Host ("  size {0:N0} bytes, modified {1:yyyy-MM-dd HH:mm}" -f $item.Length, $item.LastWriteTime)

    # 1. signature
    $out = & $apksigner verify --print-certs $item.FullName 2>&1
    $verified = ($LASTEXITCODE -eq 0)
    Check $verified "apksigner verify succeeded"
    $shaLine = $out | Where-Object { $_ -match 'certificate SHA-256 digest' } | Select-Object -First 1
    $signer = ''
    if ($shaLine) { $hexRaw = ($shaLine -replace '.*digest:\s*', '').Trim().ToUpper(); $signer = (($hexRaw -split '(?<=\G..)(?!$)') -join ':') }
    $hasOfficialVault = Test-Path (Join-Path $ProjectRoot 'app/community-vault.jks')
    if ($hasOfficialVault) {
        Check ($signer -ne '' -and ($KNOWN_SIGNERS -contains $signer)) ("signer certificate matches official release key ($signer)")
    } else {
        Check ($signer -ne '') ("signer certificate is valid (open-source / custom build: $signer)")
    }

    # 2. architecture & credentials + 3. dex
    $zip = [IO.Compression.ZipFile]::OpenRead($item.FullName)
    try {
        $libs = @($zip.Entries | Where-Object { $_.FullName -like 'lib/*/*.so' })
        Check ($libs.Count -eq 0) "contains no native libraries (pure DEX, per-install key)"

        $dex = ''
        foreach ($e in @($zip.Entries | Where-Object { $_.FullName -like 'classes*.dex' })) {
            $dex += [Text.Encoding]::UTF8.GetString((Read-Entry $e))
        }
        Check (-not [regex]::IsMatch($dex, 'MII[A-Za-z0-9+/]{40}')) "no embedded Base64 private key in DEX"
        Check (-not $dex.Contains('BEGIN PRIVATE KEY')) "no PEM private key header in DEX"
        Check ($dex.Contains('First write OK, second write failed')) "dex has H02 marker (second-write failure reported)"
        Check ($dex.Contains('ACTOR SAFETY NET in ')) "dex has M01 marker (fault pause handler)"
        Check ($dex.Contains('leaving accessibility settings untouched')) "dex has H07 marker (accessibility list guard)"
        Check ($dex.Contains('ENFORCING_BOOT_EARLY: mode is now ')) "dex has No-Action write-cancel marker"
        Check ($dex.Contains('adb_key.pk8')) "dex has S8 marker (AdbKeyStore per-install key persistence)"
    } finally { $zip.Dispose() }

    # 4. version
    if ((Test-Path $aapt2) -and ($ExpectedVersionCode -or $ExpectedVersionName)) {
        $badging = & $aapt2 dump badging $item.FullName 2>&1 | Where-Object { $_ -like 'package:*' } | Select-Object -First 1
        Write-Host "  $badging"
        if ($ExpectedVersionCode) { Check ($badging -match "versionCode='$ExpectedVersionCode'") "versionCode == $ExpectedVersionCode" }
        if ($ExpectedVersionName) { Check ($badging -match "versionName='$([regex]::Escape($ExpectedVersionName))'") "versionName == $ExpectedVersionName" }
    }
}

Write-Host ""
if ($script:failures -eq 0) { Write-Host "OVERALL: PASS" -ForegroundColor Green; exit 0 }
else { Write-Host ("OVERALL: FAIL ({0} check(s) failed)" -f $script:failures) -ForegroundColor Red; exit 1 }
