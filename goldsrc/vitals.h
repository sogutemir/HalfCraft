#pragma once
class CBasePlayer;
namespace hc_vitals {
bool owns(CBasePlayer* player);
int damage(CBasePlayer* player,entvars_t* inflictor,entvars_t* attacker,float amount,int bits);
int heal(CBasePlayer* player,float amount);
bool repair(CBasePlayer* player,float amount);
void frame();
}
