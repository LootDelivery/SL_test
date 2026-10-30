#include "scx_v4_writer.h"

#include <algorithm>
#include <cmath>
#include <cstring>
#include <limits>
#include <utility>

namespace
{
    using Bytes = std::vector<std::uint8_t>;

    constexpr std::uint32_t kEntryMaterial = 0;
    constexpr std::uint32_t kEntryHardSurface = 1;
    constexpr std::uint32_t kEntryVertices = 4;
    constexpr std::uint32_t kEntryFaces = 5;

    constexpr std::uint32_t kVertexPosition = 0x00000001;
    constexpr std::uint32_t kVertexEmissive = 0x00000080;
    constexpr std::uint32_t kVertexColor = 0x00000100;
    constexpr std::uint32_t kVertexUv1 = 0x00000200;
    constexpr std::uint32_t kVertexUv2 = 0x00000400;
    constexpr std::uint32_t kVertexUv3 = 0x00000800;
    constexpr std::uint32_t kVertexNormal = 0x00000040;
    constexpr std::uint32_t kVertexBumpNormal = 0x00040000;

    constexpr std::uint32_t kMaterialDiffuseColor = 0x00000000;
    constexpr std::uint32_t kMaterialSpecularColor = 0x00000001;
    constexpr std::uint32_t kMaterialEmissiveColor = 0x00000002;
    constexpr std::uint32_t kMaterialDiffuseIntensity = 0x01000000;
    constexpr std::uint32_t kMaterialSpecularIntensity = 0x01000001;
    constexpr std::uint32_t kMaterialEmissiveIntensity = 0x01000002;
    constexpr std::uint32_t kMaterialName = 0x08000000;

    // The material flags are still fixture-derived and not fully decoded.
    constexpr std::uint32_t kObservedMaterialFlags = 0x33;

    bool FitsU32(std::size_t value)
    {
        return value <= static_cast<std::size_t>((std::numeric_limits<std::uint32_t>::max)());
    }

    void PutU8(Bytes& out, std::uint8_t value)
    {
        out.push_back(value);
    }

    void PutU16(Bytes& out, std::uint16_t value)
    {
        out.push_back(static_cast<std::uint8_t>(value & 0xFF));
        out.push_back(static_cast<std::uint8_t>((value >> 8) & 0xFF));
    }

    void PutU32(Bytes& out, std::uint32_t value)
    {
        out.push_back(static_cast<std::uint8_t>(value & 0xFF));
        out.push_back(static_cast<std::uint8_t>((value >> 8) & 0xFF));
        out.push_back(static_cast<std::uint8_t>((value >> 16) & 0xFF));
        out.push_back(static_cast<std::uint8_t>((value >> 24) & 0xFF));
    }

    void PutF32(Bytes& out, float value)
    {
        static_assert(sizeof(float) == sizeof(std::uint32_t), "SCX requires 32-bit floats");
        std::uint32_t bits = 0;
        std::memcpy(&bits, &value, sizeof(bits));
        PutU32(out, bits);
    }

    void PutColor(Bytes& out, const scx::Color8& color)
    {
        for (const auto channel : color)
        {
            PutU8(out, channel);
        }
    }

    void PutFixedString(Bytes& out, const std::string& text, std::size_t length)
    {
        const std::size_t count = (std::min)(text.size(), length);
        out.insert(out.end(), text.begin(), text.begin() + count);
        out.insert(out.end(), length - count, 0);
    }

    bool IsFinite(float value)
    {
        return std::isfinite(value) != 0;
    }

    bool ValidateVec2(const scx::Vec2& value)
    {
        return IsFinite(value.x) && IsFinite(value.y);
    }

    bool ValidateVec3(const scx::Vec3& value)
    {
        return IsFinite(value.x) && IsFinite(value.y) && IsFinite(value.z);
    }

    void PutVec2(Bytes& out, const scx::Vec2& value, bool flipV)
    {
        PutF32(out, value.x);
        PutF32(out, flipV ? 1.0f - value.y : value.y);
    }

    void PutVec3(Bytes& out, const scx::Vec3& value)
    {
        PutF32(out, value.x);
        PutF32(out, value.y);
        PutF32(out, value.z);
    }

    struct Block
    {
        std::uint32_t type = 0;
        Bytes bytes;
    };

    Block MakeMaterialBlock(const scx::Material& material)
    {
        Bytes payload;
        std::uint32_t entryCount = 2; // Name + diffuse color.

        if (material.specular) ++entryCount;
        if (material.emissive) ++entryCount;
        if (material.diffuseIntensity) ++entryCount;
        if (material.specularIntensity) ++entryCount;
        if (material.emissiveIntensity) ++entryCount;
        entryCount += static_cast<std::uint32_t>(material.maps.size());

        PutU32(payload, kObservedMaterialFlags);
        PutF32(payload, static_cast<float>(material.transparencyType));
        PutU32(payload, entryCount);

        PutU32(payload, kMaterialName);
        PutFixedString(payload, material.name, 32);

        PutU32(payload, kMaterialDiffuseColor);
        PutColor(payload, material.diffuse);

        if (material.specular)
        {
            PutU32(payload, kMaterialSpecularColor);
            PutColor(payload, *material.specular);
        }

        if (material.emissive)
        {
            PutU32(payload, kMaterialEmissiveColor);
            PutColor(payload, *material.emissive);
        }

        if (material.diffuseIntensity)
        {
            PutU32(payload, kMaterialDiffuseIntensity);
            PutF32(payload, *material.diffuseIntensity);
        }

        if (material.specularIntensity)
        {
            PutU32(payload, kMaterialSpecularIntensity);
            PutF32(payload, *material.specularIntensity);
        }

        if (material.emissiveIntensity)
        {
            PutU32(payload, kMaterialEmissiveIntensity);
            PutF32(payload, *material.emissiveIntensity);
        }

        for (const auto& map : material.maps)
        {
            PutU32(payload, static_cast<std::uint32_t>(map.entry));
            PutU32(payload, map.textureIndex);
            PutU32(payload, map.mapChannel);
            PutU32(payload, map.tilingFlags);
            PutF32(payload, map.tiling.x);
            PutF32(payload, map.tiling.y);
            PutF32(payload, map.offset.x);
            PutF32(payload, map.offset.y);
        }

        Block block;
        block.type = kEntryMaterial;
        PutU32(block.bytes, block.type);
        PutU32(block.bytes, static_cast<std::uint32_t>(8 + payload.size()));
        block.bytes.insert(block.bytes.end(), payload.begin(), payload.end());
        return block;
    }

    Block MakeHardSurfaceBlock(std::uint32_t payloadValue)
    {
        Block block;
        block.type = kEntryHardSurface;
        PutU32(block.bytes, block.type);
        PutU32(block.bytes, 12);
        PutU32(block.bytes, payloadValue);
        return block;
    }

    bool HasEmissive(const scx::Mesh& mesh)
    {
        return std::any_of(mesh.vertices.begin(), mesh.vertices.end(),
            [](const scx::Vertex& v) { return v.emissive.has_value(); });
    }

    bool HasColor(const scx::Mesh& mesh)
    {
        return std::any_of(mesh.vertices.begin(), mesh.vertices.end(),
            [](const scx::Vertex& v) { return v.color.has_value(); });
    }

    bool HasUv1(const scx::Mesh& mesh)
    {
        return std::any_of(mesh.vertices.begin(), mesh.vertices.end(),
            [](const scx::Vertex& v) { return v.uv1.has_value(); });
    }

    bool HasUv2(const scx::Mesh& mesh)
    {
        return std::any_of(mesh.vertices.begin(), mesh.vertices.end(),
            [](const scx::Vertex& v) { return v.uv2.has_value(); });
    }

    bool HasUv3(const scx::Mesh& mesh)
    {
        return std::any_of(mesh.vertices.begin(), mesh.vertices.end(),
            [](const scx::Vertex& v) { return v.uv3.has_value(); });
    }

    bool HasBumpNormal(const scx::Mesh& mesh)
    {
        return std::any_of(mesh.vertices.begin(), mesh.vertices.end(),
            [](const scx::Vertex& v) { return v.bumpMapNormal.has_value(); });
    }

    Block MakeVertexBlock(const scx::Mesh& mesh)
    {
        const bool hasEmissive = HasEmissive(mesh);
        const bool hasColor = HasColor(mesh);
        const bool hasUv1 = HasUv1(mesh);
        const bool hasUv2 = HasUv2(mesh);
        const bool hasUv3 = HasUv3(mesh);
        const bool hasBumpNormal = HasBumpNormal(mesh);

        std::uint32_t flags = kVertexPosition | kVertexNormal;
        if (hasEmissive) flags |= kVertexEmissive;
        if (hasColor) flags |= kVertexColor;
        if (hasUv1) flags |= kVertexUv1;
        if (hasUv2) flags |= kVertexUv2;
        if (hasUv3) flags |= kVertexUv3;
        if (hasBumpNormal) flags |= kVertexBumpNormal;

        Bytes payload;
        PutU32(payload, static_cast<std::uint32_t>(mesh.vertices.size()));
        PutU32(payload, flags);

        for (const auto& vertex : mesh.vertices)
        {
            PutVec3(payload, vertex.position);
            PutVec3(payload, vertex.normal);

            if (hasEmissive)
            {
                PutColor(payload, vertex.emissive.value_or(scx::Color8{ 0, 0, 0, 0 }));
            }

            if (hasColor)
            {
                PutColor(payload, vertex.color.value_or(scx::Color8{ 255, 255, 255, 255 }));
            }

            if (hasUv1) PutVec2(payload, vertex.uv1.value_or(scx::Vec2{}), true);
            if (hasUv2) PutVec2(payload, vertex.uv2.value_or(scx::Vec2{}), true);
            if (hasUv3) PutVec2(payload, vertex.uv3.value_or(scx::Vec2{}), true);

            if (hasBumpNormal)
            {
                PutVec3(payload, vertex.bumpMapNormal.value_or(scx::Vec3{}));
            }
        }

        Block block;
        block.type = kEntryVertices;
        PutU32(block.bytes, block.type);
        PutU32(block.bytes, static_cast<std::uint32_t>(8 + payload.size()));
        block.bytes.insert(block.bytes.end(), payload.begin(), payload.end());
        return block;
    }

    Block MakeFaceBlock(const scx::Mesh& mesh)
    {
        Bytes payload;
        PutU32(payload, static_cast<std::uint32_t>(mesh.indices.size()));

        for (const auto index : mesh.indices)
        {
            PutU16(payload, static_cast<std::uint16_t>(index));
        }

        Block block;
        block.type = kEntryFaces;
        PutU32(block.bytes, block.type);
        PutU32(block.bytes, static_cast<std::uint32_t>(8 + payload.size()));
        block.bytes.insert(block.bytes.end(), payload.begin(), payload.end());
        return block;
    }

    bool ValidateScene(const scx::Scene& scene, std::string& error)
    {
        if (scene.meshes.empty())
        {
            error = "The scene contains no exportable meshes.";
            return false;
        }

        for (std::size_t meshIndex = 0; meshIndex < scene.meshes.size(); ++meshIndex)
        {
            const auto& mesh = scene.meshes[meshIndex];
            const std::string prefix = "Mesh " + std::to_string(meshIndex) + ": ";

            if (mesh.vertices.empty())
            {
                error = prefix + "has no vertices.";
                return false;
            }

            if (mesh.vertices.size() > 65536)
            {
                error = prefix + "has more than 65,536 vertices; it must be split before SCX v4 serialization.";
                return false;
            }

            if (mesh.indices.empty() || mesh.indices.size() % 3 != 0)
            {
                error = prefix + "face index count must be a non-zero multiple of 3.";
                return false;
            }

            if (mesh.indices.size() > (std::numeric_limits<std::uint32_t>::max)())
            {
                error = prefix + "has too many face indices.";
                return false;
            }

            for (std::size_t vertexIndex = 0; vertexIndex < mesh.vertices.size(); ++vertexIndex)
            {
                const auto& vertex = mesh.vertices[vertexIndex];
                if (!ValidateVec3(vertex.position) || !ValidateVec3(vertex.normal))
                {
                    error = prefix + "vertex " + std::to_string(vertexIndex) + " contains a non-finite position or normal.";
                    return false;
                }
                if (vertex.uv1 && !ValidateVec2(*vertex.uv1))
                {
                    error = prefix + "vertex " + std::to_string(vertexIndex) + " has a non-finite UV1.";
                    return false;
                }
                if (vertex.uv2 && !ValidateVec2(*vertex.uv2))
                {
                    error = prefix + "vertex " + std::to_string(vertexIndex) + " has a non-finite UV2.";
                    return false;
                }
                if (vertex.uv3 && !ValidateVec2(*vertex.uv3))
                {
                    error = prefix + "vertex " + std::to_string(vertexIndex) + " has a non-finite UV3.";
                    return false;
                }
                if (vertex.bumpMapNormal && !ValidateVec3(*vertex.bumpMapNormal))
                {
                    error = prefix + "vertex " + std::to_string(vertexIndex) + " has a non-finite bump normal.";
                    return false;
                }
            }

            for (const auto index : mesh.indices)
            {
                if (index >= mesh.vertices.size() || index > 65535)
                {
                    error = prefix + "contains a face index outside the supported 16-bit vertex range.";
                    return false;
                }
            }

            if (mesh.material.maps.size() > (std::numeric_limits<std::uint32_t>::max)() - 8)
            {
                error = prefix + "has too many material map entries.";
                return false;
            }
            for (const auto& map : mesh.material.maps)
            {
                if (map.textureIndex >= scene.texturePaths.size())
                {
                    error = prefix + "references a texture index outside the scene texture table.";
                    return false;
                }
                if (!ValidateVec2(map.tiling) || !ValidateVec2(map.offset))
                {
                    error = prefix + "contains a non-finite material map transform.";
                    return false;
                }
            }

            if (mesh.material.transparencyType > 2)
            {
                error = prefix + "contains an unsupported Standard Material transparency mode.";
                return false;
            }

            if ((mesh.material.diffuseIntensity && !IsFinite(*mesh.material.diffuseIntensity)) ||
                (mesh.material.specularIntensity && !IsFinite(*mesh.material.specularIntensity)) ||
                (mesh.material.emissiveIntensity && !IsFinite(*mesh.material.emissiveIntensity)))
            {
                error = prefix + "contains a non-finite material intensity.";
                return false;
            }
        }

        if (scene.meshes.size() > (std::numeric_limits<std::uint32_t>::max)() / 4)
        {
            error = "The scene contains too many mesh records.";
            return false;
        }

        return true;
    }
}

namespace scx
{
    bool WriteV4(const Scene& scene, std::vector<std::uint8_t>& output, std::string& error)
    {
        output.clear();
        error.clear();

        if (!ValidateScene(scene, error))
        {
            return false;
        }

        std::vector<Block> blocks;
        blocks.reserve(scene.meshes.size() * 4);

        for (const auto& mesh : scene.meshes)
        {
            blocks.push_back(MakeMaterialBlock(mesh.material));
            blocks.push_back(MakeHardSurfaceBlock(mesh.hardSurfacePayload));
            blocks.push_back(MakeVertexBlock(mesh));
            blocks.push_back(MakeFaceBlock(mesh));
        }

        const std::size_t headerSize = 12 + blocks.size() * 8;
        if (!FitsU32(headerSize))
        {
            error = "SCX v4 header exceeds the 32-bit offset range.";
            return false;
        }

        std::size_t totalSize = headerSize;
        for (const auto& block : blocks)
        {
            if (!FitsU32(block.bytes.size()) || totalSize > (std::numeric_limits<std::uint32_t>::max)() - block.bytes.size())
            {
                error = "SCX v4 output exceeds the 32-bit file-offset range.";
                return false;
            }
            totalSize += block.bytes.size();
        }

        output.reserve(totalSize);
        output.insert(output.end(), { 'I', 'N', 'V', 'O' });
        PutU32(output, 4);
        PutU32(output, static_cast<std::uint32_t>(blocks.size()));

        std::uint32_t offset = static_cast<std::uint32_t>(headerSize);
        for (const auto& block : blocks)
        {
            PutU32(output, block.type);
            PutU32(output, offset);
            offset += static_cast<std::uint32_t>(block.bytes.size());
        }

        for (const auto& block : blocks)
        {
            output.insert(output.end(), block.bytes.begin(), block.bytes.end());
        }

        return true;
    }
}
