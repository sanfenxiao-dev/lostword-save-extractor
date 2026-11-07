param([string]$AdbPath,[string]$Serial,[string]$GamePackage,[string]$OutputDirectory=$PSScriptRoot)
$ErrorActionPreference='Stop'
$Serial=$Serial.Trim()
$GamePackage=$GamePackage.Trim()
if ($GamePackage -and $GamePackage -notmatch '^[A-Za-z0-9_]+(\.[A-Za-z0-9_]+)+$') {throw 'Invalid game package.'}
if (-not $AdbPath) {
    $candidates=@()
    $found=Get-Command adb -ErrorAction SilentlyContinue
    if ($found) {$candidates+=$found.Source}
    $emulators=@(Get-Process -ErrorAction SilentlyContinue | Where-Object {$_.ProcessName -match 'MuMu|Nemu|dnplayer|ldplayer|LdVBox|HD-Player|Nox|MEmu'})
    foreach ($process in $emulators) {
        if ($process.Path) {
            $dir=Split-Path $process.Path
            foreach ($relative in @('adb.exe','shell\adb.exe','adb\adb.exe')) {$candidates+=Join-Path $dir $relative}
            $candidates+=Join-Path (Split-Path $dir) 'adb.exe'
        }
    }
    foreach ($drive in @(Get-PSDrive -PSProvider FileSystem)) {
        foreach ($relative in @('Program Files\Netease\MuMu\nx_main\adb.exe','Program Files (x86)\Netease\MuMu\nx_main\adb.exe','leidian\LDPlayer9\adb.exe','LDPlayer\LDPlayer9\adb.exe')) {$candidates+=Join-Path $drive.Root $relative}
    }
    $AdbPath=$candidates | Where-Object {Test-Path -LiteralPath $_ -PathType Leaf} | Select-Object -First 1
    if (-not $AdbPath) {throw 'ADB not found. Pass -AdbPath with your emulator adb.exe path.'}
}
if (-not (Test-Path -LiteralPath $AdbPath -PathType Leaf)) {throw "ADB not found: $AdbPath"}
function Invoke-AdbRaw([string[]]$Arguments,[switch]$AllowFailure) {
    $previousPreference=$ErrorActionPreference
    try {
        $ErrorActionPreference='Continue'
        $result=& $AdbPath @Arguments 2>&1
        $nativeExitCode=$LASTEXITCODE
    } finally {$ErrorActionPreference=$previousPreference}
    $message=($result | ForEach-Object {"$_"}) -join "`n"
    if ($nativeExitCode -ne 0 -and -not $AllowFailure) {throw "ADB failed (exit $nativeExitCode): adb $($Arguments -join ' ')`n$message"}
    return $message
}
function Invoke-Adb([string[]]$Arguments) {Invoke-AdbRaw (@('-s',$Serial)+$Arguments)}
function Get-AdbDevices {
    $listing=Invoke-AdbRaw @('devices')
    @($listing -split '\r?\n' | Where-Object {$_ -match '^([^\s]+)\s+device\s*$'} | ForEach-Object {($_ -split '\s+')[0]})
}
function Connect-LocalAdb([string]$Endpoint) {
    # Non-ADB emulator listeners may accept TCP without answering the ADB handshake.
    # Keep each discovery attempt bounded and always dispose the helper process.
    $start=New-Object System.Diagnostics.ProcessStartInfo
    $start.FileName=$AdbPath
    $start.Arguments="connect $Endpoint"
    $start.UseShellExecute=$false
    $start.CreateNoWindow=$true
    $start.RedirectStandardOutput=$true
    $start.RedirectStandardError=$true
    $child=New-Object System.Diagnostics.Process
    $child.StartInfo=$start
    try {
        $null=$child.Start()
        if (-not $child.WaitForExit(4000)) {$child.Kill(); $child.WaitForExit()}
    } finally {$child.Dispose()}
}
$null=Invoke-AdbRaw @('start-server')
if ($Serial -match '^127\.0\.0\.1:\d+$') {Connect-LocalAdb $Serial}
$devices=@(Get-AdbDevices)
if (-not $Serial -and $devices.Count -eq 0) {
    # Discover loopback listeners belonging to emulator processes; do not scan a port range or LAN.
    $owners=@(Get-Process -ErrorAction SilentlyContinue | Where-Object {$_.ProcessName -match 'MuMu|Nemu|dnplayer|ldplayer|LdVBox|HD-Player|Nox|MEmu'} | ForEach-Object {$_.Id})
    $listeners=@(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object {$_.OwningProcess -in $owners -and $_.LocalAddress -in @('127.0.0.1','0.0.0.0','::1','::')})
    foreach ($port in @($listeners.LocalPort | Sort-Object -Unique)) {
        Connect-LocalAdb "127.0.0.1:$port"
    }
    $devices=@(Get-AdbDevices)
}
if (-not $Serial) {
    if ($devices.Count -eq 0) {throw 'No online device. Start your emulator and enable ADB debugging; alternatively pass -Serial 127.0.0.1:PORT.'}
    if ($devices.Count -gt 1) {throw "Multiple devices online. Pass -Serial using one of: $($devices -join ', ')"}
    $Serial=$devices[0]
}
if ($Serial -notin $devices) {throw "Device '$Serial' is not online. Online devices: $($devices -join ', ')"}
Write-Host "ADB: $AdbPath | Device: $Serial"
$apk=Join-Path $PSScriptRoot 'lostword-save-extractor-0.2.0.apk'
if (-not (Test-Path -LiteralPath $apk)) {throw 'Place this script next to lostword-save-extractor-0.2.0.apk (use the complete updated tool bundle).'}
$null=Invoke-Adb @('install','-r',$apk)
$identity=Invoke-Adb @('shell','id')
if ($identity -notmatch 'uid=0\b') {
    $null=Invoke-AdbRaw @('-s',$Serial,'root')
    Start-Sleep -Seconds 2
    if ($Serial -match '^127\.0\.0\.1:\d+$') {$null=Invoke-AdbRaw @('connect',$Serial)}
    $null=Invoke-Adb @('wait-for-device')
    $identity=Invoke-Adb @('shell','id')
    if ($identity -notmatch 'uid=0\b') {throw 'ADB root is unavailable. Enable emulator root or use the APK with an authorized su root manager.'}
}
$packagePath=(Invoke-Adb @('shell','pm','path','local.lostword.saveextractor')).Trim() -replace '^package:',''
if ($packagePath -notmatch '^/[A-Za-z0-9_./=+~-]+\.apk$') {throw 'Unexpected installed APK path.'}
$remote='/data/local/tmp/lostword-save-extractor'
$null=Invoke-Adb @('shell',"mkdir -p $remote")
$destination="$remote/original-save.json"
$null=Invoke-Adb @('shell',"rm -f $destination $remote/capture-status.txt")
$command="CLASSPATH=$packagePath /system/bin/app_process /system/bin local.lostword.saveextractor.ReaderMain $packagePath $destination"
if ($GamePackage) {$command+=" $GamePackage"}
try {$null=Invoke-Adb @('shell',$command)}
catch {
    $originalError=$_.Exception.Message
    $detail=Invoke-AdbRaw @('-s',$Serial,'shell','cat',"$remote/capture-status.txt") -AllowFailure
    throw "Extraction did not complete: $detail`n$originalError"
}
$null=New-Item -ItemType Directory -Force -Path $OutputDirectory
$target=Join-Path $OutputDirectory ('lostword-original-'+(Get-Date -Format 'yyyyMMdd-HHmmss')+'.json')
$null=Invoke-Adb @('pull',$destination,$target)
$null=Invoke-Adb @('shell',"rm -f $destination")
Write-Host "Saved: $target"
Write-Host 'Import this JSON into an empty save slot on your private account page.'
