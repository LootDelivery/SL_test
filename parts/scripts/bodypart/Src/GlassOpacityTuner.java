package java.game.parts.bodypart;

import java.util.*;
import java.util.resource.*;
import java.render.*;
import java.render.osd.*;
import java.game.*;
import java.game.parts.*;

public class GlassOpacityTuner
{
	static final int CMD_GLASS_OPACITY = 9301;

	// Runtime/session-only override. This does not extend the car-save format.
	int opacity = 127;
	int oldOpacity = 127;
	int tuningHadOverride = 0;

	RenderRef sourceTypeHandle;
	ResourceRef sourceMeshHandle;
	ResourceRef targetMeshHandle;
	RenderRef privateTypeHandle;

	int originalRenderType = 0;
	int originalMesh = 0;
	int renderInstance = 0;

	public void buildTuningMenu( Menu m, Part target )
	{
		oldOpacity = opacity;
		if (privateTypeHandle && target.getRenderType() == privateTypeHandle.id())
			tuningHadOverride = 1;
		else
			tuningHadOverride = 0;

		Slider slider = m.addItem( "Glass opacity", CMD_GLASS_OPACITY,
			opacity, 0.0, 255.0, 256, null );
		slider.changeVLabelText( Float.toString(opacity*100.0/255.0, "   %1.0f%%") );
	}

	public void handleMessage( Event m, Part target )
	{
		if (m.cmd == CMD_GLASS_OPACITY)
		{
			int previousOpacity = opacity;
			int requestedOpacity = (int)((Slider)m.gadget).value;

			if (requestedOpacity < 0) requestedOpacity = 0;
			if (requestedOpacity > 255) requestedOpacity = 255;

			if (applyOpacity(target, requestedOpacity))
			{
				opacity = requestedOpacity;
				((Slider)m.gadget).changeVLabelText(
					Float.toString(opacity*100.0/255.0, "   %1.0f%%") );
			}
			else
			{
				((Slider)m.gadget).setValue(previousOpacity);
				((Slider)m.gadget).changeVLabelText(
					Float.toString(previousOpacity*100.0/255.0, "   %1.0f%%") );
			}
		}
	}

	public void endTuningSession( int cancelled, Part target )
	{
		if (cancelled)
		{
			opacity = oldOpacity;

			if (tuningHadOverride != 0)
			{
				// Existing override: restore the value from before this dialog.
				if (privateTypeHandle && target.getRenderType() == privateTypeHandle.id())
					applyOpacity(target, opacity);
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

	int applyOpacity( Part target, int alpha )
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

		// The tested windshield SCX has DiffuseColor RGB (22, 13, 13).
		// Alpha is controlled from 0 to 255. Other glass tint RGBs may need separate handling.
		// Avoid bit-shift expressions: this older SLRR VM has shown stack errors in shift-heavy expressions.
		// 0x160D0D = 1445133; multiply alpha by 2^24 to build the ARGB high byte.
		int argb = alpha * 16777216 + 1445133;
		RenderRef active = new RenderRef(instanceID);
		active.setColor(argb);
		renderInstance = instanceID;
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
		return 1;
	}
}
