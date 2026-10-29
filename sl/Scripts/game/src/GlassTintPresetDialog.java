package java.game;

import java.util.*;
import java.render.*;
import java.render.osd.*;
import java.render.osd.dialog.*;
import java.game.parts.Part;
import java.game.parts.bodypart.GlassOpacityTuner;

public class GlassTintPresetDialog extends Dialog
{
	final static int CMD_CANCEL = 0;
	final static int CMD_PICK_COLOR = 1000;

	String[] names;
	int currentPreset;
	GlassOpacityTuner owner;
	Part target;

	Style itemStyle = new Style(0.15, 0.075, Frontend.smallFont,
		Text.ALIGN_LEFT, Osd.RRT_TEST);

	public GlassTintPresetDialog( String[] options, int selectedPreset, GlassOpacityTuner tuner, Part part )
	{
		// Modeless child popup: use show(), not display(), so the parent tuning dialog
		// continues processing its own OK/CANCEL commands while the list is open.
		super(GameLogic.player.controller,
			Dialog.DF_WIDE|Dialog.DF_HIGHPRI,
			"GLASS COLOR", "CANCEL");

		escapeCmd = CMD_CANCEL;
		names = options;
		currentPreset = selectedPreset;
		owner = tuner;
		target = part;
		osd.globalHandler = this;

		// Two centered columns keep all 15 choices visible.
		Menu leftChoices = osd.createMenu(itemStyle, -0.2, -0.30, 0, Osd.MD_VERTICAL);
		Menu rightChoices = osd.createMenu(itemStyle, 0.1, -0.30, 0, Osd.MD_VERTICAL);
		for (int i = 0; i < names.length; i++)
		{
			String label = names[i];
			if (currentPreset == i+1)
				label = "* " + label;

			if (i < 8)
				leftChoices.addItem(label, CMD_PICK_COLOR+i);
			else
				rightChoices.addItem(label, CMD_PICK_COLOR+i);
		}
	}

	public void osdCommand( int cmd )
	{
		if (cmd >= CMD_PICK_COLOR && cmd < CMD_PICK_COLOR+names.length)
		{
			int preset = cmd - CMD_PICK_COLOR + 1;
			if (owner && target)
				owner.selectTintPreset(target, preset);
		}

		// A color click closes the popup after applying it; CANCEL/ESC only closes it.
		dismiss();
	}

	public void dismiss()
	{
		GlassOpacityTuner oldOwner = owner;
		owner = null;
		target = null;
		hide();
		if (oldOwner)
			oldOwner.tintPickerClosed(this);
	}
}
