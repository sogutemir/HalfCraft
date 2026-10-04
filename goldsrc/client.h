#pragma once
struct usercmd_s;
struct ref_params_s;
void HCClientInit();
void HCClientReset();
void HCClientFrame();
void HCClientMouse(bool active);
bool HCClientScreen();
void HCClientCursor(int dx,int dy);
void HCClientMove(usercmd_s* cmd, int active);
bool HCClientKey(int down, int key, const char* binding);
void HCClientView(ref_params_s* params);
void HCClientDraw();
void HCClientHUD();
void HCClientClose();
bool HCClientMinecraftHUD();
