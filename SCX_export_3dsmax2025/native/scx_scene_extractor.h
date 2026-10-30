#pragma once

#include "scx_scene.h"

#include <string>

class Interface;

namespace scx
{
    // Extracts evaluated triangular geometry from the scene.
    // Positions are baked to world space and converted to SCX coordinates.
    bool ExtractMaxScene(
        Interface& ip,
        bool selectedOnly,
        Scene& output,
        std::string& error);
}
