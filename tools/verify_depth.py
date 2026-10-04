"""Live pixel check: world cube visible in corridor, absent behind BSP wall."""
import json
from pathlib import Path
from PIL import Image
from window import run

if __name__=='__main__':
    root=Path(__file__).resolve().parents[1]/'local'
    visible=root/'depth-visible.png'; hidden=root/'depth-hidden.png'
    run('HalfCraft Bridge',['open','hc_look 0 180','hc_render_probe 1 250 318 -175','close','wait:.5',f'shot:{visible}'])
    run('HalfCraft Bridge',['open','hc_render_probe 1 250 450 -175','close','wait:.5',f'shot:{hidden}'])
    def cyan(path):
        with Image.open(path) as image:
            return sum(r<50 and g>200 and 100<b<220 for r,g,b in image.convert('RGB').crop((0,100,image.width,image.height-60)).getdata())
    shown,blocked=cyan(visible),cyan(hidden)
    run('HalfCraft Bridge',['open','hc_render_probe 0','close'])
    assert shown>1000 and blocked==0,(shown,blocked)
    result=dict(result='PASS',visible_pixels=shown,behind_BSP_pixels=blocked)
    (root/'depth-results.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8')
    print(json.dumps(result))
