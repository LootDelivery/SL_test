#include <max.h>
#include <iparamb2.h>
#include <impexp.h>

namespace
{
    // A dedicated ID for this temporary load-test exporter.
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
            return index == 0 ? _M("scxprobe") : nullptr;
        }

        const MCHAR* LongDesc() override
        {
            return _M("SCX exporter registration test (*.scxprobe)");
        }

        const MCHAR* ShortDesc() override
        {
            return _M("SCX Exporter Load Test");
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
            // No UI is needed for the registration test.
        }

        int DoExport(
            const MCHAR*,
            ExpInterface*,
            Interface*,
            BOOL,
            DWORD) override
        {
            // Intentionally do not write a file in this milestone.
            return IMPEXP_FAIL;
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
            return _M("SCX Exporter Load Test");
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
        return _M("SCX Exporter Load Test for 3ds Max 2025");
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
