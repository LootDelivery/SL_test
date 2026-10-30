#include <max.h>
#include <iparamb2.h>
#include <impexp.h>

#include "scx_scene_extractor.h"
#include "scx_v4_writer.h"

#include <filesystem>
#include <fstream>
#include <exception>
#include <limits>
#include <string>
#include <vector>

namespace
{
    // Dedicated class ID for the SLRR SCX v4 exporter prototype.
    const Class_ID kScxExporterLoadTestClassId(0x5B7C19A3, 0x3E6D48F1);

    HINSTANCE g_moduleInstance = nullptr;

    class ScxExporterLoadTest final : public SceneExport
    {
    public:
        int ExtCount() override
        {
            return 1;
        }

        const MCHAR* Ext(int index) override
        {
            return index == 0 ? _M("scx") : nullptr;
        }

        BOOL SupportsOptions(int ext, DWORD options) override
        {
            // Advertise the Max "Export Selected" command for this extension.
            // Unknown option bits are deliberately rejected.
            return ext == 0 && (options & ~SCENE_EXPORT_SELECTED) == 0;
        }

        const MCHAR* LongDesc() override
        {
            return _M("SLRR SCX v4 Exporter prototype (*.scx)");
        }

        const MCHAR* ShortDesc() override
        {
            return _M("SLRR SCX v4 Exporter");
        }

        const MCHAR* AuthorName() override
        {
            return _M("LootDelivery");
        }

        const MCHAR* CopyrightMessage() override
        {
            return _M("");
        }

        const MCHAR* OtherMessage1() override
        {
            return _M("");
        }

        const MCHAR* OtherMessage2() override
        {
            return _M("");
        }

        unsigned int Version() override
        {
            return 1;
        }

        void ShowAbout(HWND) override
        {
            // The prototype has no settings dialog yet.
        }

        int DoExport(
            const MCHAR* fileName,
            ExpInterface*,
            Interface* ip,
            BOOL suppressPrompts,
            DWORD options) override
        {
            const auto reportError = [suppressPrompts](const std::string& message)
            {
                OutputDebugStringA(("SLRR SCX exporter: " + message + "\n").c_str());
                if (!suppressPrompts)
                {
                    MessageBoxA(
                        GetCOREInterface()->GetMAXHWnd(),
                        message.c_str(),
                        "SLRR SCX v4 Exporter",
                        MB_OK | MB_ICONERROR);
                }
            };

            if (fileName == nullptr || fileName[0] == 0 || ip == nullptr)
            {
                reportError("Invalid export file name or 3ds Max interface.");
                return IMPEXP_FAIL;
            }

            scx::Scene scene;
            std::string error;
            const bool selectedOnly = (options & SCENE_EXPORT_SELECTED) != 0;
            if (!scx::ExtractMaxScene(*ip, selectedOnly, scene, error))
            {
                reportError(error);
                return IMPEXP_FAIL;
            }

            std::vector<std::uint8_t> bytes;
            if (!scx::WriteV4(scene, bytes, error))
            {
                reportError(error);
                return IMPEXP_FAIL;
            }

            if (bytes.size() > static_cast<std::size_t>((std::numeric_limits<std::streamsize>::max)()))
            {
                reportError("The generated SCX file is too large for the current output stream.");
                return IMPEXP_FAIL;
            }

            try
            {
                const std::filesystem::path outputPath(fileName);
                std::ofstream outputFile(
                    outputPath,
                    std::ios::binary | std::ios::trunc);

                if (!outputFile)
                {
                    reportError("Could not open the output file for writing.");
                    return IMPEXP_FAIL;
                }

                outputFile.write(
                    reinterpret_cast<const char*>(bytes.data()),
                    static_cast<std::streamsize>(bytes.size()));
                outputFile.close();

                if (!outputFile)
                {
                    reportError("An error occurred while writing the SCX file.");
                    return IMPEXP_FAIL;
                }
            }
            catch (const std::exception& exception)
            {
                reportError(std::string("File output failed: ") + exception.what());
                return IMPEXP_FAIL;
            }

            return IMPEXP_SUCCESS;
        }
    };

    class ScxExporterLoadTestClassDesc final : public ClassDesc2
    {
    public:
        int IsPublic() override
        {
            return TRUE;
        }

        void* Create(BOOL) override
        {
            return new ScxExporterLoadTest();
        }

        const MCHAR* ClassName() override
        {
            return _M("SLRR SCX v4 Exporter");
        }

        SClass_ID SuperClassID() override
        {
            return SCENE_EXPORT_CLASS_ID;
        }

        Class_ID ClassID() override
        {
            return kScxExporterLoadTestClassId;
        }

        const MCHAR* Category() override
        {
            return _M("SCX Tools");
        }

        const MCHAR* NonLocalizedClassName() override
        {
            return _M("SLRR SCX v4 Exporter");
        }

        const MCHAR* InternalName() override
        {
            return _M("SCXExporterLoadTest");
        }

        HINSTANCE HInstance() override
        {
            return g_moduleInstance;
        }
    };

    ScxExporterLoadTestClassDesc g_classDescriptor;
}

BOOL WINAPI DllMain(HINSTANCE instance, ULONG reason, LPVOID)
{
    if (reason == DLL_PROCESS_ATTACH)
    {
        g_moduleInstance = instance;
        DisableThreadLibraryCalls(instance);
    }

    return TRUE;
}

extern "C"
{
    __declspec(dllexport) const MCHAR* LibDescription()
    {
        return _M("SLRR SCX v4 Exporter prototype for 3ds Max 2025");
    }

    __declspec(dllexport) int LibNumberClasses()
    {
        return 1;
    }

    __declspec(dllexport) ClassDesc* LibClassDesc(int index)
    {
        return index == 0 ? &g_classDescriptor : nullptr;
    }

    __declspec(dllexport) ULONG LibVersion()
    {
        return VERSION_3DSMAX;
    }
}
