package net.sf.l2j.gameserver.handler.admincommandhandlers;

import net.sf.l2j.gameserver.handler.IAdminCommandHandler;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.entity.Tournament.TournamentManager;

/**
 * @author Rouxy
 */
public class AdminTournament implements IAdminCommandHandler
{
	
	@Override
	public void useAdminCommand(String command, Player player)
	{
		if (command.startsWith("admin_tour") || command.equals("tour"))
		{
			if (TournamentManager.getInstance().isRunning())
			{
				TournamentManager.getInstance().finishEvent();
				player.sendMessage("Tournament Event: Stopped.");
			}
			else
			{
				TournamentManager.getInstance().startEvent();
				player.sendMessage("Tournament Event: Started.");
			}
		}
		
	}
	
	@Override
	public String[] getAdminCommandList()
	{
		
		return new String[]
		{
			"admin_tour",
			"tour"
		};
	}
	
}

