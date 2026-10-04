#pragma once
#include <cstdint>
#include <cstddef>
namespace halfcraft_input {
constexpr wchar_t Mapping[]=L"Local\\HalfCraft_input_v2";
constexpr std::uint32_t Magic=0x49435248,Version=2,Capacity=256;
enum Type : std::uint32_t { Key=1,Button=2,Scroll=3,Cursor=4,Text=5 };
struct Event { std::uint32_t type,code; std::int32_t value; std::uint32_t timestamp; };
struct Shared {
    std::uint32_t seq,magic,version,bytes,pid,session,heartbeat,enabled;
    std::uint32_t write,reset; float yaw,pitch;
    std::uint32_t epoch,requested; std::uint8_t padding[8];
    std::uint32_t read,guestPid,guestHeartbeat,ready,guestSession;
    std::uint8_t guestPadding[44];
    Event events[Capacity];
};
constexpr std::uint32_t Bytes=sizeof(Shared);
static_assert(Bytes==4224 && offsetof(Shared,read)==64 && offsetof(Shared,events)==128);
}
