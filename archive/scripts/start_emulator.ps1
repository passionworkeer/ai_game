$env:ANDROID_SDK_ROOT = "D:\android-sdk"
$env:ANDROID_HOME = "D:\android-sdk"
$proc = Start-Process -FilePath "D:\android-sdk\emulator\emulator.exe" -ArgumentList "-avd","aiyougame_test","-no-snapshot-load","-no-audio","-no-window" -PassThru -WindowStyle Hidden
Write-Host "Started emulator, PID: $($proc.Id)"
Start-Sleep -Seconds 15
& "D:\android-sdk\platform-tools\adb.exe" devices
