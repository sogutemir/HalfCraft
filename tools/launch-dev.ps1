param([string]$Map='c1a0',[switch]$HostOnly,[switch]$Background,[switch]$BlockCheck)
$ErrorActionPreference='Stop'
if($Map -notmatch '^[a-zA-Z0-9_-]{1,63}$') { throw 'Invalid map name' }
$root=Split-Path $PSScriptRoot -Parent
$game='K:\SteamLibrary\steamapps\common\Half-Life'
if(!(Test-Path -LiteralPath "$game\halfcraft_bridge\dlls\hl.dll")) { throw 'Deploy bridge host first' }
if(!(Get-Process hl -ErrorAction SilentlyContinue)) {
    $probe=if($BlockCheck) { ' -halfcraft-block-check' } else { '' }
    Start-Process -FilePath "$game\hl.exe" -WorkingDirectory $game -ArgumentList "-game halfcraft_bridge -console -condebug -windowed -w 1280 -h 720 +map $Map$probe"
}
if(!$HostOnly) {
    & python "$root\tools\setup-minecraft.py"
    if($LASTEXITCODE) { throw 'Minecraft instance setup failed' }
    Start-Process -FilePath "$root\local\Prism\prismlauncher.exe" -WorkingDirectory "$root\local\Prism" -ArgumentList "--dir `"$root\local\Prism`" --launch HalfCraftBridge"
}
if($Background) {
    $deadline=(Get-Date).AddSeconds(30)
    while(!(Get-Process hl -ErrorAction SilentlyContinue | Where-Object MainWindowTitle -eq 'HalfCraft Bridge')) {
        if((Get-Date) -gt $deadline) { throw 'Half-Life window did not open' }
        Start-Sleep -Milliseconds 200
    }
    & python "$root\tools\window.py" wait:2 open 'hc_background 1' close
    if($LASTEXITCODE) { throw 'Background setting failed' }
}
