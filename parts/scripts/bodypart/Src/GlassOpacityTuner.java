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
	static final int CMD_GLASS_TINT = 9305;

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
	int originalMesh = 0;
	int renderInstance = 0;
	Button glassColorButton;

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
		oldTintR = tintR;
		oldTintG = tintG;
		oldTintB = tintB;
		oldAppearanceCustomized = appearanceCustomized;
		if (privateTypeHandle && target.getRenderType() == privateTypeHandle.id())
			tuningHadOverride = 1;
		else
			tuningHadOverride = 0;

		int percent = snapPercentToFive(opacityPercent());
		Slider opacitySlider = m.addItem( "Glass opacity", CMD_GLASS_OPACITY,
			percent, 0.0, 100.0, 21, null );
		opacitySlider.changeVLabelText( Float.toString(percent, "   %1.0f%%") );

		glassColorButton = m.addItem( getTintLabel(), CMD_GLASS_TINT,
			"Choose glass tint" );
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
		else if (m.cmd == CMD_GLASS_TINT)
		{
			previous = findTintPreset();
			java.game.GlassTintPresetDialog picker = new java.game.GlassTintPresetDialog(getTintNames(), previous);
			requested = picker.display();

			// The dialog returns preset IDs 1..15, or 0 when cancelled.
			if (requested > 0)
			{
				requested = clampTintPreset(requested);
				int red = tintRed(requested);
				int green = tintGreen(requested);
				int blue = tintBlue(requested);

				if (applyAppearance(target, opacity, red, green, blue))
				{
					tintR = red;
					tintG = green;
					tintB = blue;
					appearanceCustomized = 1;
					if (glassColorButton)
						glassColorButton.changeLabelText(getTintLabel());
				}
			}
		}
	}

	public void endTuningSession( int cancelled, Part target )
	{
		if (cancelled)
		{
			opacity = oldOpacity;
			tintR = oldTintR;
			tintG = oldTintG;
			tintB = oldTintB;
			appearanceCustomized = oldAppearanceCustomized;

			if (tuningHadOverride != 0)
			{
				// Existing override: restore opacity and tint from before this dialog.
				if (privateTypeHandle && target.getRenderType() == privateTypeHandle.id())
					applyAppearance(target, opacity, tintR, tintG, tintB);
			}
			else
			{
				// First-time edit cancelled: return to the part's original render type.
				if (privateTypeHandle && target.getRenderType() == privateTypeHandle.id())
					restoreOverride(target);
			}
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

