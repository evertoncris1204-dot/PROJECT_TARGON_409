package net.sf.l2j.gameserver.handler.voicedcommandhandlers;

import net.sf.l2j.gameserver.handler.IVoicedCommandHandler;
import net.sf.l2j.gameserver.model.actor.Player;

public class BuffManagerVCmd implements IVoicedCommandHandler
{
	// Define os comandos que esta ".rembuff", por exemplo pode ser ".dispell".
	private static final String[] VOICED_COMMANDS =
	{
		"rembuff"
	};
	
	@Override
	public boolean useVoicedCommand(String command, Player player, String target)
	{
		if (command.startsWith("rembuff"))
		{
			player.sendMessage("comando rembuff actived");
		}
		return true;
	}
	
	@Override
	public String[] getVoicedCommandList()
	{
		return VOICED_COMMANDS;
		
	}
	
}