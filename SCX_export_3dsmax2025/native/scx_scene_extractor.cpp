#include "scx_scene_extractor.h"

#include <max.h>
#include <triobj.h>
#include <units.h>

#include <algorithm>
#include <cmath>
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
                log << message << "\\n";
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

    scx::Material ExtractMaterial(Mtl* material, const std::string& fallbackName)
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

        // Texture-map enumeration is deliberately deferred. SLRR uses
        // external .tex indices and map-channel metadata; guessing indices
        // from Max texture slots would silently create broken materials.
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

        const Face& face = mesh.getFace(faceIndex);
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
        Mesh& mesh = triObject->GetMesh();

        if (mesh.getNumFaces() == 0)
        {
            if (deleteTriObject) triObject->DeleteThis();
            return;
        }

        Mtl* nodeMaterial = node->GetMtl();
        TraceExtraction(
            "Node '" + nodeName + "': mesh acquired; vertices=" +
            std::to_string(mesh.getNumVerts()) + ", faces=" +
            std::to_string(mesh.getNumFaces()));

        struct MaterialBucket
        {
            scx::Material material;
            std::vector<scx::Mesh> chunks;
            scx::Mesh current;
            bool initialized = false;
        };

        std::map<MtlID, MaterialBucket> buckets;

        const int faceCount = mesh.getNumFaces();
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
                bucket.material = ExtractMaterial(faceMaterial, nodeName);
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
