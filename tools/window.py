"""Bridge-owned window automation. No credential handling. Pillow already installed locally."""
import argparse
import ctypes
from ctypes import wintypes as w
import time
from pathlib import Path
from PIL import ImageGrab

user=ctypes.WinDLL('user32',use_last_error=True)
user.FindWindowW.argtypes=(w.LPCWSTR,w.LPCWSTR); user.FindWindowW.restype=w.HWND
user.SetForegroundWindow.argtypes=(w.HWND,); user.GetForegroundWindow.restype=w.HWND
user.PostMessageW.argtypes=(w.HWND,w.UINT,w.WPARAM,w.LPARAM)
user.GetWindowRect.argtypes=(w.HWND,ctypes.POINTER(w.RECT))
def key(vk):
    user.keybd_event(vk,user.MapVirtualKeyW(vk,0),0,0); time.sleep(.1)
    user.keybd_event(vk,user.MapVirtualKeyW(vk,0),2,0); time.sleep(.1)
def console_visible(hwnd):
    bounds=w.RECT(); user.GetWindowRect(hwnd,ctypes.byref(bounds))
    image=ImageGrab.grab(bbox=(bounds.left,bounds.top,bounds.right,bounds.bottom))
    colors=[image.getpixel((x,450))[:3] for x in (80,180,400)]
    return colors[0]==colors[1]==colors[2] and colors[0][1]>=colors[0][0] and colors[0][2]<colors[0][0]
def paste(hwnd,text):
    if user.GetForegroundWindow()!=hwnd: raise RuntimeError('Target window lost foreground')
    kernel=ctypes.WinDLL('kernel32',use_last_error=True)
    kernel.GlobalAlloc.argtypes=(w.UINT,ctypes.c_size_t); kernel.GlobalAlloc.restype=ctypes.c_void_p
    kernel.GlobalLock.argtypes=(ctypes.c_void_p,); kernel.GlobalLock.restype=ctypes.c_void_p
    kernel.GlobalUnlock.argtypes=(ctypes.c_void_p,)
    user.SetClipboardData.argtypes=(w.UINT,ctypes.c_void_p); user.SetClipboardData.restype=ctypes.c_void_p
    user.OpenClipboard.argtypes=(w.HWND,)
    payload=(text+'\0').encode('utf-16-le'); memory=kernel.GlobalAlloc(2,len(payload)); pointer=kernel.GlobalLock(memory)
    ctypes.memmove(pointer,payload,len(payload)); kernel.GlobalUnlock(memory)
    if not user.OpenClipboard(hwnd): raise RuntimeError('Clipboard busy')
    try: user.EmptyClipboard(); user.SetClipboardData(13,memory)
    finally: user.CloseClipboard()
    user.keybd_event(0x11,0,0,0); key(0x56); user.keybd_event(0x11,0,2,0)
def run(title,actions):
    hwnd=user.FindWindowW(None,title)
    if not hwnd: raise RuntimeError('Window not found: '+title)
    if actions==['close-window']: user.PostMessageW(hwnd,0x10,0,0); return
    user.ShowWindow(hwnd,9); user.keybd_event(0x12,0,0,0); user.SetForegroundWindow(hwnd); user.keybd_event(0x12,0,2,0); time.sleep(.5)
    if user.GetForegroundWindow()!=hwnd: raise RuntimeError('Target window not foreground')
    for action in actions:
        if action=='toggle': key(0x79)
        elif action=='open':
            if not console_visible(hwnd): key(0x79); time.sleep(.3)
            if not console_visible(hwnd): raise RuntimeError('Console not visible')
        elif action=='close':
            if console_visible(hwnd): key(0x79); key(0x1b)
        elif action=='grave': key(0xc0)
        elif action=='escape': key(0x1b)
        elif action.startswith('click:'):
            _,x,y=action.split(':'); bounds=w.RECT(); user.GetWindowRect(hwnd,ctypes.byref(bounds))
            x,y=int(x),int(y)
            if not (0<=x<bounds.right-bounds.left and 0<=y<bounds.bottom-bounds.top): raise ValueError('Click outside target window')
            user.SetCursorPos(bounds.left+x,bounds.top+y); user.mouse_event(2,0,0,0,0); user.mouse_event(4,0,0,0,0)
        elif action.startswith('wait:'): time.sleep(float(action[5:]))
        elif action.startswith('hold:'):
            _,letter,seconds=action.split(':'); keys=[{'SPACE':0x20,'CTRL':0x11,'SHIFT':0x10,'TAB':0x09,'ENTER':0x0d}.get(part.upper(),ord(part.upper()) if len(part)==1 else 0) for part in letter.split('+')]
            if not all(keys): raise ValueError('Unknown hold key: '+letter)
            if user.GetForegroundWindow()!=hwnd: raise RuntimeError('Target window lost foreground')
            for vk in keys: user.keybd_event(vk,user.MapVirtualKeyW(vk,0),0,0)
            try: time.sleep(float(seconds))
            finally:
                for vk in keys: user.keybd_event(vk,user.MapVirtualKeyW(vk,0),2,0)
        elif action.startswith('mouse:'):
            _,button,seconds=action.split(':'); down,up={'LEFT':(2,4),'RIGHT':(8,16)}[button.upper()]
            if user.GetForegroundWindow()!=hwnd: raise RuntimeError('Target window lost foreground')
            user.mouse_event(down,0,0,0,0)
            try: time.sleep(float(seconds))
            finally: user.mouse_event(up,0,0,0,0)
        elif action.startswith('move:'):
            _,dx,dy=action.split(':')
            if user.GetForegroundWindow()!=hwnd: raise RuntimeError('Target window lost foreground')
            user.mouse_event(1,int(dx),int(dy),0,0)
        elif action.startswith('wheel:'):
            if user.GetForegroundWindow()!=hwnd: raise RuntimeError('Target window lost foreground')
            user.mouse_event(0x800,0,0,int(action[6:])*120,0)
        elif action.startswith('shot:'):
            bounds=w.RECT(); user.GetWindowRect(hwnd,ctypes.byref(bounds)); target=Path(action[5:]); target.parent.mkdir(parents=True,exist_ok=True)
            ImageGrab.grab(bbox=(bounds.left,bounds.top,bounds.right,bounds.bottom)).save(target)
        else:
            if title=='HalfCraft Bridge':
                bounds=w.RECT(); user.GetWindowRect(hwnd,ctypes.byref(bounds))
                user.SetCursorPos(bounds.left+220,bounds.top+450)
                user.mouse_event(2,0,0,0,0); user.mouse_event(4,0,0,0,0)
                user.keybd_event(0x11,0,0,0); key(0x41); user.keybd_event(0x11,0,2,0); key(0x08)
            paste(hwnd,action); key(0x0d)
        time.sleep(.3)
if __name__=='__main__':
    parser=argparse.ArgumentParser(); parser.add_argument('--title',default='HalfCraft Bridge'); parser.add_argument('actions',nargs='+'); args=parser.parse_args(); run(args.title,args.actions)
