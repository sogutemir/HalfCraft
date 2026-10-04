#define CL_CreateMove HCOriginalCreateMove
#define HUD_Key_Event HCOriginalKeyEvent
#define HUD_Shutdown HCOriginalShutdown
#include "sdk/cl_dll/input.cpp"
#undef CL_CreateMove
#undef HUD_Key_Event
#undef HUD_Shutdown
#include "client.h"
extern "C" {
void CL_DLLEXPORT CL_CreateMove(float time, usercmd_s* cmd, int active) { HCOriginalCreateMove(time,cmd,active); HCClientMove(cmd,active); }
int CL_DLLEXPORT HUD_Key_Event(int down, int key, const char* binding) { return HCClientKey(down,key,binding) ? 0 : HCOriginalKeyEvent(down,key,binding); }
void CL_DLLEXPORT HUD_Shutdown() { HCClientClose(); HCOriginalShutdown(); }
}
