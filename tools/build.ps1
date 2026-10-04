param([switch]$HostOnly,[switch]$FabricOnly)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
if (!(Test-Path -LiteralPath $root)) { throw 'Bridge root missing' }
if (!(Test-Path -LiteralPath "$root\goldsrc\build")) { New-Item -ItemType Directory -Path "$root\goldsrc\build" | Out-Null }
if (!$FabricOnly) {
    & python "$root\tools\generate-player-bridge.py"
    if($LASTEXITCODE) { throw 'Player bridge generation failed' }
    $vswhere="${env:ProgramFiles(x86)}\Microsoft Visual Studio\Installer\vswhere.exe"
    $vs=& $vswhere -latest -products '*' -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
    if(!$vs) { throw 'MSVC Build Tools missing' }
    & "$vs\MSBuild\Current\Bin\MSBuild.exe" "$root\goldsrc\sdk\projects\vs2019\hldll.vcxproj" /m /p:Configuration=Release /p:Platform=Win32 /p:PlatformToolset=v143 /p:WindowsTargetPlatformVersion=10.0 /p:PostBuildEventUseInBuild=false "/p:ForceImportBeforeCppTargets=$root\goldsrc\bridge.props" "/p:OutDir=$root\goldsrc\build\" "/p:IntDir=$root\goldsrc\build\obj\" /verbosity:quiet /clp:ErrorsOnly
    if($LASTEXITCODE) { throw 'GoldSrc host build failed' }
    & "$vs\MSBuild\Current\Bin\MSBuild.exe" "$root\goldsrc\sdk\projects\vs2019\hl_cdll.vcxproj" /m /p:Configuration=Release /p:Platform=Win32 /p:PlatformToolset=v143 /p:WindowsTargetPlatformVersion=10.0 /p:PostBuildEventUseInBuild=false "/p:ForceImportBeforeCppTargets=$root\goldsrc\client.props" "/p:OutDir=$root\goldsrc\build\" "/p:IntDir=$root\goldsrc\build\client-obj\" /verbosity:quiet /clp:ErrorsOnly
    if($LASTEXITCODE) { throw 'GoldSrc client build failed' }
    & cmd /c "`"$vs\VC\Auxiliary\Build\vcvarsall.bat`" x86 >nul && cl /nologo /EHsc /std:c++17 `"$root\goldsrc\layout_check.cpp`" /Fe:`"$root\goldsrc\build\layout_check.exe`" /Fo:`"$root\goldsrc\build\layout_check.obj`" && `"$root\goldsrc\build\layout_check.exe`""
    if($LASTEXITCODE) { throw 'C++ layout check failed' }
}
if (!$HostOnly) {
    if(!$env:JAVA_HOME) { $env:JAVA_HOME='C:\Users\sogut\jdk\jdk-25.0.1+8' }
    Push-Location "$root\fabric"
    try { & .\gradlew.bat clean build -Phalfcraft --console=plain; if($LASTEXITCODE) { throw 'Fabric build failed' } }
    finally { Pop-Location }
    & "$env:JAVA_HOME\bin\java.exe" --enable-native-access=ALL-UNNAMED -cp "$root\fabric\build\classes\java\client" dev.halfcraft.HalfCraftLink
    if($LASTEXITCODE) { throw 'Java layout check failed' }
    & "$env:JAVA_HOME\bin\java.exe" -cp "$root\fabric\build\classes\java\main" dev.halfcraft.HalfCraftLoot
    if($LASTEXITCODE) { throw 'Campaign loot checks failed' }
}
& python "$root\tools\test_protocol.py"
if($LASTEXITCODE) { throw 'Protocol checks failed' }
& python "$root\tools\test_collision.py"
if($LASTEXITCODE) { throw 'Collision checks failed' }
& python "$root\tools\test_client_protocol.py"
if($LASTEXITCODE) { throw 'Client protocol checks failed' }
'HalfCraft Bridge build passed'
