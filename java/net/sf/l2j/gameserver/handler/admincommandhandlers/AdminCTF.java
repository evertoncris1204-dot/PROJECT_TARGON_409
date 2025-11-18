package net.sf.l2j.gameserver.handler.admincommandhandlers;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.handler.IAdminCommandHandler;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.entity.events.ctf.CTFEvent;
import net.sf.l2j.gameserver.model.entity.events.ctf.CTFManager;

public class AdminCTF implements IAdminCommandHandler
{
	private static final String[] ADMIN_COMMANDS =
	{
		"admin_ctf",
		"admin_ctf_start",
		"admin_ctf_stop",
		"admin_ctf_status",
		"ctf",
		"ctf_start",
		"ctf_stop",
		"ctf_status"
	};
	
	@Override
	public void useAdminCommand(String command, Player player)
	{
		if (!Config.CTF_EVENT_ENABLED)
		{
			player.sendMessage("CTF Event is disabled.");
			return;
		}
		
		if (command.equals("admin_ctf") || command.equals("ctf"))
		{
			showCTFMenu(player);
		}
		else if (command.equals("admin_ctf_start") || command.equals("ctf_start"))
		{
			if (CTFEvent.isInactive())
			{
				CTFManager.getInstance().startReg();
				player.sendMessage("CTF Event: Registration started.");
			}
			else
			{
				player.sendMessage("CTF Event: Event is already active.");
			}
		}
		else if (command.equals("admin_ctf_stop") || command.equals("ctf_stop"))
		{
			if (CTFEvent.isParticipating() || CTFEvent.isStarted())
			{
				CTFEvent.stopFight();
				player.sendMessage("CTF Event: Event stopped.");
			}
			else
			{
				player.sendMessage("CTF Event: Event is not active.");
			}
		}
		else if (command.equals("admin_ctf_status") || command.equals("ctf_status"))
		{
			String status = "CTF Event Status:\n";
			status += "State: " + getEventState() + "\n";
			
			if (CTFEvent.isParticipating() || CTFEvent.isStarted())
			{
				status += "Team 1 (" + Config.CTF_EVENT_TEAM_1_NAME + "): " + CTFEvent.getTeamByIndex(0).getParticipatedPlayerCount() + " players, " + CTFEvent.getTeamByIndex(0).getPoints() + " points\n";
				status += "Team 2 (" + Config.CTF_EVENT_TEAM_2_NAME + "): " + CTFEvent.getTeamByIndex(1).getParticipatedPlayerCount() + " players, " + CTFEvent.getTeamByIndex(1).getPoints() + " points\n";
			}
			
			player.sendMessage(status);
		}
	}
	
	private void showCTFMenu(Player player)
	{
		String html = "<html><body>";
		html += "<center><font color=\"LEVEL\">CTF Event Admin Panel</font></center><br>";
		html += "<table width=300>";
		html += "<tr><td>State:</td><td>" + getEventState() + "</td></tr>";
		
		if (CTFEvent.isParticipating() || CTFEvent.isStarted())
		{
			html += "<tr><td>Team 1 (" + Config.CTF_EVENT_TEAM_1_NAME + "):</td><td>" + CTFEvent.getTeamByIndex(0).getParticipatedPlayerCount() + " players, " + CTFEvent.getTeamByIndex(0).getPoints() + " points</td></tr>";
			html += "<tr><td>Team 2 (" + Config.CTF_EVENT_TEAM_2_NAME + "):</td><td>" + CTFEvent.getTeamByIndex(1).getParticipatedPlayerCount() + " players, " + CTFEvent.getTeamByIndex(1).getPoints() + " points</td></tr>";
		}
		
		html += "</table><br>";
		html += "<center>";
		
		if (CTFEvent.isInactive())
		{
			html += "<button value=\"Start Event\" action=\"bypass -h admin_ctf_start\" width=100 height=25 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_CT1.Button_DF\">";
		}
		else
		{
			html += "<button value=\"Stop Event\" action=\"bypass -h admin_ctf_stop\" width=100 height=25 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_CT1.Button_DF\">";
		}
		
		html += "<button value=\"Status\" action=\"bypass -h admin_ctf_status\" width=100 height=25 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_CT1.Button_DF\">";
		html += "</center>";
		html += "</body></html>";
		
		net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage htmlMsg = new net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage(0);
		htmlMsg.setHtml(html);
		player.sendPacket(htmlMsg);
	}
	
	private String getEventState()
	{
		if (CTFEvent.isInactive())
			return "INACTIVE";
		else if (CTFEvent.isParticipating())
			return "PARTICIPATING";
		else if (CTFEvent.isStarting())
			return "STARTING";
		else if (CTFEvent.isStarted())
			return "STARTED";
		else if (CTFEvent.isRewarding())
			return "REWARDING";
		else if (CTFEvent.isInactivating())
			return "INACTIVATING";
		else
			return "UNKNOWN";
	}
	
	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
}

