package net.sf.l2j.gameserver.model.actor.instance;

import net.sf.l2j.commons.random.Rnd;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.enums.EventState;
import net.sf.l2j.gameserver.network.serverpackets.ActionFailed;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;

import dev.bossInstancedEvent.BossEvent;

/**
 * @author Zaun
 */
public class BossEventNpc extends Folk
{
	public BossEventNpc(int objectId, NpcTemplate template)
	{
		super(objectId, template);
	}
	
	@Override
	public void showChatWindow(Player player, int val)
	{
		NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
		html.setFile("data/html/mods/BossEvent.htm");
		html.replace("%objectId%", String.valueOf(getObjectId()));
		html.replace("%npcname%", getName());
		html.replace("%regCount%", String.valueOf(BossEvent.getInstance().eventPlayers.size()));
		player.sendPacket(html);
	}
	
	@Override
	public void onBypassFeedback(Player activeChar, String command)
	{
		super.onBypassFeedback(activeChar, command);
		if (command.startsWith("register"))
		{
			if (BossEvent.getInstance().getState() != EventState.PARTICIPATING)
			{
				activeChar.sendMessage("Boss Event is not running!");
				return;
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
	}
}

