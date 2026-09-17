$ErrorActionPreference = "Continue"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
try { chcp 65001 | Out-Null } catch {}
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
Write-Host "Starting emulator and Echo..."
$argLine = $args -join " "
cmd /c "`"$PSScriptRoot\run-emulator.cmd`" $argLine"
$code = $LASTEXITCODE
Write-Host ""
if ($code -eq 0) {
  Write-Host "Echo is running. You can close this window."
} else {
  Write-Host ("Start failed (exit code {0})." -f $code)
}
exit $code
