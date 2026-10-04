#define V_CalcRefdef HCOriginalCalcRefdef
#include "sdk/cl_dll/view.cpp"
#undef V_CalcRefdef
#include "client.h"
extern "C" void CL_DLLEXPORT V_CalcRefdef(ref_params_s* params) { HCOriginalCalcRefdef(params); HCClientView(params); }
