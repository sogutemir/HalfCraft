#pragma once
#include <cstdint>
#include <cstddef>
namespace halfcraft_vitals {
constexpr wchar_t Mapping[]=L"Local\\HalfCraft_vitals_v1";
constexpr unsigned Magic=0x56435248,Version=1,Capacity=128;
enum Kind { Damage=1,Heal=2,Repair=3 };
struct Host { std::uint32_t seq,magic,version,bytes,pid,session,heartbeat,enabled,life,alive,write,ack; std::uint8_t padding[16]; };
struct Guest { std::uint32_t seq,pid,session,heartbeat,life,ack,deaths,ready; float health,maxHealth; std::uint32_t repair; std::uint8_t padding[20]; };
struct Event { std::uint32_t seq,life,kind,bits; float amount,position[3]; };
struct Shared { Host host; Guest guest; Event events[Capacity]; };
constexpr unsigned Bytes=sizeof(Shared);
static_assert(sizeof(Host)==64 && sizeof(Guest)==64 && sizeof(Event)==32 && Bytes==4224);
}
