package net.sf.l2j.gameserver.model.actor.instance;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.data.cache.HtmCache;
import net.sf.l2j.gameserver.enums.EventState;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;

import dev.tvtEvent.TvTEvent;

/**
 * TvT Event NPC
 */
public class TvTEventNpc extends Folk
{
	public TvTEventNpc(int objectId, NpcTemplate template)
	{
		super(objectId, template);
	}
	
	@Override
	public void onBypassFeedback(Player player, String command)
	{
		if (command.startsWith("tvt_register"))
		{
			if (TvTEvent.getInstance().getState() == EventState.PARTICIPATING)
			{
				if (TvTEvent.getInstance().isRegistered(player))
				{
					player.sendMessage("You are already registered!");
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
			else
			{
				player.sendMessage("Registration is not open at this time.");
			}
			showChatWindow(player, 0);
		}
		else if (command.startsWith("tvt_unregister"))
		{
			if (TvTEvent.getInstance().getState() == EventState.PARTICIPATING)
			{
				if (TvTEvent.getInstance().removePlayer(player))
				{
					player.sendMessage("You have successfully unregistered from the TvT Event!");
				}
				else
				{
					player.sendMessage("You are not registered for the TvT Event!");
				}
			}
			else
			{
				player.sendMessage("You cannot unregister at this time.");
			}
			showChatWindow(player, 0);
		}
		else
		{
			super.onBypassFeedback(player, command);
		}
	}
	
	@Override
	public void showChatWindow(Player player, int val)
	{
		String html = HtmCache.getInstance().getHtm("data/html/mods/TvTEvent.htm");
		if (html == null)
		{
			html = "<html><body>TvT Event Manager</body></html>";
		}
		
		// Replace placeholders
		EventState state = TvTEvent.getInstance().getState();
		String stateText = state.toString();
		int registeredCount = TvTEvent.getInstance().eventPlayers.size();
		
		html = html.replace("%state%", stateText);
		html = html.replace("%registered%", String.valueOf(registeredCount));
		html = html.replace("%minplayers%", String.valueOf(Config.TVT_EVENT_MIN_PLAYERS));
		
		// Add register/unregister button
		if (state == EventState.PARTICIPATING)
		{
			if (TvTEvent.getInstance().isRegistered(player))
			{
				html = html.replace("%button%", "<button value=\"Unregister\" action=\"bypass -h npc_" + getObjectId() + "_tvt_unregister\" width=100 height=25 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_CT1.Button_DF\">");
			}
			else
			{
				html = html.replace("%button%", "<button value=\"Register\" action=\"bypass -h npc_" + getObjectId() + "_tvt_register\" width=100 height=25 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_CT1.Button_DF\">");
			}
		}
		else
		{
			html = html.replace("%button%", "<font color=\"LEVEL\">Registration is closed</font>");
		}
		
		NpcHtmlMessage htmlMsg = new NpcHtmlMessage(getObjectId());
		htmlMsg.setHtml(html);
		player.sendPacket(htmlMsg);
	}
}

