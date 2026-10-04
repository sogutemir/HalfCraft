param([string]$Game='K:\SteamLibrary\steamapps\common\Half-Life')
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
if (!(Test-Path -LiteralPath "$Game\hl.exe")) { throw 'Half-Life runtime missing' }
if (Get-Process hl -ErrorAction SilentlyContinue) { throw 'Close Half-Life before deployment' }
$target=Join-Path $Game 'halfcraft_bridge'
foreach($directory in @($target,"$target\dlls","$target\cl_dlls")) {
    if (!(Test-Path -LiteralPath (Split-Path $directory -Parent))) { throw "Parent missing: $directory" }
    if (!(Test-Path -LiteralPath $directory)) { New-Item -ItemType Directory -Path $directory | Out-Null }
}
Copy-Item -LiteralPath "$root\goldsrc\build\hl.dll" -Destination "$target\dlls\hl.dll"
Copy-Item -LiteralPath "$root\goldsrc\build\client.dll" -Destination "$target\cl_dlls\client.dll"
Copy-Item -LiteralPath "$root\goldsrc\runtime\liblist.gam","$root\goldsrc\runtime\autoexec.cfg" -Destination $target
Copy-Item -LiteralPath "$root\goldsrc\sdk\LICENSE" -Destination "$target\LICENSE-Valve-SDK"
"Host deployed: $target"
