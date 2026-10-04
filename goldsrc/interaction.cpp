#include "../protocol/halfcraft_interaction.h"
namespace hc_interaction {
using namespace halfcraft_interaction;
Shared* shared=nullptr; HANDLE mapping=nullptr;
std::uint32_t deathAck=0;
bool damageable(edict_t* e) {
    if(!e || e->free || e->v.takedamage==DAMAGE_NO || e->v.health<=0 || (e->v.flags&FL_CLIENT)
        || e->v.deadflag!=DEAD_NO || e->v.solid==SOLID_NOT || e->v.solid==SOLID_TRIGGER) return false;
    auto* entity=CBaseEntity::Instance(e);
    return entity && (entity->MyMonsterPointer() || FClassnameIs(e,"func_breakable") || FClassnameIs(e,"func_pushable"));
}
void frame() {
    if(!shared) {
        mapping=CreateFileMappingW(INVALID_HANDLE_VALUE,nullptr,PAGE_READWRITE,0,Bytes,Mapping);
        shared=mapping?static_cast<Shared*>(MapViewOfFile(mapping,FILE_MAP_ALL_ACCESS,0,0,Bytes)):nullptr;
        if(!shared) return;
        std::memset(shared,0,Bytes);
        shared->host.magic=Magic; shared->host.version=Version; shared->host.bytes=Bytes;
        shared->host.pid=GetCurrentProcessId(); shared->host.session=hc::latest.session;
    }
    auto* p=INDEXENT(1); auto& h=shared->host;
    unsigned next=(hc::load(h.seq)+1)&~1u; hc::store(h.seq,next+1);
    Guest g{}; unsigned seq=hc::load(shared->guest.seq);
    if(seq && !(seq&1) && seq!=h.ack) {
        std::memcpy(&g,&shared->guest,sizeof(g)); MemoryBarrier();
        if(seq==hc::load(shared->guest.seq)) {
            if(g.pid==hc::guestPid && g.session==h.session && GetTickCount()-g.heartbeat<1000
                && shared->deathWrite-g.deathAck<=Capacity) deathAck=g.deathAck;
            if(hc_physics::enabled && hc::connected && p && !p->free && p->v.health>0
                && g.pid==hc::guestPid && g.session==h.session && g.epoch==hc_physics::out.epoch
                && GetTickCount()-g.heartbeat<1000 && g.id>0 && g.id<unsigned(gpGlobals->maxEntities)
                && std::isfinite(g.damage) && g.damage>0 && g.damage<=1000 && g.flags<=3) {
                edict_t* e=INDEXENT(g.id);
                if(damageable(e) && unsigned(e->serialnumber)==g.serial) {
                    Vector eye=p->v.origin+p->v.view_ofs,nearest;
                    for(int axis=0;axis<3;++axis) nearest[axis]=(std::max)(e->v.absmin[axis],(std::min)(eye[axis],e->v.absmax[axis]));
                    TraceResult visible{};
                    // Brush centers can sit behind walls even when their near face is reachable.
                    Vector contact=nearest+((e->v.absmin+e->v.absmax)*.5f-nearest).Normalize()*.5f;
                    { hc_blocks::PlayerTrace exclude; UTIL_TraceLine(eye,contact,dont_ignore_monsters,p,&visible); }
                    auto* entity=CBaseEntity::Instance(e);
                    if(entity && (nearest-eye).Length()<((g.flags&1)?4096:200) && (visible.flFraction==1 || visible.pHit==e)) {
                        int classification=entity->Classify();
                        bool enemy=entity->MyMonsterPointer() && classification!=CLASS_NONE && classification!=CLASS_PLAYER
                            && classification!=CLASS_HUMAN_PASSIVE && classification!=CLASS_PLAYER_ALLY && classification!=CLASS_PLAYER_BIOWEAPON;
                        // Keep confirmed death rewards until guest acknowledges them; never infer death from actor disappearance.
                        if(enemy && shared->deathWrite-deathAck>=Capacity) { h.ack=seq; hc::store(h.seq,next+2); return; }
                        unsigned tier=classification==CLASS_HUMAN_MILITARY?1:0;
                        if(FClassnameIs(e,"monster_gargantua") || FClassnameIs(e,"monster_bigmomma")
                            || FClassnameIs(e,"monster_nihilanth") || FClassnameIs(e,"monster_alien_grunt")
                            || FClassnameIs(e,"monster_ichthyosaur") || FClassnameIs(e,"monster_apache")) tier=2;
                        entity->TakeDamage(&p->v,&p->v,g.damage*5,(g.flags&1)?DMG_BULLET:DMG_CLUB);
                        if(enemy && (e->free || unsigned(e->serialnumber)!=g.serial || e->v.health<=0 || e->v.deadflag!=DEAD_NO)) {
                            unsigned write=shared->deathWrite;
                            shared->deaths[write%Capacity]={write+1,g.id,g.serial,g.epoch,tier|((g.flags&2)?4u:0u)}; shared->deathWrite=write+1;
                        }
                        ALERT(at_console,"HC_HIT id=%u class=%s damage=%.3f health=%.3f projectile=%u\n",g.id,STRING(e->v.classname),g.damage*5,e->v.health,g.flags); }
                    else ALERT(at_console,"HC_HIT_REJECT id=%u class=%s distance=%.3f fraction=%.3f blocker=%d\n",g.id,STRING(e->v.classname),(nearest-eye).Length(),visible.flFraction,visible.pHit?ENTINDEX(visible.pHit):0);
                }
            }
            h.ack=seq;
        }
    }
    h.heartbeat=GetTickCount(); h.enabled=hc_physics::enabled && hc::connected; h.epoch=hc_physics::out.epoch; h.count=0; h.target=0; h.targetId=0;
    if(h.enabled && p && !p->free && p->v.health>0) {
        float distances[Capacity]{};
        for(int i=1;i<gpGlobals->maxEntities;++i) {
            auto* e=INDEXENT(i); if(!damageable(e)) continue;
            Vector lo=e->v.absmin,hi=e->v.absmax;
            if(hi.x<=lo.x || hi.y<=lo.y || hi.z<=lo.z || ((lo+hi)*.5f-p->v.origin).Length()>4096) continue;
            Vector nearest;
            for(int axis=0;axis<3;++axis) nearest[axis]=(std::max)(lo[axis],(std::min)(p->v.origin[axis],hi[axis]));
            float distance=(nearest-p->v.origin).Length(); unsigned slot=h.count;
            if(slot==Capacity) {
                slot=0; for(unsigned j=1;j<Capacity;++j) if(distances[j]>distances[slot]) slot=j;
                if(distance>=distances[slot]) continue;
            } else ++h.count;
            distances[slot]=distance;
            auto& a=shared->actors[slot]; a.id=i; a.serial=e->serialnumber;
            float bounds[]={lo.x/40,lo.z/40,-hi.y/40,hi.x/40,hi.z/40,-lo.y/40};
            std::memcpy(a.bounds,bounds,sizeof(bounds)); a.health=e->v.health; a.flags=(FClassnameIs(e,"func_breakable") || FClassnameIs(e,"func_pushable"))?1:0;
        }
        UTIL_MakeVectors(p->v.v_angle);
        Vector start=p->v.origin+p->v.view_ofs;
        TraceResult tr{}; { hc_blocks::PlayerTrace exclude; UTIL_TraceLine(start,start+gpGlobals->v_forward*180,dont_ignore_monsters,p,&tr); }
        if(tr.flFraction<1 && !tr.fStartSolid) {
            h.point[0]=tr.vecEndPos.x/40; h.point[1]=tr.vecEndPos.z/40; h.point[2]=-tr.vecEndPos.y/40;
            h.targetId=tr.pHit?ENTINDEX(tr.pHit):0;
            // OneBlock sources only stationary world surfaces; buttons/doors/platforms stay native.
            h.target=(!tr.pHit || ENTINDEX(tr.pHit)==0)?1:2;
        }
    }
    hc::store(h.seq,next+2);
}
struct Cleanup { ~Cleanup(){ if(shared) UnmapViewOfFile(shared); if(mapping) CloseHandle(mapping); } } cleanup;
}
