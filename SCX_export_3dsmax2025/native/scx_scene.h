#pragma once

#include <array>
#include <cstdint>
#include <optional>
#include <string>
#include <vector>

namespace scx
{
    struct Vec2
    {
        float x = 0.0f;
        float y = 0.0f;
    };

    struct Vec3
    {
        float x = 0.0f;
        float y = 0.0f;
        float z = 0.0f;
    };

    using Color8 = std::array<std::uint8_t, 4>;

    enum class MaterialMapEntry : std::uint32_t
    {
        Diffuse = 0x06000000,
        DiffuseMixSecond = 0x06000001,
        Bump = 0x06000002,
        Reflection = 0x06000003,
        Emissive = 0x06000004
    };

    struct MaterialMap
    {
        MaterialMapEntry entry = MaterialMapEntry::Diffuse;
        std::uint32_t textureIndex = 0;
        std::uint32_t mapChannel = 1;
        std::uint32_t tilingFlags = 3;
        Vec2 tiling{ 1.0f, 1.0f };
        Vec2 offset{ 0.0f, 0.0f };
    };

    struct Material
    {
        std::string name = "Default";
        Color8 diffuse{ 255, 255, 255, 255 };
        std::optional<Color8> specular;
        std::optional<Color8> emissive;
        std::optional<float> specularIntensity;
        std::optional<float> reflectionIntensity;
        std::optional<float> bumpIntensity;
        std::vector<MaterialMap> maps;
    };

    struct Vertex
    {
        // Positions are stored in centimetres. The reader converts them to metres
        // by dividing the file values by 100.
        Vec3 position;
        Vec3 normal;

        std::optional<Color8> emissive;
        std::optional<Color8> color;
        std::optional<Vec2> uv1;
        std::optional<Vec2> uv2;
        std::optional<Vec2> uv3;
        std::optional<Vec3> bumpMapNormal;
    };

    struct Mesh
    {
        std::string name;
        Material material;
        std::vector<Vertex> vertices;

        // Triangle list. Each index is local to this mesh's vertex array.
        // SCX v4 serializes these as unsigned 16-bit values.
        std::vector<std::uint32_t> indices;

        // The meaning of the legacy HardSurface payload is not yet recovered.
        // Keep the observed fixture value until reference exports establish it.
        std::uint32_t hardSurfacePayload = 0;
    };

    struct Scene
    {
        std::vector<Mesh> meshes;
    };
}
