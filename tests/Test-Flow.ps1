$ErrorActionPreference='Stop'
$temp=Join-Path ([IO.Path]::GetTempPath()) ('lostword-flow-'+[guid]::NewGuid())
$null=New-Item -ItemType Directory $temp
$savedMode=$env:LOSTWORD_TEST_MODE
$savedState=$env:LOSTWORD_TEST_STATE
try {
    $env:LOSTWORD_TEST_STATE=Join-Path $temp 'connected'
    $fake=Join-Path $temp 'adb.exe'
    Add-Type -OutputAssembly $fake -OutputType ConsoleApplication -TypeDefinition @'
using System;
using System.IO;
public class FakeAdb {
  public static int Main(string[] input) {
    string mode=Environment.GetEnvironmentVariable("LOSTWORD_TEST_MODE");
    string state=Environment.GetEnvironmentVariable("LOSTWORD_TEST_STATE");
    string cmd=String.Join(" ",input);
    if(cmd=="start-server")return 0;
    if(cmd.StartsWith("connect ")) {File.WriteAllText(state,"yes");return 0;}
    if(cmd=="devices") {
      Console.WriteLine("List of devices attached");
      if(mode!="none"&&(mode!="discovery"||File.Exists(state)))Console.WriteLine("emulator-5554\tdevice");
      if(mode=="multiple")Console.WriteLine("emulator-5556\tdevice");
      return 0;
    }
    if(cmd.Contains(" install "))return 0;
    if(cmd.EndsWith("shell id")){Console.WriteLine("uid=0(root)");return 0;}
    if(cmd.Contains("pm path")){Console.WriteLine("package:/data/app/test/base.apk");return 0;}
    if(cmd.Contains("ReaderMain")){if(mode=="experimental"&&!cmd.EndsWith("com.gg.lostword.mi --allow-unverified-library"))return 2; if(mode=="success"&&cmd.Contains("--allow-unverified-library"))return 2; return mode=="reader-fail"?1:0;}
    if(cmd.Contains("capture-status.txt")&&cmd.Contains(" cat ")){Console.WriteLine("Unsupported core hash: test");return 0;}
    if(cmd.Contains(" pull ")){File.WriteAllText(input[input.Length-1],"{}");return 0;}
    return 0;
  }
}
'@
    $script=Join-Path $temp 'Export-OriginalSave.ps1'
    Copy-Item (Join-Path (Split-Path $PSScriptRoot) 'Export-OriginalSave.ps1') $script
    Set-Content (Join-Path $temp 'lostword-save-extractor-0.2.1.apk') 'mock'
    function Get-Process { [pscustomobject]@{ProcessName='MuMu';Id=4242;Path=$fake} }
    function Get-NetTCPConnection { [pscustomobject]@{OwningProcess=4242;LocalAddress='127.0.0.1';LocalPort=16384} }
    foreach ($mode in @('success','discovery','multiple','none','reader-fail','explicit','experimental')) {
        $env:LOSTWORD_TEST_MODE=$mode
        if (Test-Path $env:LOSTWORD_TEST_STATE) {Remove-Item -LiteralPath $env:LOSTWORD_TEST_STATE}
        $failure=''
        try {
            if ($mode -eq 'experimental') {& $script -AdbPath $fake -GamePackage 'com.gg.lostword.mi' -AllowUnverifiedLibrary -OutputDirectory $temp | Out-Null}
            elseif ($mode -eq 'explicit') {& $script -AdbPath $fake -Serial ' emulator-5554 ' -GamePackage 'com.gg.lostword.mi' -OutputDirectory $temp | Out-Null}
            else {& $script -AdbPath $fake -OutputDirectory $temp | Out-Null}
        } catch {$failure=$_.Exception.Message}
        $expected=@{multiple='Multiple devices';none='No online device';'reader-fail'='Unsupported core hash'}
        if ($expected.ContainsKey($mode)) {
            if ($failure -notmatch $expected[$mode]) {throw "$mode did not produce expected error: $failure"}
        } elseif ($failure) {throw "$mode failed: $failure"}
        if ($mode -eq 'discovery' -and -not (Test-Path $env:LOSTWORD_TEST_STATE)) {throw 'Discovery never attempted connection'}
        Write-Host "PASS flow: $mode"
    }
} finally {
    $env:LOSTWORD_TEST_MODE=$savedMode
    $env:LOSTWORD_TEST_STATE=$savedState
    Remove-Item -LiteralPath $temp -Recurse -Force
}
