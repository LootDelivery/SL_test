package java.game;

import java.util.*;
import java.render.*;
import java.render.osd.*;
import java.render.osd.dialog.*;

public class GlassTintPresetDialog extends Dialog
{
	final static int CMD_CANCEL = 0;
	final static int CMD_PICK_COLOR = 1000;

	String[] names;
	int currentPreset;

	Style itemStyle = new Style(0.45, 0.075, Frontend.smallFont,
		Text.ALIGN_LEFT, Osd.RRT_TEST);

	public GlassTintPresetDialog( String[] options, int selectedPreset )
	{
		// This is a transparent child popup over the existing tuning panel.
		// High priority gives it focus without drawing another background/dimming layer.
		super(GameLogic.player.controller,
			Dialog.DF_WIDE|Dialog.DF_MODAL|Dialog.DF_HIGHPRI,
			"GLASS COLOR", "CANCEL");

		escapeCmd = CMD_CANCEL;
		names = options;
		currentPreset = selectedPreset;
		osd.globalHandler = this;

		// Two centered columns keep all 15 choices visible.
		Menu leftChoices = osd.createMenu(itemStyle, -0.48, 0.42, 0, Osd.MD_VERTICAL);
		Menu rightChoices = osd.createMenu(itemStyle, 0.03, 0.42, 0, Osd.MD_VERTICAL);
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
			result = cmd - CMD_PICK_COLOR + 1;
		else
			result = 0;

		notify();
	}
}
