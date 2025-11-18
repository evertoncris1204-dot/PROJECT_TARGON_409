package net.sf.l2j.gameserver.handler.skin;

import net.sf.l2j.gameserver.model.actor.Player;

public interface ICustomByPassHandler
{
	public String[] getByPassCommands();
	
	public void handleCommand(String command, Player player, String parameters);
}

