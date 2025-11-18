package dev.bossInstancedEvent;

import net.sf.l2j.gameserver.handler.IVoicedCommandHandler;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.enums.EventState;

/**
 * @author Zaun
 */
public class BossEventCMD implements IVoicedCommandHandler
{
	@Override
	public boolean useVoicedCommand(String command, Player activeChar, String target)
	{
		if (command.startsWith("bossevent"))
		{
			if (BossEvent.getInstance().getState() != EventState.PARTICIPATING)
			{
				activeChar.sendMessage("Boss Event is not running!");
				return false;
			}
			if (!BossEvent.getInstance().isRegistered(activeChar))
			{
				if (BossEvent.getInstance().addPlayer(activeChar))
				{
					activeChar.sendMessage("You have been successfully registered in Boss Event!");
				}
			}
			else
			{
				if (BossEvent.getInstance().removePlayer(activeChar))
				{
					activeChar.sendMessage("You have been successfully removed of Boss Event!");
				}
			}
		}
		return false;
	}
	
	@Override
	public String[] getVoicedCommandList()
	{
		return new String[]
		{
			"bossevent"
		};
	}
}

