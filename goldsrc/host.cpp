// Observation-only GoldSrc bridge. MIT adapter; SDK translation unit retains Valve SDK license.
#include "../protocol/halfcraft_protocol.h"
#include <atomic>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <thread>
#include <mutex>
#include "sdk/dlls/monsters.h"

namespace hc {
using namespace halfcraft;
Shared* shared = nullptr;
HANDLE mapping = nullptr, owner = nullptr;
std::atomic<bool> running{false};
std::thread worker;
std::mutex lock;
Host latest{};
bool connected = false;
std::uint32_t guestPid = 0;

std::uint32_t load(std::uint32_t& value) { return InterlockedCompareExchange(reinterpret_cast<LONG*>(&value),0,0); }
void store(std::uint32_t& value,std::uint32_t v) { InterlockedExchange(reinterpret_cast<LONG*>(&value),v); }
bool alive(std::uint32_t pid) {
    if (!pid) return false;
    HANDLE p = OpenProcess(SYNCHRONIZE,FALSE,pid);
    if (!p) return false;
    bool ok = WaitForSingleObject(p,0)==WAIT_TIMEOUT;
    CloseHandle(p); return ok;
}
bool readGuest(Guest& out) {
    static Guest cached{};
    for (int i=0;i<16;++i) {
        auto seq=load(shared->guest.seq);
        if (!seq || (seq&1)) continue;
        std::memcpy(&out,&shared->guest,sizeof(out));
        MemoryBarrier();
        if (load(shared->guest.seq)==seq) { cached=out; return true; }
    }
    out=cached;
    return out.pid!=0;
}
void publish() {
    std::lock_guard<std::mutex> guard(lock);
    Host snapshot=latest;
    snapshot.heartbeat=GetTickCount();
    auto seq=(load(shared->host.seq)+1)&~1u;
    store(shared->host.seq,seq+1);
    std::memcpy(reinterpret_cast<char*>(&shared->host)+4,reinterpret_cast<char*>(&snapshot)+4,sizeof(Host)-4);
    store(shared->host.seq,seq+2);
}
void shutdown() {
    if (!shared) return;
    running=false;
    if (worker.joinable()) worker.join();
    { std::lock_guard<std::mutex> guard(lock); latest.state=SHUTDOWN; }
    publish();
    UnmapViewOfFile(shared); shared=nullptr;
    CloseHandle(mapping); mapping=nullptr;
    CloseHandle(owner); owner=nullptr;
}
struct Cleanup { ~Cleanup() { shutdown(); } } cleanup;
void init() {
    if (shared) return;
    owner=CreateMutexW(nullptr,FALSE,L"Local\\HalfCraft_v1_host_owner");
    if (!owner || GetLastError()==ERROR_ALREADY_EXISTS) {
        if(owner) CloseHandle(owner); owner=nullptr;
        ALERT(at_console,"HALFCRAFT HOST ERROR: another host owns mapping\n"); return;
    }
    mapping=CreateFileMappingW(INVALID_HANDLE_VALUE,nullptr,PAGE_READWRITE,0,Bytes,Mapping);
    bool existed=GetLastError()==ERROR_ALREADY_EXISTS;
    shared=mapping ? static_cast<Shared*>(MapViewOfFile(mapping,FILE_MAP_ALL_ACCESS,0,0,Bytes)) : nullptr;
    if (!shared) {
        ALERT(at_console,"HALFCRAFT HOST ERROR: mapping error %lu\n",GetLastError());
        if(mapping) CloseHandle(mapping); mapping=nullptr; CloseHandle(owner); owner=nullptr; return;
    }
    if (existed && (load(shared->header.magic)!=Magic || shared->header.version!=Version || shared->header.bytes!=Bytes)) {
        ALERT(at_console,"HALFCRAFT HOST ERROR: incompatible protocol\n"); shutdown(); return;
    }
    if (!existed) std::memset(shared,0,Bytes);
    shared->header.version=Version; shared->header.bytes=Bytes;
    latest={}; latest.pid=GetCurrentProcessId(); latest.session=GetTickCount() ^ latest.pid;
    if (!latest.session) latest.session=1;
    latest.started=GetTickCount(); latest.state=HOST_READY;
    publish(); store(shared->header.magic,Magic);
    running=true;
    worker=std::thread([] { while(running) { publish(); std::this_thread::sleep_for(std::chrono::milliseconds(100)); } });
    ALERT(at_console,"HALFCRAFT HOST INITIALIZED pid=%lu protocol=1\nHALFCRAFT WAITING FOR MINECRAFT\n",GetCurrentProcessId());
}
void frame() {
    if (!shared) return;
    Guest guest{}; bool got=readGuest(guest);
    auto now=GetTickCount();
    bool live=got && guest.hostSession==latest.session && guest.state!=SHUTDOWN && guest.state!=LINK_ERROR
        && guest.heartbeat && std::uint32_t(now-guest.heartbeat)<Timeout && alive(guest.pid);
    if (live && (!connected || guest.pid!=guestPid)) {
        ALERT(at_console,"HALFCRAFT GUEST CONNECTED pid=%u\nHALFCRAFT BRIDGE CONNECTED\n",guest.pid); guestPid=guest.pid;
    }
    if (!live && connected) ALERT(at_console,"HALFCRAFT GUEST LOST\n");
    connected=live;
    {
        std::lock_guard<std::mutex> guard(lock);
        latest.state=live ? CONNECTED : HOST_READY;
        const char* map=STRING(gpGlobals->mapname);
        strncpy_s(latest.map,map ? map : "",_TRUNCATE);
        edict_t* player=INDEXENT(1);
        latest.playerPresent=player && !player->free && (player->v.flags&FL_CLIENT);
        if (latest.playerPresent) for (int i=0;i<3;++i) { latest.origin[i]=player->v.origin[i]; latest.angles[i]=player->v.v_angle[i]; }
    }
    static DWORD next=0;
    if (live && std::int32_t(now-next)>=0) {
        next=now+5000;
        ALERT(at_console,"HC host=(%.3f,%.3f,%.3f) map=%s guest=(%.3f,%.3f,%.3f) velocity=(%.3f,%.3f,%.3f) world=%u onGround=%u\n",
            latest.origin[0],latest.origin[1],latest.origin[2],latest.map,guest.position[0],guest.position[1],guest.position[2],
            guest.velocity[0],guest.velocity[1],guest.velocity[2],guest.worldLoaded,guest.onGround);
    }
}
void regression() {
    static Vector positions[4096]; static float frames[4096]; static int sequences[4096],types[4096];
    static bool scripted[4096],baseline=false;
    int npcs=0,scripts=0,doors=0,npcChanges=0,doorChanges=0,scriptedChanges=0;
    for(int i=0;i<gpGlobals->maxEntities && i<4096;++i) {
        edict_t* e=INDEXENT(i); if(!e || e->free) continue;
        const char* name=STRING(e->v.classname); if(!name) continue;
        int type=0;
        if(!std::strncmp(name,"monster_",8)) { ++npcs; type=1; }
        if(!std::strncmp(name,"scripted_",9)) ++scripts;
        if(!std::strncmp(name,"func_door",9)) { ++doors; type=2; }
        bool changed=(positions[i]-e->v.origin).Length()>0.01f || std::fabs(frames[i]-e->v.frame)>0.01f || sequences[i]!=e->v.sequence;
        if(baseline && types[i]==type && changed) { if(type==1) ++npcChanges; if(type==2) ++doorChanges; if(scripted[i]) ++scriptedChanges; }
        CBaseEntity* entity=CBaseEntity::Instance(e);
        CBaseMonster* monster=entity ? entity->MyMonsterPointer() : nullptr;
        scripted[i]=monster && monster->m_pCine;
        positions[i]=e->v.origin; frames[i]=e->v.frame; sequences[i]=e->v.sequence; types[i]=type;
    }
    ALERT(at_console,"HC_REGRESSION map=%s npcs=%d scripts=%d doors=%d npc_changes=%d door_changes=%d scripted_npc_changes=%d baseline=%d\n",STRING(gpGlobals->mapname),npcs,scripts,doors,npcChanges,doorChanges,scriptedChanges,baseline?1:0);
    baseline=true;
}
}

static cvar_t hc_autostart={"hc_autostart","0",0};
void HCGameDLLInit() {
    GameDLLInit(); CVAR_REGISTER(&hc_autostart);
    if(g_engfuncs.pfnCheckParm && g_engfuncs.pfnCheckParm("-halfcraft-play",nullptr)) CVAR_SET_FLOAT("hc_autostart",1);
    hc::init();
}
void HCStartFrame() { StartFrame(); hc::frame(); }
void HCServerActivate(edict_t* edicts,int count,int clients) { ServerActivate(edicts,count,clients); hc::init(); hc::frame(); }
void HCServerDeactivate() { ServerDeactivate(); if(hc::shared) { std::lock_guard<std::mutex> guard(hc::lock); hc::latest.playerPresent=0; hc::latest.map[0]=0; } }
void HCClientCommand(edict_t* player) {
    if(!std::strcmp(CMD_ARGV(0),"hc_bridge_check")) { hc::frame(); hc::regression(); return; }
    ClientCommand(player);
}
