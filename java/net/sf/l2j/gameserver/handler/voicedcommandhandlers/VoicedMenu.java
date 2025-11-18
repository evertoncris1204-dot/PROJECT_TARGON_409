package net.sf.l2j.gameserver.handler.voicedcommandhandlers;

import net.sf.l2j.gameserver.data.cache.HtmCache;
import net.sf.l2j.gameserver.handler.IVoicedCommandHandler;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;

/**
 * @author evert
 */
public class VoicedMenu implements IVoicedCommandHandler
{
	private static final String[] VOICED_COMMANDS =
	{
		"menu",
	};
	
	@Override
	public boolean useVoicedCommand(String command, Player player, String params)
	{
		if (command.equals("menu"))
		{
			showMenu(player);
			return true;
		}
		
		return false;
	}
	
	@Override
	public String[] getVoicedCommandList()
	{
		return VOICED_COMMANDS;
	}
	
	public void showMenu(Player player)
	{
		String html = HtmCache.getInstance().getHtm("data/html/menu.htm");
		if (html == null)
		{
			html = HtmCache.getInstance().getHtm("html/menu.htm");
		}
		
		if (html == null)
		{
			player.sendMessage("Menu HTML file not found.");
			return;
		}
		
		// Replace placeholders with checkbox styles
		html = html.replace("%html_Party%", player.getPartyRefusal() ? "back=L2UI.CheckBox_checked fore=L2UI.CheckBox_checked" : "back=L2UI.CheckBox fore=L2UI.CheckBox");
		html = html.replace("%html_trade%", player.getTradeRefusal() ? "back=L2UI.CheckBox_checked fore=L2UI.CheckBox_checked" : "back=L2UI.CheckBox fore=L2UI.CheckBox");
		html = html.replace("%html_Buffs%", player.getBuffsRefusal() ? "back=L2UI.CheckBox_checked fore=L2UI.CheckBox_checked" : "back=L2UI.CheckBox fore=L2UI.CheckBox");
		html = html.replace("%html_Message%", player.getMessageRefusal() ? "back=L2UI.CheckBox_checked fore=L2UI.CheckBox_checked" : "back=L2UI.CheckBox fore=L2UI.CheckBox");
		
		NpcHtmlMessage msg = new NpcHtmlMessage(0);
		msg.setHtml(html);
		player.sendPacket(msg);
	}
	
	public void setPartyRefuse(Player player)
	{
		player.setPartyRefusal(!player.getPartyRefusal());
		showMenu(player);
	}
	
	public void setTradeRefuse(Player player)
	{
		player.setTradeRefusal(!player.getTradeRefusal());
		showMenu(player);
	}
	
	public void setBuffsRefuse(Player player)
	{
		player.setBuffsRefusal(!player.getBuffsRefusal());
		showMenu(player);
	}
	
	public void setMessageRefuse(Player player)
	{
		player.setMessageRefusal(!player.getMessageRefusal());
		showMenu(player);
	}
	
	public void showRegisteHtml(Player player)
	{
		// TODO: Implement register HTML if needed
		player.sendMessage("Register function not implemented yet.");
	}
	
	public void showInfoHtml(Player player)
	{
		IVoicedCommandHandler handler = net.sf.l2j.gameserver.handler.VoicedCommandHandler.getInstance().getHandler("info");
		if (handler != null)
		{
			handler.useVoicedCommand("info", player, null);
		}
	}
	
	public void showEpic(Player player)
	{
		IVoicedCommandHandler handler = net.sf.l2j.gameserver.handler.VoicedCommandHandler.getInstance().getHandler("raidinfo");
		if (handler != null)
		{
			handler.useVoicedCommand("raidinfo", player, null);
		}
	}
	
	public void showEventTime(Player player)
	{
		net.sf.l2j.gameserver.handler.voicedcommandhandlers.EventTime handler = 
			(net.sf.l2j.gameserver.handler.voicedcommandhandlers.EventTime) net.sf.l2j.gameserver.handler.VoicedCommandHandler.getInstance().getHandler("eventtime");
		if (handler != null)
		{
			handler.showEventTime(player);
		}
		else
		{
			player.sendMessage("Event Time system not available.");
		}
	}
	
	public void showCombine(Player player)
	{
		// TODO: Implement combine items if needed
		player.sendMessage("Combine Items function not implemented yet.");
	}
}
