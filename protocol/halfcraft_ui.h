#pragma once
#include <cstdint>
#include <cstddef>
namespace halfcraft_ui {
constexpr wchar_t Mapping[]=L"Local\\HalfCraft_ui_v1";
constexpr std::uint32_t Magic=0x55435248,Version=1,MaxWidth=3840,MaxHeight=2160,Capacity=MaxWidth*MaxHeight*4;
struct Shared {
    std::uint32_t seq,magic,version,bytes,pid,session,heartbeat,enabled;
    std::uint32_t epoch,width,height,ack,padding[4];
    std::uint32_t stateSeq,guestPid,guestSession,guestEpoch,guestHeartbeat,screen,guiScale,reserved;
    std::uint32_t frameSeq,frameWidth,frameHeight,flags,frameSession,frameEpoch,frameHeartbeat,frameId;
    std::uint8_t pixels[Capacity];
};
constexpr std::uint32_t Bytes=sizeof(Shared);
static_assert(Bytes==33177728 && offsetof(Shared,stateSeq)==64 && offsetof(Shared,frameSeq)==96 && offsetof(Shared,pixels)==128);
}
