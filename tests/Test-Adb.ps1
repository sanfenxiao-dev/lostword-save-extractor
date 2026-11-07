$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot
$tokens=$null; $errors=$null
$ast=[System.Management.Automation.Language.Parser]::ParseFile((Join-Path $root 'Export-OriginalSave.ps1'),[ref]$tokens,[ref]$errors)
if ($errors.Count) {throw ($errors -join "`n")}
foreach ($name in @('Invoke-AdbRaw','Invoke-Adb','Get-AdbDevices')) {
    $node=$ast.Find({param($n) $n -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -eq $name},$true)
    Invoke-Expression $node.Extent.Text
}
function Require($condition,$message) {if (-not $condition) {throw $message}}
$temp=Join-Path ([IO.Path]::GetTempPath()) ('lostword-adb-test-'+[guid]::NewGuid())
$null=New-Item -ItemType Directory $temp
try {
    $AdbPath=Join-Path $temp 'fake-adb.ps1'
    @'
$global:LASTEXITCODE=0
if ($args[0] -eq 'devices') {"List of devices attached`nemulator-5554`tdevice`n127.0.0.1:16384`toffline"; return}
if ($args -contains 'fail') {$global:LASTEXITCODE=1; return}
if ($args -contains 'progress') {Write-Error 'progress'; 'Success'; return}
$args -join '|'
'@ | Set-Content -LiteralPath $AdbPath
    $devices=@(Get-AdbDevices)
    Require ($devices.Count -eq 1 -and $devices[0] -eq 'emulator-5554') 'Device parsing failed'
    $Serial='emulator-5554'
    Require ((Invoke-Adb @('shell','id')) -eq '-s|emulator-5554|shell|id') 'Argument forwarding failed'
    $rejected=$false
    try {$null=Invoke-Adb @('shell','fail')} catch {$rejected=$_.Exception.Message -match 'exit 1.*adb -s emulator-5554 shell fail'}
    Require $rejected 'Empty native failures must include command and exit code'
    $null=Invoke-AdbRaw @('fail') -AllowFailure
    Require ((Invoke-Adb @('progress')) -match 'Success') 'Successful stderr progress must not fail'
    Write-Host 'PASS: syntax, device parsing, argument forwarding, empty errors, allowed failures, stderr progress'
} finally {Remove-Item -LiteralPath $temp -Recurse -Force}
