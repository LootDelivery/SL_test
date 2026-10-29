package java.game.parts.bodypart;

import java.io.*;
import java.util.*;
import java.util.resource.*;
import java.render.*;
import java.render.osd.*;
import java.game.*;
import java.game.cars.*;
import java.game.parts.*;
import java.game.parts.enginepart.*;
import java.game.parts.rgearpart.*;
import java.game.parts.rgearpart.reciprocatingrgearpart.*;

public class Window extends BodyPart
{
	static final int CMD_GLASS_OPACITY = 9301;

	// Session-only override. The private render type stays alive while this
	// Window object exists; the game's normal part-save format is not changed.
	int glassOpacity = 127;
	int oldGlassOpacity = 127;
	int tuningHadGlassOverride = 0;

	RenderRef glassSourceType;
	ResourceRef glassSourceMesh;
	ResourceRef glassTargetMesh;
	RenderRef glassPrivateType;

	int glassOriginalRenderType = 0;
	int glassOriginalMesh = 0;
	int glassRenderInstance = 0;

	public Window( int id )
	{
		super( id );

		name = "Window";

		prestige_calc_weight = 15.0;
		drag_reduction = 0.03;
	}

	// Make standard Window parts available in the stock screwdriver tuning menu.
	public int isTuneable()
	{
		return 1;
	}

	public void buildTuningMenu( Menu m )
	{
		oldGlassOpacity = glassOpacity;
		if (glassPrivateType && getRenderType() == glassPrivateType.id())
			tuningHadGlassOverride = 1;
		else
			tuningHadGlassOverride = 0;

		Slider slider = m.addItem( "Glass opacity", CMD_GLASS_OPACITY,
			glassOpacity, 0.0, 255.0, 256, null );
		slider.changeVLabelText( Float.toString(glassOpacity*100.0/255.0, "   %1.0f%%") );
	}

	public void handleMessage( Event m )
	{
		if (m.cmd == CMD_GLASS_OPACITY)
		{
			int previousOpacity = glassOpacity;
			int requestedOpacity = (int)((Slider)m.gadget).value;

			if (requestedOpacity < 0) requestedOpacity = 0;
			if (requestedOpacity > 255) requestedOpacity = 255;

			if (applyGlassOpacity(requestedOpacity))
			{
				glassOpacity = requestedOpacity;
				((Slider)m.gadget).changeVLabelText(
					Float.toString(glassOpacity*100.0/255.0, "   %1.0f%%") );
			}
			else
			{
				// Put the control back if the runtime render-type setup failed.
				((Slider)m.gadget).setValue(previousOpacity);
				((Slider)m.gadget).changeVLabelText(
					Float.toString(previousOpacity*100.0/255.0, "   %1.0f%%") );
			}
		}
	}

	public void endTuningSession( int cancelled )
	{
		if (cancelled)
		{
			glassOpacity = oldGlassOpacity;

			if (tuningHadGlassOverride != 0)
			{
				// An override existed before this dialog: restore its previous value.
				if (glassPrivateType && getRenderType() == glassPrivateType.id())
					applyGlassOpacity(glassOpacity);
			}
			else
			{
				// No override existed before this dialog: remove any temporary
				// render type created by moving the slider, restoring factory glass.
				if (glassPrivateType && getRenderType() == glassPrivateType.id())
					restoreGlassOpacityOverride();
			}
		}

		tuningHadGlassOverride = 0;
	}

	int applyGlassOpacity( int opacity )
	{
		if (!ensureGlassOpacityOverride())
			return 0;

		int instanceID = findGlassRenderInstance(this, glassPrivateType.id());
		if (instanceID == 0)
		{
			System.print("Glass opacity: live render instance not found; keeping current type alive.", System.PF_WARNING);
			return 0;
		}

		// Glass diffuse RGB observed in the tested windshield SCX: (22, 13, 13).
		// Alpha is supplied by the slider, 0..255. Left shift intentionally builds ARGB.
		int argb = (opacity << 24) | 0x00160D0D;
		RenderRef active = new RenderRef(instanceID);
		active.setColor(argb);
		glassRenderInstance = instanceID;
		return 1;
	}

	int ensureGlassOpacityOverride()
	{
		if (glassPrivateType)
		{
			if (getRenderType() == glassPrivateType.id())
				return 1;

			// Do not silently overwrite a render-type change made by another system.
			System.print("Glass opacity: render type changed externally; aborting override.", System.PF_WARNING);
			return 0;
		}

		int originalRenderID = getRenderType();
		int originalMeshID = getMesh();
		if (originalRenderID == 0 || originalMeshID == 0)
		{
			System.print("Glass opacity: part has no render type or mesh.", System.PF_WARNING);
			return 0;
		}

		// The tested Build 940 setup uses frontend:0x21r (paintcan), whose
		// render type supports instance-color alpha. Replace only its mesh resource.
		RenderRef sourceType = new RenderRef(frontend:0x0021r);
		ResourceRef sourceMesh = new ResourceRef(frontend:0x005Er);
		ResourceRef targetMesh = new ResourceRef(originalMeshID);

		if (sourceType.type() != 14 || sourceMesh.type() != 5 || targetMesh.type() != 5)
		{
			System.print("Glass opacity: expected render type/mesh resource types are not available; aborting.", System.PF_WARNING);
			return 0;
		}

		sourceMesh.load();
		targetMesh.load();

		RenderRef privateType = new RenderRef();
		privateType.duplicate(sourceType);
		if (privateType.id() == 0 || privateType.type() != 14)
		{
			if (privateType.id() != 0) privateType.destroy();
			System.print("Glass opacity: failed to duplicate the source render type.", System.PF_WARNING);
			return 0;
		}

		// Keep resource handles as fields for the entire time this override is active.
		glassOriginalRenderType = originalRenderID;
		glassOriginalMesh = originalMeshID;
		glassSourceType = sourceType;
		glassSourceMesh = sourceMesh;
		glassTargetMesh = targetMesh;
		glassPrivateType = privateType;

		privateType.changeResource(sourceMesh, targetMesh);
		setRenderType(privateType.id());

		if (getRenderType() != privateType.id() || getMesh() != originalMeshID)
		{
			System.print("Glass opacity: private render type did not bind as expected; restoring.", System.PF_WARNING);
			restoreGlassOpacityOverride();
			return 0;
		}

		int instanceID = findGlassRenderInstance(this, privateType.id());
		if (instanceID == 0)
		{
			System.print("Glass opacity: private type bound but its live render instance was not found; restoring.", System.PF_WARNING);
			restoreGlassOpacityOverride();
			return 0;
		}

		glassRenderInstance = instanceID;
		return 1;
	}

	int findGlassRenderInstance( GameRef target, int renderTypeID )
	{
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

	int restoreGlassOpacityOverride()
	{
		if (!glassPrivateType)
			return 1;

		int copyID = glassPrivateType.id();
		if (getRenderType() == copyID)
		{
			int instanceID = findGlassRenderInstance(this, copyID);
			if (instanceID != 0)
			{
				RenderRef active = new RenderRef(instanceID);
				active.setColor(0xFFFFFFFF);
			}

			setRenderType(glassOriginalRenderType);
			if (getRenderType() != glassOriginalRenderType)
			{
				System.print("Glass opacity: failed to restore original render type; temporary type kept alive.", System.PF_WARNING);
				return 0;
			}

			if (getMesh() != glassOriginalMesh)
				System.print("Glass opacity: original render type restored, but mesh ID changed.", System.PF_WARNING);
		}
		else if (getRenderType() != glassOriginalRenderType)
		{
			System.print("Glass opacity: render type changed externally; leaving it untouched and keeping temporary resources alive.", System.PF_WARNING);
			return 0;
		}

		glassPrivateType.destroy();
		glassPrivateType = null;
		glassSourceType = null;
		glassSourceMesh = null;
		glassTargetMesh = null;
		glassOriginalRenderType = 0;
		glassOriginalMesh = 0;
		glassRenderInstance = 0;
		return 1;
	}
}
