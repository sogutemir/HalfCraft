#pragma once
#include <cstdint>
#include <cstddef>
namespace halfcraft_interaction {
constexpr wchar_t Mapping[]=L"Local\\HalfCraft_interaction_v3";
constexpr unsigned Magic=0x58435248,Version=3,Capacity=128;
struct Actor { std::uint32_t id,serial; float bounds[6],health; std::uint32_t flags; };
struct Host {
    std::uint32_t seq,magic,version,bytes,pid,session,heartbeat,enabled,epoch,ack,count,target;
    float point[3]; std::uint32_t targetId;
};
struct Guest {
    std::uint32_t seq,pid,session,epoch,heartbeat,id,serial,flags;
    float damage; std::uint8_t padding[24]; std::uint32_t deathAck;
};
// tier bits 0..1: Xen/soldier/heavy; bit 2: lethal fire hit cooks beef.
struct Death { std::uint32_t seq,id,serial,epoch,tier; };
struct Shared { Host host; Actor actors[Capacity]; Guest guest; std::uint32_t deathWrite; std::uint8_t padding[12]; Death deaths[Capacity]; };
constexpr unsigned Bytes=sizeof(Shared),GuestOffset=offsetof(Shared,guest);
static_assert(sizeof(Host)==64 && sizeof(Actor)==40 && sizeof(Guest)==64 && sizeof(Death)==20 && Bytes==7824 && GuestOffset==5184);
static_assert(offsetof(Shared,deathWrite)==5248 && offsetof(Shared,deaths)==5264 && offsetof(Guest,deathAck)==60);
}
