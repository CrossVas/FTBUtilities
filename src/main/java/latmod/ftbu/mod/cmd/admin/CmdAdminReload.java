package latmod.ftbu.mod.cmd.admin;

import cpw.mods.fml.relauncher.Side;
import latmod.ftbu.core.*;
import latmod.ftbu.core.api.EventFTBUReload;
import latmod.ftbu.core.cmd.*;
import latmod.ftbu.core.net.*;
import latmod.ftbu.mod.FTBUTickHandler;
import latmod.ftbu.mod.config.FTBUConfig;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.IChatComponent;

public class CmdAdminReload extends CommandLM
{
	public CmdAdminReload(String s)
	{ super(s, CommandLevel.OP); }
	
	public IChatComponent onCommand(ICommandSender ics, String[] args)
	{
		float prevRRTimer = FTBUConfig.general.restartTimer.floatValue();
		
		FTBUConfig.instance.load();
		for(EntityPlayerMP ep : LatCoreMC.getAllOnlinePlayers(null))
			IServerConfig.Registry.updateConfig(ep, null);
		
		if(FTBUConfig.general.isDedi())
		{
			if(prevRRTimer != FTBUConfig.general.restartTimer.floatValue())
				FTBUTickHandler.serverStarted();
		}
		
		new EventFTBUReload(Side.SERVER, ics).post();
		LMNetHelper.sendTo(null, new MessageReload());
		LatCoreMC.printChat(BroadcastSender.inst, "FTBU reloaded (Server)");
		return null;
	}
}