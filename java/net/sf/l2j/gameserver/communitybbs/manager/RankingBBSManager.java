package net.sf.l2j.gameserver.communitybbs.manager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.StringTokenizer;
import java.util.stream.IntStream;

import net.sf.l2j.commons.lang.StringUtil;
import net.sf.l2j.commons.pool.ConnectionPool;

import net.sf.l2j.gameserver.data.cache.HtmCache;
import net.sf.l2j.gameserver.data.manager.RankingManager;
import net.sf.l2j.gameserver.data.manager.RankingManager.RankingEntry;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.actor.Player;

public class RankingBBSManager extends BaseBBSManager
{
	private static final StringBuilder PVP = new StringBuilder();
	private static final StringBuilder PKS = new StringBuilder();
	private static final StringBuilder PVE = new StringBuilder();
	
	private static final int PAGE_LIMIT_15 = 15;
	
	private long _nextUpdate;
	
	protected RankingBBSManager()
	{
	}
	
	@Override
	public void parseCmd(String command, Player player)
	{
		if (command.equals("_bbsranking") || command.equals("_bbsranking;"))
		{
			showRankingList(player);
		}
		else if (command.startsWith("_bbsranking;"))
		{
			StringTokenizer st = new StringTokenizer(command, ";");
			st.nextToken(); // Skip "_bbsranking"
			
			String action = st.hasMoreTokens() ? st.nextToken() : "";
			if (action.equals("claimweekly"))
			{
				claimWeeklyRewards(player);
			}
			else if (action.equals("claimmonthly"))
			{
				claimMonthlyRewards(player);
			}
			else
			{
				showRankingList(player);
			}
		}
		else
		{
			super.parseCmd(command, player);
		}
	}
	
	public void showRankingList(Player player)
	{
		try
		{
			if (_nextUpdate < System.currentTimeMillis())
			{
				PVP.setLength(0);
				PKS.setLength(0);
				PVE.setLength(0);
				
				RankingManager rankingManager = RankingManager.getInstance();
				
				// Get PvP rankings
				List<RankingEntry> pvpRankings = rankingManager.getTopRankings("pvp", PAGE_LIMIT_15);
				buildRankingTable(PVP, pvpRankings, "pvp");
				
				// Get PK rankings
				List<RankingEntry> pkRankings = rankingManager.getTopRankings("pk", PAGE_LIMIT_15);
				buildRankingTable(PKS, pkRankings, "pk");
				
				// Get PvE rankings
				List<RankingEntry> pveRankings = rankingManager.getTopRankings("pve", PAGE_LIMIT_15);
				buildRankingTable(PVE, pveRankings, "pve");
				
				_nextUpdate = System.currentTimeMillis() + 60000L; // Update every minute
			}
		}
		catch (Exception e)
		{
			LOGGER.error("Error showing ranking list", e);
			separateAndSend("<html><body><br><br><center>Error loading ranking data. Please try again later.</center></body></html>", player);
			return;
		}
		
		String content = HtmCache.getInstance().getHtm(CB_PATH + getFolder() + "ranklist.htm");
		if (content == null || content.isEmpty())
		{
			// Fallback to generated HTML
			content = generateRankingHtml(player);
		}
		else
		{
			content = content.replaceAll("%name%", player.getName());
			content = content.replaceAll("%pvp%", PVP.toString());
			content = content.replaceAll("%pks%", PKS.toString());
			content = content.replaceAll("%pve%", PVE.toString());
			content = content.replaceAll("%time%", String.valueOf((_nextUpdate - System.currentTimeMillis()) / 1000));
		}
		
		if (content == null || content.isEmpty())
		{
			separateAndSend("<html><body><br><br><center>Error loading ranking page. Please try again later.</center></body></html>", player);
			return;
		}
		
		separateAndSend(content, player);
	}
	
	private void buildRankingTable(StringBuilder sb, List<RankingEntry> rankings, String type)
	{
		int index = 1;
		for (RankingEntry entry : rankings)
		{
			final Player databasePlayer = World.getInstance().getPlayer(entry.playerName);
			final String status = "L2UI_CH3.msnicon" + (databasePlayer != null && databasePlayer.isOnline() ? "1" : "4");
			
			String value;
			switch (type)
			{
				case "pvp":
					value = StringUtil.formatNumber(entry.pvpKills);
					break;
				case "pk":
					value = StringUtil.formatNumber(entry.pkKills);
					break;
				case "pve":
					value = StringUtil.formatNumber(entry.pveKills);
					break;
				default:
					value = "0";
			}
			
			StringUtil.append(sb, "<table width=300 bgcolor=000000><tr><td width=20 align=right>", getColor(index), String.format("%02d", index), "</td>");
			StringUtil.append(sb, "<td width=20 height=18><img src=", status, " width=16 height=16></td><td width=160 align=left>", entry.playerName, "</td>");
			StringUtil.append(sb, "<td width=100 align=right>", value, "</font></td></tr></table><img src=L2UI.SquareGray width=296 height=1>");
			index++;
		}
		
		IntStream.range(index - 1, PAGE_LIMIT_15).forEach(x -> applyEmpty(sb));
	}
	
	private String generateRankingHtml(Player player)
	{
		StringBuilder html = new StringBuilder();
		html.append("<html><body><center><img height=20>");
		html.append("<img src=l2ui.squaregray width=630 height=1>");
		html.append("<table width=630 height=40 bgcolor=000000>");
		html.append("<tr>");
		html.append("<td width=40 align=center><table bgcolor=FFFFFF cellpadding=6 cellspacing=\"-5\"><tr><td><button width=32 height=32 back=icon.skill1028 fore=icon.skill1028></td></tr></table></td>");
		html.append("<td width=590>Welcome <font color=LEVEL>").append(player.getName()).append("</font> to server Ranking Dashboard.");
		html.append("<br1><font color=B09878>Top 15 players in PvP, PK, and PvE rankings.</font></td>");
		html.append("</tr>");
		html.append("</table>");
		html.append("<img src=l2ui.squaregray width=630 height=1>");
		html.append("<img height=10>");
		
		// PvP and PK Rankings
		html.append("<table width=630><tr>");
		html.append("<td width=315 align=center>");
		html.append("<img src=L2UI.SquareWhite width=296 height=1>");
		html.append("<table width=300 bgcolor=000000><tr>");
		html.append("<td width=20 align=center><font color=A9A9A9>#</td>");
		html.append("<td width=20 height=18></td>");
		html.append("<td width=160 align=left>Player Name</td>");
		html.append("<td width=100 align=right>PVP</font></td>");
		html.append("</tr></table><img src=L2UI.SquareWhite width=296 height=1>");
		html.append(PVP.toString());
		html.append("<img src=L2UI.SquareWhite width=296 height=1>");
		html.append("</td>");
		
		html.append("<td width=315 align=center>");
		html.append("<img src=L2UI.SquareWhite width=296 height=1>");
		html.append("<table width=300 bgcolor=000000><tr>");
		html.append("<td width=20 align=center><font color=A9A9A9>#</td>");
		html.append("<td width=20 height=18 align=center></td>");
		html.append("<td width=160 align=left>Player Name</td>");
		html.append("<td width=100 align=right>PK</font></td>");
		html.append("</tr></table><img src=L2UI.SquareWhite width=296 height=1>");
		html.append(PKS.toString());
		html.append("<img src=L2UI.SquareWhite width=296 height=1>");
		html.append("</td>");
		html.append("</tr></table>");
		
		html.append("<img height=10>");
		
		// PvE Rankings (centered)
		html.append("<table width=630><tr>");
		html.append("<td width=315></td>");
		html.append("<td width=315 align=center>");
		html.append("<img src=L2UI.SquareWhite width=296 height=1>");
		html.append("<table width=300 bgcolor=000000><tr>");
		html.append("<td width=20 align=center><font color=A9A9A9>#</td>");
		html.append("<td width=20 height=18 align=center></td>");
		html.append("<td width=160 align=left>Player Name</td>");
		html.append("<td width=100 align=right>PvE Kills</font></td>");
		html.append("</tr></table><img src=L2UI.SquareWhite width=296 height=1>");
		html.append(PVE.toString());
		html.append("<img src=L2UI.SquareWhite width=296 height=1>");
		html.append("</td>");
		html.append("<td width=315></td>");
		html.append("</tr></table>");
		
		html.append("<img height=10>");
		html.append("<img src=L2UI.SquareWhite width=300 height=1>");
		html.append("<table width=300 bgcolor=000000><tr>");
		html.append("<td width=150 align=center><button value=\"Claim Weekly\" action=\"bypass _bbsranking;claimweekly\" width=120 height=21 back=\"L2UI.DefaultButton_click\" fore=\"L2UI.DefaultButton\"></td>");
		html.append("<td width=150 align=center><button value=\"Claim Monthly\" action=\"bypass _bbsranking;claimmonthly\" width=120 height=21 back=\"L2UI.DefaultButton_click\" fore=\"L2UI.DefaultButton\"></td>");
		html.append("</tr></table>");
		html.append("<table width=300 bgcolor=000000><tr><td width=300 align=center><font color=AAAAAA>Next update in ").append((_nextUpdate - System.currentTimeMillis()) / 1000).append(" second(s)</font></td></tr></table>");
		html.append("<img src=L2UI.SquareWhite width=300 height=1>");
		html.append("</center></body></html>");
		
		return html.toString();
	}
	
	private void claimWeeklyRewards(Player player)
	{
		// This will be handled by the reward distribution system
		player.sendMessage("Weekly rewards are automatically distributed. Check your inventory!");
		showRankingList(player);
	}
	
	private void claimMonthlyRewards(Player player)
	{
		// This will be handled by the reward distribution system
		player.sendMessage("Monthly rewards are automatically distributed. Check your inventory!");
		showRankingList(player);
	}
	
	protected void applyEmpty(StringBuilder sb)
	{
		sb.append("<table width=300 bgcolor=000000><tr>");
		sb.append("<td width=20 align=right><font color=B09878>--</font></td><td width=20 height=18></td>");
		sb.append("<td width=160 align=left><font color=B09878>----------------</font></td>");
		sb.append("<td width=100 align=right><font color=FF0000>0</font></td>");
		sb.append("</tr></table><img src=L2UI.SquareGray width=296 height=1>");
	}
	
	protected String getColor(int index)
	{
		switch (index)
		{
			case 1:
				return "<font color=FFFF00>"; // Gold
			case 2:
				return "<font color=FFA500>"; // Orange
			case 3:
				return "<font color=E9967A>"; // Light Salmon
		}
		return "";
	}
	
	@Override
	protected String getFolder()
	{
		return "ranking/";
	}
	
	public static RankingBBSManager getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private static class SingletonHolder
	{
		protected static final RankingBBSManager INSTANCE = new RankingBBSManager();
	}
}
