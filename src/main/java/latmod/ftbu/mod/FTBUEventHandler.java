package latmod.ftbu.mod;
import java.io.File;
import java.util.*;

import cpw.mods.fml.common.eventhandler.*;
import latmod.ftbu.core.*;
import latmod.ftbu.core.api.LMPlayerServerEvent;
import latmod.ftbu.core.inv.LMInvUtils;
import latmod.ftbu.core.item.ICreativeSafeItem;
import latmod.ftbu.core.net.*;
import latmod.ftbu.core.tile.ISecureTile;
import latmod.ftbu.core.util.*;
import latmod.ftbu.core.world.*;
import latmod.ftbu.mod.backups.Backups;
import latmod.ftbu.mod.cmd.CmdMotd;
import latmod.ftbu.mod.config.FTBUConfig;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityChicken;
import net.minecraft.entity.player.*;
import net.minecraft.event.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.world.*;
import net.minecraftforge.common.util.FakePlayer;

public class FTBUEventHandler // FTBUTickHandler
{
	@SubscribeEvent
	public void playerLoggedIn(cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent e)
	{
		if(!(e.player instanceof EntityPlayerMP)) return;
		EntityPlayerMP ep = (EntityPlayerMP)e.player;
		
		LMPlayerServer p = LMWorldServer.inst.getPlayer(ep);
		
		boolean first = (p == null);
		boolean sendAll = false;
		
		if(first)
		{
			p = new LMPlayerServer(LMWorldServer.inst, LMPlayerServer.nextPlayerID(), ep.getGameProfile());
			LMWorldServer.inst.players.add(p);
			sendAll = true;
		}
		else if(!p.getName().equals(p.gameProfile.getName()))
		{
			p.setName(p.gameProfile.getName());
			sendAll = true;
		}
		
		p.setPlayer(ep);
		p.updateLastSeen();
		
		new LMPlayerServerEvent.LoggedIn(p, ep, first).post();
		LMNetHelper.sendTo(sendAll ? null : ep, new MessageLMWorldUpdate(LMWorldServer.inst.worldID, p.playerID));
		IServerConfig.Registry.updateConfig(ep, null);
		
		LMNetHelper.sendTo(ep, new MessageLMPlayerLoggedIn(p, first, true));
		for(EntityPlayerMP ep1 : LatCoreMC.getAllOnlinePlayers(ep))
			LMNetHelper.sendTo(ep1, new MessageLMPlayerLoggedIn(p, first, false));
		
		if(first)
		{
			List<ItemStack> items = FTBUConfig.login.getStartingItems(ep.getUniqueID());
			if(items != null && !items.isEmpty()) for(ItemStack is : items)
				LMInvUtils.giveItem(ep, is);
		}
		
		LMNetHelper.sendTo(null, new MessageLMPlayerInfo(p));
		CmdMotd.printMotd(ep);
		Backups.shouldRun = true;
		
		//if(first) teleportToSpawn(ep);
		
		int requests = 0;
		
		for(LMPlayerServer p1 : LMWorldServer.inst.players)
		{
			if(p1.isFriendRaw(p) && !p.isFriendRaw(p1))
				requests++;
		}
		
		if(requests > 0)
		{
			IChatComponent cc = new ChatComponentText("You got " + requests + " new friend requests!"); //LANG
			cc.getChatStyle().setColor(EnumChatFormatting.GREEN);
			Notification n = new Notification("new_friend_requests", cc, 2000);
			n.setDesc(new ChatComponentText("Click to add all as friends"));
			n.setClickEvent(new NotificationClick(NotificationClick.CMD, "/ftbu friends addall"));
			LatCoreMC.notifyPlayer(ep, n);
		}
	}
	
	@SubscribeEvent
	public void playerLoggedOut(cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent e)
	{ if(e.player instanceof EntityPlayerMP) playerLoggedOut((EntityPlayerMP)e.player); }
	
	public static void playerLoggedOut(EntityPlayerMP ep)
	{
		LMPlayerServer p = LMWorldServer.inst.getPlayer(ep);
		if(p == null) return;
		p.updateLastSeen();
		
		for(int i = 0; i < 4; i++)
			p.lastArmor[i] = ep.inventory.armorInventory[i];
		p.lastArmor[4] = ep.inventory.getCurrentItem();
		
		new LMPlayerServerEvent.LoggedOut(p, ep).post();
		LMNetHelper.sendTo(null, new MessageLMPlayerLoggedOut(p));
		LMNetHelper.sendTo(null, new MessageLMPlayerInfo(p));
		
		LatCoreMC.runCommand(LatCoreMC.getServer(), "admin player saveinv " + p.getName());
		
		p.setPlayer(null);
		//Backups.shouldRun = true;
	}
	
	@SubscribeEvent
	public void worldLoaded(net.minecraftforge.event.world.WorldEvent.Load e)
	{
		if(LatCoreMC.isServer() && e.world.provider.dimensionId == 0 && e.world instanceof WorldServer)
		{
			IServerConfig.Registry.load();
			
			File latmodFolder = new File(e.world.getSaveHandler().getWorldDirectory(), "latmod/");
			NBTTagCompound tagWorldData = LMNBTUtils.readMap(new File(latmodFolder, "LMWorld.dat"));
			if(tagWorldData == null) tagWorldData = new NBTTagCompound();
			LMWorldServer.inst = new LMWorldServer(tagWorldData.hasKey("UUID") ? LatCoreMC.getUUIDFromString(tagWorldData.getString("UUID")) : UUID.randomUUID(), (WorldServer)e.world, latmodFolder);
			LMWorldServer.inst.load(tagWorldData);
			
			ILMWorldData.Registry.load(Phase.PRE);
			
			NBTTagCompound tagPlayers = LMNBTUtils.readMap(new File(latmodFolder, "LMPlayers.dat"));
			if(tagPlayers != null && tagPlayers.hasKey("Players"))
			{
				LMPlayerServer.lastPlayerID = tagPlayers.getInteger("LastID");
				LMWorldServer.inst.readPlayersFromServer(tagPlayers.getCompoundTag("Players"));
			}
			
			for(int i = 0; i < LMWorldServer.inst.players.size(); i++)
				LMWorldServer.inst.players.get(i).setPlayer(null);
			
			ILMWorldData.Registry.load(Phase.POST);
			
			LatCoreMC.logger.info("LatCoreMC data loaded");
		}
	}
	
	@SubscribeEvent
	public void worldSaved(net.minecraftforge.event.world.WorldEvent.Save e)
	{
		if(LatCoreMC.isServer() && e.world.provider.dimensionId == 0 && e.world instanceof WorldServer)
		{
			ILMWorldData.Registry.save();
			
			{
				NBTTagCompound tag = new NBTTagCompound();
				LMWorldServer.inst.save(tag);
				tag.setString("UUID", LMWorldServer.inst.worldIDS);
				LMNBTUtils.writeMap(new File(LMWorldServer.inst.latmodFolder, "LMWorld.dat"), tag);
			}
			
			{
				NBTTagCompound tag = new NBTTagCompound();
				NBTTagCompound players = new NBTTagCompound();
				LMWorldServer.inst.writePlayersToServer(players);
				tag.setTag("Players", players);
				tag.setInteger("LastID", LMPlayerServer.lastPlayerID);
				LMNBTUtils.writeMap(new File(LMWorldServer.inst.latmodFolder, "LMPlayers.dat"), tag);
			}
			
			// Export player list //
			
			try
			{
				FastList<String> l = new FastList<String>();
				int[] list = LMWorldServer.inst.getAllPlayerIDs();
				Arrays.sort(list);
				
				for(int i = 0; i < list.length; i++)
				{
					LMPlayer p = LMWorldServer.inst.getPlayer(list[i]);
					
					StringBuilder sb = new StringBuilder();
					sb.append(LMStringUtils.fillString("" + p.playerID, ' ', 6));
					sb.append(LMStringUtils.fillString(p.getName(), ' ', 21));
					sb.append(p.uuidString);
					l.add(sb.toString());
				}
				
				LMFileUtils.save(new File(e.world.getSaveHandler().getWorldDirectory(), "latmod/LMPlayers.txt"), l);
			}
			catch(Exception ex)
			{ ex.printStackTrace(); }
		}
	}
	
	@SubscribeEvent
	public void onBlockClick(net.minecraftforge.event.entity.player.PlayerInteractEvent e)
	{
		if(e.action == net.minecraftforge.event.entity.player.PlayerInteractEvent.Action.RIGHT_CLICK_AIR) return;
		else if(!canInteract(e.entityPlayer, e.x, e.y, e.z, e.action == net.minecraftforge.event.entity.player.PlayerInteractEvent.Action.LEFT_CLICK_BLOCK))
			e.setCanceled(true);
	}
	
	public static boolean canInteract(EntityPlayer ep, int x, int y, int z, boolean leftClick)
	{
		World w = ep.worldObj;
		boolean server = !w.isRemote;
		if(server && Claims.isOutsideWorldBorderD(w.provider.dimensionId, x, z)) return false;
		
		if(ep.capabilities.isCreativeMode && leftClick && ep.getHeldItem() != null && ep.getHeldItem().getItem() instanceof ICreativeSafeItem)
		{
			if(server) w.markBlockRangeForRenderUpdate(x, y, z, x, y, z);
			else w.markBlockForUpdate(x, y, z);
			return true;
		}
		
		if(!server || FTBUConfig.general.allowInteractSecure(ep)) return true;
		
		Block block = w.getBlock(x, y, z);
		
		if(block.hasTileEntity(w.getBlockMetadata(x, y, z)))
		{
			TileEntity te = w.getTileEntity(x, y, z);
			if(te instanceof ISecureTile && !te.isInvalid() && !((ISecureTile)te).canPlayerInteract(ep, leftClick))
			{ ((ISecureTile)te).onPlayerNotOwner(ep, leftClick); return false; }
		}
		
		LMPlayerServer p = LMWorldServer.inst.getPlayer(ep);
		if(!FTBUConfig.general.isDedi() || p.isOP()) return true;
		return ChunkType.getD(w.provider.dimensionId, x, z, p).isFriendly();
	}
	
	@SubscribeEvent
	public void onPlayerDeath(net.minecraftforge.event.entity.living.LivingDeathEvent e)
	{
		if(e.entity instanceof EntityPlayerMP)
		{
			LMPlayerServer p = LMWorldServer.inst.getPlayer(e.entity);
			p.deaths++;
			
			if(p.lastDeath == null) p.lastDeath = new EntityPos(e.entity);
			else p.lastDeath.set(e.entity);
			
			LMNetHelper.sendTo(null, new MessageLMPlayerDied(p));
		}
	}
	
	@SubscribeEvent
	public void onMobSpawned(net.minecraftforge.event.entity.EntityJoinWorldEvent e)
	{
		if(!FTBUConfig.general.safeSpawn || !FTBUConfig.general.isDedi()) return;
		
		if((e.entity instanceof IMob || (e.entity instanceof EntityChicken && e.entity.riddenByEntity != null)) && Claims.isInSpawnD(e.world.provider.dimensionId, e.entity.posX, e.entity.posZ))
			e.setCanceled(true);
	}
	
	@SubscribeEvent
	public void onPlayerAttacked(net.minecraftforge.event.entity.living.LivingAttackEvent e)
	{
		if(!FTBUConfig.general.isDedi()) return;
		
		int dim = e.entity.dimension;
		if(dim != 0 || !(e.entity instanceof EntityPlayerMP) || e.entity instanceof FakePlayer) return;
		
		Entity entity = e.source.getSourceOfDamage();
		
		if(entity != null && entity instanceof EntityPlayerMP && !(entity instanceof FakePlayer))
		{
			if(FTBUConfig.general.allowInteractSecure((EntityPlayerMP)entity)) return;
			
			int cx = MathHelperLM.chunk(e.entity.posX);
			int cz = MathHelperLM.chunk(e.entity.posZ);
			
			if(Claims.isOutsideWorldBorder(dim, cx, cz) || (FTBUConfig.general.safeSpawn && Claims.isInSpawn(dim, cx, cz))) e.setCanceled(true);
			/*else
			{
				ClaimedChunk c = Claims.get(dim, cx, cz);
				if(c != null && c.claims.settings.isSafe()) e.setCanceled(true);
			}*/
		}
	}
	
	@SubscribeEvent
	public void onExplosionStart(net.minecraftforge.event.world.ExplosionEvent.Start e)
	{
		if(e.world.isRemote || !FTBUConfig.general.isDedi()) return;
		
		int dim = e.world.provider.dimensionId;
		if(dim != 0) return;
		int cx = MathHelperLM.chunk(e.explosion.explosionX);
		int cz = MathHelperLM.chunk(e.explosion.explosionZ);
		
		if(Claims.isOutsideWorldBorder(dim, cx, cz) || (FTBUConfig.general.safeSpawn && Claims.isInSpawn(dim, cx, cz))) e.setCanceled(true);
		/*else
		{
			ClaimedChunk c = Claims.get(dim, cx, cz);
			if(c != null && c.claims.isSafe()) e.setCanceled(true);
		}*/
	}
	
	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public void onChatEvent(net.minecraftforge.event.ServerChatEvent e)
	{
		String[] msg = e.message.split(" ");
		
		LatCoreMC.logger.info(LMStringUtils.strip(msg));
		
		FastList<String> links = new FastList<String>();
		
		for(String s : msg)
		{
			int index = s.indexOf("http://");
			if(index == -1) index = s.indexOf("https://");
			if(index != -1) links.add(s.substring(index).trim());
		}
		
		if(!links.isEmpty())
		{
			final IChatComponent line = new ChatComponentText("");
			boolean oneLink = links.size() == 1;
			
			for(int i = 0; i < links.size(); i++)
			{
				String link = links.get(i);
				IChatComponent c = new ChatComponentText(oneLink ? "[ Link ]" : ("[ Link #" + (i + 1) + " ]"));
				c.getChatStyle().setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ChatComponentText(link)));
				c.getChatStyle().setChatClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, link));
				line.appendSibling(c);
				if(!oneLink) line.appendSibling(new ChatComponentText(" "));
			}
			
			line.getChatStyle().setColor(EnumChatFormatting.GOLD);
			
			Thread t = new Thread("LM_PrintLinks")
			{
				public void run()
				{
					try { Thread.sleep(25L); }
					catch(Exception e) { e.printStackTrace(); }
					
					for(LMPlayerServer p : LMWorldServer.inst.getAllOnlinePlayers())
					{ LatCoreMC.printChat(p.getPlayer(), p.chatLinks); if(p.chatLinks) LatCoreMC.printChat(p.getPlayer(), line); }
				}
			};
			
			t.setDaemon(true);
			t.start();
		}
	}
}