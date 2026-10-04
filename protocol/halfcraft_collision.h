// Bounded sampled BSP experiment. Independent from stable HalfCraft_v1 handshake.
#pragma once
#include <cstddef>
#include <cstdint>
namespace halfcraft_collision {
constexpr wchar_t Mapping[]=L"Local\\HalfCraft_collision_v1";
constexpr std::uint32_t Magic=0x43435248,Version=2,Dim=8,Sub=4,Cells=Dim*Dim*Dim;
#pragma pack(push,8)
struct alignas(8) Host {
    std::uint32_t seq,magic,version,bytes,pid,session,heartbeat,enabled;
    std::uint32_t epoch,generation,ready,correction;
    std::int32_t base[3]; std::uint32_t dim;
    double feet[3]; float yaw,pitch;
    std::uint32_t sub,occupied,nativeControl; std::uint8_t padding[20];
};
struct alignas(8) Guest {
    std::uint32_t seq,pid,session,epoch,active,heartbeat,correctionAck,reserved;
    double feet[3]; float yaw,pitch;
};
struct alignas(8) Shared { Host host; std::uint64_t masks[Cells]; Guest guest; };
#pragma pack(pop)
constexpr std::uint32_t Bytes=sizeof(Shared),GuestOffset=offsetof(Shared,guest);
static_assert(sizeof(Host)==128 && sizeof(Guest)==64 && Bytes==4288 && GuestOffset==4224);
static_assert(offsetof(Host,base)==48 && offsetof(Host,feet)==64 && offsetof(Host,sub)==96);
static_assert(offsetof(Host,nativeControl)==104);
static_assert(offsetof(Guest,feet)==32 && offsetof(Guest,yaw)==56);
}
