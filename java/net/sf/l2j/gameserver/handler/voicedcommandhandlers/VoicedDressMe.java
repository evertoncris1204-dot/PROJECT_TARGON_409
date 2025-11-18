package net.sf.l2j.gameserver.handler.voicedcommandhandlers;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.handler.IVoicedCommandHandler;
import net.sf.l2j.gameserver.handler.skin.ICustomByPassHandler;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.network.clientpackets.RequestBypassToServer;

public class VoicedDressMe implements IVoicedCommandHandler, ICustomByPassHandler
{
	@Override
	public String[] getVoicedCommandList()
	{
		String command = Config.DRESS_ME_COMMAND;
		if (command == null || command.isEmpty())
			command = "dressme";
		
		return new String[]
		{
			command
		};
	}
	
	@Override
	public boolean useVoicedCommand(String command, Player activeChar, String target)
	{
		if (!Config.ALLOW_DRESS_ME_SYSTEM)
		{
			activeChar.sendMessage("DressMe system is disabled.");
			return false;
		}
		
		if (!Config.ALLOW_DRESS_ME_IN_OLY && activeChar.isInOlympiadMode())
		{
			activeChar.sendMessage("DressMe can't be used in Olympiad.");
			return false;
		}
		
		if (Config.ALLOW_DRESS_ME_FOR_PREMIUM && activeChar.getMemos().getLong("PremiumTime", 0) <= System.currentTimeMillis())
		{
			activeChar.sendMessage("DressMe is only available for Premium players.");
			return false;
		}
		
		RequestBypassToServer.showDressMeMainPage(activeChar);
		return true;
	}
	
	@Override
	public String[] getByPassCommands()
	{
		return new String[]
		{
			"custom_dressme_back"
		};
	}
	
	@Override
	public void handleCommand(String command, Player player, String parameters)
	{
		if (command.equals("custom_dressme_back"))
		{
			RequestBypassToServer.showDressMeMainPage(player);
		}
	}
}
