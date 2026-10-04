// Preserve SDK source and original native callbacks; replace callback-table entries only.
#include <atomic>
#include <thread>
#include <mutex>
#include <cmath>
#include <cstdio>
#include <cstring>
#include "sdk/dlls/cbase.cpp"
#include "sdk/dlls/weapons.h"
#include "host.cpp"
#include "collision.cpp"
#include "blocks.cpp"
#include "interaction.cpp"
#include "vitals.cpp"
void HCPhysicsFrame() { HCStartFrame(); hc_vitals::frame(); hc_physics::frame(); hc_blocks::frame(); hc_interaction::frame(); }
void HCPhysicsCommand(edict_t* player) {
    if(!std::strcmp(CMD_ARGV(0),"hc_vitals_probe")) {
        auto* native=static_cast<CBasePlayer*>(CBaseEntity::Instance(player));
        float amount=std::strtof(CMD_ARGV(2),nullptr);
        if(!std::strcmp(CMD_ARGV(1),"damage")) native->TakeDamage(&INDEXENT(0)->v,&INDEXENT(0)->v,amount,DMG_CLUB);
        else if(!std::strcmp(CMD_ARGV(1),"fall")) native->TakeDamage(&INDEXENT(0)->v,&INDEXENT(0)->v,amount,DMG_FALL);
        else if(!std::strcmp(CMD_ARGV(1),"blast")) native->TakeDamage(&INDEXENT(0)->v,&INDEXENT(0)->v,amount,DMG_BLAST);
        else if(!std::strcmp(CMD_ARGV(1),"slash")) native->TakeDamage(&INDEXENT(0)->v,&INDEXENT(0)->v,amount,DMG_SLASH);
        else if(!std::strcmp(CMD_ARGV(1),"shock")) native->TakeDamage(&INDEXENT(0)->v,&INDEXENT(0)->v,amount,DMG_SHOCK);
        else if(!std::strcmp(CMD_ARGV(1),"bullet")) native->TakeDamage(&INDEXENT(0)->v,&INDEXENT(0)->v,amount,DMG_BULLET);
        else if(!std::strcmp(CMD_ARGV(1),"heal")) native->TakeHealth(amount,DMG_GENERIC);
        else if(!std::strcmp(CMD_ARGV(1),"repair")) hc_vitals::repair(native,amount);
        ALERT(at_console,"HC_VITALS owns=%d ready=%d health=%.3f nativeHealth=%.3f repair=%u life=%u write=%u ack=%u\n",
            hc_vitals::owns(native),hc_vitals::ready(),hc_vitals::guest.health,player->v.health,hc_vitals::guest.repair,
            hc_vitals::shared?hc_vitals::shared->host.life:0,hc_vitals::shared?hc_vitals::shared->host.write:0,hc_vitals::shared?hc_vitals::shared->host.ack:0);
        return;
    }
    if(!std::strcmp(CMD_ARGV(0),"hc_weapon_check")) {
        auto* native=static_cast<CBasePlayer*>(CBaseEntity::Instance(player));
        auto* item=native?native->m_pActiveItem:nullptr;
        auto* weapon=item?static_cast<CBasePlayerWeapon*>(item->GetWeaponPtr()):nullptr;
        ALERT(at_console,"HC_WEAPON name=%s clip=%d health=%.1f\n",item?STRING(item->pev->classname):"none",weapon?weapon->m_iClip:-1,player->v.health); return;
    }
    if(!std::strcmp(CMD_ARGV(0),"hc_physics")) { hc_physics::command(player); return; }
    if(!std::strcmp(CMD_ARGV(0),"hc_blocks_check")) { hc_blocks::check(); return; }
    if(!std::strcmp(CMD_ARGV(0),"hc_blocks_probe")) { hc_blocks::probe(); return; }
    if(!std::strcmp(CMD_ARGV(0),"hc_npc_place_probe")) { hc_blocks::placementProbe(); return; }
    HCClientCommand(player);
}
void HCPhysicsDeactivate() { hc_blocks::clear(); hc_physics::suspend("map transition"); HCServerDeactivate(); }
void HCPhysicsPre(edict_t* player) { PlayerPreThink(player); hc_physics::pre(player); }
void HCPhysicsPost(edict_t* player) { PlayerPostThink(player); hc_blocks::PlayerTrace trace; hc_physics::post(player); }
void HCPhysicsSave(edict_t* entity,SAVERESTOREDATA* data) { hc_blocks::clear(); hc_physics::suspend("native save"); DispatchSave(entity,data); }
int HCPhysicsRestore(edict_t* entity,SAVERESTOREDATA* data,int globalEntity) {
    hc_blocks::clear(); hc_physics::suspend("native restore");
    int result=DispatchRestore(entity,data,globalEntity);
    // Old development checkpoints stored godmode; never restore that immunity into survival.
    if(entity && !entity->free && (entity->v.flags&FL_CLIENT)) entity->v.flags&=~FL_GODMODE;
    return result;
}
namespace {
struct BridgeCallbacks {
    BridgeCallbacks() {
        gFunctionTable.pfnGameInit=HCGameDLLInit;
        gFunctionTable.pfnStartFrame=HCPhysicsFrame;
        gFunctionTable.pfnServerActivate=HCServerActivate;
        gFunctionTable.pfnServerDeactivate=HCPhysicsDeactivate;
        gFunctionTable.pfnClientCommand=HCPhysicsCommand;
        gFunctionTable.pfnPlayerPreThink=HCPhysicsPre;
        gFunctionTable.pfnPlayerPostThink=HCPhysicsPost;
        gFunctionTable.pfnSave=HCPhysicsSave;
        gFunctionTable.pfnRestore=HCPhysicsRestore;
    }
} bridgeCallbacks;
}
