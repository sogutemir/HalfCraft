#pragma once
#include <cstddef>
#include <cstdint>
namespace halfcraft_blocks {
constexpr wchar_t Mapping[]=L"Local\\HalfCraft_blocks_v2";
constexpr std::uint32_t Magic=0x42435248,Version=2,Capacity=1024,ActorCapacity=256;
#pragma pack(push,8)
struct Host { std::uint32_t seq,magic,version,bytes,pid,session,heartbeat,enabled,epoch,ack,proxies,actors,actorsReady; std::uint8_t padding[12]; };
struct Guest { std::uint32_t seq,pid,session,epoch,heartbeat,count,generation,ready; std::uint8_t padding[32]; };
struct Box { float min[3],max[3]; };
struct Shared { Host host; Guest guest; Box boxes[Capacity]; Box actors[ActorCapacity]; };
#pragma pack(pop)
constexpr std::uint32_t Bytes=sizeof(Shared);
static_assert(sizeof(Host)==64 && sizeof(Guest)==64 && sizeof(Box)==24 && Bytes==30848 && offsetof(Shared,actors)==24704);
}
