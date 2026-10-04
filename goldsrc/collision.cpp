// ponytail: 8-block window, 10-unit point-content/brush samples;
// replace occupancy with exact BSP triangles before slopes/full campaign support.
#include "../protocol/halfcraft_collision.h"
#include "../protocol/halfcraft_input.h"
#include "sdk/dlls/player.h"
#include <algorithm>
namespace hc_physics {
using namespace halfcraft_collision;
Shared* shared=nullptr; HANDLE mapping=nullptr;
HANDLE inputHandle=nullptr;
halfcraft_input::Shared* input=nullptr;
Host out{}; std::uint64_t masks[Cells]{};
bool enabled=false,building=false,puppeting=false,resumeRequested=false;
unsigned cursor=0; DWORD nextBuild=0; edict_t* puppet=nullptr;
int savedMove=0; float savedGravity=0; Vector lastOrigin;
bool crouched=false;
bool nativeControl=false;
double originHeight() { return crouched?18.03125:36.03125; }
void traceBody(const Vector& from,const Vector& to,edict_t* player,TraceResult& trace) {
    UTIL_TraceHull(from,to,dont_ignore_monsters,crouched?head_hull:human_hull,player,&trace);
    if(crouched) {
        TraceResult upper{};
        UTIL_TraceHull(from+Vector(0,0,24),to+Vector(0,0,24),dont_ignore_monsters,head_hull,player,&upper);
        if(upper.fStartSolid || upper.fAllSolid || upper.flFraction<trace.flFraction) {
            trace=upper; trace.vecEndPos=trace.vecEndPos-Vector(0,0,24);
        }
    }
}
DWORD correctionStarted=0,nextCorrectionLog=0,resumeAfter=0;
struct Brush { Vector min,max; edict_t* entity; }; Brush brushes[256]; unsigned brushCount=0;
void publish();
void release(const char* reason) {
    if(puppeting && puppet && !puppet->free) {
        if(puppet->v.movetype==MOVETYPE_NONE && puppet->v.deadflag==DEAD_NO) puppet->v.movetype=savedMove;
        puppet->v.gravity=savedGravity; puppet->v.velocity=g_vecZero;
        if(crouched) { puppet->v.flags|=FL_DUCKING; UTIL_SetSize(&puppet->v,VEC_DUCK_HULL_MIN,VEC_DUCK_HULL_MAX); }
    }
    if(enabled) ALERT(at_console,"HC_PHYSICS OFF reason=%s\n",reason);
    puppeting=false; enabled=false; puppet=nullptr; out.enabled=0; resumeRequested=false; crouched=false;
    nativeControl=false; out.nativeControl=0;
}
void suspend(const char* reason) {
    bool resume=enabled || resumeRequested;
    release(reason); resumeRequested=resume; resumeAfter=GetTickCount()+250;
    out.ready=0; building=false; publish();
}
void close() {
    release("shutdown");
    if(shared) { UnmapViewOfFile(shared); shared=nullptr; }
    if(mapping) { CloseHandle(mapping); mapping=nullptr; }
    if(input) { UnmapViewOfFile(input); input=nullptr; }
    if(inputHandle) { CloseHandle(inputHandle); inputHandle=nullptr; }
}
struct Cleanup { ~Cleanup(){ close(); } } cleanup;
void init() {
    if(shared || !hc::shared) return;
    mapping=CreateFileMappingW(INVALID_HANDLE_VALUE,nullptr,PAGE_READWRITE,0,Bytes,Mapping);
    shared=mapping ? static_cast<Shared*>(MapViewOfFile(mapping,FILE_MAP_ALL_ACCESS,0,0,Bytes)) : nullptr;
    if(!shared) { if(mapping) CloseHandle(mapping); mapping=nullptr; ALERT(at_console,"HC_PHYSICS mapping error\n"); return; }
    std::memset(shared,0,Bytes); out={}; out.magic=Magic; out.version=Version; out.bytes=Bytes;
    out.pid=GetCurrentProcessId(); out.session=hc::latest.session; out.dim=Dim; out.sub=Sub;
}
void publish() {
    if(!shared) return;
    out.heartbeat=GetTickCount(); out.enabled=enabled;
    auto seq=(hc::load(shared->host.seq)+1)&~1u;
    hc::store(shared->host.seq,seq+1);
    std::memcpy(reinterpret_cast<char*>(&shared->host)+4,reinterpret_cast<char*>(&out)+4,sizeof(Host)-4);
    if(out.ready) std::memcpy(shared->masks,masks,sizeof(masks));
    hc::store(shared->host.seq,seq+2);
}
bool read(Guest& guest) {
    if(!shared) return false;
    static Guest cached{};
    for(int i=0;i<16;++i) {
        auto seq=hc::load(shared->guest.seq); if(!seq || seq&1) continue;
        std::memcpy(&guest,&shared->guest,sizeof(guest)); MemoryBarrier();
        if(hc::load(shared->guest.seq)==seq) { cached=guest; break; }
    }
    guest=cached;
    return guest.pid==hc::guestPid && guest.session==out.session && guest.epoch==out.epoch && guest.active
        && std::uint32_t(GetTickCount()-guest.heartbeat)<2000;
}
void startBuild(edict_t* p) {
    double feet[]={p->v.origin.x/40.,(p->v.origin.z-originHeight())/40.,-p->v.origin.y/40.};
    for(int i=0;i<3;++i) out.base[i]=static_cast<int>(std::floor(feet[i]))-(i==1?2:4);
    std::memset(masks,0,sizeof(masks)); cursor=0; brushCount=0; building=true; out.ready=0; out.occupied=0;
    for(int i=1;i<gpGlobals->maxEntities && brushCount<256;++i) {
        edict_t* e=INDEXENT(i); if(!e || e->free || e->v.solid!=SOLID_BSP) continue;
        brushes[brushCount++]={e->v.absmin,e->v.absmax,e};
    }
}
void gather() {
    for(unsigned stop=(std::min)(cursor+4096u,Cells*64u);cursor<stop;++cursor) {
        unsigned cell=cursor/64,bit=cursor%64;
        double x=double(out.base[0])+cell%Dim+(bit%Sub+.5)/Sub;
        // ponytail: floor samples round up within 10 units; exact BSP triangles remove this ceiling.
        double y=double(out.base[1])+cell/Dim%Dim+double(bit/Sub%Sub)/Sub+.03125/40.;
        double z=double(out.base[2])+cell/(Dim*Dim)+(bit/(Sub*Sub)+.5)/Sub;
        Vector point(x*40,-z*40,y*40);
        bool solid=POINT_CONTENTS(point)==CONTENTS_SOLID;
        for(unsigned i=0;!solid && i<brushCount;++i) {
            const auto& b=brushes[i];
            if(b.entity->free || b.entity->v.solid!=SOLID_BSP || point.x<b.min.x || point.x>b.max.x || point.y<b.min.y || point.y>b.max.y || point.z<b.min.z || point.z>b.max.z) continue;
            TraceResult trace{}; Vector end=point+Vector(.01f,0,0);
            g_engfuncs.pfnTraceModel(point,end,point_hull,b.entity,&trace);
            solid=trace.fStartSolid || trace.fAllSolid;
        }
        if(solid) { masks[cell]|=std::uint64_t(1)<<bit; ++out.occupied; }
    }
    if(cursor==Cells*64) { building=false; out.ready=1; ++out.generation; nextBuild=GetTickCount()+500; }
}
bool canEnable(edict_t* p) {
    halfcraft::Guest observation{};
    if(!hc::shared || !hc::readGuest(observation) || !observation.worldLoaded || observation.state!=halfcraft::CONNECTED) return false;
    return hc::connected && shared && p && !p->free && (p->v.flags&FL_CLIENT)
        && !(p->v.flags&(FL_DUCKING|FL_FROZEN|FL_ONTRAIN)) && !p->v.waterlevel
        && p->v.movetype==MOVETYPE_WALK && p->v.deadflag==DEAD_NO && p->v.health>0;
}
void enable(edict_t* p) {
    resumeRequested=false; enabled=true; ++out.epoch; out.ready=0; startBuild(p); out.generation=0;
    nativeControl=true; out.nativeControl=1;
    out.correction=0;
    ALERT(at_console,"HC_PHYSICS ON epoch=%u sampling=10units window=8blocks\n",out.epoch);
}
void nativeMode(edict_t* p,bool value) {
    if(nativeControl==value) return;
    if(value && puppeting) {
        if(p->v.movetype==MOVETYPE_NONE) p->v.movetype=savedMove;
        p->v.gravity=savedGravity; p->v.velocity=g_vecZero;
        puppeting=false; puppet=nullptr;
    }
    nativeControl=value; out.nativeControl=value;
    if(!value) { crouched=(p->v.flags&FL_DUCKING)!=0; ++out.epoch; out.correction=0; startBuild(p); }
    ALERT(at_console,"HC_PHYSICS nativeControl=%d origin=[%.3f %.3f %.3f]\n",value,p->v.origin.x,p->v.origin.y,p->v.origin.z);
}
void frame() {
    init(); if(!shared) return;
    edict_t* p=INDEXENT(1);
    if(resumeRequested && std::int32_t(GetTickCount()-resumeAfter)>=0 && canEnable(p)) enable(p);
    if(enabled && (!hc::connected || !p || p->free || p->v.deadflag!=DEAD_NO || p->v.health<=0
        || p->v.flags&FL_FROZEN)) suspend("native death/control or guest lost");
    if(enabled) {
        if(nativeControl) crouched=(p->v.flags&FL_DUCKING)!=0;
        if(!building && std::int32_t(GetTickCount()-nextBuild)>=0) startBuild(p);
        if(building) gather();
        out.feet[0]=p->v.origin.x/40.; out.feet[1]=(p->v.origin.z-originHeight())/40.; out.feet[2]=-p->v.origin.y/40.;
        out.yaw=-p->v.v_angle.y-90; out.pitch=p->v.v_angle.x;
        std::uint32_t pose=crouched?1:0; std::memcpy(out.padding,&pose,sizeof(pose));
    }
    publish();
}
void command(edict_t* p) {
    init();
    if(!std::strcmp(CMD_ARGV(1),"0")) { release("command"); publish(); return; }
    if(std::strcmp(CMD_ARGV(1),"1") || (!enabled && !canEnable(p))) { ALERT(at_console,"HC_PHYSICS refused: connected standing WALK player required; hc_physics 0|1\n"); return; }
    if(enabled) return;
    enable(p);
}
void pre(edict_t* p) {
    if(!enabled) return;
    if(nativeControl) return;
    if(p->v.deadflag!=DEAD_NO || p->v.health<=0 || p->v.flags&(FL_FROZEN|FL_ONTRAIN) || p->v.waterlevel
        || (puppeting && p->v.movetype!=MOVETYPE_NONE && p->v.movetype!=MOVETYPE_WALK)) { release("native player takeover"); publish(); return; }
    Guest guest{}; if(!read(guest)) return;
    if(!puppeting) { puppet=p; savedMove=p->v.movetype; savedGravity=p->v.gravity; lastOrigin=p->v.origin; puppeting=true; ALERT(at_console,"HC_PUPPET ACTIVE\n"); }
    p->v.movetype=MOVETYPE_NONE; p->v.gravity=0; p->v.velocity=g_vecZero;
}
void post(edict_t* p) {
    if(!enabled || !puppeting) return;
    Guest guest{}; if(!read(guest)) {
        halfcraft::Guest observation{};
        if(hc::connected && hc::readGuest(observation) && !observation.worldLoaded) suspend("Minecraft world loading");
        else release("stale physics guest");
        publish(); return;
    }
    if(guest.correctionAck!=out.correction) {
        if(std::uint32_t(GetTickCount()-correctionStarted)>3000) { release("collision correction acknowledgement timeout"); publish(); }
        return;
    }
    bool duck=(guest.reserved&1)!=0;
    if(duck!=crouched) {
        float shift=duck?-18.f:18.f;
        Vector origin=p->v.origin+Vector(0,0,shift);
        if(!duck) {
            TraceResult stand{}; UTIL_TraceHull(origin,origin,dont_ignore_monsters,human_hull,p,&stand);
            if(stand.fStartSolid || stand.fAllSolid) return;
        }
        crouched=duck;
        if(duck) p->v.flags|=FL_DUCKING; else p->v.flags&=~FL_DUCKING;
        UTIL_SetSize(&p->v,duck?VEC_DUCK_HULL_MIN:VEC_HULL_MIN,duck?Vector(16,16,42):VEC_HULL_MAX);
        UTIL_SetOrigin(&p->v,origin); lastOrigin=origin;
    }
    Vector target(guest.feet[0]*40,-guest.feet[2]*40,guest.feet[1]*40+originHeight());
    if(!std::isfinite(target.x)||!std::isfinite(target.y)||!std::isfinite(target.z) || (target-lastOrigin).Length()>80
        || !std::isfinite(guest.yaw) || !std::isfinite(guest.pitch) || std::fabs(guest.pitch)>90
        || (p->v.origin-lastOrigin).Length()>80 || p->v.flags&(FL_FROZEN|FL_ONTRAIN)) { release("native teleport or invalid movement"); return; }
    TraceResult trace{}; traceBody(p->v.origin,target,p,trace);
    bool stepCorrection=false;
    // Vanilla MC steps along an up/forward/down path, not a straight diagonal sweep.
    // Accept that path only within GoldSrc's own step height and with every hull leg clear.
    if(!trace.fStartSolid && !trace.fAllSolid && trace.flFraction<1 && (p->v.flags&FL_ONGROUND)) {
        float step=CVAR_GET_FLOAT("sv_stepsize");
        if(target.z>=p->v.origin.z-10 && target.z-p->v.origin.z<=step+.1f) {
            Vector up=p->v.origin+Vector(0,0,step),across(target.x,target.y,up.z);
            TraceResult rise{},forward{},down{};
            traceBody(p->v.origin,up,p,rise);
            traceBody(up,across,p,forward);
            traceBody(across,target,p,down);
            if(!rise.fStartSolid && !rise.fAllSolid && rise.flFraction==1
                && !forward.fStartSolid && !forward.fAllSolid && forward.flFraction==1
                && !down.fStartSolid && !down.fAllSolid && (down.flFraction==1 || down.vecPlaneNormal.z>=.7f)) {
                if(down.flFraction<1) { target=down.vecEndPos; stepCorrection=true; }
                trace.flFraction=1;
                ALERT(at_console,"HC_PHYSICS STEP height=%.3f limit=%.3f\n",target.z-p->v.origin.z,step);
            }
        }
    }
    if(trace.fStartSolid || trace.fAllSolid || trace.flFraction<1) {
        // Clip against native planes; retain jump/slide motion instead of rewinding every axis.
        Vector position=p->v.origin, remaining=target-position;
        for(int bump=0;bump<4;++bump) {
            TraceResult clip{};
            traceBody(position,position+remaining,p,clip);
            if(clip.fStartSolid || clip.fAllSolid) break;
            position=clip.vecEndPos;
            if(clip.flFraction==1) break;
            remaining=remaining*(1-clip.flFraction);
            remaining=remaining-clip.vecPlaneNormal*DotProduct(remaining,clip.vecPlaneNormal);
            if(remaining.Length()<.001f) break;
        }
        target=position;
        stepCorrection=true;
        if(std::int32_t(GetTickCount()-nextCorrectionLog)>=0) {
            nextCorrectionLog=GetTickCount()+2000;
            ALERT(at_console,"HC_PHYSICS BLOCKED correction=%u fraction=%.3f startSolid=%d active=1 from=[%.3f %.3f %.3f] guest=[%.3f %.3f %.3f] normal=[%.3f %.3f %.3f] hit=%s\n",out.correction+1,trace.flFraction,trace.fStartSolid,
                p->v.origin.x,p->v.origin.y,p->v.origin.z,guest.feet[0]*40,-guest.feet[2]*40,guest.feet[1]*40+36.03125,
                trace.vecPlaneNormal.x,trace.vecPlaneNormal.y,trace.vecPlaneNormal.z,trace.pHit?STRING(trace.pHit->v.classname):"none");
        }
    }
    if(stepCorrection) {
        ++out.correction; correctionStarted=GetTickCount();
        out.feet[0]=target.x/40.; out.feet[1]=(target.z-originHeight())/40.; out.feet[2]=-target.y/40.;
        publish();
    }
    UTIL_SetOrigin(&p->v,target); lastOrigin=target;
    p->v.v_angle.x=guest.pitch; p->v.v_angle.y=-guest.yaw-90; p->v.angles.y=p->v.v_angle.y;
    // HL owns mouse look while forwarding; server fixangle would feed delayed MC angles back into mouse input.
    if(!input) {
        inputHandle=OpenFileMappingW(FILE_MAP_READ,FALSE,halfcraft_input::Mapping);
        if(inputHandle) input=static_cast<halfcraft_input::Shared*>(MapViewOfFile(inputHandle,FILE_MAP_READ,0,0,halfcraft_input::Bytes));
        if(!input && inputHandle) { CloseHandle(inputHandle); inputHandle=nullptr; }
    }
    bool hostLook=input && input->magic==halfcraft_input::Magic && input->pid==GetCurrentProcessId()
        && input->session==out.session && input->requested && GetTickCount()-input->heartbeat<1000;
    p->v.fixangle=hostLook?0:1;
    p->v.velocity=g_vecZero;
    halfcraft::Guest observation{};
    if(hc::readGuest(observation)) {
        if(observation.onGround) p->v.flags|=FL_ONGROUND; else p->v.flags&=~FL_ONGROUND;
    }
    // MOVETYPE_NONE skips engine's walk-trigger pass; preserve native trigger Touch callbacks.
    for(int i=1;i<gpGlobals->maxEntities && enabled;++i) {
        edict_t* e=INDEXENT(i); if(!e || e->free || e->v.solid!=SOLID_TRIGGER) continue;
        if(e->v.absmin.x>p->v.absmax.x || e->v.absmax.x<p->v.absmin.x || e->v.absmin.y>p->v.absmax.y
            || e->v.absmax.y<p->v.absmin.y || e->v.absmin.z>p->v.absmax.z || e->v.absmax.z<p->v.absmin.z) continue;
        DispatchTouch(e,p);
        if((p->v.origin-target).Length()>1 || p->v.deadflag!=DEAD_NO || p->v.flags&(FL_FROZEN|FL_ONTRAIN)) release("native trigger takeover");
    }
}
}
