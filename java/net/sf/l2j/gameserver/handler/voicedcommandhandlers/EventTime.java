package net.sf.l2j.gameserver.handler.voicedcommandhandlers;

import java.text.SimpleDateFormat;
import java.util.Calendar;

import net.sf.l2j.gameserver.handler.IVoicedCommandHandler;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.entity.events.CheckNextEvent;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;
import dev.bossInstancedEvent.NextBossEvent;

/**
 * @author evert
 */
public class EventTime implements IVoicedCommandHandler
{
	private static final String[] VOICED_COMMANDS =
	{
		"eventtime",
	};
	
	private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm");
	private static final SimpleDateFormat DATE_TIME_FORMAT = new SimpleDateFormat("dd/MM/yyyy HH:mm");
	
	@Override
	public boolean useVoicedCommand(String command, Player player, String params)
	{
		if (command.equals("eventtime"))
		{
			showEventTime(player);
			return true;
		}
		
		return false;
	}
	
	@Override
	public String[] getVoicedCommandList()
	{
		return VOICED_COMMANDS;
	}
	
	public void showEventTime(Player player)
	{
		StringBuilder html = new StringBuilder();
		html.append("<html>");
		html.append("\t<body>");
		html.append("\t\t<center>");
		html.append("\t\t\t<table width=\"256\">");
		html.append("\t\t\t\t<tr><td width=\"256\" align=\"center\"><font color=\"LEVEL\">Next Event Times</font></td></tr>");
		html.append("\t\t\t</table>");
		html.append("\t\t\t<br>");
		
		CheckNextEvent eventChecker = CheckNextEvent.getInstance();
		NextBossEvent bossEvent = NextBossEvent.getInstance();
		
		// TvT Event
		Calendar nextTvT = eventChecker.getNextTvTEventTime();
		if (nextTvT != null)
		{
			long timeUntil = nextTvT.getTimeInMillis() - System.currentTimeMillis();
			String timeStr = formatTimeUntil(timeUntil);
			html.append("\t\t\t<table width=\"224\" bgcolor=\"000000\">");
			html.append("\t\t\t\t<tr>");
			html.append("\t\t\t\t\t<td width=\"140\"><font color=\"00FF00\">TvT Event</font></td>");
			html.append("\t\t\t\t\t<td width=\"84\" align=\"right\"><font color=\"B09878\">").append(TIME_FORMAT.format(nextTvT.getTime())).append("</font></td>");
			html.append("\t\t\t\t</tr>");
			html.append("\t\t\t\t<tr><td colspan=\"2\"><font color=\"AAAAAA\">In: ").append(timeStr).append("</font></td></tr>");
			html.append("\t\t\t</table>");
			html.append("\t\t\t<br>");
		}
		
		// Party Zone Event
		Calendar nextPartyZone = eventChecker.getNextPartyZoneventTime();
		if (nextPartyZone != null)
		{
			long timeUntil = nextPartyZone.getTimeInMillis() - System.currentTimeMillis();
			String timeStr = formatTimeUntil(timeUntil);
			html.append("\t\t\t<table width=\"224\" bgcolor=\"000000\">");
			html.append("\t\t\t\t<tr>");
			html.append("\t\t\t\t\t<td width=\"140\"><font color=\"00FF00\">Party Zone</font></td>");
			html.append("\t\t\t\t\t<td width=\"84\" align=\"right\"><font color=\"B09878\">").append(TIME_FORMAT.format(nextPartyZone.getTime())).append("</font></td>");
			html.append("\t\t\t\t</tr>");
			html.append("\t\t\t\t<tr><td colspan=\"2\"><font color=\"AAAAAA\">In: ").append(timeStr).append("</font></td></tr>");
			html.append("\t\t\t</table>");
			html.append("\t\t\t<br>");
		}
		
		// Tournament Event
		Calendar nextTournament = eventChecker.getNextTournamentEventTime();
		if (nextTournament != null)
		{
			long timeUntil = nextTournament.getTimeInMillis() - System.currentTimeMillis();
			String timeStr = formatTimeUntil(timeUntil);
			html.append("\t\t\t<table width=\"224\" bgcolor=\"000000\">");
			html.append("\t\t\t\t<tr>");
			html.append("\t\t\t\t\t<td width=\"140\"><font color=\"00FF00\">Tournament</font></td>");
			html.append("\t\t\t\t\t<td width=\"84\" align=\"right\"><font color=\"B09878\">").append(TIME_FORMAT.format(nextTournament.getTime())).append("</font></td>");
			html.append("\t\t\t\t</tr>");
			html.append("\t\t\t\t<tr><td colspan=\"2\"><font color=\"AAAAAA\">In: ").append(timeStr).append("</font></td></tr>");
			html.append("\t\t\t</table>");
			html.append("\t\t\t<br>");
		}
		
		// Boss Event (from NextBossEvent)
		try
		{
			String bossEventTime = bossEvent.getNextTime();
			if (bossEventTime != null && !bossEventTime.equals("Erro"))
			{
				html.append("\t\t\t<table width=\"224\" bgcolor=\"000000\">");
				html.append("\t\t\t\t<tr>");
				html.append("\t\t\t\t\t<td width=\"140\"><font color=\"00FF00\">Boss Event</font></td>");
				html.append("\t\t\t\t\t<td width=\"84\" align=\"right\"><font color=\"B09878\">").append(bossEventTime).append("</font></td>");
				html.append("\t\t\t\t</tr>");
				html.append("\t\t\t</table>");
				html.append("\t\t\t<br>");
			}
		}
		catch (Exception e)
		{
			// Boss event not available, skip
		}
		
		html.append("\t\t\t<br>");
		html.append("\t\t\t<button action=\"bypass -h voiced_menu\" value=\"Back\" width=204 height=20 back=\"sek.cbui81\" fore=\"sek.cbui82\">");
		html.append("\t\t</center>");
		html.append("\t</body>");
		html.append("</html>");
		
		NpcHtmlMessage msg = new NpcHtmlMessage(0);
		msg.setHtml(html.toString());
		player.sendPacket(msg);
	}
	
	private String formatTimeUntil(long milliseconds)
	{
		if (milliseconds < 0)
			return "Now";
		
		long seconds = milliseconds / 1000;
		long minutes = seconds / 60;
		long hours = minutes / 60;
		long days = hours / 24;
		
		if (days > 0)
			return days + "d " + (hours % 24) + "h " + (minutes % 60) + "m";
		else if (hours > 0)
			return hours + "h " + (minutes % 60) + "m";
		else if (minutes > 0)
			return minutes + "m " + (seconds % 60) + "s";
		else
			return seconds + "s";
	}
}

