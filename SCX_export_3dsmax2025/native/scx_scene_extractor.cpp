#include "scx_scene_extractor.h"

#include <max.h>
#include <triobj.h>
#include <units.h>

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <map>
#include <string>
#include <vector>

namespace
{
    constexpr std::size_t kMaxV4VerticesPerMesh = 65536;

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

    bool HasMapCorner(const Mesh& mesh, int channel, int faceIndex, int corner)
    {
        if (!mesh.mapSupport(channel))
        {
            return false;
        }

        const TVFace* faces = mesh.mapFaces(channel);
        const UVVert* vertices = mesh.mapVerts(channel);
        if (faces == nullptr || vertices == nullptr)
        {
            return false;
        }

        const int mapVertexIndex = static_cast<int>(faces[faceIndex].t[corner]);
        return mapVertexIndex >= 0 && mapVertexIndex < mesh.getNumMapVerts(channel);
    }

    std::optional<scx::Vec2> GetUv(const Mesh& mesh, int channel, int faceIndex, int corner)
    {
        if (!HasMapCorner(mesh, channel, faceIndex, corner))
        {
            return std::nullopt;
        }

        const TVFace* faces = mesh.mapFaces(channel);
        const UVVert* vertices = mesh.mapVerts(channel);
        const UVVert& uv = vertices[faces[faceIndex].t[corner]];
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

    scx::Vertex ExtractCorner(
        const Mesh& mesh,
        int faceIndex,
        int corner,
        const Matrix3& objectTM,
        const Matrix3& normalTM,
        double fileUnitsPerMaxUnit)
    {
        const Face& face = mesh.getFace(faceIndex);
        const int vertexIndex = static_cast<int>(face.v[corner]);

        const Point3 worldPosition = mesh.getVert(vertexIndex) * objectTM;
        const Point3 filePosition(
            static_cast<float>(worldPosition.x * fileUnitsPerMaxUnit),
            static_cast<float>(worldPosition.z * fileUnitsPerMaxUnit),
            static_cast<float>(-worldPosition.y * fileUnitsPerMaxUnit));

        Point3 localNormal = mesh.ComputeRenderNormal(faceIndex, corner);
        Point3 worldNormal = VectorTransform(normalTM, localNormal);
        worldNormal = ConvertNormal(worldNormal);

        scx::Vertex vertex;
        vertex.position = { filePosition.x, filePosition.y, filePosition.z };
        vertex.normal = { worldNormal.x, worldNormal.y, worldNormal.z };
        vertex.uv1 = GetUv(mesh, 1, faceIndex, corner);
        vertex.uv2 = GetUv(mesh, 2, faceIndex, corner);
        vertex.uv3 = GetUv(mesh, 3, faceIndex, corner);
        return vertex;
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

        ObjectState objectState = node->EvalWorldState(time);
        Object* evaluatedObject = objectState.obj;
        if (evaluatedObject == nullptr ||
            !evaluatedObject->CanConvertToType(Class_ID(TRIOBJ_CLASS_ID, 0)))
        {
            return;
        }

        const Matrix3 objectTM = node->GetObjTMAfterWSM(time);
        Matrix3 normalTM;
        AffineTranspose(normalTM, objectTM);

        TriObject* triObject = static_cast<TriObject*>(
            evaluatedObject->ConvertToType(time, Class_ID(TRIOBJ_CLASS_ID, 0)));
        if (triObject == nullptr)
        {
            error = "Could not convert node '" + ToUtf8(node->GetName()) + "' to a triangular mesh.";
            return;
        }

        const bool deleteTriObject = triObject != evaluatedObject;
        const Mesh& mesh = triObject->GetMesh();

        if (mesh.getNumFaces() == 0)
        {
            if (deleteTriObject) triObject->DeleteMe();
            return;
        }

        const std::string nodeName = ToUtf8(node->GetName());
        Mtl* nodeMaterial = node->GetMtl();

        struct MaterialBucket
        {
            scx::Material material;
            std::vector<scx::Mesh> chunks;
            scx::Mesh current;
            bool initialized = false;
        };

        std::map<MtlID, MaterialBucket> buckets;

        for (int faceIndex = 0; faceIndex < mesh.getNumFaces(); ++faceIndex)
        {
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
                const scx::Vertex vertex = ExtractCorner(
                    mesh, faceIndex, corner, objectTM, normalTM, fileUnitsPerMaxUnit);

                const std::uint32_t newIndex =
                    static_cast<std::uint32_t>(bucket.current.vertices.size());

                bucket.current.vertices.push_back(vertex);
                bucket.current.indices.push_back(newIndex);
            }
        }

        if (deleteTriObject)
        {
            triObject->DeleteMe();
        }

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

        const double centimetresPerSystemUnit = GetSystemUnitScale(UNITS_CENTIMETERS);
        if (!std::isfinite(centimetresPerSystemUnit) || centimetresPerSystemUnit <= 0.0)
        {
            error = "3ds Max returned an invalid system-unit scale.";
            return false;
        }

        // SCX v4 positions are read by independent tooling as file-units / 100.
        // Store Max centimetres * 100, after the Z-up Max to Y-up SCX axis map.
        const double fileUnitsPerMaxUnit = centimetresPerSystemUnit * 100.0;
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
