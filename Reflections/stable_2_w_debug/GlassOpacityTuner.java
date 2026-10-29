package java.game.parts.bodypart;

import java.io.*;
import java.util.*;
import java.util.resource.*;
import java.render.*;
import java.render.osd.*;
import java.game.*;
import java.game.parts.*;

public class GlassOpacityTuner
{
        static final int CMD_GLASS_OPACITY = 9301;
        static final int CMD_GLASS_TINT_PRESET_BASE = 9320;

        // The chosen alpha is persisted in the part record. Runtime render handles are rebuilt after installation.
        int opacity = 127;
        int oldOpacity = 127;
        int tintR = 22;
        int tintG = 13;
        int tintB = 13;
        int oldTintR = 22;
        int oldTintG = 13;
        int oldTintB = 13;
        int hasOpacityOverride = 0;
        // Persistent user choice is separate from the temporary render-type override.
        int appearanceCustomized = 0;
        int oldAppearanceCustomized = 0;
        int restorePending = 0;
        int tuningHadOverride = 0;
        // Inventory preview render children can appear one frame after show().
        // Keep the temporary render type alive until a child instance exists.
        int previewRefreshInProgress = 0;
        RenderRef sourceTypeHandle;
        ResourceRef sourceMeshHandle;
        ResourceRef targetMeshHandle;
        RenderRef privateTypeHandle;

        int originalRenderType = 0;
        // --- reflection strength (requires dinput8.dll v1.4+ runtime hook) ---
        // Protocol: set() is ALSO used by the stock ResourceRef(int) constructor,
        // so the DLL only intercepts values with the magic prefix 0x7AFE0000
        // (sent as REFL_MAGIC + percent -- plain ADDITION, no bitwise ops:
        // this older SLRR VM has shown stack errors in bit-shift-heavy code)
        // and forwards everything else to the original native untouched.
        //
        // v1.4 semantics: the hook patches the ReflectionIntensity material
        // entries of the MESH RESOURCE the ref points to (both the runtime
        // entry arrays and the raw SCX chunk copies, so re-parses keep it).
        // We patch the part's ORIGINAL shared mesh -- like a live .scx edit.
        // Consequence: every car using the same glass part shows the same
        // reflection strength (the resource is shared engine-wide).
        static final int CMD_GLASS_REFLECTION = 9340;
        static final int STOCK_REFLECTION = 30;      // slider default; visual stock outer glass ~ 50
        // v1.8: computed from SMALL literals (31486 * 65536 = 0x7AFE0000).
        // Immune to any hex-literal / big-constant parser quirk in the
        // game compiler -- small ints and one multiply always survive.
        static final int REFL_MAGIC = 31486 * 65536;    // DLL hook command prefix
        static final int REFL_DUMP   = REFL_MAGIC + 255;  // DLL hook: dump resource
        // v1.12: the v1.11 second band (0x7AFD0000, render TYPE receiver) is
        // RETIRED. That premise was wrong: the type resource's GetData object
        // is not mesh-shaped, and the DLL's mesh walk on it crashed the game
        // on car load / save. The live wrappers are created per render child
        // when the engine (re)creates it (Part_rebind*Res), re-reading the
        // mesh's raw SCX copies -- which the MESH patch above already covers.
        // The real-time update is now forced right here: refreshRenderChild()
        // bounces the part's render type A -> B -> A inside one script
        // handler, so the engine rebuilds the render child from the just-
        // patched copies before the next frame is drawn.
        int reflection = STOCK_REFLECTION;
        int oldReflection = STOCK_REFLECTION;
        int originalMesh = 0;
        int renderInstance = 0;
        Button[] glassTintButtons;

        // Version-4 part payload: override marker, alpha, and RGB tint. Runtime handles are not saved.
        public void save( File saveGame )
        {
                int savedOverride = 0;
                if (appearanceCustomized != 0 || hasOpacityOverride != 0 || restorePending != 0)
                        savedOverride = 1;

                saveGame.write( savedOverride );
                saveGame.write( opacity );
                saveGame.write( tintR );
                saveGame.write( tintG );
                saveGame.write( tintB );
                saveGame.write( reflection );
        }

        public void load( File saveGame, int version )
        {
                int savedOverride = saveGame.readInt();
                int savedOpacity = saveGame.readInt();

                if (savedOpacity < 0) savedOpacity = 0;
                if (savedOpacity > 255) savedOpacity = 255;

                opacity = savedOpacity;
                oldOpacity = savedOpacity;
                tintR = 22;
                tintG = 13;
                tintB = 13;
                if (version >= 4)
                {
                        tintR = saveGame.readInt();
                        tintG = saveGame.readInt();
                        tintB = saveGame.readInt();
                }
                if (version >= 5)
                        reflection = clampPercent(saveGame.readInt());
                tintR = clampColor(tintR);
                tintG = clampColor(tintG);
                tintB = clampColor(tintB);
                oldTintR = tintR;
                oldTintG = tintG;
                oldTintB = tintB;
                hasOpacityOverride = 0;

                // Older saves may contain the chosen values even if the transient
                // render-type flag was lost before saving. Recover that customization.
                appearanceCustomized = 0;
                if (savedOverride != 0 || opacity != 127 || tintR != 22 || tintG != 13 || tintB != 13)
                        appearanceCustomized = 1;
                oldAppearanceCustomized = appearanceCustomized;
                restorePending = appearanceCustomized;
                tuningHadOverride = 0;
        }

        int clampPercent( int value )
        {
                if (value < 0) return 0;
                if (value > 100) return 100;
                return value;
        }

        int clampColor( int value )
        {
                if (value < 0) return 0;
                if (value > 255) return 255;
                return value;
        }

        int opacityPercent()
        {
                return (opacity * 100 + 127) / 255;
        }

        int snapPercentToFive( int value )
        {
                if (value < 0) value = 0;
                if (value > 100) value = 100;
                value = ((value + 2) / 5) * 5;
                if (value > 100) value = 100;
                return value;
        }

        int alphaFromPercent( int value )
        {
                return (value * 255 + 50) / 100;
        }

        // Named tint presets used by the compact color picker.
        String[] getTintNames()
        {
                String[] names = new String[15];
                names[0] = "Stock";
                names[1] = "Black";
                names[2] = "Charcoal";
                names[3] = "Gray";
                names[4] = "Silver";
                names[5] = "White";
                names[6] = "Red";
                names[7] = "Orange";
                names[8] = "Yellow";
                names[9] = "Green";
                names[10] = "Cyan";
                names[11] = "Blue";
                names[12] = "Purple";
                names[13] = "Pink";
                names[14] = "Brown";
                return names;
        }

        int clampTintPreset( int value )
        {
                if (value < 0) return 0;
                if (value > 15) return 15;
                return value;
        }

        String getTintLabel()
        {
                int preset = findTintPreset();
                if (preset > 0)
                        return "Glass color: " + getTintNames()[preset-1];
                return "Glass color: Custom";
        }

        int findTintPreset()
        {
                for (int i = 1; i <= 15; i++)
                        if (tintR == tintRed(i) && tintG == tintGreen(i) && tintB == tintBlue(i))
                                return i;
                return 0;
        }

        int tintRed( int preset )
        {
                if (preset == 1) return 22;
                if (preset == 2) return 0;
                if (preset == 3) return 32;
                if (preset == 4) return 85;
                if (preset == 5) return 145;
                if (preset == 6) return 205;
                if (preset == 7) return 130;
                if (preset == 8) return 170;
                if (preset == 9) return 150;
                if (preset == 10) return 20;
                if (preset == 11) return 20;
                if (preset == 12) return 25;
                if (preset == 13) return 95;
                if (preset == 14) return 165;
                if (preset == 15) return 85;
                return tintR;
        }

        int tintGreen( int preset )
        {
                if (preset == 1) return 13;
                if (preset == 2) return 0;
                if (preset == 3) return 32;
                if (preset == 4) return 85;
                if (preset == 5) return 150;
                if (preset == 6) return 210;
                if (preset == 7) return 24;
                if (preset == 8) return 65;
                if (preset == 9) return 125;
                if (preset == 10) return 105;
                if (preset == 11) return 125;
                if (preset == 12) return 55;
                if (preset == 13) return 30;
                if (preset == 14) return 50;
                if (preset == 15) return 45;
                return tintG;
        }

        int tintBlue( int preset )
        {
                if (preset == 1) return 13;
                if (preset == 2) return 0;
                if (preset == 3) return 36;
                if (preset == 4) return 90;
                if (preset == 5) return 155;
                if (preset == 6) return 215;
                if (preset == 7) return 30;
                if (preset == 8) return 20;
                if (preset == 9) return 25;
                if (preset == 10) return 40;
                if (preset == 11) return 140;
                if (preset == 12) return 140;
                if (preset == 13) return 130;
                if (preset == 14) return 90;
                if (preset == 15) return 24;
                return tintB;
        }

        int packAppearanceColor( int alpha, int red, int green, int blue )
        {
                return alpha * 16777216 + red * 65536 + green * 256 + blue;
        }

        // Called after the part has been installed into the scene graph.
        // Loading a nested part happens before the parent installs it, so rebuilding
        // the temporary render type from load() would be too early.
        public void onInstalled( Part target )
        {
                // The mesh resource may have been reloaded from disk (scene/game
                // load) -- re-patch it so the glass keeps the chosen strength.
                // v1.12: LIVE rebuild too -- the render child created during the
                // install read the mesh BEFORE this patch; the bounce makes the
                // chosen reflection visible without a save/load cycle.
                applyReflectionLive(target, reflection);

                if (restorePending != 0)
                {
                        if (applyAppearance(target, opacity, tintR, tintG, tintB))
                                restorePending = 0;
                }
        }

        // Refresh the live render instance used by InventoryItem_Part's preview camera.
        // Moving a part between the car and inventory can recreate its child render instance.
        public void onInventoryPreview( Part target )
        {
                previewRefreshInProgress = 1;
                // Preview rebuilds can follow a mesh reload -- keep it patched.
                applyReflection(target, reflection);
                if (restorePending != 0 || hasOpacityOverride == 0)
                {
                        if (restorePending != 0 && applyAppearance(target, opacity, tintR, tintG, tintB))
                                restorePending = 0;
                        previewRefreshInProgress = 0;
                        return;
                }

                if (!privateTypeHandle)
                {
                        if (applyAppearance(target, opacity, tintR, tintG, tintB))
                                restorePending = 0;
                        previewRefreshInProgress = 0;
                        return;
                }

                int currentType = target.getRenderType();
                if (currentType == originalRenderType)
                        target.setRenderType(privateTypeHandle.id());
                else if (currentType != privateTypeHandle.id())
                {
                        previewRefreshInProgress = 0;
                        return;
                }

                int instanceID = findGlassRenderInstance(target.id(), privateTypeHandle.id());
                if (instanceID == 0)
                {
                        previewRefreshInProgress = 0;
                        return;
                }

                RenderRef active = new RenderRef(instanceID);
                active.setColor(packAppearanceColor(opacity, tintR, tintG, tintB));
                renderInstance = instanceID;
                previewRefreshInProgress = 0;
        }

        public void buildTuningMenu( Menu m, Part target )
        {
                oldOpacity = opacity;
                oldReflection = reflection;
                oldTintR = tintR;
                oldTintG = tintG;
                oldTintB = tintB;
                oldAppearanceCustomized = appearanceCustomized;
                if (privateTypeHandle && target.getRenderType() == privateTypeHandle.id())
                        tuningHadOverride = 1;
                else
                        tuningHadOverride = 0;
                // The mesh resource may have been reloaded from disk since the
                // last tuning (scene change) -- re-apply the saved strength.
                applyReflection(target, reflection);
                // Use the OSD that owns the tuning menu. This keeps the controls in the
                // same window and avoids a second dialog or any changes to the caller.
                Osd tuningOsd = m.osd;

                int percent = snapPercentToFive(opacityPercent());
                Style sliderStyle = new Style(0.45, 0.06, Frontend.smallFont,
                        Text.ALIGN_CENTER, new ResourceRef(Osd.RID_SLD_BACK));
                Style knobStyle = new Style(0.04, 0.05, Frontend.smallFont,
                        Text.ALIGN_RIGHT, new ResourceRef(Osd.RID_SLD_KNOB));
                Slider opacitySlider = tuningOsd.createSlider(sliderStyle, knobStyle,
                        0.0, -0.4, "Glass opacity", CMD_GLASS_OPACITY,
                        percent, 0.0, 100.0, 21);
                opacitySlider.changeVLabelText( Float.toString(percent, "   %1.0f%%") );

                // Reflection strength slider: drives ResourceRef.set(percent) on a
                // per-part duplicated mesh (native redirected by dinput8.dll).
                Slider reflSlider = tuningOsd.createSlider(sliderStyle, knobStyle,
                        0.0, -0.47, "Reflection", CMD_GLASS_REFLECTION,
                        reflection, 0.0, 100.0, 21);
                reflSlider.changeVLabelText( Float.toString(reflection, "   %1.0f%%") );

                String[] tintNames = getTintNames();
                int selectedTint = findTintPreset();
                glassTintButtons = new Button[tintNames.length];

                // Two columns, retaining the coordinates and item size from the picker.
                Style itemStyle = new Style(0.15, 0.075, Frontend.smallFont,
                        Text.ALIGN_LEFT, Osd.RRT_TEST);
                Menu leftChoices = tuningOsd.createMenu(itemStyle, -0.2, -0.30, 0, Osd.MD_VERTICAL);
                Menu rightChoices = tuningOsd.createMenu(itemStyle, 0.1, -0.30, 0, Osd.MD_VERTICAL);

                for (int i = 0; i < tintNames.length; i++)
                {
                        String label = tintNames[i];
                        if (selectedTint == i + 1)
                                label = "* " + label;
                        if (i < 8)
                                glassTintButtons[i] = leftChoices.addItem(label, CMD_GLASS_TINT_PRESET_BASE + i);
                        else
                                glassTintButtons[i] = rightChoices.addItem(label, CMD_GLASS_TINT_PRESET_BASE + i);
                }
        }

        public void handleMessage( Event m, Part target )
        {
                int requested;
                int previous;

                if (m.cmd == CMD_GLASS_OPACITY)
                {
                        previous = snapPercentToFive(opacityPercent());
                        requested = snapPercentToFive((int)((Slider)m.gadget).value);

                        if (applyAppearance(target, alphaFromPercent(requested), tintR, tintG, tintB))
                        {
                                opacity = alphaFromPercent(requested);
                                appearanceCustomized = 1;
                                ((Slider)m.gadget).setValue(requested);
                                ((Slider)m.gadget).changeVLabelText(
                                        Float.toString(requested, "   %1.0f%%") );
                        }
                        else
                        {
                                ((Slider)m.gadget).setValue(previous);
                                ((Slider)m.gadget).changeVLabelText(
                                        Float.toString(previous, "   %1.0f%%") );
                        }
                }
                else if (m.cmd == CMD_GLASS_REFLECTION)
                {
                        previous = reflection;
                        requested = snapPercentToFive((int)((Slider)m.gadget).value);

                        if (applyReflectionLive(target, requested))
                        {
                                reflection = requested;
                                appearanceCustomized = 1;
                                ((Slider)m.gadget).setValue(requested);
                                ((Slider)m.gadget).changeVLabelText(
                                        Float.toString(requested, "   %1.0f%%") );
                        }
                        else
                        {
                                ((Slider)m.gadget).setValue(previous);
                                ((Slider)m.gadget).changeVLabelText(
                                        Float.toString(previous, "   %1.0f%%") );
                        }
                }
                else if (m.cmd >= CMD_GLASS_TINT_PRESET_BASE &&
                        m.cmd < CMD_GLASS_TINT_PRESET_BASE + 15)
                {
                        selectTintPreset(target, m.cmd - CMD_GLASS_TINT_PRESET_BASE + 1);
                }
        }

        public void selectTintPreset( Part target, int preset )
        {
                preset = clampTintPreset(preset);
                int red = tintRed(preset);
                int green = tintGreen(preset);
                int blue = tintBlue(preset);

                if (applyAppearance(target, opacity, red, green, blue))
                {
                        tintR = red;
                        tintG = green;
                        tintB = blue;
                        appearanceCustomized = 1;
                        updateTintPresetLabels();
                }
        }

        void updateTintPresetLabels()
        {
                if (!glassTintButtons)
                        return;

                int selectedTint = findTintPreset();
                String[] tintNames = getTintNames();
                for (int i = 0; i < tintNames.length; i++)
                {
                        String label = tintNames[i];
                        if (selectedTint == i + 1)
                                label = "* " + label;
                        if (glassTintButtons[i])
                                glassTintButtons[i].changeLabelText(label);
                }
        }

        public void endTuningSession( int cancelled, Part target )
        {
                // Revert the FIELD values only on Cancel. (A stray unconditional
                // block here used to revert tint/opacity after EVERY close,
                // even OK -- that is why the tint never persisted.)
                if (cancelled)
                {
                        reflection = oldReflection;
                        opacity = oldOpacity;
                        tintR = oldTintR;
                        tintG = oldTintG;
                        tintB = oldTintB;
                        appearanceCustomized = oldAppearanceCustomized;
                }

                // Push the final (possibly restored) strength into the shared
                // mesh: render children rebuilt after this dialog closes keep
                // the chosen value.
                // v1.12: LIVE rebuild -- on Cancel the dragged value must not
                // stay visible on the current render child.
                applyReflectionLive(target, reflection);

                if (tuningHadOverride != 0)
                {
                        // Override existed before this dialog: re-apply the final
                        // appearance (kept values on OK, restored values on Cancel).
                        if (privateTypeHandle && target.getRenderType() == privateTypeHandle.id())
                                applyAppearance(target, opacity, tintR, tintG, tintB);
                }
                else if (cancelled)
                {
                        // First-time edit cancelled: return to the part's original
                        // render type. (On OK the override now STAYS.)
                        if (privateTypeHandle && target.getRenderType() == privateTypeHandle.id())
                                restoreOverride(target);
                }

                tuningHadOverride = 0;
        }

        int applyAppearance( Part target, int alpha, int red, int green, int blue )
        {
                if (!ensureOverride(target))
                {
                        return 0;
                }

                int instanceID = findGlassRenderInstance(target.id(), privateTypeHandle.id());
                if (instanceID == 0)
                {
                        if (previewRefreshInProgress != 0)
                                return 0;
                        restoreOverride(target);
                        return 0;
                }

                // Avoid bit-shift expressions: this older SLRR VM has shown stack errors in shift-heavy expressions.
                // Pack ARGB by multiplication so alpha and the RGB tint are independently controllable.
                int argb = packAppearanceColor(alpha, red, green, blue);
                RenderRef active = new RenderRef(instanceID);
                active.setColor(argb);
                renderInstance = instanceID;
                hasOpacityOverride = 1;
                applyReflection(target, reflection);
                return 1;
        }

        // Reflection strength control.
        // Requires dinput8.dll v1.12+ in the game dir: it hooks the native
        // ResourceRef.set(I)V. Because the stock constructor uses set(id) too,
        // the hook only reacts to magic values 0x7AFExxxx (see REFL_MAGIC);
        // REFL_MAGIC + percent -> reflection strength, REFL_MAGIC+255 -> dump.
        //
        // IMPORTANT: this is completely independent of the opacity/tint render
        // type override. Binding the reflection to that override was what made
        // the glass jump to a mirror look the moment the slider was touched
        // (the override render type is a paint-can type: full envmap).
        // We patch the part's ORIGINAL shared mesh resource -- like a live
        // .scx edit -- so no render type change is needed for the VALUE.
        //
        // The render child only re-reads the material entries when it is
        // (re)created -- that is why v1.4..v1.11 only showed a change after
        // SAVE (machine refresh). applyReflection() is the cheap mesh patch
        // (safe to call anywhere); applyReflectionLive() additionally
        // rebuilds the render child (see refreshRenderChild) so the change
        // is VISIBLE immediately.
        int applyReflection( Part target, int percent )
        {
                int meshID = target.getMesh();
                if (meshID == 0)
                        return 0;
                if (!targetMeshHandle || targetMeshHandle.id() != meshID)
                        targetMeshHandle = new ResourceRef(meshID);
                targetMeshHandle.set( REFL_MAGIC + percent );
                return 1;
        }

        // Mesh patch + live render-child rebuild. Use this wherever the
        // reflection VALUE just changed (slider events, session end, part
        // install) and the part is visible in the scene.
        int applyReflectionLive( Part target, int percent )
        {
                if (applyReflection(target, percent) == 0)
                        return 0;
                refreshRenderChild(target);
                return 1;
        }

        // Rebuild the part's render child so the renderer re-imports the
        // (just-patched) material entries. The engine skips the rebind when
        // the resource id does not change (Part_rebindMeshRes/
        // Part_rebindRenderTypeRes bail out on a same-id set), so the rebuild
        // is forced by switching the render type away and back WITHIN this
        // handler: the intermediate child is created and destroyed before
        // the next frame -- it is never drawn, so there is no flicker.
        void refreshRenderChild( Part target )
        {
                int currentType = target.getRenderType();
                if (currentType == 0)
                        return;

                if (privateTypeHandle && originalRenderType != 0)
                {
                        if (currentType == privateTypeHandle.id())
                        {
                                // Override active: private -> original -> private.
                                // The bounce replaces the live instance, so the
                                // appearance color must be re-applied to the
                                // fresh instance afterwards.
                                target.setRenderType(originalRenderType);
                                if (target.getRenderType() != originalRenderType)
                                        return;
                                target.setRenderType(privateTypeHandle.id());
                                if (target.getRenderType() == privateTypeHandle.id())
                                        applyInstanceColor(target);
                        }
                        else if (currentType == originalRenderType)
                        {
                                // Stock look (a private copy exists but is not
                                // active): original -> private -> original.
                                target.setRenderType(privateTypeHandle.id());
                                target.setRenderType(originalRenderType);
                        }
                        // Otherwise another system owns the render type right
                        // now -- leave it alone (same policy as ensureOverride).
                }
                else
                {
                        // No override infrastructure: bounce through a short-
                        // lived duplicate of the paint-can type. It is never
                        // drawn (we switch away within the same handler), so
                        // it does not need a mesh retarget.
                        RenderRef sourceType = new RenderRef(frontend:0x0021r);
                        if (sourceType.type() != 14)
                                return;
                        RenderRef tempType = new RenderRef();
                        tempType.duplicate(sourceType);
                        if (tempType.id() == 0)
                                return;
                        target.setRenderType(tempType.id());
                        target.setRenderType(currentType);
                        tempType.destroy();
                }
        }

        // Color the CURRENT live render instance with the saved appearance
        // (used after refreshRenderChild replaced the instance).
        int applyInstanceColor( Part target )
        {
                if (!privateTypeHandle)
                        return 0;
                int instanceID = findGlassRenderInstance(target.id(), privateTypeHandle.id());
                if (instanceID == 0)
                        return 0;
                RenderRef active = new RenderRef(instanceID);
                active.setColor(packAppearanceColor(opacity, tintR, tintG, tintB));
                renderInstance = instanceID;
                return 1;
        }

        // One-shot calibration: dumps mesh + render-type resource memory to
        // grefl_dump.bin next to the game exe (analysed offline).
        int dumpGlassResources( Part target )
        {
                if (!ensureOverride(target))
                        return 0;
                if (targetMeshHandle)
                        targetMeshHandle.set( REFL_DUMP );
                if (sourceTypeHandle)
                        sourceTypeHandle.set( REFL_DUMP );
                if (privateTypeHandle)
                        privateTypeHandle.set( REFL_DUMP );
                return 1;
        }

        int ensureOverride( Part target )
        {
                if (privateTypeHandle)
                {
                        if (target.getRenderType() == privateTypeHandle.id())
                        {
                                // During inventory preview the part can receive its temporary type
                                // before the engine creates/rebuilds its live render child. On later
                                // retries, rebind the type if no child of that type exists yet.
                                if (previewRefreshInProgress != 0 &&
                                        findGlassRenderInstance(target.id(), privateTypeHandle.id()) == 0)
                                {
                                        target.setRenderType(originalRenderType);
                                        target.setRenderType(privateTypeHandle.id());
                                }
                                return 1;
                        }

                        // Do not silently overwrite a render-type change made by another system.
                        return 0;
                }

                int originalRenderID = target.getRenderType();
                int originalMeshID = target.getMesh();
                if (originalRenderID == 0 || originalMeshID == 0)
                {
                        return 0;
                }

                // Confirmed in the tested Build 940 installation: frontend:0x21r is
                // the paintcan render type with instance-color mode; 0x5Er is its mesh.
                RenderRef sourceType = new RenderRef(frontend:0x0021r);
                ResourceRef sourceMesh = new ResourceRef(frontend:0x005Er);
                ResourceRef targetMesh = new ResourceRef(originalMeshID);

                if (sourceType.type() != 14 || sourceMesh.type() != 5 || targetMesh.type() != 5)
                {
                        return 0;
                }

                sourceMesh.load();
                targetMesh.load();

                RenderRef privateType = new RenderRef();
                privateType.duplicate(sourceType);
                if (privateType.id() == 0 || privateType.type() != 14)
                {
                        if (privateType.id() != 0) privateType.destroy();
                        return 0;
                }

                // Keep all resource handles alive until the original type is restored.
                originalRenderType = originalRenderID;
                originalMesh = originalMeshID;
                sourceTypeHandle = sourceType;
                sourceMeshHandle = sourceMesh;
                targetMeshHandle = targetMesh;
                privateTypeHandle = privateType;

                privateType.changeResource(sourceMesh, targetMesh);
                target.setRenderType(privateType.id());

                if (target.getRenderType() != privateType.id() || target.getMesh() != originalMeshID)
                {
                        restoreOverride(target);
                        return 0;
                }

                int instanceID = findGlassRenderInstance(target.id(), privateType.id());
                if (instanceID == 0)
                {
                        if (previewRefreshInProgress != 0)
                                return 1;
                        restoreOverride(target);
                        return 0;
                }

                renderInstance = instanceID;
                return 1;
        }

        int findGlassRenderInstance( int targetID, int renderTypeID )
        {
                GameRef target = new GameRef(targetID);
                if (!target) return 0;

                ResourceRef child = target.getFirstChild();
                for (int i = 0; child && i < 64; i++)
                {
                        if (child.type() == 3)
                        {
                                RenderRef live = new RenderRef(child.id());
                                if (live.getTypeID() == renderTypeID)
                                        return child.id();
                        }
                        child = child.getNextChild();
                }
                return 0;
        }

        int restoreOverride( Part target )
        {
                if (!privateTypeHandle)
                        return 1;

                // The render child is about to be rebuilt from the original
                // mesh resource: keep the chosen reflection strength on it.
                if (targetMeshHandle)
                        targetMeshHandle.set( REFL_MAGIC + reflection );

                int copyID = privateTypeHandle.id();
                if (target.getRenderType() == copyID)
                {
                        int instanceID = findGlassRenderInstance(target.id(), copyID);
                        if (instanceID != 0)
                        {
                                RenderRef active = new RenderRef(instanceID);
                                active.setColor(0xFFFFFFFF);
                        }

                        target.setRenderType(originalRenderType);
                        if (target.getRenderType() != originalRenderType)
                        {
                                return 0;
                        }

                }
                else if (target.getRenderType() != originalRenderType)
                {
                        return 0;
                }

                privateTypeHandle.destroy();
                privateTypeHandle = null;
                sourceTypeHandle = null;
                sourceMeshHandle = null;
                targetMeshHandle = null;
                originalRenderType = 0;
                originalMesh = 0;
                renderInstance = 0;
                hasOpacityOverride = 0;
                return 1;
        }
}

