package latmod.core.mod;
import java.util.*;
import latmod.core.LatCore;
import latmod.core.OreHelper;
import latmod.core.OreHelper.StackEntry;
import net.minecraft.item.*;
import net.minecraftforge.common.*;
import net.minecraftforge.event.*;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.oredict.*;

public class LC_TooltipHandler
{
	public LC_TooltipHandler()
	{
		MinecraftForge.EVENT_BUS.register(this);
	}
	
	@ForgeSubscribe
	public void onTooltip(ItemTooltipEvent e)
	{
		if(e.showAdvancedItemTooltips && e.itemStack != null)
		{
			Item i = e.itemStack.getItem();
			
			e.toolTip.add("Unlocalized name:");
			e.itemStack.getUnlocalizedName();
			
			ArrayList<String> ores = OreHelper.getOreNames(e.itemStack);
			
			if(ores != null)
			{
				e.toolTip.add("Ore Dictionary names:");
				for(String or : ores)
				e.toolTip.add("> " + or);
			}
		}
	}
}