@echo off
set ANDROID_SDK_ROOT=D:\android-sdk
set ANDROID_HOME=D:\android-sdk
cd /d D:\android-sdk
start /b emulator\emulator.exe -avd aiyougame_test -no-snapshot-load -no-audio -no-window
