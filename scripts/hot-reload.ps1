$ErrorActionPreference = "Continue"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
try { chcp 65001 | Out-Null } catch {}
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
Write-Host "Starting emulator and Echo, then watching files..."
cmd /c "`"$PSScriptRoot\run-emulator.cmd`""
$code = $LASTEXITCODE
if ($code -ne 0) {
  Write-Host "Emulator or app failed to start. Hot reload not started."
  exit $code
}
Write-Host ""
Write-Host "Hot reload is on. Save code to update the emulator."
Write-Host ""
& "$PSScriptRoot\watch-hot-reload.ps1"
exit $LASTEXITCODE
