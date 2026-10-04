#pragma once
#include <cstdint>
#include <cstddef>
namespace halfcraft_render {
constexpr wchar_t Mapping[]=L"Local\\HalfCraft_render_v3";
constexpr std::uint32_t Magic=0x52435248,Version=3,AtlasCapacity=64*1024*1024,MeshCapacity=8*1024*1024,CrackCapacity=16,ItemCapacity=128;
struct Vertex { float x,y,z,u,v; std::uint8_t rgba[4]; std::uint32_t light,flags; };
struct Crack { float bounds[6],uv[4]; std::int32_t stage; std::uint32_t reserved; };
struct Item { std::uint32_t id,kind; float x,y,z,yaw,size; std::uint32_t tint; float uv[12]; };
struct Shared {
    std::uint32_t seq,magic,version,bytes,pid,session,heartbeat,enabled;
    std::uint32_t reset,atlasAck,meshAck,epoch;
    std::uint8_t padding[16];
    std::uint32_t atlasSeq,generation,width,height,atlasBytes,atlasSession;
    std::uint8_t atlasPadding[8];
    std::uint32_t meshSeq,meshGeneration; std::int32_t sx,sy,sz;
    std::uint32_t count,meshBytes,meshSession;
    std::uint8_t atlas[AtlasCapacity],mesh[MeshCapacity];
    std::uint32_t crackSeq,crackSession,crackGeneration,crackHeartbeat,crackCount,crackPadding[3];
    Crack cracks[CrackCapacity];
    std::uint32_t itemSeq,itemSession,itemGeneration,itemHeartbeat,itemCount,itemPadding[3];
    Item items[ItemCapacity];
};
constexpr std::uint32_t Bytes=sizeof(Shared),AtlasOffset=128,MeshOffset=128+AtlasCapacity;
static_assert(sizeof(Vertex)==32 && sizeof(Crack)==48 && sizeof(Item)==80 && offsetof(Shared,atlas)==AtlasOffset && offsetof(Shared,mesh)==MeshOffset && offsetof(Shared,crackSeq)==75497600 && offsetof(Shared,itemSeq)==75498400 && Bytes==75508672);
}
