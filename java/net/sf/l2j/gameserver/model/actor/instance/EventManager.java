package net.sf.l2j.gameserver.model.actor.instance;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.data.cache.HtmCache;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.model.entity.events.ctf.CTFEvent;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;

public class EventManager extends Folk
{
	private static final String htmlPath = "data/html/mods/events/ctf/";
	
	public EventManager(int objectId, NpcTemplate template)
	{
		super(objectId, template);
	}
	
	@Override
	public void showChatWindow(Player player, int val)
	{
		if (Config.CTF_EVENT_ENABLED && getNpcId() == Config.CTF_EVENT_PARTICIPATION_NPC_ID)
		{
			if (CTFEvent.isParticipating())
			{
				CTFEvent.showParticipationHtml(player);
			}
			else if (CTFEvent.isStarting() || CTFEvent.isStarted())
			{
				String htmContent = HtmCache.getInstance().getHtm(htmlPath + "status.htm");
				if (htmContent == null)
					htmContent = "<html><body>CTF Event is currently running. Use .ctfinfo for more information.</body></html>";
				
				NpcHtmlMessage npcHtmlMessage = new NpcHtmlMessage(getObjectId());
				npcHtmlMessage.setHtml(htmContent);
				player.sendPacket(npcHtmlMessage);
			}
			else
			{
				String htmContent = HtmCache.getInstance().getHtm(htmlPath + "inactive.htm");
				if (htmContent == null)
					htmContent = "<html><body>CTF Event is not currently active.</body></html>";
				
				NpcHtmlMessage npcHtmlMessage = new NpcHtmlMessage(getObjectId());
				npcHtmlMessage.setHtml(htmContent);
				player.sendPacket(npcHtmlMessage);
			}
		}
		else
		{
			super.showChatWindow(player, val);
		}
	}
	
	@Override
	public void onBypassFeedback(Player player, String command)
	{
		if (Config.CTF_EVENT_ENABLED && getNpcId() == Config.CTF_EVENT_PARTICIPATION_NPC_ID)
		{
			CTFEvent.onBypass(command, player);
		}
		else
		{
			super.onBypassFeedback(player, command);
		}
	}
}

