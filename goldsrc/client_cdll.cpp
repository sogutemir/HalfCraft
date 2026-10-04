#define Initialize HCOriginalInitialize
#define HUD_Init HCOriginalHUDInit
#define HUD_VidInit HCOriginalVidInit
#define HUD_Reset HCOriginalReset
#define HUD_Frame HCOriginalFrame
#define HUD_Redraw HCOriginalRedraw
#define F HCOriginalF
#include "sdk/cl_dll/cdll_int.cpp"
#undef Initialize
#undef HUD_Init
#undef HUD_VidInit
#undef HUD_Reset
#undef HUD_Frame
#undef HUD_Redraw
#undef F
#include "client.h"
extern "C" {
int CL_DLLEXPORT Initialize(cl_enginefunc_t* engine, int version) { return HCOriginalInitialize(engine,version); }
void CL_DLLEXPORT HUD_Init() { HCOriginalHUDInit(); HCClientInit(); }
int CL_DLLEXPORT HUD_VidInit() { HCClientReset(); return HCOriginalVidInit(); }
void CL_DLLEXPORT HUD_Reset() { HCClientReset(); HCOriginalReset(); }
void CL_DLLEXPORT HUD_Frame(double time) { HCOriginalFrame(time); HCClientFrame(); }
int CL_DLLEXPORT HUD_Redraw(float time,int intermission) {
    int saved=gHUD.m_iHideHUDDisplay;
    if(HCClientMinecraftHUD()) gHUD.m_iHideHUDDisplay|=HIDEHUD_HEALTH|HIDEHUD_WEAPONS;
    int result=HCOriginalRedraw(time,intermission); gHUD.m_iHideHUDDisplay=saved;
    HCClientHUD(); return result;
}
void CL_DLLEXPORT F(void* pv) {
    HCOriginalF(pv);
    auto* table=static_cast<cldll_func_t*>(pv);
    table->pInitFunc=Initialize; table->pHudInitFunc=HUD_Init;
    table->pHudVidInitFunc=HUD_VidInit; table->pHudResetFunc=HUD_Reset; table->pHudFrame=HUD_Frame;
    table->pHudRedrawFunc=HUD_Redraw;
}
}
