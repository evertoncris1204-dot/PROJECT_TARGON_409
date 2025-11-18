package net.sf.l2j.gameserver.handler.admincommandhandlers;

import dev.bossInstancedEvent.BossEvent;
import net.sf.l2j.gameserver.enums.EventState;
import net.sf.l2j.gameserver.handler.IAdminCommandHandler;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;

public class AdminBossEvent implements IAdminCommandHandler
{
	private static final String[] ADMIN_COMMANDS =
	{
		"admin_bossevent",
		"admin_bossevent_start",
		"admin_bossevent_stop",
		"admin_bossevent_status",
		"bossevent",
		"bossevent_start",
		"bossevent_stop",
		"bossevent_status"
	};
	
	@Override
	public void useAdminCommand(String command, Player player)
	{
		if (command.equals("admin_bossevent") || command.equals("bossevent"))
		{
			showBossEventMenu(player);
		}
		else if (command.equals("admin_bossevent_start") || command.equals("bossevent_start"))
		{
			EventState state = BossEvent.getInstance().getState();
			if (state == EventState.INACTIVE)
			{
				BossEvent.getInstance().startRegistration();
				player.sendMessage("Boss Event: Registration started.");
			}
			else
			{
				player.sendMessage("Boss Event: Event is already active. Current state: " + state);
			}
		}
		else if (command.equals("admin_bossevent_stop") || command.equals("bossevent_stop"))
		{
			EventState state = BossEvent.getInstance().getState();
			if (state != EventState.INACTIVE)
			{
				BossEvent.getInstance().finishEvent();
				BossEvent.getInstance().setState(EventState.INACTIVE);
				BossEvent.getInstance().eventPlayers.clear();
				player.sendMessage("Boss Event: Event stopped.");
			}
			else
			{
				player.sendMessage("Boss Event: Event is not active.");
			}
		}
		else if (command.equals("admin_bossevent_status") || command.equals("bossevent_status"))
		{
			showBossEventStatus(player);
		}
	}
	
	private void showBossEventMenu(Player player)
	{
		String html = "<html><body>";
		html += "<center><font color=\"LEVEL\">Boss Event Admin Panel</font></center><br>";
		html += "<table width=300>";
		html += "<tr><td>State:</td><td>" + BossEvent.getInstance().getState() + "</td></tr>";
		
		if (BossEvent.getInstance().getState() != EventState.INACTIVE)
		{
			html += "<tr><td>Registered Players:</td><td>" + BossEvent.getInstance().eventPlayers.size() + "</td></tr>";
			if (BossEvent.getInstance().bossSpawn != null && BossEvent.getInstance().bossSpawn.getNpc() != null)
			{
				html += "<tr><td>Boss:</td><td>" + BossEvent.getInstance().bossSpawn.getNpc().getName() + "</td></tr>";
				html += "<tr><td>Boss HP:</td><td>" + (int)BossEvent.getInstance().bossSpawn.getNpc().getStatus().getHp() + " / " + (int)BossEvent.getInstance().bossSpawn.getNpc().getStatus().getMaxHp() + "</td></tr>";
			}
		}
		
		html += "</table><br>";
		html += "<center>";
		
		if (BossEvent.getInstance().getState() == EventState.INACTIVE)
		{
			html += "<button value=\"Start Event\" action=\"bypass -h admin_bossevent_start\" width=120 height=25 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_CT1.Button_DF\">";
		}
		else
		{
			html += "<button value=\"Stop Event\" action=\"bypass -h admin_bossevent_stop\" width=120 height=25 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_CT1.Button_DF\">";
		}
		
		html += "<button value=\"Status\" action=\"bypass -h admin_bossevent_status\" width=120 height=25 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_CT1.Button_DF\">";
		html += "</center>";
		html += "</body></html>";
		
		NpcHtmlMessage htmlMsg = new NpcHtmlMessage(0);
		htmlMsg.setHtml(html);
		player.sendPacket(htmlMsg);
	}
	
	private void showBossEventStatus(Player player)
	{
		String status = "Boss Event Status:\n";
		status += "State: " + BossEvent.getInstance().getState() + "\n";
		status += "Registered Players: " + BossEvent.getInstance().eventPlayers.size() + "\n";
		
		if (BossEvent.getInstance().bossSpawn != null && BossEvent.getInstance().bossSpawn.getNpc() != null)
		{
			status += "Boss: " + BossEvent.getInstance().bossSpawn.getNpc().getName() + "\n";
			status += "Boss HP: " + (int)BossEvent.getInstance().bossSpawn.getNpc().getStatus().getHp() + " / " + (int)BossEvent.getInstance().bossSpawn.getNpc().getStatus().getMaxHp() + "\n";
		}
		
		if (BossEvent.getInstance().getLastAttacker() != null)
		{
			status += "Last Attacker: " + BossEvent.getInstance().getLastAttacker().getName() + "\n";
		}
		
		player.sendMessage(status);
	}
	
	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
}

