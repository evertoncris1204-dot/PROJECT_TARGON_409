package net.sf.l2j.gameserver.handler.admincommandhandlers;

import net.sf.l2j.gameserver.enums.EventState;
import net.sf.l2j.gameserver.handler.IAdminCommandHandler;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;

import dev.tvtEvent.TvTEvent;

/**
 * Admin commands for TvT Event
 */
public class AdminTvTEvent implements IAdminCommandHandler
{
	private static final String[] ADMIN_COMMANDS =
	{
		"admin_tvt",
		"admin_tvt_start",
		"admin_tvt_stop",
		"admin_tvt_status",
		"tvt",
		"tvt_start",
		"tvt_stop",
		"tvt_status"
	};
	
	@Override
	public void useAdminCommand(String command, Player player)
	{
		if (command.startsWith("admin_tvt") || command.startsWith("tvt"))
		{
			if (command.equals("admin_tvt") || command.equals("tvt"))
			{
				showTvTMenu(player);
			}
			else if (command.equals("admin_tvt_start") || command.equals("tvt_start"))
			{
				if (TvTEvent.getInstance().getState() == EventState.INACTIVE)
				{
					TvTEvent.getInstance().startRegistration();
					player.sendMessage("TvT Event: Registration started.");
				}
				else
				{
					player.sendMessage("TvT Event: Event is already active.");
				}
			}
			else if (command.equals("admin_tvt_stop") || command.equals("tvt_stop"))
			{
				if (TvTEvent.getInstance().getState() != EventState.INACTIVE)
				{
					TvTEvent.getInstance().finishEvent();
					player.sendMessage("TvT Event: Event stopped.");
				}
				else
				{
					player.sendMessage("TvT Event: Event is not active.");
				}
			}
			else if (command.equals("admin_tvt_status") || command.equals("tvt_status"))
			{
				String status = "TvT Event Status:\n";
				status += "State: " + TvTEvent.getInstance().getState() + "\n";
				if (TvTEvent.getInstance().getState() != EventState.INACTIVE)
				{
					status += "Registered Players: " + TvTEvent.getInstance().eventPlayers.size() + "\n";
					status += "Blue Team: " + TvTEvent.getInstance().teamBlue.size() + " players\n";
					status += "Red Team: " + TvTEvent.getInstance().teamRed.size() + " players\n";
					if (TvTEvent.getInstance().getState() == EventState.STARTED)
					{
						status += "Blue Score: " + TvTEvent.getInstance().blueScore + "\n";
						status += "Red Score: " + TvTEvent.getInstance().redScore + "\n";
					}
				}
				player.sendMessage(status);
			}
		}
	}
	
	private void showTvTMenu(Player player)
	{
		String html = "<html><body>";
		html += "<center><font color=\"LEVEL\">TvT Event Admin Panel</font></center><br>";
		html += "<table width=300>";
		html += "<tr><td>State:</td><td>" + TvTEvent.getInstance().getState() + "</td></tr>";
		
		if (TvTEvent.getInstance().getState() != EventState.INACTIVE)
		{
			html += "<tr><td>Registered Players:</td><td>" + TvTEvent.getInstance().eventPlayers.size() + "</td></tr>";
			html += "<tr><td>Blue Team:</td><td>" + TvTEvent.getInstance().teamBlue.size() + " players</td></tr>";
			html += "<tr><td>Red Team:</td><td>" + TvTEvent.getInstance().teamRed.size() + " players</td></tr>";
			if (TvTEvent.getInstance().getState() == EventState.STARTED)
			{
				html += "<tr><td>Blue Score:</td><td>" + TvTEvent.getInstance().blueScore + "</td></tr>";
				html += "<tr><td>Red Score:</td><td>" + TvTEvent.getInstance().redScore + "</td></tr>";
			}
		}
		
		html += "</table><br>";
		html += "<center>";
		
		if (TvTEvent.getInstance().getState() == EventState.INACTIVE)
		{
			html += "<button value=\"Start Event\" action=\"bypass -h admin_tvt_start\" width=100 height=25 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_CT1.Button_DF\">";
		}
		else
		{
			html += "<button value=\"Stop Event\" action=\"bypass -h admin_tvt_stop\" width=100 height=25 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_CT1.Button_DF\">";
		}
		
		html += "<button value=\"Status\" action=\"bypass -h admin_tvt_status\" width=100 height=25 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_CT1.Button_DF\">";
		html += "</center>";
		html += "</body></html>";
		
		NpcHtmlMessage htmlMsg = new NpcHtmlMessage(0);
		htmlMsg.setHtml(html);
		player.sendPacket(htmlMsg);
	}
	
	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
}

