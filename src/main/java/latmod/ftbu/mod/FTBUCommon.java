package latmod.ftbu.mod;

import java.util.UUID;

import com.google.gson.GsonBuilder;

import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import latmod.ftbu.core.api.readme.ReadmeFile;
import latmod.ftbu.core.net.*;
import latmod.ftbu.core.tile.TileLM;
import latmod.ftbu.core.world.LMWorld;
import net.minecraft.entity.player.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityEvent;

public class FTBUCommon // FTBUClient
{
	public void preInit()
	{
	}
	
	public void postInit()
	{
	}
	
	public void onReadmeEvent(ReadmeFile file)
	{
	}
	
	public void onGsonEvent(GsonBuilder gb)
	{
	}
	
	public boolean isShiftDown() { return false; }
	public boolean isCtrlDown() { return false; }
	public boolean isTabDown() { return false; }
	public boolean inGameHasFocus() { return true; }
	
	public EntityPlayer getClientPlayer()
	{ return null; }
	
	public EntityPlayer getClientPlayer(UUID id)
	{ return null; }
	
	public World getClientWorld()
	{ return null; }
	
	public LMWorld<?> getClientWorldLM()
	{ return null; }
	
	public double getReachDist(EntityPlayer ep)
	{
		if(ep != null && ep instanceof EntityPlayerMP)
			return ((EntityPlayerMP)ep).theItemInWorldManager.getBlockReachDistance();
		return 0D;
	}
	
	public void spawnDust(World w, double x, double y, double z, int col) { }
	public boolean openClientGui(EntityPlayer ep, String mod, int id, NBTTagCompound data) { return false; }
	public <M extends MessageLM<?>> void handleClientMessage(IClientMessageLM<M> m, MessageContext ctx) { }
	public void readTileData(TileLM t, S35PacketUpdateTileEntity p) { }
	public void clientChunkChanged(EntityEvent.EnteringChunk e) { }
}