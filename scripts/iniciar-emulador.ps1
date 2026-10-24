param(
    [ValidatePattern('^[A-Za-z0-9_.-]+$')]
    [string]$Nome = 'Pixel_5'
)

# Fechar primeiro este dispositivo no Device Manager do Android Studio.
# O arranque preserva os dados e usa DNS explícito para evitar "unknown host".
$taskSdkRoot = if ($env:ANDROID_HOME) { $env:ANDROID_HOME }
    elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT }
    else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$taskEmulator = Join-Path $taskSdkRoot 'emulator\emulator.exe'
if (-not (Test-Path -LiteralPath $taskEmulator)) {
    throw 'SDK Android não encontrado. Define ANDROID_HOME para a pasta do SDK.'
}
& $taskEmulator -avd $Nome -dns-server '8.8.8.8,8.8.4.4' -no-snapshot-load
