param(
    [string]$Root = "",
    [int]$DebounceMs = 1500,
    [string]$WslDistro = "Ubuntu-24.04"
)

$ErrorActionPreference = "Continue"
if (-not $Root) {
    $Root = Split-Path -Parent $PSScriptRoot
}

function ConvertTo-WslPath([string]$WinPath) {
    $full = [IO.Path]::GetFullPath($WinPath)
    if ($full -match "^([A-Za-z]):\\(.*)$") {
        return "/mnt/" + $Matches[1].ToLowerInvariant() + "/" + ($Matches[2] -replace "\\", "/")
    }
    return $full
}

$watchScriptUnix = ConvertTo-WslPath (Join-Path $PSScriptRoot "wsl-install-debug.sh")
$queue = New-Object "System.Collections.Concurrent.ConcurrentQueue[string]"

function Should-Watch([string]$Path) {
    if (-not $Path) { return $false }
    if ($Path -match "[\\/](build|\.gradle|\.idea|\.cxx|\.kotlin)([\\/]|$)") { return $false }
    $ext = [IO.Path]::GetExtension($Path).ToLowerInvariant()
    return $ext -in @(".kt", ".xml", ".png", ".webp", ".jpg", ".jpeg", ".gif")
}

function Invoke-Reload {
    $started = Get-Date
    Write-Host ""
    Write-Host ("[{0}] Reloading Echo onto the emulator..." -f $started.ToString("HH:mm:ss"))
    wsl -d $WslDistro -- bash -c "sed -i 's/\r`$//' '$watchScriptUnix' && bash '$watchScriptUnix'"
    $code = $LASTEXITCODE
    if ($code -ne 0) {
        Write-Host ("[{0}] Reload failed (exit {1}). Save again to retry." -f (Get-Date).ToString("HH:mm:ss"), $code) -ForegroundColor Red
        return
    }
    $sec = [int]((Get-Date) - $started).TotalSeconds
    Write-Host ("[{0}] Echo updated ({1}s)." -f (Get-Date).ToString("HH:mm:ss"), $sec) -ForegroundColor Green
}

$watchers = @()
$subs = @()
$watchDirs = New-Object System.Collections.Generic.List[string]
$appSrc = Join-Path $Root "app\src"
if (Test-Path $appSrc) { $watchDirs.Add($appSrc) }
foreach ($top in @("feature", "core")) {
    $base = Join-Path $Root $top
    if (-not (Test-Path $base)) { continue }
    Get-ChildItem -Path $base -Directory -Recurse -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -eq "src" } |
        ForEach-Object { $watchDirs.Add($_.FullName) }
}

$handler = {
    $q = $Event.MessageData
    $q.Enqueue($Event.SourceEventArgs.FullPath)
}

foreach ($dir in $watchDirs) {
    $w = New-Object System.IO.FileSystemWatcher
    $w.Path = $dir
    $w.IncludeSubdirectories = $true
    $w.Filter = "*.*"
    $w.NotifyFilter = [IO.NotifyFilters]::FileName -bor [IO.NotifyFilters]::LastWrite
    $w.EnableRaisingEvents = $true
    $subs += Register-ObjectEvent -InputObject $w -EventName Changed -MessageData $queue -Action $handler
    $subs += Register-ObjectEvent -InputObject $w -EventName Created -MessageData $queue -Action $handler
    $subs += Register-ObjectEvent -InputObject $w -EventName Renamed -MessageData $queue -Action $handler
    $watchers += $w
}

Write-Host "ECHO hot reload is watching source files."
Write-Host "Save Kotlin / layout / resources in Cursor to update the emulator."
Write-Host "Echo will relaunch (playback position is not kept). Ctrl+C to stop."
Write-Host ""

$pending = $false
$due = Get-Date
try {
    while ($true) {
        $item = [string]$null
        while ($queue.TryDequeue([ref]$item)) {
            if (Should-Watch $item) {
                $pending = $true
                $due = (Get-Date).AddMilliseconds($DebounceMs)
            }
        }
        if ($pending -and ((Get-Date) -ge $due)) {
            $pending = $false
            $drain = [string]$null
            while ($queue.TryDequeue([ref]$drain)) { }
            Invoke-Reload
        }
        Start-Sleep -Milliseconds 200
    }
} finally {
    foreach ($s in $subs) {
        Unregister-Event -SourceIdentifier $s.Name -Force -ErrorAction SilentlyContinue
    }
    foreach ($w in $watchers) {
        $w.EnableRaisingEvents = $false
        $w.Dispose()
    }
}
