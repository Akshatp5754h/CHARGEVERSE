# Machine-specific build environment for CHARGEVERSE on this laptop.
# Usage: . .\build-env.ps1   (then use .\gradlew <task>)
$env:GRADLE_USER_HOME = "G:\AI_STORAGE\.gradle"
$env:JAVA_HOME = "D:\ANDROID_STUDIO\jbr"
$env:ANDROID_HOME = "C:\Users\Akshat\AppData\Local\Android\Sdk"
if (-not $env:Path.Contains($env:JAVA_HOME)) {
    $env:Path = "$env:JAVA_HOME\bin;" + $env:Path
}
Write-Host "Build env ready: GRADLE_USER_HOME=$env:GRADLE_USER_HOME JAVA_HOME=$env:JAVA_HOME"
