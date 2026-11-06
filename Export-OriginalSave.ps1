param([string]$AdbPath,[string]$Serial,[string]$OutputDirectory=$PSScriptRoot)
$ErrorActionPreference='Stop'
if (-not $AdbPath) {
    $found=Get-Command adb -ErrorAction SilentlyContinue
    if ($found) {$AdbPath=$found.Source}
    elseif (Test-Path 'C:\Program Files\Netease\MuMu\nx_main\adb.exe') {$AdbPath='C:\Program Files\Netease\MuMu\nx_main\adb.exe'}
    else {throw 'ADB not found. Pass -AdbPath with the path to adb.exe.'}
}
if (-not $Serial) {
    $devices=@(& $AdbPath devices | Select-String '^([^\s]+)\s+device$' | ForEach-Object {$_.Matches[0].Groups[1].Value})
    if ($devices.Count -ne 1) {throw 'Connect exactly one device, or pass -Serial explicitly.'}
    $Serial=$devices[0]
}
function Invoke-Adb([string[]]$Arguments) {
    # Windows PowerShell 5.1 turns native stderr progress into ErrorRecords.
    # ADB reports successful pull/install progress there; judge the exit code.
    $previousPreference=$ErrorActionPreference
    try {
        $ErrorActionPreference='Continue'
        $result=& $AdbPath -s $Serial @Arguments 2>&1
        $nativeExitCode=$LASTEXITCODE
    } finally {$ErrorActionPreference=$previousPreference}
    if ($nativeExitCode -ne 0) {throw ($result -join "`n")}
    return ($result -join "`n")
}
$apk=Join-Path $PSScriptRoot 'lostword-save-extractor-0.1.0.apk'
if (-not (Test-Path -LiteralPath $apk)) {throw 'Place this script next to the extractor APK.'}
$null=Invoke-Adb @('install','--no-incremental','-r',$apk)
$identity=Invoke-Adb @('shell','id')
if ($identity -notmatch 'uid=0') {
    & $AdbPath -s $Serial root | Out-Null
    Start-Sleep -Seconds 2
    if ($Serial -match '^127\.0\.0\.1:\d+$') {& $AdbPath connect $Serial | Out-Null}
    $identity=Invoke-Adb @('shell','id')
    if ($identity -notmatch 'uid=0') {throw 'ADB root is unavailable. Use the APK on a device with an authorized su root manager.'}
}
$original=Invoke-Adb @('shell','pidof','com.gg.lostword.bilibili')
if ($original.Trim() -notmatch '^\d+$') {throw 'Open original Bilibili LostWord, log in to Home, then run this script again.'}
$packagePath=(Invoke-Adb @('shell','pm','path','local.lostword.saveextractor')).Trim() -replace '^package:',''
if ($packagePath -notmatch '^/[A-Za-z0-9_./=+~-]+\.apk$') {throw 'Unexpected installed APK path.'}
$remote='/data/local/tmp/lostword-save-extractor'
$null=Invoke-Adb @('shell',"mkdir -p $remote")
$destination="$remote/original-save.json"
$null=Invoke-Adb @('shell',"rm -f $destination")
$command="CLASSPATH=$packagePath /system/bin/app_process /system/bin local.lostword.saveextractor.ReaderMain $packagePath $destination"
try {$null=Invoke-Adb @('shell',$command)}
catch {
    $detail=& $AdbPath -s $Serial shell cat "$remote/capture-status.txt" 2>$null
    throw "Extraction did not complete: $detail"
}
$null=New-Item -ItemType Directory -Force -Path $OutputDirectory
$target=Join-Path $OutputDirectory ('lostword-original-'+(Get-Date -Format 'yyyyMMdd-HHmmss')+'.json')
$null=Invoke-Adb @('pull',$destination,$target)
$null=Invoke-Adb @('shell',"rm -f $destination")
Write-Host "Saved: $target"
Write-Host 'Import this JSON into an empty save slot on your private account page.'
