#pragma once
#include <cstdint>
#include <cstddef>
namespace halfcraft_scene {
constexpr wchar_t Mapping[]=L"Local\\HalfCraft_scene_v1";
constexpr std::uint32_t Magic=0x53435248,Version=1,Capacity=4*1024*1024;
struct Shared {
    std::uint32_t seq,magic,version,bytes,pid,session,heartbeat,enabled;
    std::uint32_t epoch,ack,padding[6];
    std::uint32_t messageSeq,messageSession,messageEpoch,messageType,headerBytes,bodyBytes,messageHeartbeat,reserved;
    std::uint8_t payload[Capacity];
};
constexpr std::uint32_t Bytes=sizeof(Shared);
static_assert(Bytes==4194400 && offsetof(Shared,messageSeq)==64 && offsetof(Shared,payload)==96);
}
