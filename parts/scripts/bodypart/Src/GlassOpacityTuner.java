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
	static final int CMD_GLASS_RED = 9302;
	static final int CMD_GLASS_GREEN = 9303;
	static final int CMD_GLASS_BLUE = 9304;

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
	int restorePending = 0;
	int tuningHadOverride = 0;

	RenderRef sourceTypeHandle;
	ResourceRef sourceMeshHandle;
	ResourceRef targetMeshHandle;
	RenderRef privateTypeHandle;

	int originalRenderType = 0;
	int originalMesh = 0;
	int renderInstance = 0;

	// Version-4 part payload: override marker, alpha, and RGB tint. Runtime handles are not saved.
	public void save( File saveGame )
	{
		int savedOverride = 0;
		if (hasOpacityOverride != 0 || restorePending != 0)
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
		if (savedOverride != 0)
			restorePending = 1;
		else
			restorePending = 0;
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

	// Called after the part has been installed into the scene graph.
	// Loading a nested part happens before the parent installs it, so rebuilding
	// the temporary render type from load() would be too early.
	public void onInstalled( Part target )
	{
		if (restorePending != 0)
		{
			if (applyAppearance(target, opacity, tintR, tintG, tintB))
				restorePending = 0;
			else
				System.print("Glass opacity: saved override will remain pending; install-time render instance was unavailable.");
		}
	}

	public void buildTuningMenu( Menu m, Part target )
	{
		oldOpacity = opacity;
		oldTintR = tintR;
		oldTintG = tintG;
		oldTintB = tintB;
		if (privateTypeHandle && target.getRenderType() == privateTypeHandle.id())
			tuningHadOverride = 1;
		else
			tuningHadOverride = 0;

		int percent = snapPercentToFive(opacityPercent());
		Slider opacitySlider = m.addItem( "Glass opacity", CMD_GLASS_OPACITY,
			percent, 0.0, 100.0, 21, null );
		opacitySlider.changeVLabelText( Float.toString(percent, "   %1.0f%%") );

		Slider redSlider = m.addItem( "Glass tint red", CMD_GLASS_RED,
			tintR, 0.0, 255.0, 256, null );
		redSlider.changeVLabelText( Float.toString(tintR, "   R %1.0f") );

		Slider greenSlider = m.addItem( "Glass tint green", CMD_GLASS_GREEN,
			tintG, 0.0, 255.0, 256, null );
		greenSlider.changeVLabelText( Float.toString(tintG, "   G %1.0f") );

		Slider blueSlider = m.addItem( "Glass tint blue", CMD_GLASS_BLUE,
			tintB, 0.0, 255.0, 256, null );
		blueSlider.changeVLabelText( Float.toString(tintB, "   B %1.0f") );
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
		else if (m.cmd == CMD_GLASS_RED)
		{
			previous = tintR;
			requested = clampColor((int)((Slider)m.gadget).value);
			if (applyAppearance(target, opacity, requested, tintG, tintB))
			{
				tintR = requested;
				((Slider)m.gadget).setValue(tintR);
				((Slider)m.gadget).changeVLabelText(Float.toString(tintR, "   R %1.0f"));
			}
			else
			{
				((Slider)m.gadget).setValue(previous);
				((Slider)m.gadget).changeVLabelText(Float.toString(previous, "   R %1.0f"));
			}
		}
		else if (m.cmd == CMD_GLASS_GREEN)
		{
			previous = tintG;
			requested = clampColor((int)((Slider)m.gadget).value);
			if (applyAppearance(target, opacity, tintR, requested, tintB))
			{
				tintG = requested;
				((Slider)m.gadget).setValue(tintG);
				((Slider)m.gadget).changeVLabelText(Float.toString(tintG, "   G %1.0f"));
			}
			else
			{
				((Slider)m.gadget).setValue(previous);
				((Slider)m.gadget).changeVLabelText(Float.toString(previous, "   G %1.0f"));
			}
		}
		else if (m.cmd == CMD_GLASS_BLUE)
		{
			previous = tintB;
			requested = clampColor((int)((Slider)m.gadget).value);
			if (applyAppearance(target, opacity, tintR, tintG, requested))
			{
				tintB = requested;
				((Slider)m.gadget).setValue(tintB);
				((Slider)m.gadget).changeVLabelText(Float.toString(tintB, "   B %1.0f"));
			}
			else
			{
				((Slider)m.gadget).setValue(previous);
				((Slider)m.gadget).changeVLabelText(Float.toString(previous, "   B %1.0f"));
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
			return 0;

		int instanceID = findGlassRenderInstance(target.id(), privateTypeHandle.id());
		if (instanceID == 0)
		{
			System.print("Glass opacity: live render instance not found; restoring override.");
			restoreOverride(target);
			return 0;
		}

		// Avoid bit-shift expressions: this older SLRR VM has shown stack errors in shift-heavy expressions.
		// Pack ARGB by multiplication so alpha and the RGB tint are independently controllable.
		int argb = alpha * 16777216 + red * 65536 + green * 256 + blue;
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
				return 1;

			// Do not silently overwrite a render-type change made by another system.
			System.print("Glass opacity: render type changed externally; aborting override.");
			return 0;
		}

		int originalRenderID = target.getRenderType();
		int originalMeshID = target.getMesh();
		if (originalRenderID == 0 || originalMeshID == 0)
		{
			System.print("Glass opacity: part has no render type or mesh.");
			return 0;
		}

		// Confirmed in the tested Build 940 installation: frontend:0x21r is
		// the paintcan render type with instance-color mode; 0x5Er is its mesh.
		RenderRef sourceType = new RenderRef(frontend:0x0021r);
		ResourceRef sourceMesh = new ResourceRef(frontend:0x005Er);
		ResourceRef targetMesh = new ResourceRef(originalMeshID);

		if (sourceType.type() != 14 || sourceMesh.type() != 5 || targetMesh.type() != 5)
		{
			System.print("Glass opacity: expected render type/mesh resource types are unavailable.");
			return 0;
		}

		sourceMesh.load();
		targetMesh.load();

		RenderRef privateType = new RenderRef();
		privateType.duplicate(sourceType);
		if (privateType.id() == 0 || privateType.type() != 14)
		{
			if (privateType.id() != 0) privateType.destroy();
			System.print("Glass opacity: failed to duplicate the source render type.");
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
			System.print("Glass opacity: private render type did not bind as expected; restoring.");
			restoreOverride(target);
			return 0;
		}

		int instanceID = findGlassRenderInstance(target.id(), privateType.id());
		if (instanceID == 0)
		{
			System.print("Glass opacity: private type bound but live render instance was not found; restoring.");
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
				System.print("Glass opacity: failed to restore original render type; keeping temporary resources alive.");
				return 0;
			}

			if (target.getMesh() != originalMesh)
				System.print("Glass opacity: original type restored, but mesh ID differs from its saved ID.");
		}
		else if (target.getRenderType() != originalRenderType)
		{
			System.print("Glass opacity: render type changed externally; leaving it untouched and keeping temporary resources alive.");
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
