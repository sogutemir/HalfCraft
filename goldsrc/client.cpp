#include "../protocol/halfcraft_protocol.h"
#include "../protocol/halfcraft_collision.h"
#include "../protocol/halfcraft_input.h"
#include "../protocol/halfcraft_render.h"
#include "../protocol/halfcraft_ui.h"
#include "../protocol/halfcraft_scene.h"
#include <vector>
#include <map>
#include <tuple>
#include <cmath>
#include <algorithm>
#include <chrono>
#include "sdk/cl_dll/hud.h"
#include "sdk/cl_dll/cl_util.h"
#include "sdk/common/ref_params.h"
#include "sdk/common/usercmd.h"
#include "sdk/common/in_buttons.h"
#include "client.h"
#include "winsani_in.h"
#include <windows.h>
#include "winsani_out.h"
#include <GL/gl.h>

namespace {
bool probe=false, anchored=false;
float anchor[3];
HANDLE inputHandle=nullptr,linkHandle=nullptr,physicsHandle=nullptr;
halfcraft_input::Shared* input=nullptr;
halfcraft::Shared* link=nullptr;
halfcraft_collision::Shared* physics=nullptr;
bool requested=false;
bool mouseActive=false;
bool guestAttack=false,nativeAttack=false;
bool viewModelHidden=false;
float savedViewModel=1;
void hideViewModel(bool hidden) {
    if(hidden==viewModelHidden) return;
    if(hidden) savedViewModel=gEngfuncs.pfnGetCvarFloat("r_drawviewmodel");
    char name[]="r_drawviewmodel"; gEngfuncs.Cvar_SetValue(name,hidden?0:savedViewModel);
    viewModelHidden=hidden;
}
cvar_t* background=nullptr;
DWORD lastMove=0;
halfcraft_collision::Guest cameraSample{};
float cameraFrom[3]{},cameraTo[3]{};
DWORD cameraTime=0;
bool cameraReady=false;
HANDLE sceneHandle=nullptr;
halfcraft_scene::Shared* scene=nullptr;
struct SceneBatch { unsigned texture,first,count,flags; };
std::map<unsigned,GLuint> sceneTextures;
std::vector<halfcraft_render::Vertex> sceneVertices;
std::vector<SceneBatch> sceneBatches;
double sceneOrigin[3]{};
DWORD sceneTime=0;
void clearScene() {
    if(wglGetCurrentContext()) for(auto& entry:sceneTextures) glDeleteTextures(1,&entry.second);
    sceneTextures.clear(); sceneVertices.clear(); sceneBatches.clear(); sceneTime=0;
}
HANDLE uiHandle=nullptr;
halfcraft_ui::Shared* ui=nullptr;
GLuint uiTexture=0;
unsigned uiWidth=0,uiHeight=0,uiFrameSession=0,uiFrameEpoch=0;
DWORD uiFrameTime=0;
bool screenOpen=false;
int cursorX=0,cursorY=0;
HWND gameWindow=nullptr;
WNDPROC previousWindowProc=nullptr;
bool modifierDown[3]{};
wchar_t pendingSurrogate=0;
HANDLE renderHandle=nullptr;
halfcraft_render::Shared* render=nullptr;
GLuint texture=0;
unsigned renderGeneration=0,renderSession=0,renderEpoch=0,renderReset=0;
using Section=std::tuple<int,int,int>;
std::map<Section,std::vector<halfcraft_render::Vertex>> sections;
void clearRender() { sections.clear(); renderGeneration=0; if(texture && wglGetCurrentContext()) glDeleteTextures(1,&texture); texture=0; }
bool foreground() { DWORD pid=0; GetWindowThreadProcessId(GetForegroundWindow(),&pid); return pid==GetCurrentProcessId(); }
unsigned load(const std::uint32_t& value) { unsigned result=*reinterpret_cast<const volatile std::uint32_t*>(&value); MemoryBarrier(); return result; }
void store(std::uint32_t& value,unsigned n) { InterlockedExchange(reinterpret_cast<volatile LONG*>(&value),n); }
template<class T> T* open(const wchar_t* name,HANDLE& handle,unsigned size) {
    handle=OpenFileMappingW(FILE_MAP_READ,FALSE,name);
    T* result=handle ? static_cast<T*>(MapViewOfFile(handle,FILE_MAP_READ,0,0,size)) : nullptr;
    if(!result && handle) { CloseHandle(handle); handle=nullptr; }
    return result;
}
void inputCommand() {
    requested=gEngfuncs.Cmd_Argc()>1 && atoi(gEngfuncs.Cmd_Argv(1))!=0;
    if(input) {
        auto seq=(load(input->seq)+1)&~1u; store(input->seq,seq+1);
        ++input->reset; input->enabled=0; input->requested=requested;
        store(input->seq,seq+2);
    }
    gEngfuncs.Con_Printf("HC_INPUT requested=%d\n",requested);
}
bool live() {
    if(!input || !link || !physics) return false;
    auto& host=link->host; auto& guest=link->guest; auto& ph=physics->host;
    return requested && mouseActive && foreground() && !gEngfuncs.Con_IsVisible() && host.playerPresent
        && host.pid==GetCurrentProcessId() && host.state==halfcraft::CONNECTED
        && GetTickCount()-host.heartbeat<1000 && guest.hostSession==host.session && guest.worldLoaded
        && GetTickCount()-guest.heartbeat<1000 && ph.session==host.session && ph.enabled
        && input->ready && input->guestPid==guest.pid && input->guestSession==host.session
        && GetTickCount()-load(input->guestHeartbeat)<1000;
}
void publish(bool enabled,const float* angles=nullptr) {
    if(!input) return;
    auto seq=(load(input->seq)+1)&~1u; store(input->seq,seq+1);
    unsigned session=link?link->host.session:0,epoch=physics?physics->host.epoch:0;
    if(input->session!=session || input->epoch!=epoch || (input->enabled && !enabled)) ++input->reset;
    input->session=session; input->epoch=epoch; input->heartbeat=GetTickCount(); input->enabled=enabled;
    input->requested=requested;
    *reinterpret_cast<std::uint32_t*>(input->padding)=background && background->value!=0;
    if(angles) { input->yaw=-angles[1]-90; input->pitch=angles[0]; }
    store(input->seq,seq+2);
}
bool physicsLive() {
    return link && physics && link->header.magic==halfcraft::Magic && physics->host.magic==halfcraft_collision::Magic
        && link->host.pid==GetCurrentProcessId() && link->host.state==halfcraft::CONNECTED
        && physics->host.session==link->host.session && physics->host.enabled
        && GetTickCount()-link->guest.heartbeat<1000;
}
bool blockTarget() {
    if(!input || !live()) return false;
    auto* bytes=reinterpret_cast<char*>(input);
    auto* sequence=reinterpret_cast<std::uint32_t*>(bytes+124);
    unsigned seq=load(*sequence),target=*reinterpret_cast<std::uint32_t*>(bytes+108);
    MemoryBarrier(); return seq && !(seq&1) && seq==load(*sequence) && target>=2;
}
void publishRender() {
    if(!render) return;
    unsigned session=link?link->host.session:0,epoch=physics?physics->host.epoch:0;
    if(session!=renderSession || epoch!=renderEpoch) { clearRender(); clearScene(); ++renderReset; renderSession=session; renderEpoch=epoch; }
    auto seq=(load(render->seq)+1)&~1u; store(render->seq,seq+1);
    render->session=session; render->heartbeat=GetTickCount(); render->enabled=physicsLive(); render->reset=renderReset; render->epoch=epoch;
    store(render->seq,seq+2);
    if(scene) {
        unsigned next=(load(scene->seq)+1)&~1u; store(scene->seq,next+1);
        scene->session=session; scene->epoch=epoch; scene->heartbeat=GetTickCount(); scene->enabled=physicsLive(); store(scene->seq,next+2);
    }
}
void receiveScene() {
    if(!scene || !physicsLive()) return;
    unsigned seq=load(scene->messageSeq); if(!seq || seq&1 || seq==load(scene->ack)) return;
    unsigned h=scene->headerBytes,b=scene->bodyBytes,type=scene->messageType;
    bool valid=std::uint64_t(h)+b<=halfcraft_scene::Capacity && scene->messageSession==renderSession && scene->messageEpoch==renderEpoch && GetTickCount()-scene->messageHeartbeat<1000;
    if(valid && type==4 && h==16) {
        unsigned header[4]; memcpy(header,scene->payload,16);
        unsigned id=header[0],w=header[1],height=header[2]; GLint maxSize=0; glGetIntegerv(GL_MAX_TEXTURE_SIZE,&maxSize);
        valid=id && id<=256 && w && height && w<=unsigned(maxSize) && height<=unsigned(maxSize) && std::uint64_t(w)*height*4==b;
        if(valid) {
            GLuint texture=0; glGenTextures(1,&texture); glBindTexture(GL_TEXTURE_2D,texture);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST); glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP); glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP); glPixelStorei(GL_UNPACK_ALIGNMENT,1);
            while(glGetError()!=GL_NO_ERROR) {}
            glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,w,height,0,GL_RGBA,GL_UNSIGNED_BYTE,scene->payload+h);
            if(glGetError()==GL_NO_ERROR) { if(sceneTextures.count(id)) glDeleteTextures(1,&sceneTextures[id]); sceneTextures[id]=texture; }
            else { glDeleteTextures(1,&texture); valid=false; }
        }
    } else if(valid && type==6 && h>=32) {
        double origin[3]; unsigned counts[2]; memcpy(origin,scene->payload,24); memcpy(counts,scene->payload+24,8);
        valid=counts[0]<=256 && std::uint64_t(counts[0])*16+32==h && std::uint64_t(counts[1])*32==b && counts[1]%3==0;
        for(double value:origin) if(!std::isfinite(value)||fabs(value)>32768) valid=false;
        std::vector<SceneBatch> batches; std::vector<halfcraft_render::Vertex> vertices;
        if(valid) {
            batches.resize(counts[0]); vertices.resize(counts[1]);
            if(counts[0]) memcpy(batches.data(),scene->payload+32,counts[0]*16);
            if(counts[1]) memcpy(vertices.data(),scene->payload+h,b);
            for(auto& batch:batches) if(batch.texture>256 || batch.first>counts[1] || batch.count>counts[1]-batch.first || batch.count%3 || batch.flags>1) valid=false;
            for(auto& v:vertices) if(!std::isfinite(v.x)||!std::isfinite(v.y)||!std::isfinite(v.z)||!std::isfinite(v.u)||!std::isfinite(v.v)
                || fabs(v.x)>128 || fabs(v.y)>128 || fabs(v.z)>128 || fabs(v.u)>16 || fabs(v.v)>16) valid=false;
        }
        if(valid) { sceneBatches=std::move(batches); sceneVertices=std::move(vertices); memcpy(sceneOrigin,origin,24); sceneTime=GetTickCount(); }
    } else valid=false;
    if(!valid) gEngfuncs.Con_Printf("HC_SCENE rejected type=%u header=%u body=%u\n",type,h,b);
    store(scene->ack,seq);
}
void receiveRender() {
    if(!render || !physicsLive()) { if(!sections.empty()) clearRender(); return; }
    unsigned seq=load(render->atlasSeq);
    if(seq && !(seq&1) && seq!=load(render->atlasAck)) {
        GLint maxSize=0; glGetIntegerv(GL_MAX_TEXTURE_SIZE,&maxSize);
        auto w=render->width,h=render->height;
        if(render->atlasSession==renderSession && w && h && w<=unsigned(maxSize) && h<=unsigned(maxSize)
            && std::uint64_t(w)*h*4==render->atlasBytes && render->atlasBytes<=halfcraft_render::AtlasCapacity) {
            GLuint next=0; glGenTextures(1,&next); glBindTexture(GL_TEXTURE_2D,next);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST); glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP); glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP);
            glPixelStorei(GL_UNPACK_ALIGNMENT,1);
            while(glGetError()!=GL_NO_ERROR) {}
            glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,w,h,0,GL_RGBA,GL_UNSIGNED_BYTE,render->atlas);
            if(glGetError()==GL_NO_ERROR) { clearRender(); texture=next; renderGeneration=render->generation; gEngfuncs.Con_Printf("HC_RENDER atlas=%ux%u generation=%u\n",w,h,renderGeneration); }
            else { glDeleteTextures(1,&next); gEngfuncs.Con_Printf("HC_RENDER atlas upload failed\n"); }
        } else gEngfuncs.Con_Printf("HC_RENDER atlas rejected\n");
        store(render->atlasAck,seq);
    }
    seq=load(render->meshSeq);
    if(seq && !(seq&1) && seq!=load(render->meshAck)) {
        auto count=render->count;
        bool valid=render->meshSession==renderSession && render->meshGeneration==renderGeneration && texture
            && count%3==0 && std::uint64_t(count)*sizeof(halfcraft_render::Vertex)==render->meshBytes
            && render->meshBytes<=halfcraft_render::MeshCapacity
            && render->sx>=-2048 && render->sx<=2048 && render->sy>=-2048 && render->sy<=2048 && render->sz>=-2048 && render->sz<=2048;
        std::vector<halfcraft_render::Vertex> vertices;
        if(valid) {
            auto* data=reinterpret_cast<const halfcraft_render::Vertex*>(render->mesh);
            vertices.assign(data,data+count);
            for(auto& v:vertices) if(!std::isfinite(v.x)||!std::isfinite(v.y)||!std::isfinite(v.z)||!std::isfinite(v.u)||!std::isfinite(v.v)
                || v.x< -1 || v.x>17 || v.y< -1 || v.y>17 || v.z< -1 || v.z>17 || v.u<0 || v.u>1 || v.v<0 || v.v>1 || (v.flags&2)) { valid=false; break; }
        }
        if(valid) {
            Section key{render->sx,render->sy,render->sz};
            if(count && (sections.count(key) || sections.size()<27)) sections[key]=std::move(vertices);
            else if(!count) sections.erase(key);
            gEngfuncs.Con_Printf("HC_RENDER section=%d %d %d count=%u stored=%u\n",render->sx,render->sy,render->sz,count,unsigned(sections.size()));
        } else gEngfuncs.Con_Printf("HC_RENDER mesh rejected count=%u\n",count);
        store(render->meshAck,seq);
    }
}
void event(unsigned type,unsigned code,int value) {
    if(!input || (!live() && !(type==halfcraft_input::Button && value==0))) return;
    auto write=load(input->write),read=load(input->read);
    if(write-read>=halfcraft_input::Capacity) { ++input->reset; publish(false); return; }
    input->events[write%halfcraft_input::Capacity]={type,code,value,GetTickCount()};
    MemoryBarrier(); store(input->write,write+1);
}
LRESULT CALLBACK windowProc(HWND window,UINT message,WPARAM w,LPARAM l) {
    if(screenOpen && live()) {
        if((message==WM_KEYDOWN || message==WM_KEYUP) && w==VK_ESCAPE) { event(halfcraft_input::Key,41,message==WM_KEYDOWN); return 0; }
        if(message==WM_CHAR) {
            unsigned cp=unsigned(w);
            if(cp>=0xD800 && cp<=0xDBFF) { pendingSurrogate=wchar_t(cp); return 0; }
            if(cp>=0xDC00 && cp<=0xDFFF && pendingSurrogate) cp=0x10000+((pendingSurrogate-0xD800)<<10)+(cp-0xDC00);
            pendingSurrogate=0;
            if(cp>=32 && !(cp>=0xD800 && cp<=0xDFFF)) event(halfcraft_input::Text,0,cp);
            return 0;
        }
    }
    return CallWindowProc(previousWindowProc,window,message,w,l);
}
void pollModifiers() {
    const int keys[]={VK_SHIFT,VK_CONTROL,VK_MENU},scancodes[]={225,224,226};
    for(int i=0;i<3;i++) {
        bool down=screenOpen && live() && (GetAsyncKeyState(keys[i])&0x8000);
        if(down!=modifierDown[i]) { modifierDown[i]=down; event(halfcraft_input::Key,scancodes[i],down); }
    }
}
void publishUI() {
    if(!ui) return;
    unsigned seq=(load(ui->seq)+1)&~1u; store(ui->seq,seq+1);
    ui->session=link?link->host.session:0; ui->epoch=physics?physics->host.epoch:0;
    ui->heartbeat=GetTickCount(); ui->enabled=requested && physicsLive();
    ui->width=(std::min)(unsigned(gHUD.m_scrinfo.iWidth),halfcraft_ui::MaxWidth);
    ui->height=(std::min)(unsigned(gHUD.m_scrinfo.iHeight),halfcraft_ui::MaxHeight); store(ui->seq,seq+2);
    ui->padding[0]=cursorX; ui->padding[1]=cursorY;
}
void receiveScreen() {
    bool next=false;
    if(ui && requested && physicsLive()) {
        unsigned seq=load(ui->stateSeq);
        unsigned pid=ui->guestPid,s=ui->guestSession,e=ui->guestEpoch,t=ui->guestHeartbeat,screen=ui->screen;
        MemoryBarrier();
        next=seq && !(seq&1) && seq==load(ui->stateSeq) && pid==link->guest.pid && s==ui->session && e==ui->epoch && GetTickCount()-t<500 && screen==1;
    }
    if(next!=screenOpen) {
        screenOpen=next;
        if(next) { cursorX=ui->width/2; cursorY=ui->height/2; event(halfcraft_input::Cursor,cursorX,cursorY); }
        else { pendingSurrogate=0; uiFrameTime=0; }
        gEngfuncs.Con_Printf("HC_UI screen=%d\n",next);
    }
}
int menuScancode(int key) {
    if(key>='a' && key<='z') return key-'a'+4;
    if(key>='1' && key<='9') return key-'1'+30;
    if(key=='0') return 39;
    if(key>=135 && key<=146) return key-135+58;
    switch(key) {
        case 9:return 43; case 13:return 40; case 27:return 41; case 32:return 44; case 127:return 42;
        case 128:return 82; case 129:return 81; case 130:return 80; case 131:return 79;
        case 132:return 226; case 133:return 224; case 134:return 225;
        case 147:return 73; case 148:return 76; case 149:return 78; case 150:return 75; case 151:return 74; case 152:return 77;
        case '-':return 45; case '=':return 46; case '[':return 47; case ']':return 48; case '\\':return 49;
        case ';':return 51; case '\'':return 52; case ',':return 54; case '.':return 55; case '/':return 56;
        default:return 0;
    }
}
int scancode(int key,const char* binding) {
    if(key=='w' || key=='a' || key=='s' || key=='d') return key-'a'+4;
    if(key>='1' && key<='9') return key-'1'+30;
    if(key=='0') return 39;
    if(key==32) return 44;
    if(binding && !strcmp(binding,"+forward")) return 26;
    if(binding && !strcmp(binding,"+back")) return 22;
    if(binding && !strcmp(binding,"+moveleft")) return 4;
    if(binding && !strcmp(binding,"+moveright")) return 7;
    if(binding && !strcmp(binding,"+jump")) return 44;
    if(binding && !strcmp(binding,"+duck")) return 225;
    return 0;
}
void probeCommand() {
    probe=gEngfuncs.Cmd_Argc()>1 && atoi(gEngfuncs.Cmd_Argv(1))!=0;
    anchored=false;
    if(probe && gEngfuncs.Cmd_Argc()==5) {
        bool valid=true;
        for(int i=0;i<3;i++) { char* end=nullptr; anchor[i]=strtof(gEngfuncs.Cmd_Argv(i+2),&end); if(!end || *end || !std::isfinite(anchor[i]) || fabs(anchor[i])>32768) valid=false; }
        anchored=valid; probe=valid;
    }
    gEngfuncs.Con_Printf("HC_CLIENT probe=%d\n",probe);
}
void lookCommand() {
    if(gEngfuncs.Cmd_Argc()!=3) { gEngfuncs.Con_Printf("hc_look pitch yaw\n"); return; }
    char* end=nullptr; float angles[3]{};
    angles[0]=strtof(gEngfuncs.Cmd_Argv(1),&end); if(!end || *end || !std::isfinite(angles[0]) || fabs(angles[0])>89) return;
    angles[1]=strtof(gEngfuncs.Cmd_Argv(2),&end); if(!end || *end || !std::isfinite(angles[1]) || fabs(angles[1])>360) return;
    gEngfuncs.SetViewAngles(angles);
}
void cursorCommand() {
    if(!ui || gEngfuncs.Cmd_Argc()!=3) return;
    char* end=nullptr; long x=strtol(gEngfuncs.Cmd_Argv(1),&end,10); if(!end || *end || x<0 || x>=long(ui->width)) return;
    long y=strtol(gEngfuncs.Cmd_Argv(2),&end,10); if(!end || *end || y<0 || y>=long(ui->height)) return;
    cursorX=int(x); cursorY=int(y);
}
}
void HCClientInit() {
    requested=true;
    gEngfuncs.pfnAddCommand("hc_render_probe",probeCommand); gEngfuncs.pfnAddCommand("hc_input",inputCommand);
    gEngfuncs.pfnAddCommand("hc_look",lookCommand);
    gEngfuncs.pfnAddCommand("hc_ui_cursor",cursorCommand);
    background=gEngfuncs.pfnRegisterVariable("hc_background","1",0);
    gEngfuncs.Cvar_SetValue("hc_background",1);
    if(!input) {
        inputHandle=CreateFileMappingW(INVALID_HANDLE_VALUE,nullptr,PAGE_READWRITE,0,halfcraft_input::Bytes,halfcraft_input::Mapping);
        input=inputHandle?static_cast<halfcraft_input::Shared*>(MapViewOfFile(inputHandle,FILE_MAP_ALL_ACCESS,0,0,halfcraft_input::Bytes)):nullptr;
        if(input) { memset(input,0,halfcraft_input::Bytes); input->magic=halfcraft_input::Magic; input->version=halfcraft_input::Version; input->bytes=halfcraft_input::Bytes; input->pid=GetCurrentProcessId(); }
    }
    if(!ui) {
        uiHandle=CreateFileMappingW(INVALID_HANDLE_VALUE,nullptr,PAGE_READWRITE,0,halfcraft_ui::Bytes,halfcraft_ui::Mapping);
        ui=uiHandle?static_cast<halfcraft_ui::Shared*>(MapViewOfFile(uiHandle,FILE_MAP_ALL_ACCESS,0,0,halfcraft_ui::Bytes)):nullptr;
        if(ui) { memset(ui,0,halfcraft_ui::Bytes); ui->magic=halfcraft_ui::Magic; ui->version=halfcraft_ui::Version; ui->bytes=halfcraft_ui::Bytes; ui->pid=GetCurrentProcessId(); }
    }
    if(!scene) {
        sceneHandle=CreateFileMappingW(INVALID_HANDLE_VALUE,nullptr,PAGE_READWRITE,0,halfcraft_scene::Bytes,halfcraft_scene::Mapping);
        scene=sceneHandle?static_cast<halfcraft_scene::Shared*>(MapViewOfFile(sceneHandle,FILE_MAP_ALL_ACCESS,0,0,halfcraft_scene::Bytes)):nullptr;
        if(scene) { memset(scene,0,halfcraft_scene::Bytes); scene->magic=halfcraft_scene::Magic; scene->version=halfcraft_scene::Version; scene->bytes=halfcraft_scene::Bytes; scene->pid=GetCurrentProcessId(); }
    }
    if(!render) {
        renderHandle=CreateFileMappingW(INVALID_HANDLE_VALUE,nullptr,PAGE_READWRITE,0,halfcraft_render::Bytes,halfcraft_render::Mapping);
        render=renderHandle?static_cast<halfcraft_render::Shared*>(MapViewOfFile(renderHandle,FILE_MAP_ALL_ACCESS,0,0,halfcraft_render::Bytes)):nullptr;
        if(render) { memset(render,0,halfcraft_render::Bytes); render->magic=halfcraft_render::Magic; render->version=halfcraft_render::Version; render->bytes=halfcraft_render::Bytes; render->pid=GetCurrentProcessId(); }
    }
    gEngfuncs.Con_Printf("HC_CLIENT loaded build=2 input=%d\n",input!=nullptr);
}
void HCClientReset() { anchored=false; cameraReady=false; guestAttack=nativeAttack=false; clearRender(); ++renderReset; if(input) { ++input->reset; publish(false); } }
void HCClientFrame() {
    if(!gameWindow && foreground()) {
        gameWindow=GetForegroundWindow();
        previousWindowProc=reinterpret_cast<WNDPROC>(SetWindowLongPtr(gameWindow,GWLP_WNDPROC,reinterpret_cast<LONG_PTR>(windowProc)));
        if(!previousWindowProc) gameWindow=nullptr;
    }
    if(!link) link=open<halfcraft::Shared>(halfcraft::Mapping,linkHandle,halfcraft::Bytes);
    if(!physics) physics=open<halfcraft_collision::Shared>(halfcraft_collision::Mapping,physicsHandle,halfcraft_collision::Bytes);
    hideViewModel(requested && physicsLive());
    // Key-up can be lost when console/focus changes or automation stops mid-hold.
    if(guestAttack && (!(GetAsyncKeyState(VK_LBUTTON)&0x8000) || !live())) {
        guestAttack=false; event(halfcraft_input::Button,1,0);
    }
    publish(live() && GetTickCount()-lastMove<200);
    publishRender();
    publishUI(); receiveScreen();
    pollModifiers();
    if(screenOpen && live()) event(halfcraft_input::Cursor,cursorX,cursorY);
}
bool HCClientScreen() { return screenOpen && live(); }
void HCClientCursor(int dx,int dy) {
    if(!HCClientScreen() || !ui) return;
    if(!dx && !dy) return;
    cursorX=(std::max)(0,(std::min)(int(ui->width)-1,cursorX+dx)); cursorY=(std::max)(0,(std::min)(int(ui->height)-1,cursorY+dy));
    event(halfcraft_input::Cursor,cursorX,cursorY);
}
void HCClientMouse(bool active) { mouseActive=active; if(!active) publish(false); }
void HCClientMove(usercmd_s* cmd,int active) {
    lastMove=GetTickCount(); bool enabled=active && live(); publish(enabled,cmd->viewangles);
    if(enabled) {
        bool native=physics->host.nativeControl!=0;
        if(!native || screenOpen) cmd->forwardmove=cmd->sidemove=cmd->upmove=0;
        cmd->buttons&=screenOpen?0:((native?(IN_FORWARD|IN_BACK|IN_MOVELEFT|IN_MOVERIGHT|IN_JUMP|IN_DUCK):0)|IN_USE);
        if(screenOpen) { cmd->impulse=0; cmd->weaponselect=0; }
    }
}
bool HCClientKey(int down,int key,const char* binding) {
    if(!down && binding && !strcmp(binding,"+attack")) {
        if(nativeAttack) { nativeAttack=false; return false; }
        if(guestAttack) { guestAttack=false; event(halfcraft_input::Button,1,0); return true; }
    }
    if(!live()) return false;
    if(key=='`' || key=='~' || (binding && !strcmp(binding,"toggleconsole"))) return false;
    if(screenOpen) {
        if(key>=241 && key<=245) { const int buttons[]={1,3,2,4,5}; event(halfcraft_input::Button,buttons[key-241],down); return true; }
        if(key==239 || key==240) { if(down) event(halfcraft_input::Scroll,0,key==240?120:-120); return true; }
        int sc=menuScancode(key); if(sc) event(halfcraft_input::Key,sc,down);
        return true;
    }
    if(key=='e' || (binding && !strcmp(binding,"+use"))) return false;
    if(key=='i') { event(halfcraft_input::Key,12,down); return true; }
    if(key==239 || key==240) { if(down) event(halfcraft_input::Scroll,0,key==240?120:-120); return true; }
    if(binding && !strcmp(binding,"+attack")) {
        guestAttack=down!=0; event(halfcraft_input::Button,1,down); return true;
    }
    if(binding && !strcmp(binding,"+attack2")) { event(halfcraft_input::Button,3,down); return true; }
    int sc=scancode(key,binding);
    if(sc) {
        bool movement=sc==26 || sc==22 || sc==4 || sc==7 || sc==44 || sc==225;
        event(halfcraft_input::Key,sc,down);
        return !(movement && physics->host.nativeControl);
    }
    return false;
}
void HCClientView(ref_params_s* p) {
    if(requested && physicsLive()) {
        auto* model=gEngfuncs.GetViewModel();
        if(model) model->model=nullptr;
    }
    if(requested && physicsLive() && !physics->host.nativeControl && !p->intermission) {
        halfcraft_collision::Guest sample{};
        bool got=false;
        for(int attempt=0;attempt<16;attempt++) {
            unsigned seq=load(physics->guest.seq); if(!seq || seq&1) continue;
            memcpy(&sample,&physics->guest,sizeof(sample)); MemoryBarrier();
            if(seq==load(physics->guest.seq)) { got=true; break; }
        }
        if(got && sample.active && sample.session==renderSession && sample.epoch==renderEpoch && GetTickCount()-sample.heartbeat<250) {
            float eye=float(sample.reserved>>8)/1000.f;
            if(eye<.4f || eye>1.8f) eye=1.62f;
            float target[]={float(sample.feet[0]*40),float(-sample.feet[2]*40),float(sample.feet[1]*40+eye*40)};
            bool valid=true; for(float value:target) if(!std::isfinite(value)||fabs(value)>1310720) valid=false;
            if(valid) {
                DWORD now=GetTickCount(); float t=(std::min)(float(now-cameraTime)/50.f,1.f);
                if(sample.seq!=cameraSample.seq) {
                    for(int i=0;i<3;i++) {
                        cameraFrom[i]=cameraReady?cameraFrom[i]+(cameraTo[i]-cameraFrom[i])*t:target[i];
                        if(fabs(target[i]-cameraFrom[i])>80) cameraFrom[i]=target[i];
                        cameraTo[i]=target[i];
                    }
                    cameraTime=now; cameraSample=sample; cameraReady=true; t=0;
                }
                if(cameraReady) for(int i=0;i<3;i++) p->vieworg[i]=cameraFrom[i]+(cameraTo[i]-cameraFrom[i])*t;
            }
        }
    } else cameraReady=false;
    if(probe && !anchored && !p->intermission) {
        for(int i=0;i<3;++i) anchor[i]=p->vieworg[i]+p->forward[i]*100;
        anchored=true;
        gEngfuncs.Con_Printf("HC_CLIENT anchor=%.3f %.3f %.3f\n",anchor[0],anchor[1],anchor[2]);
    }
}
void HCClientDraw() {
    if(!wglGetCurrentContext()) return;
    auto drawStarted=std::chrono::steady_clock::now();
    // ponytail: fixed-function OpenGL renderer only; add another backend when required.
    glPushAttrib(GL_ALL_ATTRIB_BITS);
    glPushClientAttrib(GL_CLIENT_PIXEL_STORE_BIT);
    using ActiveTexture=void (APIENTRY*)(GLenum);
    auto activeTexture=reinterpret_cast<ActiveTexture>(wglGetProcAddress("glActiveTexture"));
    GLint previousUnit=0x84C0;
    if(activeTexture) {
        glGetIntegerv(0x84E0,&previousUnit);
        GLint units=1; glGetIntegerv(0x84E2,&units);
        for(int unit=1;unit<units;unit++) { activeTexture(0x84C0+unit); glDisable(GL_TEXTURE_2D); }
        activeTexture(0x84C0);
    }
    glEnable(GL_DEPTH_TEST); glDepthFunc(GL_LEQUAL); glDepthMask(GL_TRUE);
    glDisable(GL_TEXTURE_2D); glDisable(GL_BLEND); glDisable(GL_CULL_FACE); glDisable(GL_ALPHA_TEST); glDisable(GL_LIGHTING);
    receiveRender();
    receiveScene();
    if(physicsLive() && GetTickCount()-sceneTime<500) {
        glEnable(GL_TEXTURE_2D); glEnable(GL_ALPHA_TEST); glAlphaFunc(GL_GREATER,.1f); glTexEnvi(GL_TEXTURE_ENV,GL_TEXTURE_ENV_MODE,GL_MODULATE);
        for(auto& batch:sceneBatches) {
            GLuint image=batch.texture==0?texture:(sceneTextures.count(batch.texture)?sceneTextures[batch.texture]:0);
            if(!image) continue;
            glBindTexture(GL_TEXTURE_2D,image);
            if(batch.flags&1) { glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA); glDepthMask(GL_FALSE); }
            else { glDisable(GL_BLEND); glDepthMask(GL_TRUE); }
            glBegin(GL_TRIANGLES);
            for(unsigned i=batch.first;i<batch.first+batch.count;i++) {
                auto& v=sceneVertices[i]; glColor4ub(v.rgba[0],v.rgba[1],v.rgba[2],v.rgba[3]); glTexCoord2f(v.u,v.v);
                glVertex3f(float((sceneOrigin[0]+v.x)*40),float(-(sceneOrigin[2]+v.z)*40),float((sceneOrigin[1]+v.y)*40));
            }
            glEnd();
        }
        glDisable(GL_BLEND); glDepthMask(GL_TRUE);
    }
    if(texture && physicsLive()) {
        glEnable(GL_TEXTURE_2D); glBindTexture(GL_TEXTURE_2D,texture); glTexEnvi(GL_TEXTURE_ENV,GL_TEXTURE_ENV_MODE,GL_MODULATE);
        glEnable(GL_ALPHA_TEST); glAlphaFunc(GL_GREATER,.1f);
        glBegin(GL_TRIANGLES);
        for(auto& section:sections) {
            int sx=std::get<0>(section.first),sy=std::get<1>(section.first),sz=std::get<2>(section.first);
            for(auto& v:section.second) {
                glColor4ub(v.rgba[0],v.rgba[1],v.rgba[2],v.rgba[3]); glTexCoord2f(v.u,v.v);
                glVertex3f((sx*16+v.x)*40,-(sz*16+v.z)*40,(sy*16+v.y)*40);
            }
        }
        glEnd();
        unsigned itemSeq=load(render->itemSeq),itemCount=render->itemCount;
        halfcraft_render::Item items[halfcraft_render::ItemCapacity];
        bool itemValid=itemSeq && !(itemSeq&1) && itemCount<=halfcraft_render::ItemCapacity
            && render->itemSession==renderSession && render->itemGeneration==renderGeneration && GetTickCount()-render->itemHeartbeat<250;
        if(itemValid) {
            memcpy(items,render->items,itemCount*sizeof(items[0])); MemoryBarrier(); itemValid=itemSeq==load(render->itemSeq);
        }
        if(itemValid) {
            static const int faces[6][4]={{0,1,3,2},{4,6,7,5},{0,4,5,1},{2,3,7,6},{0,2,6,4},{1,5,7,3}};
            for(unsigned i=0;i<itemCount;++i) {
                auto& item=items[i]; bool valid=item.kind<=1 && std::isfinite(item.size) && item.size>0 && item.size<=1
                    && std::isfinite(item.x) && std::isfinite(item.y) && std::isfinite(item.z) && std::isfinite(item.yaw)
                    && fabs(item.x)<=32768 && fabs(item.y)<=32768 && fabs(item.z)<=32768;
                for(float uv:item.uv) if(!std::isfinite(uv)||uv<0 || uv>1) valid=false;
                if(!valid) continue;
                glPushMatrix(); glTranslatef(item.x*40,-item.z*40,item.y*40); glRotatef(-item.yaw,0,0,1);
                if(item.tint) glColor4ub(item.tint&255,(item.tint>>8)&255,(item.tint>>16)&255,(item.tint>>24)&255);
                else glColor4f(1,1,1,1);
                float radius=item.size*20;
                glBegin(GL_QUADS);
                if(item.kind==1) {
                    for(int f=0;f<6;++f) {
                        const float* uv=item.uv+(f==2?8:f==3?4:0);
                        for(int corner=0;corner<4;++corner) {
                            int n=faces[f][corner]; glTexCoord2f(uv[(corner==1 || corner==2)?2:0],uv[corner>=2?1:3]);
                            glVertex3f((n&1)?radius:-radius,(n&4)?-radius:radius,(n&2)?radius:-radius);
                        }
                    }
                } else {
                    for(int corner=0;corner<4;++corner) {
                        glTexCoord2f(item.uv[(corner==1 || corner==2)?2:0],item.uv[corner>=2?1:3]);
                        glVertex3f((corner==1 || corner==2)?radius:-radius,0,corner>=2?radius:-radius);
                    }
                }
                glEnd(); glPopMatrix();
            }
        }
        unsigned seq=load(render->crackSeq),count=render->crackCount;
        halfcraft_render::Crack cracks[halfcraft_render::CrackCapacity];
        bool valid=seq && !(seq&1) && count<=halfcraft_render::CrackCapacity
            && render->crackSession==renderSession && render->crackGeneration==renderGeneration && GetTickCount()-render->crackHeartbeat<250;
        if(valid) {
            memcpy(cracks,render->cracks,count*sizeof(cracks[0])); MemoryBarrier(); valid=seq==load(render->crackSeq);
        }
        if(valid) {
            glEnable(GL_BLEND); glBlendFunc(GL_DST_COLOR,GL_SRC_COLOR); glDepthMask(GL_FALSE);
            glEnable(GL_POLYGON_OFFSET_FILL); glPolygonOffset(-1,-1); glColor4f(1,1,1,1);
            static const int faces[6][4]={{0,1,3,2},{4,6,7,5},{0,4,5,1},{2,3,7,6},{0,2,6,4},{1,5,7,3}};
            for(unsigned i=0;i<count;++i) {
                auto& c=cracks[i]; bool bounds=c.stage>=0 && c.stage<=9;
                for(float value:c.bounds) if(!std::isfinite(value)||fabs(value)>32768) bounds=false;
                for(float value:c.uv) if(!std::isfinite(value)||value<0 || value>1) bounds=false;
                for(int axis=0;axis<3;++axis) if(c.bounds[axis+3]<=c.bounds[axis] || c.bounds[axis+3]-c.bounds[axis]>4) bounds=false;
                if(!bounds) continue;
                glBegin(GL_QUADS);
                for(auto& face:faces) for(int corner=0;corner<4;++corner) {
                    int n=face[corner]; glTexCoord2f(c.uv[(corner==1 || corner==2)?2:0],c.uv[corner>=2?3:1]);
                    glVertex3f(c.bounds[(n&1)?3:0]*40,-c.bounds[(n&4)?5:2]*40,c.bounds[(n&2)?4:1]*40);
                }
                glEnd();
            }
            glDisable(GL_POLYGON_OFFSET_FILL); glDisable(GL_BLEND); glDepthMask(GL_TRUE);
        }
    }
    if(input && physicsLive() && load(input->guestSession)==renderSession) {
        auto* bytes=reinterpret_cast<char*>(input); auto* hudSeq=reinterpret_cast<std::uint32_t*>(bytes+124);
        unsigned seq=load(*hudSeq); float bounds[6]; memcpy(bounds,bytes+84,sizeof(bounds));
        bool target=*reinterpret_cast<std::uint32_t*>(bytes+108)!=0; MemoryBarrier();
        bool valid=target && seq && !(seq&1) && seq==load(*hudSeq);
        for(float value:bounds) if(!std::isfinite(value)||fabs(value)>32768) valid=false;
        for(int i=0;i<3;i++) if(bounds[i+3]<bounds[i] || bounds[i+3]-bounds[i]>4) valid=false;
        if(valid) {
            glDisable(GL_TEXTURE_2D); glDisable(GL_ALPHA_TEST); glColor4f(1,1,1,1); glLineWidth(2);
            glBegin(GL_LINES);
            for(int n=0;n<8;n++) for(int axis=0;axis<3;axis++) if(!(n&(1<<axis))) {
                for(int corner:{n,n|(1<<axis)}) glVertex3f(bounds[(corner&1)?3:0]*40,-bounds[(corner&4)?5:2]*40,bounds[(corner&2)?4:1]*40);
            }
            glEnd();
        }
    }
    if(probe && anchored) {
    glDisable(GL_TEXTURE_2D); glDisable(GL_ALPHA_TEST); glColor4f(.1f,.9f,.7f,1);
    static const int faces[6][4]={{0,1,3,2},{4,6,7,5},{0,4,5,1},{2,3,7,6},{0,2,6,4},{1,5,7,3}};
    glBegin(GL_QUADS);
    for(auto& face:faces) for(int n:face) glVertex3f(anchor[0]+((n&1)?20:-20),anchor[1]+((n&2)?20:-20),anchor[2]+((n&4)?20:-20));
    glEnd();
    }
    glPopClientAttrib(); glPopAttrib();
    if(activeTexture) activeTexture(previousUnit);
    if(render) {
        auto micros=std::chrono::duration_cast<std::chrono::microseconds>(std::chrono::steady_clock::now()-drawStarted).count();
        auto* stats=reinterpret_cast<std::uint32_t*>(render->padding);
        stats[0]=unsigned(micros); stats[1]=(std::max)(stats[1],unsigned(micros)); stats[2]=unsigned(sections.size());
        stats[3]=0; for(auto& section:sections) stats[3]+=unsigned(section.second.size());
    }
}
void HCClientHUD() {
    if(ui && requested && physicsLive() && !gEngfuncs.Con_IsVisible() && wglGetCurrentContext()) {
        glPushAttrib(GL_ALL_ATTRIB_BITS); glPushClientAttrib(GL_CLIENT_PIXEL_STORE_BIT);
        using ActiveTexture=void (APIENTRY*)(GLenum);
        auto activeTexture=reinterpret_cast<ActiveTexture>(wglGetProcAddress("glActiveTexture"));
        GLint previousUnit=0x84C0;
        if(activeTexture) { glGetIntegerv(0x84E0,&previousUnit); GLint units=1; glGetIntegerv(0x84E2,&units); for(int i=1;i<units;i++) { activeTexture(0x84C0+i); glDisable(GL_TEXTURE_2D); } activeTexture(0x84C0); }
        unsigned seq=load(ui->frameSeq);
        if(seq && !(seq&1) && seq!=load(ui->ack)) {
            unsigned w=ui->frameWidth,h=ui->frameHeight; GLint maxSize=0; glGetIntegerv(GL_MAX_TEXTURE_SIZE,&maxSize);
            if(w && h && w<=halfcraft_ui::MaxWidth && h<=halfcraft_ui::MaxHeight && w<=unsigned(maxSize) && h<=unsigned(maxSize)
                && ui->frameSession==ui->session && ui->frameEpoch==ui->epoch && GetTickCount()-ui->frameHeartbeat<1000 && ui->flags==1) {
                if(!uiTexture) glGenTextures(1,&uiTexture);
                glBindTexture(GL_TEXTURE_2D,uiTexture); glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST); glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
                glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP); glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP); glPixelStorei(GL_UNPACK_ALIGNMENT,1);
                if(w!=uiWidth || h!=uiHeight) glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,w,h,0,GL_RGBA,GL_UNSIGNED_BYTE,ui->pixels);
                else glTexSubImage2D(GL_TEXTURE_2D,0,0,0,w,h,GL_RGBA,GL_UNSIGNED_BYTE,ui->pixels);
                uiWidth=w; uiHeight=h; uiFrameSession=ui->frameSession; uiFrameEpoch=ui->frameEpoch; uiFrameTime=ui->frameHeartbeat;
            }
            store(ui->ack,seq);
        }
        GLint matrix=0; glGetIntegerv(GL_MATRIX_MODE,&matrix);
        glMatrixMode(GL_PROJECTION); glPushMatrix(); glLoadIdentity(); glOrtho(0,gHUD.m_scrinfo.iWidth,gHUD.m_scrinfo.iHeight,0,-1,1);
        glMatrixMode(GL_MODELVIEW); glPushMatrix(); glLoadIdentity();
        glDisable(GL_DEPTH_TEST); glDepthMask(GL_FALSE); glDisable(GL_ALPHA_TEST); glDisable(GL_CULL_FACE); glDisable(GL_LIGHTING);
        glEnable(GL_BLEND); glBlendFunc(GL_ONE,GL_ONE_MINUS_SRC_ALPHA); glColor4f(1,1,1,1);
        if(uiTexture && uiFrameSession==ui->session && uiFrameEpoch==ui->epoch && GetTickCount()-uiFrameTime<1000) {
            glEnable(GL_TEXTURE_2D); glBindTexture(GL_TEXTURE_2D,uiTexture); glTexEnvi(GL_TEXTURE_ENV,GL_TEXTURE_ENV_MODE,GL_MODULATE);
            glBegin(GL_QUADS); glTexCoord2f(0,1); glVertex2f(0,0); glTexCoord2f(1,1); glVertex2f(gHUD.m_scrinfo.iWidth,0);
            glTexCoord2f(1,0); glVertex2f(gHUD.m_scrinfo.iWidth,gHUD.m_scrinfo.iHeight); glTexCoord2f(0,0); glVertex2f(0,gHUD.m_scrinfo.iHeight); glEnd();
        }
        glDisable(GL_TEXTURE_2D); glDisable(GL_BLEND);
        if(screenOpen) {
            float x=cursorX*float(gHUD.m_scrinfo.iWidth)/ui->width,y=cursorY*float(gHUD.m_scrinfo.iHeight)/ui->height;
            glColor3f(0,0,0); glBegin(GL_TRIANGLES); glVertex2f(x,y); glVertex2f(x,y+18); glVertex2f(x+12,y+12); glEnd();
            glColor3f(1,1,1); glBegin(GL_TRIANGLES); glVertex2f(x+1,y+2); glVertex2f(x+1,y+15); glVertex2f(x+9,y+11); glEnd();
        }
        glPopMatrix(); glMatrixMode(GL_PROJECTION); glPopMatrix(); glMatrixMode(matrix);
        glPopClientAttrib(); glPopAttrib(); if(activeTexture) activeTexture(previousUnit);
        return;
    }
    if(!input || !physicsLive() || load(input->guestSession)!=renderSession) return;
    auto* bytes=reinterpret_cast<char*>(input);
    int slot=*reinterpret_cast<std::uint32_t*>(bytes+112),count=*reinterpret_cast<std::uint32_t*>(bytes+116);
    if(slot<0 || slot>8 || count<0 || count>999) return;
    char text[64]; snprintf(text,sizeof(text),"Minecraft slot %d/9   count %d",slot+1,count);
    gEngfuncs.pfnDrawSetTextColor(1,1,1); gEngfuncs.pfnDrawConsoleString(20,gHUD.m_scrinfo.iHeight-40,text);
}
void HCClientClose() {
    hideViewModel(false);
    clearScene(); if(scene) { store(scene->enabled,0); UnmapViewOfFile(scene); scene=nullptr; } if(sceneHandle) { CloseHandle(sceneHandle); sceneHandle=nullptr; }
    if(gameWindow && IsWindow(gameWindow) && reinterpret_cast<WNDPROC>(GetWindowLongPtr(gameWindow,GWLP_WNDPROC))==windowProc)
        SetWindowLongPtr(gameWindow,GWLP_WNDPROC,reinterpret_cast<LONG_PTR>(previousWindowProc));
    gameWindow=nullptr; previousWindowProc=nullptr; screenOpen=false;
    if(uiTexture && wglGetCurrentContext()) glDeleteTextures(1,&uiTexture); uiTexture=0;
    if(ui) { store(ui->enabled,0); UnmapViewOfFile(ui); ui=nullptr; } if(uiHandle) { CloseHandle(uiHandle); uiHandle=nullptr; }
    requested=false; publish(false); probe=false; anchored=false;
    clearRender(); if(render) { store(render->enabled,0); UnmapViewOfFile(render); } render=nullptr;
    if(renderHandle) CloseHandle(renderHandle); renderHandle=nullptr;
    if(input) UnmapViewOfFile(input); if(link) UnmapViewOfFile(link); if(physics) UnmapViewOfFile(physics);
    input=nullptr; link=nullptr; physics=nullptr;
    if(inputHandle) CloseHandle(inputHandle); if(linkHandle) CloseHandle(linkHandle); if(physicsHandle) CloseHandle(physicsHandle);
    inputHandle=linkHandle=physicsHandle=nullptr;
}
bool HCClientMinecraftHUD() { return physicsLive(); }
