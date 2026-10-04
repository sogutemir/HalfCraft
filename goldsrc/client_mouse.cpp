#define IN_ActivateMouse HCOriginalActivateMouse
#define IN_DeactivateMouse HCOriginalDeactivateMouse
#define IN_ClearStates HCOriginalClearStates
#define IN_MouseMove HCOriginalMouseMove
#define IN_Move HCOriginalMouseInput
#include "sdk/cl_dll/inputw32.cpp"
#undef IN_ActivateMouse
#undef IN_DeactivateMouse
#undef IN_ClearStates
#undef IN_MouseMove
#undef IN_Move
#include "client.h"
void IN_MouseMove(float time,usercmd_t* cmd) {
    if(!HCClientScreen()) { HCOriginalMouseMove(time,cmd); return; }
    int x=0,y=0;
    if(IN_UseRawInput()) { SDL_GetRelativeMouseState(&x,&y); }
    else if(m_bMouseThread) { x=ThreadInterlockedExchange(&s_mouseDeltaX,0); y=ThreadInterlockedExchange(&s_mouseDeltaY,0); }
    else { POINT point; GetCursorPos(&point); x=point.x-gEngfuncs.GetWindowCenterX(); y=point.y-gEngfuncs.GetWindowCenterY(); }
    x+=mx_accum; y+=my_accum; mx_accum=my_accum=0;
    HCClientCursor(x,y); IN_ResetMouse();
}
void IN_Move(float time,usercmd_t* cmd) {
    if(!HCClientScreen()) { HCOriginalMouseInput(time,cmd); return; }
    if(!iMouseInUse && mouseactive) IN_MouseMove(time,cmd);
}
extern "C" {
void CL_DLLEXPORT IN_ActivateMouse() { HCOriginalActivateMouse(); HCClientMouse(true); }
void CL_DLLEXPORT IN_DeactivateMouse() { HCClientMouse(false); HCOriginalDeactivateMouse(); }
void CL_DLLEXPORT IN_ClearStates() { HCOriginalClearStates(); }
}
