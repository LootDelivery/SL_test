#pragma once

#include "scx_scene.h"

#include <cstdint>
#include <string>
#include <vector>

namespace scx
{
    // Serializes the currently understood SCX v4 records.
    // Returns false and fills error when input cannot be represented safely.
    bool WriteV4(
        const Scene& scene,
        std::vector<std::uint8_t>& output,
        std::string& error);
}
