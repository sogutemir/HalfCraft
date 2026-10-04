// Native invisible solids let GoldSrc NPC movement/traces consume vanilla block collision.
#include "../protocol/halfcraft_blocks.h"
namespace hc_blocks {
using namespace halfcraft_blocks;
class Block : public CBaseEntity {
public:
    int ObjectCaps() override { return FCAP_DONT_SAVE; }
};
LINK_ENTITY_TO_CLASS(halfcraft_block,Block);
Shared* shared=nullptr;
HANDLE mapping=nullptr;
EHANDLE proxies[Capacity];
unsigned count=0;
std::uint32_t generation=0,guestPid=0;
Box previous[Capacity];
DWORD nextError=0;
EHANDLE placementNpc;
void clear() {
    if(placementNpc) REMOVE_ENTITY(placementNpc->edict()); placementNpc=nullptr;
    for(unsigned i=0;i<count;++i) if(proxies[i]) REMOVE_ENTITY(proxies[i]->edict());
    count=0; generation=0; guestPid=0;
}
void placementProbe() {
    if(!std::strcmp(CMD_ARGV(1),"0")) {
        if(placementNpc) REMOVE_ENTITY(placementNpc->edict()); placementNpc=nullptr; return;
    }
    if(CMD_ARGC()!=4) { ALERT(at_console,"hc_npc_place_probe x y z | 0 (MC feet)\n"); return; }
    double feet[3];
    for(int i=0;i<3;++i) {
        char* end=nullptr; feet[i]=std::strtod(CMD_ARGV(i+1),&end);
        if(!end || *end || !std::isfinite(feet[i]) || std::fabs(feet[i])>2048) { ALERT(at_console,"HC_NPC_PLACE invalid coordinates\n"); return; }
    }
    if(placementNpc) REMOVE_ENTITY(placementNpc->edict());
    auto npc=CBaseEntity::Create("monster_scientist",Vector(feet[0]*40,-feet[2]*40,feet[1]*40),g_vecZero,nullptr);
    if(!npc) return;
    npc->pev->movetype=MOVETYPE_NONE; npc->pev->nextthink=0; npc->pev->velocity=g_vecZero;
    placementNpc=npc;
    ALERT(at_console,"HC_NPC_PLACE fixture feet=[%.3f %.3f %.3f]\n",feet[0],feet[1],feet[2]);
}
void publish(std::uint32_t ack=0) {
    auto seq=(hc::load(shared->host.seq)+1)&~1u;
    hc::store(shared->host.seq,seq+1);
    shared->host.heartbeat=GetTickCount(); shared->host.enabled=hc_physics::enabled;
    shared->host.epoch=hc_physics::out.epoch; shared->host.proxies=count;
    if(ack) shared->host.ack=ack;
    shared->host.actors=0; shared->host.actorsReady=1;
    for(int i=1;i<gpGlobals->maxEntities;++i) {
        auto e=INDEXENT(i);
        if(!e || e->free || !(e->v.flags&FL_MONSTER) || e->v.solid==SOLID_NOT || e->v.solid==SOLID_TRIGGER) continue;
        Vector lo=e->v.origin+e->v.mins,hi=e->v.origin+e->v.maxs;
        // Script helpers carry FL_MONSTER but have no physical hull.
        if(hi.x<=lo.x || hi.y<=lo.y || hi.z<=lo.z) continue;
        if(shared->host.actors==ActorCapacity) { shared->host.actorsReady=0; break; }
        shared->actors[shared->host.actors++]={{lo.x/40,lo.z/40,-hi.y/40},{hi.x/40,hi.z/40,-lo.y/40}};
    }
    hc::store(shared->host.seq,seq+2);
}
void frame() {
    if(!shared) {
        mapping=CreateFileMappingW(INVALID_HANDLE_VALUE,nullptr,PAGE_READWRITE,0,Bytes,Mapping);
        shared=mapping?static_cast<Shared*>(MapViewOfFile(mapping,FILE_MAP_ALL_ACCESS,0,0,Bytes)):nullptr;
        if(!shared) { if(mapping) CloseHandle(mapping); mapping=nullptr; return; }
        std::memset(shared,0,Bytes);
        shared->host.magic=Magic; shared->host.version=Version; shared->host.bytes=Bytes;
        shared->host.pid=GetCurrentProcessId(); shared->host.session=hc::latest.session;
    }
    Guest guest{}; bool valid=false;
    for(int attempt=0;attempt<8;++attempt) {
        auto seq=hc::load(shared->guest.seq); if(!seq || seq&1) continue;
        std::memcpy(&guest,&shared->guest,sizeof(guest)); MemoryBarrier();
        if(seq==hc::load(shared->guest.seq)) { valid=true; break; }
    }
    if(!hc_physics::enabled || !hc::connected) { clear(); publish(); return; }
    if(!valid) { publish(); return; }
    if(guest.pid!=hc::guestPid || guest.session!=hc::latest.session || guest.epoch!=hc_physics::out.epoch
        || !guest.ready || GetTickCount()-guest.heartbeat>2000) { clear(); publish(); return; }
    if(guest.generation==generation && guest.pid==guestPid) { publish(); return; }
    if(guest.count>Capacity) { clear(); publish(); return; }
    Box boxes[Capacity];
    auto seq=hc::load(shared->guest.seq);
    if(seq!=guest.seq || seq&1) { publish(); return; }
    std::memcpy(boxes,shared->boxes,guest.count*sizeof(Box)); MemoryBarrier();
    if(seq!=hc::load(shared->guest.seq)) { publish(); return; }
    bool bounds=true;
    for(unsigned i=0;i<guest.count;++i) for(int axis=0;axis<3;++axis)
        if(!std::isfinite(boxes[i].min[axis]) || !std::isfinite(boxes[i].max[axis])
            || std::fabs(boxes[i].min[axis])>2048 || std::fabs(boxes[i].max[axis])>2048
            || boxes[i].max[axis]<=boxes[i].min[axis] || boxes[i].max[axis]-boxes[i].min[axis]>16) bounds=false;
    if(!bounds) { clear(); publish(); return; }
    if(guest.count==count && guestPid==guest.pid && !std::memcmp(previous,boxes,count*sizeof(Box))) {
        generation=guest.generation; publish(guest.seq); return;
    }
    unsigned available=count;
    for(int i=gpGlobals->maxClients+1;i<gpGlobals->maxEntities;++i) { auto e=INDEXENT(i); if(!e || e->free) ++available; }
    if(available<guest.count+64) {
        if(std::int32_t(GetTickCount()-nextError)>=0) { nextError=GetTickCount()+5000; ALERT(at_console,"HC_BLOCKS entity budget exceeded boxes=%u available=%u\n",guest.count,available); }
        publish(); return;
    }
    while(count>guest.count) { --count; if(proxies[count]) REMOVE_ENTITY(proxies[count]->edict()); proxies[count]=nullptr; }
    while(count<guest.count) {
        auto entity=CBaseEntity::Create("halfcraft_block",g_vecZero,g_vecZero,nullptr);
        if(!entity) { clear(); publish(); return; }
        entity->pev->solid=SOLID_BBOX; entity->pev->movetype=MOVETYPE_NONE; entity->pev->effects|=EF_NODRAW;
        proxies[count++]=entity;
    }
    for(unsigned i=0;i<count;++i) {
        if(!proxies[i]) { clear(); publish(); return; }
        auto b=boxes[i];
        Vector minimum(b.min[0]*40,-b.max[2]*40,b.min[1]*40),maximum(b.max[0]*40,-b.min[2]*40,b.max[1]*40);
        auto e=proxies[i]->edict();
        UTIL_SetSize(&e->v,g_vecZero,maximum-minimum); UTIL_SetOrigin(&e->v,minimum);
    }
    generation=guest.generation; guestPid=guest.pid;
    std::memcpy(previous,boxes,count*sizeof(Box));
    publish(guest.seq);
}
// MC owns player/block collision with a narrower player body. Exclude mirrored solids
// only during puppet hull validation; NPC engine movement still sees every proxy.
struct PlayerTrace {
    PlayerTrace() { for(unsigned i=0;i<count;++i) if(proxies[i]) proxies[i]->pev->solid=SOLID_NOT; }
    ~PlayerTrace() { for(unsigned i=0;i<count;++i) if(proxies[i]) proxies[i]->pev->solid=SOLID_BBOX; }
};
void check() {
    ALERT(at_console,"HC_BLOCKS proxies=%u generation=%u guest=%u enabled=%d\n",count,generation,guestPid,hc_physics::enabled);
    for(unsigned i=0;i<count;++i) if(proxies[i]) {
        auto e=proxies[i]->pev;
        ALERT(at_console,"HC_BLOCK box=[%.3f %.3f %.3f %.3f %.3f %.3f]\n",e->origin.x/40,e->origin.z/40,-(e->origin.y+e->maxs.y)/40,
            (e->origin.x+e->maxs.x)/40,(e->origin.z+e->maxs.z)/40,-e->origin.y/40);
    }
    unsigned embedded=0;
    for(int index=1;index<gpGlobals->maxEntities;++index) {
        auto e=INDEXENT(index); if(!e || e->free || !(e->v.flags&FL_MONSTER) || e->v.solid!=SOLID_SLIDEBOX) continue;
        for(unsigned i=0;i<count;++i) if(proxies[i]) {
            auto b=proxies[i]->pev; Vector lo=b->origin,hi=lo+b->maxs;
            Vector min=e->v.origin+e->v.mins,max=e->v.origin+e->v.maxs;
            if(max.x>lo.x+.001f && min.x<hi.x-.001f && max.y>lo.y+.001f && min.y<hi.y-.001f && max.z>lo.z+.001f && min.z<hi.z-.001f) { ++embedded; break; }
        }
    }
    ALERT(at_console,"HC_BLOCKS embedded_npcs=%u\n",embedded);
}
void probe() {
    // Exercise engine WALK_MOVE on a real SDK scientist, without touching existing NPCs/blocks.
    auto npc=CBaseEntity::Create("monster_scientist",g_vecZero,g_vecZero,nullptr);
    if(!npc) { ALERT(at_console,"HC_BLOCKS_PROBE FAIL create\n"); return; }
    auto e=npc->edict(); bool tested=false,passed=false;
    for(unsigned i=0;i<count && !tested;++i) if(proxies[i]) {
        auto block=proxies[i]->edict();
        Vector lo=block->v.origin,hi=lo+block->v.maxs;
        if(hi.x-lo.x<39 || hi.y-lo.y<39 || hi.z-lo.z<39) continue;
        for(int axis=0;axis<2 && !tested;++axis) for(int side=-1;side<=1 && !tested;side+=2) {
            Vector start=(lo+hi)*.5f; start.z=lo.z+.03125f;
            start[axis]=side<0?lo[axis]-20:hi[axis]+20;
            Vector end=start; end[axis]+=side<0?80:-80;
            TraceResult native{};
            { PlayerTrace trace; UTIL_TraceHull(start+Vector(0,0,36),end+Vector(0,0,36),dont_ignore_monsters,human_hull,e,&native); }
            if(native.fStartSolid || native.fAllSolid || native.flFraction<1) continue;
            UTIL_SetOrigin(&e->v,start); e->v.flags|=FL_ONGROUND;
            float yaw=axis==0?(side<0?0.f:180.f):(side<0?90.f:270.f);
            for(int step=0;step<16;++step) WALK_MOVE(e,yaw,5,WALKMOVE_NORMAL);
            Vector blocked=e->v.origin;
            bool stopped=side<0?blocked[axis]<=lo[axis]-15.9f:blocked[axis]>=hi[axis]+15.9f;
            float moved=0;
            { PlayerTrace trace;
                UTIL_SetOrigin(&e->v,start); e->v.flags|=FL_ONGROUND;
                for(int step=0;step<16;++step) WALK_MOVE(e,yaw,5,WALKMOVE_NORMAL);
                moved=(e->v.origin-start).Length();
            }
            if(moved<=60) continue;
            tested=true; passed=stopped && moved>60;
            ALERT(at_console,"HC_BLOCKS_PROBE %s proxy=%u stopped=%d blocked_distance=%.3f clear_distance=%.3f\n",passed?"PASS":"FAIL",i,stopped,(blocked-start).Length(),moved);
        }
    }
    REMOVE_ENTITY(e);
    if(!tested) ALERT(at_console,"HC_BLOCKS_PROBE FAIL no clear full-block test lane\n");
}
struct Cleanup { ~Cleanup(){ if(shared) UnmapViewOfFile(shared); if(mapping) CloseHandle(mapping); } } cleanup;
}
