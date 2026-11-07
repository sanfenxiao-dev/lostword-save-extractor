$ErrorActionPreference='Stop'
[Console]::OutputEncoding=New-Object System.Text.UTF8Encoding($false)
$OutputEncoding=[Console]::OutputEncoding
$log=Join-Path $PSScriptRoot 'extraction-log.txt'
$started=$false
try {
    $null=Start-Transcript -LiteralPath $log -Force
    $started=$true
    Write-Host 'Keep your LostWord game open at Home. Starting extraction...'
    & (Join-Path $PSScriptRoot 'Export-OriginalSave.ps1')
    Write-Host 'SUCCESS. Your JSON is in this folder. Check data before importing into an empty slot.'
} catch {
    Write-Host "FAILED: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host 'Send extraction-log.txt to the developer. No additional commands needed.'
} finally {
    if ($started) {$null=Stop-Transcript}
    Write-Host "Log: $log"
}
