#include "../protocol/halfcraft_protocol.h"
#include "../protocol/halfcraft_collision.h"
#include "../protocol/halfcraft_vitals.h"
#include "../protocol/halfcraft_interaction.h"
#include <cstdio>
int main() {
    std::printf("C++ layout PASS bytes=%zu host=%zu guest=%zu origin=%zu velocity=%zu\n",sizeof(halfcraft::Shared),offsetof(halfcraft::Shared,host),offsetof(halfcraft::Shared,guest),offsetof(halfcraft::Host,origin),offsetof(halfcraft::Guest,velocity));
}
#include "../protocol/halfcraft_input.h"
#include "../protocol/halfcraft_render.h"
#include "../protocol/halfcraft_ui.h"
#include "../protocol/halfcraft_scene.h"
