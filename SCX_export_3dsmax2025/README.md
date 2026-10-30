# SCX exporter for 3ds Max 2025

Статус: исследование формата и план первой реализации. Старые файлы из `SCX_export_3dsmax5/` не изменяются.

## Что установлено по старым плагинам

В исходной папке находятся только бинарные модули:

- `InvictusExportGroups.dle`
- `InvictusExportV3.dle`
- `InvictusExportV4.dle`
- `Solidify.dlm`

Все четыре файла — PE32 / x86. Экспортёры собраны линкером MSVC 6.0; метки сборки находятся в диапазоне 2001–2003 годов. V4 импортирует старые интерфейсы `core.dll`, `geom.dll`, `mesh.dll`, `maxutil.dll`. В строках бинарника остались пути `C:\\3DSMAX42\\MAXSDK\\INCLUDE\\tab.h` и `C:\\Projects\\max_plugins\\MaxExportver4\\ascout.cpp`.

Вывод: эти бинарники нельзя загрузить в 64-битный 3ds Max 2025. В репозитории нет исходников плагинов, поэтому прямой пересборки пока не из чего делать.

## Что известно о SCX

В репозитории есть контрольная модель `F_windshield.scx`. Её заголовок начинается с `INVO`, версия — 4.

Найдена независимая реализация чтения формата: [nonamesex/io_invo_scx](https://github.com/nonamesex/io_invo_scx). Это именно импортёр Blender, а не экспортёр; он читает SCX v3 и v4. В репозитории также лежит описание структуры для ImHex: `imhex/scx.hexpat`.

Из структуры v4 уже известны основные типы записей:

| Type | Назначение |
|---:|---|
| 0 | Material |
| 1 | Hard surface |
| 2 | Sparse bone index list |
| 3 | Bone index list |
| 4 | Vertex data |
| 5 | Face indices |

Записи перечисляются в таблице заголовка с абсолютными смещениями от начала файла. В данных вершин используются флаги каналов: позиция, веса/индексы костей, нормаль, vertex emissive, vertex color, UV1–UV3 и bump-map normal. Материалы состоят из типизированных записей цвета, интенсивности, карт и имени.

## План реализации

1. Сделать независимый валидатор SCX v4: проверка сигнатуры, версии, таблицы смещений, размеров блоков, каналов вершин и индексов.
2. Собрать тестовую сцену и эталонный экспорт из 3ds Max 2025.
3. Реализовать первую версию для статической геометрии: треугольники, отдельные подмеши по материалам, позиции, нормали и UV. Добавить vertex color и дополнительные UV-каналы после базовой проверки.
4. Проверить результат независимым SCX-импортёром и затем в SLRR.
5. Отдельным этапом изучить физические меши, кости/skinning, карты материалов и поведение групп — эти функции не следует объявлять рабочими, пока они не пройдут отдельные тесты.

## Возможные способы интеграции в Max 2025

- Нативный `.dle` SceneExport под x64 SDK 2025. Это наиболее близко к старому плагину, но потребует C++-сборки с актуальным SDK.
- MAXScript-экспортёр как более быстрый первый прототип. Он позволит проверить структуру файла и данные геометрии до того, как тратить время на C++-интерфейс.

Выбор делается после минимального round-trip теста. Никакие существующие плагины или игровые файлы в этой ветке не заменяются.

## Контрольный материал

- `../SCX_export_3dsmax5/` — исходные старые бинарники, только для анализа.
- `../F_windshield.scx` и `../F_windshield_edited.scx` — две контрольные модели.
- [SCX v3/v4 reader and ImHex pattern](https://github.com/nonamesex/io_invo_scx).


## Current progress

- Legacy PE32 modules inspected: compiler/linker era, imported Max SDK APIs, resource strings and export dialogs.
- The original option labels are documented in [LEGACY_PLUGIN_UI.md](LEGACY_PLUGIN_UI.md).
- The full migration scope is tracked in [FEATURE_MATRIX.md](FEATURE_MATRIX.md).
- A standalone SCX v3/v4 inspector and synthetic unit tests are in [tools/](tools/) and [tests/](tests/). A structural check of the two repository windshield fixtures passed: each is SCX v4 with eight records, two material blocks, two vertex blocks (38 vertices each) and two face blocks (50 triangles each).
- The native build prerequisites and the exact local information needed next are in [BUILD_ENVIRONMENT.md](BUILD_ENVIRONMENT.md).

The native x64 plug-in now has an initial SCX v4 geometry path: Max scene extraction, material splits, corner normals, UV1–UV3, and a bounded v4 serializer. It is experimental and not yet validated in SLRR; V3, external textures, Physics, skinning, Animation, Pivot, Groups, and Solidify compatibility remain in the porting scope.
