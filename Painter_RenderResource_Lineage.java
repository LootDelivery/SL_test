// EXPERIMENTAL CANDIDATE RENDER-INSTANCE PROBE
// TEST ONLY: rename to Painter.java in the SLRR script source folder, compile, and back up the existing Painter first.
// Clicking a part scans its ResourceRef graph for INSTANCE_RENDER (type 3) candidates
// whose RenderRef.getTypeID() matches Part.getRenderType(), and logs their owner/depth.
// A matching type ID alone does NOT prove the candidate is the object drawing the selected part.
// This diagnostic is read-only: it does not modify colors, render types, meshes, textures, or SCX files.
package java.game;

import java.io.*;
import java.util.*;
import java.util.resource.*;
import java.render.*; //Text
import java.render.osd.*; //Text
import java.render.osd.dialog.*; //Text
import java.sound.*;
import java.lang.*;

import java.game.parts.*;
import java.game.parts.bodypart.*;


// New painter by Harrison15 and Bigg Boss93
// Special thanks to Prof. Nan C. Schaller of Rochester University
// for HSV <-> RGB conversion example
// http://www.cs.rit.edu/~ncs/color/t_convert.html

public class Painter extends GameType implements GameState
{
	final static int RID_PAINT_SPRAY = frontend:0x0130r;
	final static int RID_PAINT_PART  = frontend:0x0131r;
	final static int RID_PAINT_DECAL = frontend:0x0132r; 

	final static int RID_PAINT_LOAD = frontend:0x0011r; 
	final static int RID_PAINT_SAVE = frontend:0x0072r; 

	final static int RID_SELL_PAINT = MW_Mod:0x0157r;
	final static int RID_LIST_PAINT = MW_Mod:0x0154r;

	final static int RID_GOTO_PICKR = MW_Mod:0x0155r;
	final static int RID_SVTO_PALET = MW_Mod:0x0156r;

	final static int RID_RGB_MODE = MW_Mod:0x0152r;
	final static int RID_HSV_MODE = MW_Mod:0x0153r;

	final static int RID_BACKGROUND = frontend:0x00C0r;
	final static int RID_BACKGROUND_RGB = frontend:0x00C0r;
	final static float BACKGROUND_HEIGHT = 0.40;

	final static float PARTS_VP_TOP = 0.848;
	final static float PARTS_VP_LEFT = 0.148;
	final static float PARTS_VP_WIDTH = 0.777;
	final static float PARTS_VP_HEIGHT = 0.140;

	final static int CMD_SCROLL_UP = 0;
	final static int CMD_SCROLL_DOWN = 1;
	final static int CMD_SPRAY = 2;
	final static int CMD_PART = 3;
	final static int CMD_DECALS = 4;

	final static ResourceRef PAINTBRUSH = new ResourceRef( misc.garage:0x0102r );
	final static SfxRef SfxSPRAY = new SfxRef( GameLogic.SFX_SPRAY );
	final static SfxRef SfxDECAL = new SfxRef( GameLogic.SFX_DECAL );
	final static SfxRef SfxWARN = new SfxRef( frontend:0x0081r );

	final static int RID_button_decal = misc.garage:0x0000008Cr;

	//0-63: colorbuttons hasznaljak!
	final static int CMD_DECALBUTTONS = 64;
	//64-68: decalsbuttons hasznaljak!
	final static int CMD_NONE = 100;

	final static int CMD_BRUSHINC = 102;
	final static int CMD_BRUSHDEC = 103;
	final static int CMD_BRUSHRCW = 104;
	final static int CMD_BRUSHRCCW = 105;
	final static int CMD_BRUSHFLIP = 106;
	final static int CMD_LOADSKIN = 107;
	final static int CMD_SAVESKIN = 108;
	final static int CMD_COPYSKIN = 109;
	final static int CMD_C_COPY = 110;
	final static int CMD_C_PAINT = 111;

	final static int MODE_NONE = 0;
	final static int MODE_PAINTCOLOR = 1;
	final static int MODE_PAINTPART = 2;
	final static int MODE_PAINTDECAL = 3;
	final static int MODE_COPY = 4;

	final static int COLORMODE_SWATCH = 0;
	final static int COLORMODE_CUSTOM = 1;

	final static int SLIDERMODE_RGB = 0;
	final static int SLIDERMODE_HSV = 1;

	final static int CMD_SLIDER_R = 112;
	final static int CMD_SLIDER_G = 113;
	final static int CMD_SLIDER_B = 114;

	final static int CMD_SLIDER_H = 115;
	final static int CMD_SLIDER_S = 116;
	final static int CMD_SLIDER_V = 117;

	final static int CMD_SAVE_PAINT = 118;
	final static int CMD_SWATCH = 119;
	final static int CMD_COLORPICKER = 120;
	final static int CMD_RGB = 121;
	final static int CMD_HSV = 122;
	final static int CMD_CLEARCOLORS = 123;
	final static int CMD_LOADCOLORS = 124;
	final static int CMD_SAVECOLORS = 125;

	final static int CMD_INPUTCOLOR = 126;

	ControlSetState css;

	PaintInventory paintCans;
	PaintShopDisplay temporaryCan;
	int overVehicle; //where is the pointer?

	int mode, colormode, slidermode;
	int lastPaintMode=MODE_PAINTPART;
	int lastColorMode=COLORMODE_CUSTOM;
	int lastSliderMode=SLIDERMODE_RGB;

	GameRef paintCursor;
	ResourceRef paintBrush;

	// Diagnostic only: inspect RPK parent containers and sibling resources.
	// This does not recolor, reload, duplicate, or mutate any resource.
	void listResourceChildren(String label, ResourceRef root, String[] report, int[] totalCount)
	{
		if (!root)
		{
			report[0] += "\n" + label + ": NULL";
			return;
		}

		report[0] += "\n-- " + label
			+ " id=" + root.id()
			+ " kind=" + root.type()
			+ " parent=" + root.getParentID()
			+ " --";

		ResourceRef child = root.getFirstChild();
		int childCount = 0;
		while (child && childCount < 64 && totalCount[0] < 280)
		{
			totalCount[0] = totalCount[0] + 1;
			report[0] += "\n CHILD id=" + child.id()
				+ " kind=" + child.type()
				+ " parent=" + child.getParentID();
			if (child.type() == 3)
			{
				RenderRef renderChild = new RenderRef(child.id());
				report[0] += " typeID=" + renderChild.getTypeID();
			}
			child = child.getNextChild();
			childCount++;
		}
		if (child)
			report[0] += "\n CHILD_LIST_TRUNCATED after " + childCount;
	}

	void inspectResourceLineage(String label, int resourceID, String[] report, int[] totalCount)
	{
		report[0] += "\n\n=== RESOURCE " + label + " id=" + resourceID + " ===";
		if (resourceID == 0)
		{
			report[0] += "\nSKIP zero ID";
			return;
		}

		ResourceRef current = new ResourceRef(resourceID);
		listResourceChildren("RESOURCE_ROOT", current, report, totalCount);

		for (int depth = 0; current && depth < 3 && totalCount[0] < 280; depth++)
		{
			ResourceRef parent = current.getParent();
			if (!parent)
			{
				report[0] += "\nPARENT depth=" + depth + " NULL";
				break;
			}
			report[0] += "\nPARENT depth=" + depth
				+ " id=" + parent.id()
				+ " kind=" + parent.type()
				+ " parent=" + parent.getParentID();
			listResourceChildren("PARENT_CHILDREN depth=" + depth, parent, report, totalCount);
			current = parent;
		}
	}

	void restoreRenderAlphaTest() {}

	void toggleRenderAlphaTest(GameRef target)
	{
		if (!target)
		{
			logRenderProbe("ignored null target");
			return;
		}

		if (target.getInfo(GameType.GII_CATEGORY) != GIR_CAT_PART)
		{
			logRenderProbe("ignored non-part target id=" + target.id()
				+ " type=" + target.type());
			return;
		}

		Object scriptInstance = target.getScriptInstance();
		if (!(scriptInstance instanceof Part))
		{
			String error = "Resource lineage probe: target has no Part script instance";
			infoline.changeText(error);
			logRenderProbe(error);
			return;
		}

		Part selected = (Part)scriptInstance;
		int targetID = target.id();
		int partRenderID = selected.getRenderType();
		int partMeshID = selected.getMesh();
		int partTextureID = selected.getTexture();

		String[] report = new String[1];
		report[0] = "RESOURCE LINEAGE PROBE part=" + targetID
			+ " partRT=" + partRenderID
			+ " partMesh=" + partMeshID
			+ " partTex=" + partTextureID;

		int[] totalCount = new int[1];
		totalCount[0] = 0;
		inspectResourceLineage("PART_RENDER_TYPE", partRenderID, report, totalCount);
		inspectResourceLineage("PART_MESH", partMeshID, report, totalCount);
		inspectResourceLineage("PART_TEXTURE", partTextureID, report, totalCount);

		report[0] += "\nTOTAL_CHILD_NODES_SCANNED=" + totalCount[0]
			+ " (per parent <=64; overall cap 280)";
		infoline.changeText("Resource lineage: part " + targetID
			+ " nodes=" + totalCount[0] + " (see slrr_render_probe.sdat)");
		logRenderProbe(report[0]);
	}

	public void beginPaint( int cursor_id )
	{
		paintCursor=new GameRef( cursor_id );
		enableControlHook();
	}
	
	public void endPaint()
	{
		restoreRenderAlphaTest();
		disableControlHook();
		paintCursor=null;
	}
	
	public void setDecalButtons()
	{
		int max = player.decals.size();
		
		clearDecalButtons();
		
		for( int i=0; i<decalButtons.length; i++ )
		{
			//a gomb tipus (kicsi)
			RenderRef base = new RenderRef( misc.garage:0x0083r );
			
			if( max > showDecals+i )
			{
				//duplikaljuk a tipust, hogy a texturamodositas egyedi legyen
				decalButtonTypes[i] = new RenderRef();
				decalButtonTypes[i].duplicate( base );
				
				ResourceRef baseTex = new ResourceRef(misc.garage:0x0103r);//a gomb tipus default texturaja
				ResourceRef rr = player.decals.elementAt( showDecals+i ); //az uj textura
				//lecsereljuk!
				decalButtonTypes[i].changeResource( baseTex, rr );
				decalButtonTextures[i] = rr;
				
				decalButtonInstances[i] = new RenderRef(decalButtons[i], decalButtonTypes[i].id(), "decal button");
				decalButtons[i].queueEvent( null, GameType.EVENT_COMMAND, "render " + decalButtonInstances[i].id() );
			}
			else
			{
				decalButtonTypes[i] = null;//base;
			}
		
		}
		
		invLineTxt.changeText( Integer.toString( (showDecals+1)/decalButtons.length+1 ) );
	}
	
	public void clearDecalButtons()
	{
		for( int i=0; i<decalButtons.length; i++ )
		{
			if( decalButtonInstances[i] )
			{
				decalButtonInstances[i].destroy();
				decalButtonInstances[i]=null;
			}
			if( decalButtonTypes[i] )
			{
				decalButtonTypes[i].destroy();
				decalButtonTypes[i]=null;
			}
			if( decalButtonTextures[i] )
			{
				decalButtonTextures[i].unload();
				decalButtonTextures[i] = null;
			}
		}
	}
	
	
	
	public void handleEvent( GameRef obj_ref, int event, String param )
	{
		int tok = -1;
		
		if( event == EVENT_CURSOR )
		{
			int ec = param.token( ++tok ).intValue();
			
			int cursor_id = param.token( ++tok ).intValue();
			
			if (ec == GameType.EC_LCLICK)
			{
				int obj_id = param.token( ++tok ).intValue(); //ignored now
				Vector3 obj_pos = new Vector3(param.token( ++tok ).floatValue(),
				param.token( ++tok ).floatValue(),
				param.token( ++tok ).floatValue());
				Vector3 worldpos = new Vector3(param.token( ++tok ).floatValue(),
				param.token( ++tok ).floatValue(),
				param.token( ++tok ).floatValue());
				GameRef dest = obj_ref;
				int cat = dest.getInfo(GameType.GII_CATEGORY);
				
				if (mode==MODE_PAINTPART )
				{
					// Keep the Painter click sound as event feedback during this test.
					if (cat == GIR_CAT_PART)
					{
						SfxSPRAY.play(worldpos, 0.0, 1.0, 1.0, 0);
						toggleRenderAlphaTest(dest);
					}
				}
				else
				if (mode==MODE_PAINTDECAL)
				{ 
					if( cat == GIR_CAT_PART || cat == GIR_CAT_VEHICLE )
					{
						if( paintBrush )
						{
							SfxDECAL.play(worldpos, 0.0, 1.0, 1.0, 0); 
							doPaint( new GameRef( cursor_id), brushColor, paintBrush.id(), 0, decalRotation, decalSize, decalFlip );
							
							//itt biztos hogy Decal tipusu, es a player.decals-ban van
							if( !(-- ((Decal)paintBrush).stickies ) )
							{
								player.decals.removeElement( paintBrush );
								paintBrush=null;
								
								int max = player.decals.size(); //kiurult sor?
								if( showDecals >= decalButtons.length && showDecals >= max)
									showDecals-=decalButtons.length;
								
								setDecalButtons();
								infoline.changeText( null );
							}
							else
							{
								infoline.changeText( ((Decal)paintBrush).stickies + " stickies left" ); //mirrored!
							}
						}
					}
				}
				else
				if( mode == MODE_COPY )
				{
					if( copy_status == 0)// Copy
					{
						Object part = obj_ref.getScriptInstance();
						if( part instanceof Part || part instanceof Chassis ) // clicked object is part or chassis
						{
							copy_texture = part.getTexture();
							
							if( copy_texture )
								copy_prev.changeTexture( new ResourceRef(copy_texture) ); 
							else
							{
								SfxWARN.play();
								copy_prev.changeTexture( new ResourceRef(Frontend:0x013Cr) );
								
							}
							
							//infoline.changeText(copy_texture); //Debug
						}
					}
					else // Paint
					{
						Object part = obj_ref.getScriptInstance();
						if( part instanceof Part || part instanceof Chassis ) // clicked object is part or chassis
						{
							int tmp_texture = part.getTexture();// check is this part paintable
							
							if( copy_texture && tmp_texture )
							{
								SfxDECAL.play(worldpos, 0.0, 1.0, 1.0, 0);
								
								ResourceRef temp_texture = new ResourceRef();
								temp_texture.duplicate(new ResourceRef(copy_texture));
								
								part.setTexture( temp_texture.id() );
								
							}
							else
							SfxWARN.play();
						}
					}
				}
			}
			else
			if (ec == GameType.EC_LDOWN)
			{
				if( overVehicle )
				{
					if (mode==MODE_PAINTCOLOR)
					{
						Vector3 worldpos = Input.cursor.getPickedPos();
						spraySfxID = SfxSPRAY.play( worldpos, 0.0, 1.0, 1.0, SfxRef.SFX_LOOP | SfxRef.SFX_NOAUTOSTOP, Input.cursor.id() ); //start looped sfx
						spraySfxOn=1;
						beginPaint( cursor_id );
					}
				}
			} 
			else
			if (ec == GameType.EC_LUP)
			{
				if (mode==MODE_PAINTCOLOR)
				{
					if( spraySfxOn )
					{
						SfxSPRAY.stop( spraySfxID ); //end looped sfx
						spraySfxOn=0;
					}
					endPaint();
				}
			}
			else
			if (ec == GameType.EC_HOVER)
			{
				GameRef part, dest = new GameRef(param.token( ++tok ).intValue());
				int objectChanged;
				
				Object p = dest.getScriptInstance();
				if( p instanceof Part )
				{
					if( !overVehicle )
					{
						overVehicle=1;
						objectChanged=1;
					}
				}
				else
				{
					if( overVehicle )
					{
						overVehicle=0;
						objectChanged=1;
					}
				}
				
				if (mode==MODE_PAINTDECAL)
				{
					if( objectChanged )
					{
						if( overVehicle )
						{
							beginPaint( cursor_id );
						}
						else
						{
							endPaint();
							//flush texture
							if( player.car )
								xPaint( player.car );
						}
					}
				}
			}
			else
			if (ec == GameType.EC_RDRAGBEGIN)
			{
				//enable camera control with mouse
				//player.controller.user_Add( Input.AXIS_LOOK_UPDOWN, ControlSet.MOUSE, 1, -1.0f, 1.0f, -1.0f, 1.0f);
				//player.controller.user_Add( Input.AXIS_LOOK_LEFTRIGHT, ControlSet.MOUSE, 0, -1.0f, 1.0f, -1.0f, 1.0f);
				//disable cursor movement
				player.controller.user_Del( Input.AXIS_CURSOR_X, ControlSet.MOUSE, 0 );
				player.controller.user_Del( Input.AXIS_CURSOR_Y, ControlSet.MOUSE, 1 );
				Input.cursor.cursor.queueEvent( null, GameType.EVENT_COMMAND, "lock" );
			} 
			else
			if (ec == GameType.EC_RDRAGEND)
			{
				//disable camera control with mouse
				//player.controller.user_Del( Input.AXIS_LOOK_UPDOWN, ControlSet.MOUSE, 1 );
				//player.controller.user_Del( Input.AXIS_LOOK_LEFTRIGHT, ControlSet.MOUSE, 0 );
				//enable cursor movement
				player.controller.user_Add( Input.AXIS_CURSOR_X, ControlSet.MOUSE, 0, -1.0f, 1.0f, -1.0f, 1.0f);
				player.controller.user_Add( Input.AXIS_CURSOR_Y, ControlSet.MOUSE, 1, -1.0f, 1.0f, -1.0f, 1.0f);
				Input.cursor.cursor.queueEvent( null, GameType.EVENT_COMMAND, "unlock" );
			} 
		}
	}
	
	public void control( float t )
	{
		if( mode == MODE_PAINTDECAL )
		{
			if( paintBrush )
				doPaint( paintCursor, brushColor, paintBrush.id(), 1, decalRotation, decalSize, decalFlip);
		}
		else
		if( mode == MODE_PAINTCOLOR )
		{
			Vector3 worldpos = Input.cursor.getPickedPos();
			spraySfxID = SfxSPRAY.play( worldpos, 0.0, 1.0, 1.0, SfxRef.SFX_LOOP | SfxRef.SFX_NOAUTOSTOP, Input.cursor.id() ); //update looped sfx
			
			doPaint( paintCursor, temporaryCan.paintColor, paintBrush.id(), 0, decalRotation, decalSize, decalFlip);
		}
	}
	
	public void osdCommand( int cmd )
	{
		if( cmd == CMD_LOADSKIN )
		{
			VehicleSkinFileReqDialog d = new VehicleSkinFileReqDialog( player.controller, Dialog.DF_FREEZE|Dialog.DF_MODAL|Dialog.DF_DEFAULTBG, "LOAD PAINT JOB", "LOAD", GameLogic.skinSaveDir, "*" );
			
			if( d.display() == 0 )
			{
				String filename = GameLogic.skinSaveDir + d.fileName;
				player.car.loadSkin( filename );
			}
		}
		else
		if( cmd == CMD_SAVESKIN )
		{
			VehicleSkinFileReqDialog d = new VehicleSkinFileReqDialog( player.controller, Dialog.DF_FREEZE|Dialog.DF_MODAL|Dialog.DF_DEFAULTBG|Dialog.FRF_SAVE, "SAVE PAINT JOB", "SAVE", GameLogic.skinSaveDir, "*" );
			
			if( d.display() == 0 )
			{
				String filename = GameLogic.skinSaveDir + d.fileName;
				if (File.exists(filename))
				{
					File.delete( filename );
					File.delete( filename + ".*" );
				}
				player.car.saveSkin( filename );
				
				new WarningDialog( player.controller, Dialog.DF_FREEZE|Dialog.DF_MODAL|Dialog.DF_DEFAULTBG, "SAVE PAINT JOB", "Paint job has been saved." ).display();
			}
		}
		else
		if( cmd == CMD_SCROLL_UP )
		{
			if(( mode == MODE_PAINTCOLOR || mode == MODE_PAINTPART ) && colormode == COLORMODE_SWATCH )
			{
				paintCans.upScroll();
				invLineTxt.changeText( Integer.toString( paintCans.currentLine()+1 ) );
			}
			else
			if( mode == MODE_PAINTDECAL )
			{
				int max = player.decals.size();
				if( max > decalButtons.length )
				{
					if( showDecals >= decalButtons.length )
					{
						showDecals-=decalButtons.length;
					}
					else
					{
						showDecals=(max-1)-((max-1)%decalButtons.length);
					}
					setDecalButtons();
				}
			}
		}
		else
		if( cmd == CMD_SCROLL_DOWN )
		{
			if(( mode == MODE_PAINTCOLOR || mode == MODE_PAINTPART ) && colormode == COLORMODE_SWATCH )
			{
				paintCans.downScroll();
				invLineTxt.changeText( Integer.toString( paintCans.currentLine()+1 ) );
			}
			else
			if( mode == MODE_PAINTDECAL )
			{
				int max = player.decals.size();
				if( max > decalButtons.length )
				{
					if( showDecals+decalButtons.length < max )
					{
						showDecals+=decalButtons.length;
					}
					else
					{
						showDecals=0;
					}
					setDecalButtons();
				}
			}
		}
		else
		if( cmd == CMD_C_COPY )
		{
			copy_button.disable();
			copy_button.label.txt.changeColor( 0xFFFFCB00 );
			paint_button.enable();
			
			copy_status = 0;
			Input.cursor.setPointer( Frontend.pointers, "J"); //Default arrow
		}
		else
		if( cmd == CMD_C_PAINT )
		{
			copy_button.enable();
			paint_button.disable();
			paint_button.label.txt.changeColor( 0xFFFFCB00 );
			
			copy_status = 1;
			Input.cursor.setPointer( Frontend.pointers, "D"); // spray
		}
		else
		if( cmd == CMD_COPYSKIN )
		{
			changeMode( MODE_COPY );
		}
		if( cmd == CMD_SPRAY )
		{
			changeMode( MODE_PAINTCOLOR );
		}
		else
		if( cmd == CMD_PART )
		{
			changeMode( MODE_PAINTPART );
		}
		else
		if( cmd == CMD_DECALS )
		{
			changeMode( MODE_PAINTDECAL );
		}
		else
		if( cmd == CMD_BRUSHRCCW )
		{
			decalRotation -= 0.025;
		}
		else
		if( cmd == CMD_BRUSHRCW )
		{
			decalRotation += 0.025;
		}
		else
		if( cmd == CMD_BRUSHINC )
		{
			if( decalSize < 4.2 )
				decalSize+= 0.025;
		}
		else
		if( cmd == CMD_BRUSHDEC )
		{
			if( decalSize > 0.05 )
				decalSize-= 0.025;
		}
		else
		if( cmd == CMD_BRUSHFLIP )
		{
			decalFlip=1-decalFlip;
		}
		else
		if( cmd >= CMD_DECALBUTTONS && cmd <= CMD_DECALBUTTONS + decalButtons.length )
		{
			int decalIndex = showDecals+cmd-CMD_DECALBUTTONS;
			if( player.decals.size() > decalIndex )
			{
				paintBrush=player.decals.elementAt( decalIndex );
				infoline.changeText( ((Decal)paintBrush).stickies + " stickies left" ); //mirrored!
			}
		}
		if( cmd >= CMD_SLIDER_R && cmd <= CMD_SLIDER_B )
		{
			if( cmd == CMD_SLIDER_R )
				paintRed = osd.sliderValue;
			if( cmd == CMD_SLIDER_G )
				paintGreen = osd.sliderValue;
			if( cmd == CMD_SLIDER_B )
				paintBlue = osd.sliderValue;
			queueHexColor = rgb2hex( paintRed, paintGreen, paintBlue );
			usingRgbColor = 1;
			//refreshDisplayCan();
		}
		if( cmd >= CMD_SLIDER_H && cmd <= CMD_SLIDER_V )
		{
			if( cmd == CMD_SLIDER_H )
				paintHue = osd.sliderValue;
			if( cmd == CMD_SLIDER_S )
				paintSaturation = osd.sliderValue;
			if( cmd == CMD_SLIDER_V )
				paintValue = osd.sliderValue;
			newRgb = PainterExtensions.hsv2rgb( paintHue, paintSaturation, paintValue );
			paintRed = newRgb[0];
			paintGreen = newRgb[1];
			paintBlue = newRgb[2];
			queueHexColor = rgb2hex( paintRed, paintGreen, paintBlue );
			usingRgbColor = 1;
			//refreshDisplayCan();
		}
		else
		if( cmd == CMD_SAVE_PAINT )
		{
			paintCans.addItem( new PaintCan( queueHexColor, 1.0 ) );
		}
		if( cmd == CMD_SWATCH )
		{
			changeColorMode( COLORMODE_SWATCH );
		}
		if( cmd == CMD_COLORPICKER )
		{
			changeColorMode( COLORMODE_CUSTOM );
		}
		if( cmd == CMD_RGB )
		{
			osd.findGadget( this, CMD_SLIDER_R ).setValue( paintRed );
			osd.findGadget( this, CMD_SLIDER_G ).setValue( paintGreen );
			osd.findGadget( this, CMD_SLIDER_B ).setValue( paintBlue );
			changeSliderMode( SLIDERMODE_RGB );
		}
		if( cmd == CMD_HSV )
		{
			newHsv = PainterExtensions.rgb2hsv( paintRed, paintGreen, paintBlue );
			paintHue = newHsv[0];
			paintSaturation = newHsv[1];
			paintValue = newHsv[2];
			osd.findGadget( this, CMD_SLIDER_H ).setValue( paintHue );
			osd.findGadget( this, CMD_SLIDER_S ).setValue( paintSaturation );
			osd.findGadget( this, CMD_SLIDER_V ).setValue( paintValue );
			changeSliderMode( SLIDERMODE_HSV );
		}
		if( cmd == CMD_CLEARCOLORS )
		{
			int reallyDelete;

			reallyDelete = ( 0 == new NoYesDialog( player.controller, Dialog.DF_MODAL|Dialog.DF_DEFAULTBG, "Clear all paint colors", "This will delete all your paint colors except the last one in the list. Do you really want to clear all your paint colors?" ).display() );
			
			if( reallyDelete )
			{
				paintCans.cleanCans();
				paintCans.save( paintCansFile );
				new SfxRef( Frontend.SFX_MONEY ).play();
			}
		}
		if( cmd == CMD_LOADCOLORS )
		{
			ColorPaletteFileReqDialog d = new ColorPaletteFileReqDialog( player.controller, Dialog.DF_FREEZE|Dialog.DF_MODAL|Dialog.DF_DEFAULTBG, "LOAD COLOR PALETTE", "LOAD", paletteSaveDir, "*" );
			
			if( d.display() == 0 )
			{
				String filename = paletteSaveDir + d.fileName;
				paintCans.load( filename );
			}
		}
		else
		if( cmd == CMD_SAVECOLORS )
		{
			ColorPaletteFileReqDialog d = new ColorPaletteFileReqDialog( player.controller, Dialog.DF_FREEZE|Dialog.DF_MODAL|Dialog.DF_DEFAULTBG|Dialog.FRF_SAVE, "SAVE COLOR PALETTE", "SAVE", paletteSaveDir, "*" );
			
			if( d.display() == 0 )
			{
				String filename = paletteSaveDir + d.fileName;
				if (File.exists(filename))
				{
					File.delete( filename );
				}
				paintCans.save( filename );
				
				new WarningDialog( player.controller, Dialog.DF_FREEZE|Dialog.DF_MODAL|Dialog.DF_DEFAULTBG, "SAVE COLOR PALETTE", "The current color palette has been saved to '" + filename + "'." ).display();
			}
		}
		else
		if( cmd == CMD_INPUTCOLOR )
		{
			int typedColor;
			StringRequesterDialog d = new StringRequesterDialog( player.controller, Dialog.DF_MODAL|Dialog.DF_DEFAULTBG|Dialog.SIF_NOEMPTY, "Enter New Paint Color (Format: R255, G255, B255)", "" );
			if( d.display() == 0 )
			{
				String typedR = d.input.token( 0, "," );
				String typedG = d.input.token( 1, "," );
				String typedB = d.input.token( 2, "," );
				if( typedR && typedG && typedB )
				{
					typedColor = rgb2hex( typedR.intValue(), typedG.intValue(), typedB.intValue() );
					openHexColor( typedColor );
				}
			}
		}
	}
	
	public int rgb2hex( int R, int G, int B )
	{
		int hexColor = 0x000000;
		hexColor = hexColor + (0x010000*R) + (0x000100*G) + (0x000001*B);
		return hexColor;
	}
	
	public String prepareHexString( int input )
	{
		input = input & 0x00FFFFFF; // Display RGB only; hide the alpha byte.
		String storeString = Integer.toHexString( input ).token( 1, "x" );
		String prepareString = "";
		int i;
		if( storeString.token(i, "123456789abcdef").length() > 2 )
		{
			int newzeros = storeString.token(0, "123456789abcdef").length()-2;
			for( i = 0; i < newzeros; i++ )
			{
				prepareString = prepareString + "0";
			}
		}
		for( i = 0; i <= 2; i++)
		{
			prepareString = prepareString + ( storeString.token(i, "0") ) + ( storeString.token(1+i, "123456789abcdef") );
		}
		return prepareString;
	}
	
	public void openHexColor( int newColor )
	{
		newColor = newColor & 0x00FFFFFF; // Alpha is not part of the RGB sliders.
		int newRed = newColor / 0x00010000;
		int newGreen = (newColor-newRed*0x00010000) / 0x00000100;
		int newBlue = newColor-newGreen*0x00000100-newRed*0x00010000;
		paintRed = newRed;
		paintGreen = newGreen;
		paintBlue = newBlue;
		osd.findGadget( this, CMD_SLIDER_R ).setValue( newRed );
		osd.findGadget( this, CMD_SLIDER_G ).setValue( newGreen );
		osd.findGadget( this, CMD_SLIDER_B ).setValue( newBlue );
		queueHexColor = newColor;
		refreshDisplayCan();
		
		newHsv = PainterExtensions.rgb2hsv( paintRed, paintGreen, paintBlue );
		paintHue = newHsv[0];
		paintSaturation = newHsv[1];
		paintValue = newHsv[2];
		osd.findGadget( this, CMD_SLIDER_H ).setValue( paintHue );
		osd.findGadget( this, CMD_SLIDER_S ).setValue( paintSaturation );
		osd.findGadget( this, CMD_SLIDER_V ).setValue( paintValue );
	}
	
	public void refreshDisplayCan()
	{
		temporaryCan.removeItem( temporaryCan.lastCanId );
		temporaryCan.addItem( new PaintCan( queueHexColor, 1.0 ) );
		hexButton.changeLabelText( "#" + prepareHexString( queueHexColor ) );
	}
	
	public void run()
	{
		for(;;)
		{
			if( queueHexColor != oldQueueHexColor )
			{
				oldQueueHexColor = queueHexColor;
				refreshDisplayCan();
			}

			refresherThread.sleep( 40 );
		}
	}
}

class PaintInventory extends VisualInventory
{
	int paintColor;
	int lastCanId;

	final static int	CANSFILEID = 0xe21cd8ba; //totally random hex hash - let's hope this works
	final static int	CANSFILEVERSION = 1;
	
	Painter painter;
	
	public PaintInventory( Player player, float left, float top, float width, float height, Painter p )
	{
		super( player, left, top, width, height, 1 );
		painter = p;
		
		// super( player );
		
		// initVisuals( left, top, width, height );
	}
	
	public PaintInventory( Player player, float left, float top, float width, float height, Painter p, int intr )
	{
		super( player, left, top, width, height, intr );
		painter = p;
		
		// super( player );
		
		// initVisuals( left, top, width, height );
	}
	
// 	public void leftClick( int id )
// 	{
// 		if( id >= items.size() ) //sohasem legyen ures!!
// 		id = items.size()-1;
// 		
//		lastCanId=id;
//
//	 	PaintCan pc = getCanbyIndex( id );
// 		paintColor = pc.color;
//
//	 	new SfxRef( Frontend.SFX_MENU_SELECT ).play(); 
// 	}
	
// 	public void leftDragNDrop( GameRef draggedOnto, GameRef draggedItem )
// 	{
// 	}
	
	public void panelLeftClick( int index )
	{
		index += currentLine()*partsPerLine;
		
		if( index < items.size() ) //sohasem legyen ures!!
		{
			lastCanId=index;
			painter.changePaintSource(0);
			
			PaintCan pc = getCanbyIndex( index );
			paintColor = pc.color;
			painter.openHexColor( paintColor );
			
			new SfxRef( Frontend.SFX_MENU_SELECT ).play();
		}
	}
	
	public void panelSwap( int index_a, Gadget dropped )
	{
	}
	
	public void panelDragNDrop( int index, GameRef dropped )
	{
	}
	
	public int addItem( PaintCan p )
	{
		if( !items.size() )
			paintColor=p.color;
		
		return super.addItem( p );
	}
	
	public void removeItem( int index )
	{
		super.removeItem( index );
	}
	
	public void removeItem( InventoryItem item )
	{
		super.removeItem( item );
	}
	
	public void save( String fileName )
	{
		File cansFile = new File( fileName );

		if( cansFile.open( File.MODE_WRITE ) )
		{
			int		i;

			cansFile.write( CANSFILEID );
			cansFile.write( CANSFILEVERSION );

			cansFile.write( this.items.size() );
			cansFile.write( this.lastCanId );
			//System.log("Saved header, number of colors = " + this.items.size() );
			for( i = 0; i < this.items.size(); i++ )
			{
				PaintCan pc = getCanbyIndex( i );
				cansFile.write( pc.color );
			}
			cansFile.close();
		}
	}

	public static int fileCheck( String fileName )
	{
		int	status;
		if( fileName != Painter.paintCansFile )
		{
			File cansFile = new File( fileName );

			if( cansFile.open( File.MODE_READ ) )
			{
				if( cansFile.readInt() == CANSFILEID )
					if( cansFile.readInt() == CANSFILEVERSION )
						status = 1;

				cansFile.close();
			}
		}
		return status;
	}

	public void load( String fileName )	
	{
		File cansFile = new File( fileName );
		if( cansFile.open( File.MODE_READ ) )
		{
			if( cansFile.readInt() == CANSFILEID )
			{
				if( cansFile.readInt() == CANSFILEVERSION )
				{
					items = new Vector();
					int			i, n;
					int[]			newColors;
					n = cansFile.readInt();
					lastCanId = cansFile.readInt();
					//System.log("Read header, number of colors = " + n);
					for( i = 0; i < n; i++ )
					{
						this.addItem( new PaintCan( cansFile.readInt(), 1.0 ) );
					}
					scrollTo( lastCanId );
					painter.invLineTxt.changeText( Integer.toString( this.currentLine()+1 ) );
				}
			}
			cansFile.close();
		}
	}
	
	public void deleteAllCans()
	{
		items = new Vector();
		scrollTo( 0 );
		painter.invLineTxt.changeText( Integer.toString( this.currentLine()+1 ) );
	}
	
	public void cleanCans()
	{
		deleteAllCans();
		this.addItem( new PaintCan( painter.queueHexColor, 1.0 ) );
	}
}

class PaintShopDisplay extends PaintInventory
{
	public void initVisuals( float left, float top, float width, float height )
	{
		partsPerLine = 1;
		linesPerPage = 1;
		//mely itemek lehetnek lathatoak kezdetben?
		cline=0;
		start = cline * partsPerLine;
		stop = start + linesPerPage * partsPerLine;

		float hSpacing = 0.013, vSpacing = 0.01;
		float	itemWidth, itemHeight;
		itemWidth=(width-(partsPerLine-1)*hSpacing)/partsPerLine;
		itemHeight=(height-(linesPerPage-1)*vSpacing)/linesPerPage;

		panels=new InventoryPanel[partsPerLine*linesPerPage];

		int	index;
		float cheight=top;
		for( int i=0; i<linesPerPage; i++ )
		{
			float cwidth=left;
			for( int j=0; j<partsPerLine; j++ )
			{
				index = i*partsPerLine+j;
				panels[index]=new InventoryPanel( this, index, cwidth, cheight, itemWidth, itemHeight );
				cwidth+=itemWidth+hSpacing;
			}
			cheight+=itemHeight+vSpacing;
		}
	}
	
	public void panelLeftClick( int index )
	{
		index += currentLine();
		
		if( index < items.size() ) //sohasem legyen ures!!
		{
			lastCanId=index;
			painter.changePaintSource(1);
			
			PaintCan pc = getCanbyIndex( index );
			paintColor = pc.color;
			
			new SfxRef( Frontend.SFX_MENU_SELECT ).play();
		}
	}
}

//
class VehicleSkinFileReqDialog extends FileRequesterDialog
{
	public VehicleSkinFileReqDialog( Controller ctrl, int myflags, String mytitle, String OKButtonText, String path, String mask )
	{
		super( ctrl, myflags, mytitle, OKButtonText, path, mask );
	}
	
	public int validator( String filename )
	{
		return Vehicle.fileCheck_Skin( filename );
	}
}

class ColorPaletteFileReqDialog extends FileRequesterDialog
{	
	public ColorPaletteFileReqDialog( Controller ctrl, int myflags, String mytitle, String OKButtonText, String path, String mask )
	{
		super( ctrl, myflags, mytitle, OKButtonText, path, mask );
	}
	
	public int validator( String filename )
	{
		return PaintInventory.fileCheck( filename );
	}
}