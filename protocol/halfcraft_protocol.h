// HalfCraft v1. SkyCraft-derived latest-value/seqlock architecture; MIT, see LICENSE.
#pragma once
#include <cstddef>
#include <cstdint>

namespace halfcraft {
constexpr std::uint32_t Magic = 0x46435248; // "HRCF" bytes, distinct from SKYC
constexpr std::uint32_t Version = 1, Bytes = 384, Timeout = 8000;
constexpr wchar_t Mapping[] = L"Local\\HalfCraft_v1";
enum State : std::uint32_t { DISCONNECTED, HOST_READY, GUEST_READY, CONNECTED, LINK_ERROR, SHUTDOWN };
#pragma pack(push, 8)
struct alignas(8) Header {
    std::uint32_t magic, version, bytes, reserved;
    std::uint8_t padding[48];
};
struct alignas(8) Host {
    std::uint32_t seq, pid, heartbeat, state;
    std::uint32_t session, playerPresent, started, reserved;
    char map[64];
    double origin[3];
    float angles[3];
    std::uint8_t padding[60];
};
struct alignas(8) Guest {
    std::uint32_t seq, pid, heartbeat, state;
    std::uint32_t hostSession, worldLoaded, onGround, started;
    double position[3], velocity[3];
    std::uint8_t padding[48];
};
struct alignas(8) Shared { Header header; Host host; Guest guest; };
#pragma pack(pop)
static_assert(sizeof(Header)==64 && sizeof(Host)==192 && sizeof(Guest)==128);
static_assert(sizeof(Shared)==Bytes && offsetof(Shared,host)==64 && offsetof(Shared,guest)==256);
static_assert(offsetof(Host,map)==32 && offsetof(Host,origin)==96 && offsetof(Host,angles)==120);
static_assert(offsetof(Guest,position)==32 && offsetof(Guest,velocity)==56);
}
