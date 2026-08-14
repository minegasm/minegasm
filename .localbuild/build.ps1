# Local compile+test harness for the pure-Java core (no Gradle/Minecraft needed).
# The real project builds with Gradle+Stonecraft; this script only verifies the
# loader/Minecraft-independent core so it can be developed and tested in isolation.
param([switch]$Test)

# Native tools (javac/java) write to stderr for warnings; with -ErrorActionPreference Stop
# that would abort the script, so we use Continue and check $LASTEXITCODE explicitly.
$ErrorActionPreference = "Continue"
$jdk = "C:\Program Files\Microsoft\jdk-25.0.3.9-hotspot"
$bin = "$jdk\bin"
$proj = Split-Path $PSScriptRoot -Parent
$build = "$proj\.localbuild"
$out = "$build\out"
$gson = "$build\libs\gson-2.11.0.jar"
$junit = "$build\libs\junit-platform-console-standalone-1.11.4.jar"

Remove-Item "$out" -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path "$out\classes","$out\test-classes" | Out-Null

# Engine sources live under engine/ (one source of truth, shared with the Java 8 Classic build). The
# b4j provider is compiled separately below (it needs buttplug4j on the classpath); neoforge/mc are the
# modern Minecraft layer and stay under the root src/, never reached from here.
$mainSrc = Get-ChildItem "$proj\engine\src\main\java\net\minegasm" -Recurse -Filter *.java |
    Where-Object { $_.FullName -notmatch '\\neoforge\\' -and $_.FullName -notmatch '\\mc\\' -and $_.FullName -notmatch '\\b4j\\' } |
    Select-Object -ExpandProperty FullName
[System.IO.File]::WriteAllLines("$out\main-sources.txt", $mainSrc)

& "$bin\javac.exe" --release 8 -Xlint:all,-options -d "$out\classes" -cp $gson "@$out\main-sources.txt" 2> "$out\javac-main.log"
$mainExit = $LASTEXITCODE
Get-Content "$out\javac-main.log" -ErrorAction SilentlyContinue | Where-Object { $_ -match "error:|warning:.*minegasm" }
if ($mainExit -ne 0) { Write-Output "MAIN COMPILE FAILED"; exit 1 }
Write-Output "MAIN COMPILE OK ($($mainSrc.Count) files)"

# Optional: verify the buttplug4j-backed provider against the real library, if its jars are present.
# (net.minegasm.buttplug.b4j needs buttplug4j/Jetty/Jackson on the classpath.) ProviderFactory and
# WebSocketTransport are the loader-side JDK-WebSocket glue (java.net.http, Java 11+) and are compiled
# by the Gradle build, not here, so this Java 8 slice stays free of them.
$b4jLibs = "$build\libs\b4j"
$b4jBuilt = $false
if (Test-Path $b4jLibs) {
    $b4jCp = ((Get-ChildItem $b4jLibs -Filter *.jar | ForEach-Object { $_.FullName }) -join ';')
    $b4jSrc = @()
    $b4jSrc += Get-ChildItem "$proj\engine\src\main\java\net\minegasm\buttplug\b4j" -Filter *.java | Select-Object -ExpandProperty FullName
    [System.IO.File]::WriteAllLines("$out\b4j-sources.txt", $b4jSrc)
    New-Item -ItemType Directory -Force -Path "$out\b4j-classes" | Out-Null
    & "$bin\javac.exe" --release 8 -d "$out\b4j-classes" -cp "$out\classes;$gson;$b4jCp" "@$out\b4j-sources.txt" 2> "$out\javac-b4j.log"
    if ($LASTEXITCODE -ne 0) { Get-Content "$out\javac-b4j.log"; Write-Output "B4J COMPILE FAILED"; exit 1 }
    Write-Output "B4J COMPILE OK ($($b4jSrc.Count) files vs buttplug4j 4.0.278)"
    $b4jBuilt = $true
}

if (-not $Test) { exit 0 }

$testRoot = "$proj\engine\src\test\java"
if (-not (Test-Path $testRoot)) { Write-Output "no tests yet"; exit 0 }
$testSrc = Get-ChildItem $testRoot -Recurse -Filter *.java | Select-Object -ExpandProperty FullName
# The b4j provider classes are compiled separately (against buttplug4j). Their tests use the neutral
# facade seam, so they need the b4j classes but not the library. When the b4j step did not run (no library
# jars present), skip those tests rather than fail the whole test compile.
$b4jCpSuffix = ""
if ($b4jBuilt) {
    $b4jCpSuffix = ";$out\b4j-classes"
} else {
    $testSrc = $testSrc | Where-Object { $_ -notmatch '\\b4j\\' }
}
if ($testSrc.Count -eq 0) { Write-Output "no tests yet"; exit 0 }
[System.IO.File]::WriteAllLines("$out\test-sources.txt", $testSrc)

$cp = "$out\classes;$gson;$junit$b4jCpSuffix"
& "$bin\javac.exe" -d "$out\test-classes" -cp $cp "@$out\test-sources.txt" 2> "$out\javac-test.log"
$testExit = $LASTEXITCODE
Get-Content "$out\javac-test.log" -ErrorAction SilentlyContinue | Where-Object { $_ -match "error:" }
if ($testExit -ne 0) { Write-Output "TEST COMPILE FAILED"; exit 1 }
Write-Output "TEST COMPILE OK ($($testSrc.Count) files)"

& "$bin\java.exe" -jar $junit execute `
    --class-path "$out\classes;$out\test-classes;$gson$b4jCpSuffix" `
    --scan-classpath="$out\test-classes" `
    --details=tree --disable-banner 2> "$out\junit.log"
$runExit = $LASTEXITCODE
Get-Content "$out\junit.log" -ErrorAction SilentlyContinue
exit $runExit
