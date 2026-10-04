param([switch]$Check)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$game='K:\SteamLibrary\steamapps\common\Half-Life'
Add-Type @'
using System;
using System.Runtime.InteropServices;
public static class HalfCraftDisplay {
    [StructLayout(LayoutKind.Sequential, CharSet=CharSet.Ansi)]
    public struct Mode {
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst=32)] public string device;
        public ushort spec, driver, size, extra;
        public uint fields;
        public int x, y;
        public uint orientation, fixedOutput;
        public short color, duplex, yResolution, ttOption, collate;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst=32)] public string form;
        public ushort logPixels;
        public uint bits, width, height, flags, frequency, icmMethod, icmIntent, media, dither, reserved1, reserved2, panningWidth, panningHeight;
    }
    [DllImport("user32.dll", CharSet=CharSet.Ansi)]
    static extern bool EnumDisplaySettings(string device, int index, ref Mode mode);
    public static Mode Maximum() {
        Mode best=new Mode(), mode=new Mode();
        mode.size=(ushort)Marshal.SizeOf(typeof(Mode));
        if (mode.size!=156) throw new Exception("Invalid DEVMODE layout");
        for (int i=0; EnumDisplaySettings(null,i,ref mode); i++) {
            if (mode.bits>=32 && ((ulong)mode.width*mode.height>(ulong)best.width*best.height ||
                (mode.width==best.width && mode.height==best.height && mode.frequency>best.frequency))) best=mode;
        }
        if (best.width==0 || best.height==0) throw new Exception("No supported display mode found");
        return best;
    }
}
'@
$mode=[HalfCraftDisplay]::Maximum()
$arguments="-game halfcraft_bridge -gl -full -w $($mode.width) -h $($mode.height) -freq $($mode.frequency) -halfcraft-play +developer 0 +sv_cheats 0"
if($Check) { "HalfCraft launch check PASS: $arguments"; return }
foreach($path in @("$game\hl.exe","$game\halfcraft_bridge\dlls\hl.dll","$game\halfcraft_bridge\cl_dlls\client.dll")) {
    if(!(Test-Path -LiteralPath $path)) { throw "Missing runtime: $path" }
}
if(Get-Process hl -ErrorAction SilentlyContinue) { throw 'Close Half-Life before starting HalfCraft fullscreen.' }
if((Test-Path -LiteralPath "$root\goldsrc\build\hl.dll") -and (Test-Path -LiteralPath "$root\goldsrc\build\client.dll")) {
    & "$root\tools\deploy.ps1" -Game $game
}
& python "$root\tools\setup-minecraft.py"
if($LASTEXITCODE) { throw 'Minecraft instance setup failed' }
Start-Process -FilePath "$root\local\Prism\prismlauncher.exe" -WorkingDirectory "$root\local\Prism" -ArgumentList "--dir `"$root\local\Prism`" --launch HalfCraftBridge"
Start-Process -FilePath "$game\hl.exe" -WorkingDirectory $game -ArgumentList $arguments
