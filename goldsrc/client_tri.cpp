#define HUD_DrawNormalTriangles HCOriginalDrawNormal
#include "sdk/cl_dll/tri.cpp"
#undef HUD_DrawNormalTriangles
#include "client.h"
extern "C" void CL_DLLEXPORT HUD_DrawNormalTriangles() { HCOriginalDrawNormal(); HCClientDraw(); }
