package net.sf.l2j.gameserver.handler.voicedcommandhandlers;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.handler.IVoicedCommandHandler;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.entity.events.ctf.CTFEvent;

public class VoicedCTF implements IVoicedCommandHandler
{
	private static final String[] _voicedCommands =
	{
		"ctfjoin",
		"ctfleave",
		"ctfinfo"
	};
	
	@Override
	public boolean useVoicedCommand(String command, Player player, String target)
	{
		if (!Config.CTF_EVENT_ENABLED)
		{
			player.sendMessage("CTF Event is disabled.");
			return false;
		}
		
		if (command.equals("ctfjoin"))
		{
			if (!CTFEvent.isParticipating())
			{
				player.sendMessage("CTF Event: Registration is not open.");
				return false;
			}
			
			if (CTFEvent.isPlayerParticipant(player))
			{
				player.sendMessage("CTF Event: You are already registered.");
				return false;
			}
			
			if (player.getStatus().getLevel() < Config.CTF_EVENT_MIN_PLAYER_LEVEL || player.getStatus().getLevel() > Config.CTF_EVENT_MAX_PLAYER_LEVEL)
			{
				player.sendMessage("CTF Event: Your level is not suitable for this event.");
				return false;
			}
			
			CTFEvent.onBypass("ctfjoin", player);
			return true;
		}
		else if (command.equals("ctfleave"))
		{
			if (!CTFEvent.isParticipating())
			{
				player.sendMessage("CTF Event: Registration is not open.");
				return false;
			}
			
			if (!CTFEvent.isPlayerParticipant(player))
			{
				player.sendMessage("CTF Event: You are not registered.");
				return false;
			}
			
			CTFEvent.onBypass("ctfleave", player);
			return true;
		}
		else if (command.equals("ctfinfo"))
		{
			CTFEvent.onBypass("ctfinfo", player);
			return true;
		}
		
		return false;
	}
	
	@Override
	public String[] getVoicedCommandList()
	{
		return _voicedCommands;
	}
}

