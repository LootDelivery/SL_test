#include "scx_scene_extractor.h"

#include <max.h>
#include <triobj.h>
#include <units.h>
#include <stdmat.h>
#include <bmtex.h>

#include <algorithm>
#include <cmath>
#include <cctype>
#include <cstdint>
#include <filesystem>
#include <fstream>
#include <map>
#include <string>
#include <vector>

namespace
{
    constexpr std::size_t kMaxV4VerticesPerMesh = 65536;

    void TraceExtraction(const std::string& message)
    {
        wchar_t tempPath[MAX_PATH] = {};
        const DWORD length = GetTempPathW(MAX_PATH, tempPath);
        if (length == 0 || length >= MAX_PATH)
        {
            return;
        }

        try
        {
            std::filesystem::path logPath(tempPath);
            logPath /= L"SCXExporter_trace.log";
            std::ofstream log(logPath, std::ios::binary | std::ios::app);
            if (log)
            {
                log << message << "\n";
            }
        }
        catch (...)
        {
            // Diagnostics must never interfere with the exporter itself.
        }
    }



    std::string ToUtf8(const MCHAR* text)
    {
        if (text == nullptr || text[0] == 0)
        {
            return {};
        }

        const int required = WideCharToMultiByte(
            CP_UTF8, 0, text, -1, nullptr, 0, nullptr, nullptr);
        if (required <= 1)
        {
            return {};
        }

        std::string result(static_cast<std::size_t>(required), '\0');
        const int written = WideCharToMultiByte(
            CP_UTF8, 0, text, -1, result.data(), required, nullptr, nullptr);
        if (written <= 1)
        {
            return {};
        }

        result.resize(static_cast<std::size_t>(written - 1));
        return result;
    }

    float Clamp01(float value)
    {
        return (std::max)(0.0f, (std::min)(1.0f, value));
    }

    std::uint8_t ToByte(float value)
    {
        return static_cast<std::uint8_t>(std::lround(Clamp01(value) * 255.0f));
    }

    scx::Color8 ToColor(const Color& color, float alpha = 1.0f)
    {
        return {
            ToByte(color.r),
            ToByte(color.g),
            ToByte(color.b),
            ToByte(alpha)
        };
    }

    std::string NormalizeTexturePath(const std::string& path)
    {
        std::string normalized = path;
        for (char& character : normalized)
        {
            if (character == '\\')
            {
                character = '/';
            }
            character = static_cast<char>(
                std::tolower(static_cast<unsigned char>(character)));
        }
        return normalized;
    }

    std::uint32_t RegisterTexturePath(scx::Scene& scene, const std::string& path)
    {
        const std::string key = NormalizeTexturePath(path);
        for (std::size_t index = 0; index < scene.texturePaths.size(); ++index)
        {
            if (NormalizeTexturePath(scene.texturePaths[index]) == key)
            {
                return static_cast<std::uint32_t>(index);
            }
        }

        const std::uint32_t index = static_cast<std::uint32_t>(scene.texturePaths.size());
        scene.texturePaths.push_back(path);
        return index;
    }

    void AddStandardBitmapMap(
        Mtl* material,
        StdMat2* standardMaterial,
        int standardMapSlot,
        scx::MaterialMapEntry entry,
        bool environmentMap,
        TimeValue time,
        scx::Scene& scene,
        scx::Material& outputMaterial)
    {
        const int mapState = standardMaterial->GetMapState(standardMapSlot);
        if (mapState != 2)
        {
            if (mapState == 1)
            {
                TraceExtraction(
                    "Material '" + outputMaterial.name + "': map slot " +
                    std::to_string(standardMapSlot) + " is present but disabled");
            }
            return;
        }

        Texmap* textureMap = material->GetSubTexmap(standardMapSlot);
        if (textureMap == nullptr)
        {
            TraceExtraction(
                "Material '" + outputMaterial.name + "': active map slot " +
                std::to_string(standardMapSlot) + " has no Texmap object");
            return;
        }

        if (textureMap->ClassID() != Class_ID(BMTEX_CLASS_ID, 0))
        {
            TraceExtraction(
                "Material '" + outputMaterial.name + "': map slot " +
                std::to_string(standardMapSlot) +
                " is not a direct BitmapTex; skipped");
            return;
        }

        BitmapTex* bitmap = static_cast<BitmapTex*>(textureMap);
        const std::string texturePath = ToUtf8(bitmap->GetMapName());
        if (texturePath.empty())
        {
            TraceExtraction(
                "Material '" + outputMaterial.name + "': BitmapTex slot " +
                std::to_string(standardMapSlot) + " has an empty path; skipped");
            return;
        }

        scx::MaterialMap map;
        map.entry = entry;
        map.textureIndex = RegisterTexturePath(scene, texturePath);

        StdUVGen* uvGenerator = bitmap->GetUVGen();
        if (uvGenerator != nullptr)
        {
            // SCX stores ordinary mesh UV channels as zero-based indices:
            // Max channel 1 corresponds to SCX channel 0 in the reference files.
            if (environmentMap)
            {
                // FL_seat.scx and F_bumper.scx both use 0xFFFFFFFE (-2)
                // for reflection/environment maps rather than a mesh UV set.
                map.mapChannel = 0xFFFFFFFEu;
            }
            else
            {
                const int maxMapChannel = uvGenerator->GetMapChannel();
                const int scxMapChannel =
                    maxMapChannel >= 1 ? maxMapChannel - 1 : maxMapChannel;
                map.mapChannel = static_cast<std::uint32_t>(
                    static_cast<std::int32_t>(scxMapChannel));
            }

            map.tilingFlags = static_cast<std::uint32_t>(
                uvGenerator->GetTextureTiling());
            map.tiling = {
                uvGenerator->GetUScl(time),
                uvGenerator->GetVScl(time)
            };
            map.offset = {
                uvGenerator->GetUOffs(time),
                uvGenerator->GetVOffs(time)
            };
        }
        else if (environmentMap)
        {
            map.mapChannel = 0xFFFFFFFEu;
        }

        outputMaterial.maps.push_back(map);

        TraceExtraction(
            "Material '" + outputMaterial.name + "': SCX map 0x" +
            [&entry]() {
                char value[9] = {};
                std::snprintf(value, sizeof(value), "%08X",
                    static_cast<unsigned int>(entry));
                return std::string(value);
            }() +
            " -> texture[" + std::to_string(map.textureIndex) + "] " + texturePath +
            ", channel=" + std::to_string(static_cast<std::int32_t>(map.mapChannel)));
    }

    scx::Material ExtractMaterial(
        Mtl* material,
        const std::string& fallbackName,
        TimeValue time,
        scx::Scene& scene)
    {
        scx::Material result;
        result.name = fallbackName.empty() ? "Default" : fallbackName;

        if (material == nullptr)
        {
            return result;
        }

        const std::string materialName = ToUtf8(material->GetName().data());
        if (!materialName.empty())
        {
            result.name = materialName;
        }

        const float transparency = Clamp01(material->GetXParency());
        result.diffuse = ToColor(material->GetDiffuse(), 1.0f - transparency);
        result.specular = ToColor(material->GetSpecular());

        const float specularStrength = material->GetShinStr() * 100.0f;
        if (std::isfinite(specularStrength) && specularStrength > 0.0f)
        {
            result.specularIntensity = specularStrength;
        }

        if (material->GetSelfIllumColorOn())
        {
            result.emissive = ToColor(material->GetSelfIllumColor());
        }

        if (material->ClassID() == Class_ID(DMTL_CLASS_ID, 0))
        {
            StdMat2* standardMaterial = static_cast<StdMat2*>(material);

            // The reference SCX files establish these slot-to-entry mappings:
            // Diffuse -> DiffuseMap, Ambient -> DiffuseMixSecond,
            // Bump -> BumpMap, Reflection -> ReflectionMap,
            // Self-Illumination -> EmissiveMap.
            AddStandardBitmapMap(
                material, standardMaterial, ID_DI,
                scx::MaterialMapEntry::Diffuse, false, time, scene, result);
            AddStandardBitmapMap(
                material, standardMaterial, ID_AM,
                scx::MaterialMapEntry::DiffuseMixSecond, false, time, scene, result);
            AddStandardBitmapMap(
                material, standardMaterial, ID_BU,
                scx::MaterialMapEntry::Bump, false, time, scene, result);
            AddStandardBitmapMap(
                material, standardMaterial, ID_RL,
                scx::MaterialMapEntry::Reflection, true, time, scene, result);
            AddStandardBitmapMap(
                material, standardMaterial, ID_SI,
                scx::MaterialMapEntry::Emissive, false, time, scene, result);
        }
        else
        {
            TraceExtraction(
                "Material '" + result.name +
                "' is not a 3ds Max Standard Material; texture slots were not read");
        }

        return result;
    }

    Mtl* GetFaceMaterial(Mtl* nodeMaterial, MtlID materialId)
    {
        if (nodeMaterial == nullptr)
        {
            return nullptr;
        }

        const int subMaterialCount = nodeMaterial->NumSubMtls();
        if (subMaterialCount > 0)
        {
            if (materialId < static_cast<MtlID>(subMaterialCount))
            {
                Mtl* subMaterial = nodeMaterial->GetSubMtl(static_cast<int>(materialId));
                if (subMaterial != nullptr)
                {
                    return subMaterial;
                }
            }
            return nodeMaterial;
        }

        return nodeMaterial;
    }

    std::optional<scx::Vec2> GetUv(const Mesh& mesh, int channel, int faceIndex, int corner)
    {
        // Mapping support alone does not guarantee an allocated map-face array
        // for every geometric face. Check both counts before requesting arrays.
        if (faceIndex < 0 || faceIndex >= mesh.getNumFaces() ||
            !mesh.mapSupport(channel) ||
            mesh.getNumMapFaces(channel) <= faceIndex ||
            mesh.getNumMapVerts(channel) <= 0)
        {
            return std::nullopt;
        }

        if (faceIndex == 0)
        {
            TraceExtraction("UV channel " + std::to_string(channel) + ": requesting map arrays");
        }

        const TVFace* faces = mesh.mapFaces(channel);
        const UVVert* vertices = mesh.mapVerts(channel);
        if (faces == nullptr || vertices == nullptr)
        {
            return std::nullopt;
        }

        const int mapVertexIndex = static_cast<int>(faces[faceIndex].t[corner]);
        if (mapVertexIndex < 0 || mapVertexIndex >= mesh.getNumMapVerts(channel))
        {
            return std::nullopt;
        }

        const UVVert& uv = vertices[mapVertexIndex];
        return scx::Vec2{ uv.x, uv.y };
    }

    Point3 ConvertNormal(const Point3& value)
    {
        Point3 result(value.x, value.z, -value.y);
        const float length = Length(result);
        if (length > 1.0e-8f)
        {
            result /= length;
        }
        return result;
    }

    bool ExtractCorner(
        Mesh& mesh,
        int faceIndex,
        int corner,
        const Matrix3& objectTM,
        double fileUnitsPerMaxUnit,
        scx::Vertex& vertex,
        std::string& error)
    {
        if (faceIndex < 0 || faceIndex >= mesh.getNumFaces() || corner < 0 || corner > 2)
        {
            error = "3ds Max returned an out-of-range face or corner index.";
            return false;
        }

        if (faceIndex == 0 && corner == 0)
        {
            TraceExtraction("First face corner: begin getFace");
        }
        const Face& face = mesh.getFace(faceIndex);
        if (faceIndex == 0 && corner == 0)
        {
            TraceExtraction("First face corner: getFace returned");
        }
        Point3 worldPositions[3];

        for (int index = 0; index < 3; ++index)
        {
            const int sourceVertex = static_cast<int>(face.v[index]);
            if (sourceVertex < 0 || sourceVertex >= mesh.getNumVerts())
            {
                error = "A triangular face references a vertex outside the mesh vertex array.";
                return false;
            }
            worldPositions[index] = mesh.getVert(sourceVertex) * objectTM;
        }

        // Avoid Mesh::ComputeRenderNormal in this first crash-isolation build.
        // A flat normal computed from transformed triangle positions is safe
        // for a basic geometry test. Smoothing-group normals will be restored
        // after the conversion path is stable.
        Point3 worldNormal = CrossProd(
            worldPositions[1] - worldPositions[0],
            worldPositions[2] - worldPositions[0]);
        const float normalLength = Length(worldNormal);
        if (!std::isfinite(normalLength) || normalLength <= 1.0e-8f)
        {
            error = "A degenerate face produced an invalid surface normal.";
            return false;
        }
        worldNormal /= normalLength;
        worldNormal = ConvertNormal(worldNormal);

        const Point3& worldPosition = worldPositions[corner];
        const Point3 filePosition(
            static_cast<float>(worldPosition.x * fileUnitsPerMaxUnit),
            static_cast<float>(worldPosition.z * fileUnitsPerMaxUnit),
            static_cast<float>(-worldPosition.y * fileUnitsPerMaxUnit));

        vertex.position = { filePosition.x, filePosition.y, filePosition.z };
        vertex.normal = { worldNormal.x, worldNormal.y, worldNormal.z };
        vertex.uv1 = GetUv(mesh, 1, faceIndex, corner);
        vertex.uv2 = GetUv(mesh, 2, faceIndex, corner);
        vertex.uv3 = GetUv(mesh, 3, faceIndex, corner);
        return true;
    }

    void AddNodeMeshes(
        INode* node,
        TimeValue time,
        double fileUnitsPerMaxUnit,
        scx::Scene& scene,
        std::string& error)
    {
        if (node == nullptr)
        {
            return;
        }

        const std::string nodeName = ToUtf8(node->GetName());
        TraceExtraction("Node '" + nodeName + "': begin EvalWorldState");

        ObjectState objectState = node->EvalWorldState(time);
        Object* evaluatedObject = objectState.obj;
        TraceExtraction("Node '" + nodeName + "': EvalWorldState returned");
        if (evaluatedObject == nullptr)
        {
            TraceExtraction("Node '" + nodeName + "': evaluated object is null; skipped");
            return;
        }

        if (!evaluatedObject->CanConvertToType(Class_ID(TRIOBJ_CLASS_ID, 0)))
        {
            TraceExtraction("Node '" + nodeName + "': cannot convert to TriObject; skipped");
            return;
        }

        TraceExtraction("Node '" + nodeName + "': CanConvertToType succeeded");

        const Matrix3 objectTM = node->GetObjTMAfterWSM(time);

        TraceExtraction("Node '" + nodeName + "': begin ConvertToType");
        TriObject* triObject = static_cast<TriObject*>(
            evaluatedObject->ConvertToType(time, Class_ID(TRIOBJ_CLASS_ID, 0)));
        TraceExtraction("Node '" + nodeName + "': ConvertToType returned");
        if (triObject == nullptr)
        {
            error = "Could not convert node '" + ToUtf8(node->GetName()) + "' to a triangular mesh.";
            return;
        }

        const bool deleteTriObject = triObject != evaluatedObject;
        TraceExtraction("Node '" + nodeName + "': begin GetMesh");
        Mesh& mesh = triObject->GetMesh();
        TraceExtraction("Node '" + nodeName + "': GetMesh returned");

        const int meshVertexCount = mesh.getNumVerts();
        const int meshFaceCount = mesh.getNumFaces();
        TraceExtraction(
            "Node '" + nodeName + "': mesh counts read; vertices=" +
            std::to_string(meshVertexCount) + ", faces=" +
            std::to_string(meshFaceCount));

        if (meshFaceCount == 0)
        {
            if (deleteTriObject) triObject->DeleteThis();
            return;
        }

        TraceExtraction("Node '" + nodeName + "': begin GetMtl");
        Mtl* nodeMaterial = node->GetMtl();
        TraceExtraction("Node '" + nodeName + "': GetMtl returned");

        struct MaterialBucket
        {
            scx::Material material;
            std::vector<scx::Mesh> chunks;
            scx::Mesh current;
            bool initialized = false;
        };

        std::map<MtlID, MaterialBucket> buckets;

        const int faceCount = meshFaceCount;
        for (int faceIndex = 0; faceIndex < faceCount; ++faceIndex)
        {
            if (faceIndex == 0 || (faceIndex + 1) % 100 == 0)
            {
                TraceExtraction(
                    "Node '" + nodeName + "': processing face " +
                    std::to_string(faceIndex + 1) + "/" + std::to_string(faceCount));
            }

            const MtlID materialId = mesh.getFaceMtlIndex(faceIndex);
            MaterialBucket& bucket = buckets[materialId];

            if (!bucket.initialized)
            {
                Mtl* faceMaterial = GetFaceMaterial(nodeMaterial, materialId);
                bucket.material = ExtractMaterial(faceMaterial, nodeName, time, scene);
                bucket.current.name = nodeName;
                bucket.current.material = bucket.material;
                bucket.initialized = true;
            }

            if (bucket.current.vertices.size() + 3 > kMaxV4VerticesPerMesh)
            {
                bucket.chunks.push_back(std::move(bucket.current));
                bucket.current = scx::Mesh{};
                bucket.current.name = nodeName;
                bucket.current.material = bucket.material;
            }

            for (int corner = 0; corner < 3; ++corner)
            {
                scx::Vertex vertex;
                if (!ExtractCorner(
                    mesh, faceIndex, corner, objectTM, fileUnitsPerMaxUnit, vertex, error))
                {
                    if (deleteTriObject)
                    {
                        triObject->DeleteThis();
                    }
                    return;
                }

                const std::uint32_t newIndex =
                    static_cast<std::uint32_t>(bucket.current.vertices.size());

                bucket.current.vertices.push_back(vertex);
                bucket.current.indices.push_back(newIndex);
            }
        }

        if (deleteTriObject)
        {
            triObject->DeleteThis();
        }

        TraceExtraction("Node '" + nodeName + "': geometry loop completed");

        for (auto& pair : buckets)
        {
            MaterialBucket& bucket = pair.second;
            if (!bucket.current.indices.empty())
            {
                bucket.chunks.push_back(std::move(bucket.current));
            }

            for (auto& chunk : bucket.chunks)
            {
                scene.meshes.push_back(std::move(chunk));
            }
        }
    }

    void CollectNodes(INode* node, std::vector<INode*>& nodes)
    {
        if (node == nullptr)
        {
            return;
        }

        nodes.push_back(node);

        const int childCount = node->NumberOfChildren();
        for (int childIndex = 0; childIndex < childCount; ++childIndex)
        {
            CollectNodes(node->GetChildNode(childIndex), nodes);
        }
    }
}

namespace scx
{
    bool ExtractMaxScene(
        Interface& ip,
        bool selectedOnly,
        Scene& output,
        std::string& error)
    {
        output.meshes.clear();
        output.texturePaths.clear();
        error.clear();
        TraceExtraction(selectedOnly ? "Extraction: selected-only started" : "Extraction: full-scene started");

        const double centimetresPerSystemUnit = GetSystemUnitScale(UNITS_CENTIMETERS);
        if (!std::isfinite(centimetresPerSystemUnit) || centimetresPerSystemUnit <= 0.0)
        {
            error = "3ds Max returned an invalid system-unit scale.";
            return false;
        }

        // SCX positions are centimetres; the independent reader divides by 100
        // to convert them to metres. Convert Max system units to centimetres.
        const double fileUnitsPerMaxUnit = centimetresPerSystemUnit;
        const TimeValue time = ip.GetTime();

        std::vector<INode*> nodes;
        if (selectedOnly)
        {
            const int selectedCount = ip.GetSelNodeCount();
            nodes.reserve(static_cast<std::size_t>(selectedCount));
            for (int index = 0; index < selectedCount; ++index)
            {
                nodes.push_back(ip.GetSelNode(index));
            }
        }
        else
        {
            CollectNodes(ip.GetRootNode(), nodes);
        }

        for (INode* node : nodes)
        {
            if (node == nullptr || node == ip.GetRootNode())
            {
                continue;
            }

            AddNodeMeshes(node, time, fileUnitsPerMaxUnit, output, error);
            if (!error.empty())
            {
                return false;
            }
        }

        TraceExtraction("Extraction: completed; exported mesh chunks=" + std::to_string(output.meshes.size()));

        if (output.meshes.empty())
        {
            error = selectedOnly
                ? "No triangular mesh objects were found in the selection."
                : "No triangular mesh objects were found in the scene.";
            return false;
        }

        return true;
    }
}
