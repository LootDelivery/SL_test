package java.game;

import java.io.*;
import java.util.*;
import java.util.resource.*;
import java.render.*;
import java.render.osd.*;
import java.sound.*;
import java.game.parts.*;
import java.game.parts.bodypart.Window;
import java.game.parts.bodypart.Windshield;

public class InventoryItem_Part extends InventoryItem
{
	private	Part	partXXX;
	private	int	partTypeID;

	int	compatibility;

	// Temporary diagnostic for tracing whether inventory thumbnails call the glass preview hook.
	static String inventoryPreviewTrace = "";
	static int inventoryPreviewTraceLines = 0;
	final static String INVENTORY_PREVIEW_TRACE_PATH = "slrr_inventory_preview_trace_virtual_hook.sdat";

	void logInventoryPreviewTrace( String phase, Part p )
	{
		if (inventoryPreviewTraceLines >= 100) return;
		int partID = 0;
		int renderType = 0;
		int meshID = 0;
		int isWindow = 0;
		int isWindshield = 0;
		if (p)
		{
			partID = p.id();
			renderType = p.getRenderType();
			meshID = p.getMesh();
			if (p instanceof Window) isWindow = 1;
			if (p instanceof Windshield) isWindshield = 1;
		}
		String line = "[InventoryGlassTrace] " + phase
			+ " part=" + partID
			+ " window=" + isWindow
			+ " windshield=" + isWindshield
			+ " renderType=" + renderType
			+ " mesh=" + meshID;
		if (inventoryPreviewTraceLines == 0)
			inventoryPreviewTrace = "[InventoryGlassTrace] BUILD-MARKER virtual-hook-v1\\n";
		inventoryPreviewTrace += line + "\n";
		inventoryPreviewTraceLines++;
		File traceFile = new File(INVENTORY_PREVIEW_TRACE_PATH);
		if (traceFile.open(File.MODE_WRITE))
		{
			traceFile.write(inventoryPreviewTrace);
			traceFile.close();
		}
		else
		{
			System.log("[InventoryGlassTrace] Could not open " + INVENTORY_PREVIEW_TRACE_PATH);
		}
	}

	public InventoryItem_Part( Inventory inv, int id )
	{
		super( inv );
		setPart( id );
	}

	public InventoryItem_Part( Inventory inv, Part p )
	{
		super( inv );

		// Move the real part into this inventory item's local root.
		p.command( "remove 0 "+localroot.id() );
		p.setParent( localroot );
		p.setPos( new Vector3( 0,0,0 ) );
		setPart( p );
	}

	public void	setPart( int id )
	{
		partTypeID=id;
		partXXX=null;
	}

	public void	setPart( Part p )
	{
		partTypeID=0;
		partXXX=p;
	}

	public Part getPart()
	{
		if( !partXXX )
		{
			if( !partTypeID )
			{
				return null;
			}
			else
			{
				GameRef xa = new GameRef();
				partXXX = xa.create( localroot, new GameRef(partTypeID), "0,0,0,0,0,0", "part_created_by_inventory" );
				partTypeID=0;
			}
		}

		return partXXX;
	}

	public int getTypeID()
	{
		if(partTypeID) return partTypeID;
		return 0;
	}

	public void flush()
	{
		if( partXXX )
		{
			partTypeID=partXXX.getInfo( GameType.GII_TYPE );
			partXXX.destroy();
			partXXX=null;
		}
	}

	public int getName()
	{
		return getPart().name;
	}

	public int getDescription()
	{
		return getPart().description;
	}

	public int getPrice()
	{
		return getPart().currentPrice();
	}

	public int getLogo()
	{
		return getPart().getLogo();
	}

	public void copyToInventory( Inventory other )
	{
		other.addItem( getPart().getInfo( GameType.GII_TYPE ) );
	}

	public String installToCar( Vehicle car, Vector3 pos )
	{
		String error;

		if( car )
		{
			Part part = getPart();

			GameRef xpart = new GameRef();
			int[] slotId = part.install_OK( car, 0, xpart, 0, pos );
			if( slotId )
			{
				if( !(error=part.installCheck( xpart.getScriptInstance(), slotId )) )
				{
					if (pos)
						part.command( "install 0 "+car.id()+" 0 0 0 "+pos.x+" "+pos.y+" "+pos.z );
					else
						part.command( "install 0 "+car.id() );
				}
			}
			else
				error = "";

			if( !error )
			{
				setPart( null );
			}
		}

		return error;
	}

	public String getInfo()
	{
		Part p = getPart();

		int percentCondition=p.getConditionNoAttach()*100;
		String flags = "";
		if( p.isComplex() )
			flags = flags + "+";
		if( p.isTuneable() )
			flags = flags + "T";
		if( !p.isStreetLegal() )
			flags = flags + "!";

		if( flags )
			flags = " [" + flags + "]";

		return p.name + flags + " (" + percentCondition + "%)";
	}

	public void show( InventoryPanel ip )
	{
		Part p = getPart();
		logInventoryPreviewTrace( "ITEM-SHOW-ENTER", p );

		if( p )
		{
			p.setMatrix( null, null );
			ip.createDefCamera( getPart().getInfo( GameType.GII_SIZE )/100.0, 1 );
			ip.createDefLight();
			refreshGlassOpacityPreview();
		}
	}

	// Called on show and while this preview is focused, so delayed render children
	// can receive the saved appearance once the engine creates them.
	public void refreshGlassOpacityPreview()
	{
		Part p = getPart();
		logInventoryPreviewTrace( "ITEM-REFRESH-ENTER", p );
		if( p )
		{
			// Use virtual dispatch instead of instanceof: the SLRR script runtime
			// reports these loaded glass parts as neither Window nor Windshield here.
			p.refreshGlassOpacityPreview();
			logInventoryPreviewTrace( "PREVIEW-HOOK-RETURN", p );
		}
	}

	public void hide( InventoryPanel ip )
	{
		ip.cleanup();
	}
}
