package dev.tvtEvent;

import net.sf.l2j.gameserver.enums.EventState;
import net.sf.l2j.gameserver.handler.IVoicedCommandHandler;
import net.sf.l2j.gameserver.model.actor.Player;

/**
 * TvT Event Voiced Commands
 */
public class TvTEventCMD implements IVoicedCommandHandler
{
	private static final String[] VOICED_COMMANDS = { "tvt" };
	
	@Override
	public boolean useVoicedCommand(String command, Player player, String target)
	{
		if (command.equals("tvt"))
		{
			EventState state = TvTEvent.getInstance().getState();
			
			if (state == EventState.PARTICIPATING)
			{
				if (TvTEvent.getInstance().isRegistered(player))
				{
					if (TvTEvent.getInstance().removePlayer(player))
					{
						player.sendMessage("You have successfully unregistered from the TvT Event!");
					}
				}
				else
				{
					if (TvTEvent.getInstance().addPlayer(player))
					{
						player.sendMessage("You have successfully registered for the TvT Event!");
					}
					else
					{
						player.sendMessage("Failed to register for the TvT Event!");
					}
				}
			}
			else if (state == EventState.STARTED)
			{
				player.sendMessage("TvT Event Status:");
				player.sendMessage("Blue Team Score: " + TvTEvent.getInstance().blueScore);
				player.sendMessage("Red Team Score: " + TvTEvent.getInstance().redScore);
			}
			else
			{
				player.sendMessage("TvT Event is currently " + state.toString() + ".");
			}
			
			return true;
		}
		
		return false;
	}
	
	@Override
	public String[] getVoicedCommandList()
	{
		return VOICED_COMMANDS;
	}
}

