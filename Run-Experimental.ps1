$ErrorActionPreference='Stop'
[Console]::OutputEncoding=New-Object System.Text.UTF8Encoding($false)
$OutputEncoding=[Console]::OutputEncoding
$log=Join-Path $PSScriptRoot 'extraction-log.txt'
$transcribing=$false
try {
    $null=Start-Transcript -LiteralPath $log -Force
    $transcribing=$true
    Write-Host 'Experimental extraction. Keep your LostWord game open at Home.'
    & (Join-Path $PSScriptRoot 'Export-OriginalSave.ps1') -AllowUnverifiedLibrary
    Write-Host 'SUCCESS. Your JSON is in this folder. Check data before importing into an empty slot.'
} catch {
    Write-Host "FAILED: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host 'Send extraction-log.txt to the developer. No additional commands needed.'
} finally {
    if ($transcribing) {$null=Stop-Transcript}
    Write-Host "Log: $log"
}
