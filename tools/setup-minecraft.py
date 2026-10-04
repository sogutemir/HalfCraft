"""Install isolated authenticated Prism development instance; never reads normal launcher accounts."""
from pathlib import Path
import hashlib
import json
import re
import shutil
import urllib.request
import zipfile

ROOT=Path(__file__).resolve().parents[1]
LOCAL=ROOT/'local'
PRISM=LOCAL/'Prism'
INSTANCE=PRISM/'instances'/'HalfCraftBridge'
URL='https://github.com/PrismLauncher/PrismLauncher/releases/download/11.1.1/PrismLauncher-Windows-MSVC-Portable-11.1.1.zip'
SHA='ab35a770fb06d89d2ccc098079db5db329fb4e68f42b72babd8b095efde3d2d7'
def download(url,path):
    if path.exists(): return
    temporary=path.with_suffix(path.suffix+'.tmp')
    urllib.request.urlretrieve(url,temporary); temporary.replace(path)
def setup():
    LOCAL.mkdir(exist_ok=True)
    archive=LOCAL/'Prism-11.1.1.zip'; download(URL,archive)
    if hashlib.sha256(archive.read_bytes()).hexdigest()!=SHA: raise RuntimeError('Prism download hash mismatch')
    if not (PRISM/'prismlauncher.exe').exists():
        PRISM.mkdir(exist_ok=True)
        with zipfile.ZipFile(archive) as source:
            for info in source.infolist():
                if not (PRISM/info.filename).resolve().is_relative_to(PRISM.resolve()): raise RuntimeError('Unsafe archive path')
            source.extractall(PRISM)
    INSTANCE.mkdir(parents=True,exist_ok=True)
    pack=INSTANCE/'mmc-pack.json'
    if not pack.exists(): shutil.copyfile(ROOT/'tools/minecraft-bundle/Prism/instances/SkyCraft/mmc-pack.json',pack)
    config=INSTANCE/'instance.cfg'
    if not config.exists(): config.write_text('[General]\nname=HalfCraft Bridge\nInstanceType=OneSix\nOverrideJava=true\nJavaPath=C:/Users/sogut/jdk/jdk-25.0.1+8/bin/javaw.exe\nOverrideMemory=true\nMaxMemAlloc=16384\nMinMemAlloc=8192\nOverrideJavaArgs=true\nJvmArgs=--enable-native-access=ALL-UNNAMED\n',encoding='utf8')
    text=config.read_text(encoding='utf8')
    for key,value in (('OverrideMemory','true'),('MinMemAlloc','8192'),('MaxMemAlloc','16384')):
        text=re.sub(rf'^{key}=.*$',f'{key}={value}',text,flags=re.MULTILINE) if re.search(rf'^{key}=',text,re.MULTILINE) else text.replace('[General]',f'[General]\n{key}={value}',1)
    config.write_text(text,encoding='utf8')
    prism_config=PRISM/'prismlauncher.cfg'
    if not prism_config.exists(): prism_config.write_text('[General]\nConfigVersion=1.2\nLanguage=en_US\nJavaPath=C:/Users/sogut/jdk/jdk-25.0.1+8/bin/javaw.exe\nLastHostname=HalfCraftBridge\n',encoding='utf8')
    mods=INSTANCE/'.minecraft'/'mods'; mods.mkdir(parents=True,exist_ok=True)
    jars=list((ROOT/'fabric/build/libs').glob('halfcraft-bridge-*.jar'))
    jars=[p for p in jars if not p.name.endswith('-sources.jar')]
    if len(jars)!=1: raise RuntimeError('Build HalfCraft guest first')
    with zipfile.ZipFile(jars[0]) as jar:
        descriptor=json.loads(jar.read('fabric.mod.json'))
        if descriptor['id']!='halfcraft_bridge' or descriptor.get('mixins')!=['halfcraft.mixins.json','halfcraft.render.mixins.json']: raise RuntimeError('Wrong guest profile')
    shutil.copyfile(jars[0],mods/jars[0].name)
    api=LOCAL/'fabric-api-0.161.0+26.3.jar'
    download('https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.161.0+26.3/fabric-api-0.161.0+26.3.jar',api)
    shutil.copyfile(api,mods/api.name)
    print(f'Prism ready: {PRISM / "prismlauncher.exe"}\nInstance: HalfCraftBridge\nMicrosoft Java ownership authentication required; no accounts copied.')
if __name__=='__main__': setup()
