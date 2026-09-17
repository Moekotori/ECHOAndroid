# Sit the Android emulator on the bottom-right. Never block on a frozen Qt window.
param(
    [string]$AvdName = "ECHO_API_36",
    [int]$Margin = 24,
    [int]$TimeoutSec = 12,
    [switch]$IniOnly
)

$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Windows.Forms
Add-Type @"
using System;
using System.Runtime.InteropServices;
public struct WinRect { public int Left; public int Top; public int Right; public int Bottom; }
public class EchoWin {
    public const uint SWP_NOZORDER = 0x0004;
    public const uint SWP_NOACTIVATE = 0x0010;
    public const uint SWP_ASYNCWINDOWPOS = 0x4000;
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hWnd, out WinRect lpRect);
    [DllImport("user32.dll")] public static extern bool SetWindowPos(IntPtr hWnd, IntPtr hWndInsertAfter, int X, int Y, int cx, int cy, uint uFlags);
    [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr hWnd);
    [DllImport("user32.dll")] public static extern bool IsWindow(IntPtr hWnd);
}
"@

function Get-LcdSize([string]$AvdName) {
    $width = 1080
    $height = 2400
    $config = Join-Path $env:USERPROFILE ".android\avd\$AvdName.avd\config.ini"
    if (Test-Path $config) {
        foreach ($line in @(Get-Content $config)) {
            if ($line -match '^\s*hw\.lcd\.width\s*=\s*(\d+)') { $width = [int]$Matches[1] }
            if ($line -match '^\s*hw\.lcd\.height\s*=\s*(\d+)') { $height = [int]$Matches[1] }
        }
    }
    [pscustomobject]@{ Width = $width; Height = $height }
}

function Get-TargetSlot([string]$AvdName, [int]$Margin) {
    $work = [System.Windows.Forms.Screen]::PrimaryScreen.WorkingArea
    $lcd = Get-LcdSize $AvdName
    $maxHeight = [Math]::Max(400, [Math]::Min($work.Height - (2 * $Margin), [int]($work.Height * 0.72)))
    $scale = [Math]::Round(($maxHeight - 80) / [double]$lcd.Height, 2)
    if ($scale -lt 0.18) { $scale = 0.18 }
    if ($scale -gt 0.40) { $scale = 0.40 }
    $estWidth = [int]($lcd.Width * $scale) + 72
    $estHeight = [int]($lcd.Height * $scale) + 80
    $x = $work.Right - $estWidth - $Margin
    $y = $work.Bottom - $estHeight - $Margin
    if ($x -lt $work.Left) { $x = $work.Left }
    if ($y -lt $work.Top) { $y = $work.Top }
    [pscustomobject]@{
        X = $x
        Y = $y
        Scale = $scale
        Width = $estWidth
        Height = $estHeight
    }
}

function Update-EmulatorIni([string]$AvdName, [int]$X, [int]$Y, [double]$Scale) {
    $avdDir = Join-Path $env:USERPROFILE ".android\avd\$AvdName.avd"
    if (-not (Test-Path $avdDir)) { return }
    $ini = Join-Path $avdDir "emulator-user.ini"
    try {
        $lines = New-Object System.Collections.Generic.List[string]
        $haveX = $false
        $haveY = $false
        $haveScale = $false
        if (Test-Path $ini) {
            foreach ($line in @(Get-Content $ini -ErrorAction Stop)) {
                if ($line -match '^\s*window\.x\s*=') {
                    $lines.Add("window.x = $X"); $haveX = $true
                } elseif ($line -match '^\s*window\.y\s*=') {
                    $lines.Add("window.y = $Y"); $haveY = $true
                } elseif ($line -match '^\s*window\.scale\s*=') {
                    $lines.Add(("window.scale = {0:0.00}" -f $Scale)); $haveScale = $true
                } else {
                    $lines.Add($line)
                }
            }
        }
        if (-not $haveX) { $lines.Add("window.x = $X") }
        if (-not $haveY) { $lines.Add("window.y = $Y") }
        if (-not $haveScale) { $lines.Add(("window.scale = {0:0.00}" -f $Scale)) }
        $lines | Set-Content -Path $ini -Encoding ASCII
    } catch {
        Write-Host "Could not update emulator-user.ini."
    }
}

function Get-EmulatorHwnd {
    $procs = @(Get-Process -Name "qemu-system-x86_64" -ErrorAction SilentlyContinue)
    foreach ($p in $procs) {
        if (-not $p.Responding) { continue }
        $hwnd = $p.MainWindowHandle
        if ($hwnd -eq [IntPtr]::Zero) { continue }
        if (-not [EchoWin]::IsWindow($hwnd)) { continue }
        if (-not [EchoWin]::IsWindowVisible($hwnd)) { continue }
        return @{ Hwnd = $hwnd; Process = $p }
    }
    return $null
}

$slot = Get-TargetSlot -AvdName $AvdName -Margin $Margin
Update-EmulatorIni -AvdName $AvdName -X $slot.X -Y $slot.Y -Scale $slot.Scale
Write-Host ("Saved bottom-right slot ({0},{1}) scale {2}." -f $slot.X, $slot.Y, $slot.Scale)
if ($IniOnly) { exit 0 }

$frozen = @(Get-Process -Name "qemu-system-x86_64" -ErrorAction SilentlyContinue | Where-Object { -not $_.Responding })
if ($frozen.Count -gt 0 -and -not (Get-EmulatorHwnd)) {
    Write-Host "Emulator window is not responding; left it alone. Next launch will use the saved slot."
    exit 0
}

$deadline = (Get-Date).AddSeconds($TimeoutSec)
$found = $null
$rect = New-Object WinRect
while ((Get-Date) -lt $deadline) {
    $found = Get-EmulatorHwnd
    if ($found) {
        [void][EchoWin]::GetWindowRect($found.Hwnd, [ref]$rect)
        $width = $rect.Right - $rect.Left
        $height = $rect.Bottom - $rect.Top
        if ($width -ge 200 -and $height -ge 200) { break }
        $found = $null
    }
    Start-Sleep -Milliseconds 300
}
if (-not $found) {
    Write-Host "Emulator window not ready; skip live move."
    exit 0
}

$width = $rect.Right - $rect.Left
$height = $rect.Bottom - $rect.Top
$work = [System.Windows.Forms.Screen]::PrimaryScreen.WorkingArea
$maxWidth = [Math]::Max(240, $work.Width - (2 * $Margin))
$maxHeight = [Math]::Max(400, [Math]::Min($work.Height - (2 * $Margin), [int]($work.Height * 0.72)))
$newWidth = $width
$newHeight = $height
if ($newWidth -gt $maxWidth -or $newHeight -gt $maxHeight) {
    $fit = [Math]::Min($maxWidth / [double]$newWidth, $maxHeight / [double]$newHeight)
    $newWidth = [Math]::Max(240, [int]($newWidth * $fit))
    $newHeight = [Math]::Max(400, [int]($newHeight * $fit))
}
$x = $work.Right - $newWidth - $Margin
$y = $work.Bottom - $newHeight - $Margin
if ($x -lt $work.Left) { $x = $work.Left }
if ($y -lt $work.Top) { $y = $work.Top }

$flags = [EchoWin]::SWP_NOZORDER -bor [EchoWin]::SWP_NOACTIVATE -bor [EchoWin]::SWP_ASYNCWINDOWPOS
[void][EchoWin]::SetWindowPos($found.Hwnd, [IntPtr]::Zero, $x, $y, $newWidth, $newHeight, $flags)
Update-EmulatorIni -AvdName $AvdName -X $x -Y $y -Scale $slot.Scale
Write-Host ("Emulator window moved to bottom-right ({0},{1}) size {2}x{3}." -f $x, $y, $newWidth, $newHeight)
