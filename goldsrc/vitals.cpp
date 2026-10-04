#include "../protocol/halfcraft_vitals.h"
#include "vitals.h"
namespace hc_vitals {
using namespace halfcraft_vitals;
Shared* shared=nullptr; HANDLE mapping=nullptr;
Guest guest{}; unsigned seenDeaths=0; int serial=-1; bool wasAlive=false;
float healing=0; unsigned repairing=0;
bool owns(CBasePlayer* player) {
    return shared && player && ENTINDEX(player->edict())==1 && hc::connected
        && (hc_physics::enabled || hc_physics::resumeRequested);
}
bool ready() {
    Guest read{}; unsigned seq=hc::load(shared->guest.seq);
    if(seq && !(seq&1)) {
        std::memcpy(&read,&shared->guest,sizeof(read)); MemoryBarrier();
        if(seq==hc::load(shared->guest.seq) && read.pid==hc::guestPid && read.session==hc::latest.session
            && std::isfinite(read.health) && std::isfinite(read.maxHealth) && read.health>=0 && read.health<=read.maxHealth
            && read.maxHealth>0 && read.maxHealth<=1000 && read.repair<=100000) guest=read;
    }
    return guest.ready
        && guest.pid==hc::guestPid && guest.session==hc::latest.session && guest.life==shared->host.life
        && GetTickCount()-guest.heartbeat<1000;
}
bool enqueue(unsigned kind,float amount,unsigned bits,const Vector& position) {
    auto& h=shared->host;
    if(h.write-h.ack>=Capacity) { ALERT(at_error,"HC_VITALS queue full\n"); return false; }
    unsigned next=(hc::load(h.seq)+1)&~1u; hc::store(h.seq,next+1);
    unsigned write=h.write;
    auto& event=shared->events[write%Capacity]; event={write+1,h.life,kind,bits,amount,{position.x/40,position.z/40,-position.y/40}};
    h.write=write+1; hc::store(h.seq,next+2); return true;
}
int damage(CBasePlayer* player,entvars_t* inflictor,entvars_t* attacker,float amount,int bits) {
    if(!ready() || !player->IsAlive() || !std::isfinite(amount) || amount<=0 || player->pev->flags&FL_GODMODE
        || !g_pGameRules->FPlayerCanTakeDamage(player,CBaseEntity::Instance(attacker))) return 0;
    Vector source=inflictor?inflictor->origin:(attacker?attacker->origin:player->pev->origin);
    bool accepted=enqueue(Damage,(std::min)(amount/5,10000.f),bits,source);
    if(accepted) ALERT(at_console,"HC_NATIVE_DAMAGE amount=%.3f bits=%d\n",amount/5,bits);
    return accepted;
}
int heal(CBasePlayer* player,float amount) {
    if(!ready() || !player->IsAlive() || !std::isfinite(amount) || amount<=0 || guest.health+healing>=guest.maxHealth) return 0;
    float gain=(std::min)(amount/5,guest.maxHealth-guest.health-healing);
    if(!enqueue(Heal,gain,0,player->pev->origin)) return 0;
    healing+=gain; return 1;
}
bool repair(CBasePlayer* player,float amount) {
    if(!owns(player) || !ready() || !player->IsAlive() || !std::isfinite(amount) || amount<=0 || guest.repair<=repairing) return false;
    unsigned gain=(std::min)(unsigned(std::ceil(amount)),guest.repair-repairing);
    if(!enqueue(Repair,float(gain),0,player->pev->origin)) return false;
    repairing+=gain; return true;
}
void frame() {
    if(!shared) {
        mapping=CreateFileMappingW(INVALID_HANDLE_VALUE,nullptr,PAGE_READWRITE,0,Bytes,Mapping);
        shared=mapping?static_cast<Shared*>(MapViewOfFile(mapping,FILE_MAP_ALL_ACCESS,0,0,Bytes)):nullptr;
        if(!shared) return;
        std::memset(shared,0,Bytes); shared->host.magic=Magic; shared->host.version=Version; shared->host.bytes=Bytes;
        shared->host.pid=GetCurrentProcessId(); shared->host.session=hc::latest.session;
    }
    auto* e=INDEXENT(1); auto* player=e && !e->free?static_cast<CBasePlayer*>(CBaseEntity::Instance(e)):nullptr;
    bool alive=player && player->IsAlive(); auto& h=shared->host;
    unsigned next=(hc::load(h.seq)+1)&~1u; hc::store(h.seq,next+1);
    if(player && (serial!=e->serialnumber || (alive && !wasAlive))) { ++h.life; serial=e->serialnumber; healing=0; repairing=0; }
    wasAlive=alive;
    Guest read{}; unsigned seq=hc::load(shared->guest.seq);
    if(seq && !(seq&1)) {
        std::memcpy(&read,&shared->guest,sizeof(read)); MemoryBarrier();
        if(seq==hc::load(shared->guest.seq) && read.pid==hc::guestPid && read.session==h.session
            && GetTickCount()-read.heartbeat<1000 && h.write-read.ack<=Capacity
            && std::isfinite(read.health) && std::isfinite(read.maxHealth) && read.health>=0 && read.maxHealth>0
            && read.maxHealth<=1000 && read.health<=read.maxHealth && read.repair<=100000) {
            guest=read; h.ack=read.ack;
            healing=0; repairing=0;
            for(unsigned i=h.ack;i!=h.write;++i) { auto& event=shared->events[i%Capacity]; if(event.life==h.life) {
                if(event.kind==Heal) healing+=event.amount; if(event.kind==Repair) repairing+=unsigned(event.amount);
            } }
        }
    }
    if(owns(player) && ready()) {
        player->pev->armorvalue=0;
        if(guest.deaths!=seenDeaths) { seenDeaths=guest.deaths; if(alive) player->Killed(&INDEXENT(0)->v,GIB_NEVER); }
        else if(alive) player->pev->health=(std::max)(1.f,guest.health*5);
    } else if(guest.life!=h.life) seenDeaths=guest.deaths;
    h.alive=player && player->IsAlive(); h.enabled=hc::connected && (hc_physics::enabled || hc_physics::resumeRequested);
    h.heartbeat=GetTickCount(); hc::store(h.seq,next+2);
}
struct Cleanup { ~Cleanup(){ if(shared) UnmapViewOfFile(shared); if(mapping) CloseHandle(mapping); } } cleanup;
}
