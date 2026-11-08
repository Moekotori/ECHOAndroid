param(
    [string]$Version,
    [ValidateRange(0, 99)][int]$Revision = 0
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$timer = [Diagnostics.Stopwatch]::StartNew()
Push-Location $root
try {
    $signingFile = Join-Path $root 'keystore.properties'
    if (!(Test-Path -LiteralPath $signingFile)) {
        throw 'Local release signing is missing: configure keystore.properties with the original release key.'
    }
    $signingText = [IO.File]::ReadAllText($signingFile)
    foreach ($name in @('storeFile', 'storePassword', 'keyAlias', 'keyPassword')) {
        if ($signingText -notmatch "(?m)^$name\s*=\s*\S+") {
            throw "Missing $name in local keystore.properties."
        }
    }
    $signingText = $null

    if (!$Version) {
        $date = [DateTimeOffset]::UtcNow.ToOffset([TimeSpan]::FromHours(8))
        $Version = '{0}.{1}.{2}' -f ($date.Year % 100), $date.Month, $date.Day
    }
    if ($Version -notmatch '^\d{2}\.[1-9]\d?\.[1-9]\d?$') {
        throw 'Version must use YY.M.D, for example 26.11.8.'
    }
    $parts = @($Version.Split('.') | ForEach-Object { [int]$_ })
    $null = [DateTime]::new(2000 + $parts[0], $parts[1], $parts[2])
    $versionBase = ($parts[0] * 10000 + $parts[1] * 100 + $parts[2]) * 100
    $output = Join-Path $root 'app/build/outputs/apk/release'
    $metadataFile = Join-Path $output 'output-metadata.json'
    $previousCodes = @($versionBase)
    if (Test-Path -LiteralPath $metadataFile) {
        $previous = Get-Content -LiteralPath $metadataFile -Raw | ConvertFrom-Json
        $previousCodes += @($previous.elements | ForEach-Object { [int]$_.versionCode })
    }
    $updateFile = Join-Path $output 'update.json'
    if (Test-Path -LiteralPath $updateFile) {
        $previousCodes += [int](Get-Content -LiteralPath $updateFile -Raw | ConvertFrom-Json).versionCode
    }
    $latestCode = ($previousCodes | Where-Object { $_ -ge $versionBase -and $_ -lt $versionBase + 100 } |
        Measure-Object -Maximum).Maximum
    if ($Revision -eq 0) { $Revision = [int]$latestCode - $versionBase + 1 }
    if ($Revision -gt 99) { throw 'All same-day revisions are used; choose a later version date.' }
    $versionCode = $versionBase + $Revision
    if ($versionCode -le $latestCode) { throw 'Revision must be greater than the existing local release revision.' }

    $sdkCandidates = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)
    $localProperties = Join-Path $root 'local.properties'
    if (Test-Path -LiteralPath $localProperties) {
        $sdkLine = Get-Content -LiteralPath $localProperties | Where-Object { $_ -match '^sdk.dir=' } | Select-Object -First 1
        if ($sdkLine) { $sdkCandidates += $sdkLine.Substring(8).Replace('\:', ':').Replace('\\', '\') }
    }
    if ($env:LOCALAPPDATA) { $sdkCandidates += Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
    $sdk = $sdkCandidates | Where-Object { $_ -and (Test-Path -LiteralPath (Join-Path $_ 'build-tools')) } |
        Select-Object -First 1
    if (!$sdk) { throw 'Android SDK build-tools not found; configure local.properties or ANDROID_HOME.' }
    $buildTools = Get-ChildItem -LiteralPath (Join-Path $sdk 'build-tools') -Directory |
        Where-Object { $_.Name -match '^\d+\.\d+\.\d+$' } |
        Sort-Object { [Version]$_.Name } -Descending | Select-Object -First 1
    if (!$buildTools) { throw 'No stable Android SDK build-tools installed.' }
    $apksigner = Join-Path $buildTools.FullName 'apksigner.bat'
    if (!(Test-Path -LiteralPath $apksigner)) { throw 'apksigner.bat not found in Android SDK build-tools.' }

    Write-Host "Building local signed Release $Version ($versionCode)..."
    & (Join-Path $root 'gradlew.bat') :app:assembleRelease "-PechoVersionName=$Version" "-PechoVersionCode=$versionCode" --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Release build failed (exit $LASTEXITCODE)." }

    $metadata = Get-Content -LiteralPath $metadataFile -Raw | ConvertFrom-Json
    $elements = @($metadata.elements)
    if ($elements.Count -ne 1 -or $elements[0].versionCode -ne $versionCode -or $elements[0].versionName -ne $Version) {
        throw 'Release output metadata does not match the requested version.'
    }
    $apk = Join-Path $output $elements[0].outputFile
    $verification = & $apksigner verify --print-certs $apk 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
    $certificate = 'a55e71620fd78ef38b556d675f0f8ac081185b5a00d5e91caf7b8b2126369b42'
    if (($verification -join "`n") -notmatch "certificate SHA-256 digest: $certificate") {
        throw 'APK was not signed with the original ECHO Android release certificate.'
    }
    $timer.Stop()
    Write-Host "Signed Release APK: $apk"
    Write-Host ('Size: {0:N1} MB; build and verification: {1:N1}s' -f ((Get-Item -LiteralPath $apk).Length / 1MB), $timer.Elapsed.TotalSeconds)
} finally {
    Pop-Location
}
