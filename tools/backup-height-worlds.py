"""Closed dedicated-world backup before expanding dimension height/removing generated bedrock."""
from datetime import datetime
from pathlib import Path
import shutil
import ctypes

root=Path(__file__).resolve().parents[1]
if ctypes.windll.user32.FindWindowW(None,'Minecraft* 26.3 - Singleplayer'):
    raise RuntimeError('Close dedicated Minecraft before backup')
saves=root/'local/Prism/instances/HalfCraftBridge/.minecraft/saves'
target=root/'local'/('height-backup-'+datetime.now().strftime('%Y%m%d-%H%M%S'))
for world in saves.glob('HalfCraftPhysics*'):
    if world.is_dir(): shutil.copytree(world,target/world.name)
print('World backup:',target)
