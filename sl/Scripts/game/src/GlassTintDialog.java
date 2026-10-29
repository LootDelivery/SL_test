package java.game;

import java.util.*;
import java.render.*;
import java.render.osd.*;
import java.render.osd.dialog.*;

public class GlassTintDialog extends Dialog
{
	final static int CMD_CANCEL = 0;
	final static int CMD_PICK_COLOR = 1000;

	String[] names;
	int currentPreset;

	Style itemStyle = new Style(0.56, 0.075, Frontend.smallFont,
		Text.ALIGN_LEFT, Osd.RRT_TEST);

	public GlassTintDialog( String[] options, int selectedPreset )
	{
		super(GameLogic.player.controller,
			Dialog.DF_MODAL|Dialog.DF_DEFAULTBG|Dialog.DF_DARKEN,
			"GLASS COLOR", "CANCEL");

		escapeCmd = CMD_CANCEL;
		names = options;
		currentPreset = selectedPreset;
		osd.globalHandler = this;

		Menu choices = osd.createMenu(itemStyle, -0.28, 0.56, 0, Osd.MD_VERTICAL);
		for (int i = 0; i < names.length; i++)
		{
			String label = names[i];
			if (currentPreset == i+1)
				label = "* " + label;
			choices.addItem(label, CMD_PICK_COLOR+i);
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
