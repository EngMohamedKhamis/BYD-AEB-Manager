# Compiles the :core sources and runs the self-test with plain javac/java (no Gradle, no Android SDK, no network).
# Used to prove that a core folder is sound before (or independently of) the Gradle wiring.
#   powershell -NoProfile -ExecutionPolicy Bypass -File core_check.ps1 -CoreDir <...\core> -AdbHelper <...\AdbHelper.java>
# Exit code 0 only if both compilations succeed (javac -Xlint:all -Werror, Java 8 API level) and the self-test passes.
param(
    # defaults suit the copy installed in the project (tools\core_check.ps1); the plan passes explicit paths when run from staging
    [string]$CoreDir = (Join-Path (Split-Path -Parent $PSScriptRoot) 'core'),
    [string]$AdbHelper = (Join-Path (Split-Path -Parent $PSScriptRoot) 'app\src\main\java\com\byd\aebtoggle\AdbHelper.java'),
    [string]$Jdk = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'
)

$javac = Join-Path $Jdk 'bin\javac.exe'
$java  = Join-Path $Jdk 'bin\java.exe'
foreach ($p in $javac, $java, (Join-Path $CoreDir 'src\main\java'), (Join-Path $CoreDir 'src\test\java'), $AdbHelper) {
    if (-not (Test-Path -LiteralPath $p)) { Write-Host "[STOP] not found: $p" -ForegroundColor Red; exit 2 }
}

$tmp = Join-Path ([IO.Path]::GetTempPath()) ('core_check_' + [Guid]::NewGuid().ToString('N'))
$outMain = Join-Path $tmp 'main'; $outTest = Join-Path $tmp 'test'
New-Item -ItemType Directory -Force $outMain, $outTest | Out-Null
try {
    $main = Get-ChildItem (Join-Path $CoreDir 'src\main\java') -Recurse -Filter *.java | ForEach-Object { $_.FullName }
    $test = Get-ChildItem (Join-Path $CoreDir 'src\test\java') -Recurse -Filter *.java | ForEach-Object { $_.FullName }

    & $javac --release 8 -Xlint:all -Werror -encoding UTF-8 -d $outMain $main
    "javac main exit: $LASTEXITCODE"
    if ($LASTEXITCODE -ne 0) { exit 1 }
    & $javac --release 8 -Xlint:all -Werror -encoding UTF-8 -cp $outMain -d $outTest $test
    "javac test exit: $LASTEXITCODE"
    if ($LASTEXITCODE -ne 0) { exit 1 }

    # the self-test reads ../app/... relative paths only through this property
    Push-Location $CoreDir
    try {
        & $java "-Dadbhelper.path=$AdbHelper" -cp "$outMain;$outTest" com.byd.aebtoggle.core.RunAll
        $code = $LASTEXITCODE
    } finally { Pop-Location }
    "selftest exit: $code"
    exit $code
} finally {
    Remove-Item $tmp -Recurse -Force -ErrorAction SilentlyContinue
}
