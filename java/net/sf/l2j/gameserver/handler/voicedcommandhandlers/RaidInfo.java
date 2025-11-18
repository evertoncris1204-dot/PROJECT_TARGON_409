package net.sf.l2j.gameserver.handler.voicedcommandhandlers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import net.sf.l2j.config.RaidInfoConfig;
import net.sf.l2j.gameserver.data.cache.HtmCache;
import net.sf.l2j.gameserver.data.xml.IconTable;
import net.sf.l2j.gameserver.data.xml.ItemData;
import net.sf.l2j.gameserver.data.xml.NpcData;
import net.sf.l2j.gameserver.handler.IVoicedCommandHandler;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.instance.GrandBoss;
import net.sf.l2j.gameserver.model.actor.instance.Monster;
import net.sf.l2j.gameserver.model.actor.instance.RaidBoss;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.model.item.DropCategory;
import net.sf.l2j.gameserver.model.item.DropData;

public class RaidInfo implements IVoicedCommandHandler
{
	private static final String[] VOICED_COMMANDS =
	{
		"raidinfo"
	};
	
	@Override
	public boolean useVoicedCommand(String command, Player player, String target)
	{
		if (command.equals("raidinfo"))
		{
			if (!RaidInfoConfig.ENABLED)
			{
				player.sendMessage("RaidInfo system is disabled.");
				return true;
			}
			showRaidInfo(player);
			return true;
		}
		return false;
	}
	
	@Override
	public String[] getVoicedCommandList()
	{
		return VOICED_COMMANDS;
	}
	
	private void showRaidInfo(Player player)
	{
		String html = HtmCache.getInstance().getHtm("data/html/mods/menu/RaidInfo.htm");
		if (html == null)
		{
			html = generateRaidInfoHtml(player);
		}
		
		NpcHtmlMessage msg = new NpcHtmlMessage(0);
		msg.setHtml(html);
		player.sendPacket(msg);
	}
	
	public void showGrandBosses(Player player)
	{
		StringBuilder html = new StringBuilder();
		html.append("<html>");
		html.append("\t<body>");
		html.append("\t\t<center>");
		html.append("\t\t\t<table width=\"256\">");
		html.append("\t\t\t\t<tr><td width=\"256\" align=\"center\">Grand Bosses</td></tr>");
		html.append("\t\t\t</table>");
		html.append("\t\t\t<br>");
		html.append(generateGrandBossList(player));
		html.append("\t\t\t<br>");
		html.append("\t\t\t<br>");
		html.append("\t\t\t<button action=\"bypass -h _raidinfo\" value=\"Back\" width=204 height=20 back=\"sek.cbui81\" fore=\"sek.cbui82\">");
		html.append("\t\t</center>");
		html.append("\t</body>");
		html.append("</html>");
		
		NpcHtmlMessage msg = new NpcHtmlMessage(0);
		msg.setHtml(html.toString());
		player.sendPacket(msg);
	}
	
	public void showRaidBosses(Player player)
	{
		StringBuilder html = new StringBuilder();
		html.append("<html>");
		html.append("\t<body>");
		html.append("\t\t<center>");
		html.append("\t\t\t<table width=\"256\">");
		html.append("\t\t\t\t<tr><td width=\"256\" align=\"center\">Raid Bosses</td></tr>");
		html.append("\t\t\t</table>");
		html.append("\t\t\t<br>");
		html.append(generateRaidBossList(player));
		html.append("\t\t\t<br>");
		html.append("\t\t\t<br>");
		html.append("\t\t\t<button action=\"bypass -h _raidinfo\" value=\"Back\" width=204 height=20 back=\"sek.cbui81\" fore=\"sek.cbui82\">");
		html.append("\t\t</center>");
		html.append("\t</body>");
		html.append("</html>");
		
		NpcHtmlMessage msg = new NpcHtmlMessage(0);
		msg.setHtml(html.toString());
		player.sendPacket(msg);
	}
	
	private String generateRaidInfoHtml(Player player)
	{
		StringBuilder html = new StringBuilder();
		html.append("<html>");
		html.append("\t<body>");
		html.append("\t\t<center>");
		html.append("\t\t\t<table width=\"256\">");
		html.append("\t\t\t\t<tr><td width=\"256\" align=\"center\">Raid & Grand Boss Information</td></tr>");
		html.append("\t\t\t</table>");
		html.append("\t\t\t<br>");
		html.append("\t\t\t<table width=\"256\">");
		html.append("\t\t\t\t<tr><td width=\"256\" align=\"left\">Here you can see which Raid and Grand Bosses are currently alive in our world.</td></tr>");
		html.append("\t\t\t</table>");
		html.append("\t\t\t<br>");
		html.append("\t\t\t<br>");
		html.append("\t\t\t<button action=\"bypass -h _raidinfo;grandbosses\" value=\"Grand Bosses\" width=204 height=20 back=\"sek.cbui81\" fore=\"sek.cbui82\">");
		html.append("\t\t\t<br>");
		html.append("\t\t\t<br>");
		html.append("\t\t\t<button action=\"bypass -h _raidinfo;raidbosses\" value=\"Raid Bosses\" width=204 height=20 back=\"sek.cbui81\" fore=\"sek.cbui82\">");
		html.append("\t\t</center>");
		html.append("\t</body>");
		html.append("</html>");
		return html.toString();
	}
	
	private String generateGrandBossList(Player player)
	{
		StringBuilder html = new StringBuilder();
		List<BossInfo> grandBosses = getGrandBosses();
		
		if (grandBosses.isEmpty())
		{
			html.append("\t\t\t\t<table width=\"224\" bgcolor=\"000000\">");
			html.append("\t\t\t\t\t<tr><td width=\"224\" align=\"center\"><font color=\"AAAAAA\">No Grand Bosses Alive</font></td></tr>");
			html.append("\t\t\t\t</table>");
		}
		else
		{
			// Limit bosses based on configuration
			int maxBosses = RaidInfoConfig.MAX_GRAND_BOSSES_DISPLAY > 0 
				? Math.min(grandBosses.size(), RaidInfoConfig.MAX_GRAND_BOSSES_DISPLAY)
				: grandBosses.size();
			for (int i = 0; i < maxBosses; i++)
			{
				BossInfo boss = grandBosses.get(i);
				html.append("\t\t\t\t<table width=\"224\" bgcolor=\"000000\">");
				html.append("\t\t\t\t\t<tr>");
				html.append("\t\t\t\t\t\t<td width=\"140\"><font color=\"00FF00\">").append(boss.name).append("</font></td>");
				html.append("\t\t\t\t\t\t<td width=\"84\" align=\"right\"><font color=\"LEVEL\">Lv ").append(boss.level).append("</font></td>");
				html.append("\t\t\t\t\t</tr>");
				html.append("\t\t\t\t\t<tr><td colspan=\"2\"><font color=\"B09878\">HP: ").append(String.format("%.1f", boss.hpPercent)).append("%</font></td></tr>");
				html.append("\t\t\t\t\t<tr><td colspan=\"2\" align=\"center\"><button value=\"View Drops\" action=\"bypass -h _raidinfo;drops;").append(boss.npcId).append("\" width=100 height=21 back=\"L2UI.DefaultButton_click\" fore=\"L2UI.DefaultButton\"></td></tr>");
				html.append("\t\t\t\t</table>");
				if (i < maxBosses - 1)
					html.append("\t\t\t\t<br>");
			}
			
			if (grandBosses.size() > maxBosses)
			{
				html.append("\t\t\t\t<br>");
				html.append("\t\t\t\t<table width=\"224\" bgcolor=\"000000\">");
				html.append("\t\t\t\t\t<tr><td width=\"224\" align=\"center\"><font color=\"AAAAAA\">... and ").append(grandBosses.size() - maxBosses).append(" more</font></td></tr>");
				html.append("\t\t\t\t</table>");
			}
		}
		
		return html.toString();
	}
	
	private String generateRaidBossList(Player player)
	{
		StringBuilder html = new StringBuilder();
		List<BossInfo> raidBosses = getRaidBosses();
		
		if (raidBosses.isEmpty())
		{
			html.append("\t\t\t\t<table width=\"224\" bgcolor=\"000000\">");
			html.append("\t\t\t\t\t<tr><td width=\"224\" align=\"center\"><font color=\"AAAAAA\">No Raid Bosses Alive</font></td></tr>");
			html.append("\t\t\t\t</table>");
		}
		else
		{
			// Limit bosses based on configuration
			int maxBosses = RaidInfoConfig.MAX_RAID_BOSSES_DISPLAY > 0 
				? Math.min(raidBosses.size(), RaidInfoConfig.MAX_RAID_BOSSES_DISPLAY)
				: raidBosses.size();
			for (int i = 0; i < maxBosses; i++)
			{
				BossInfo boss = raidBosses.get(i);
				html.append("\t\t\t\t<table width=\"224\" bgcolor=\"000000\">");
				html.append("\t\t\t\t\t<tr>");
				html.append("\t\t\t\t\t\t<td width=\"140\"><font color=\"00FF00\">").append(boss.name).append("</font></td>");
				html.append("\t\t\t\t\t\t<td width=\"84\" align=\"right\"><font color=\"LEVEL\">Lv ").append(boss.level).append("</font></td>");
				html.append("\t\t\t\t\t</tr>");
				html.append("\t\t\t\t\t<tr><td colspan=\"2\"><font color=\"B09878\">HP: ").append(String.format("%.1f", boss.hpPercent)).append("%</font></td></tr>");
				html.append("\t\t\t\t\t<tr><td colspan=\"2\" align=\"center\"><button value=\"View Drops\" action=\"bypass -h _raidinfo;drops;").append(boss.npcId).append("\" width=100 height=21 back=\"L2UI.DefaultButton_click\" fore=\"L2UI.DefaultButton\"></td></tr>");
				html.append("\t\t\t\t</table>");
				if (i < maxBosses - 1)
					html.append("\t\t\t\t<br>");
			}
			
			if (raidBosses.size() > maxBosses)
			{
				html.append("\t\t\t\t<br>");
				html.append("\t\t\t\t<table width=\"224\" bgcolor=\"000000\">");
				html.append("\t\t\t\t\t<tr><td width=\"224\" align=\"center\"><font color=\"AAAAAA\">... and ").append(raidBosses.size() - maxBosses).append(" more</font></td></tr>");
				html.append("\t\t\t\t</table>");
			}
		}
		
		return html.toString();
	}
	
	private List<BossInfo> getGrandBosses()
	{
		List<BossInfo> bosses = new ArrayList<>();
		
		for (net.sf.l2j.gameserver.model.WorldObject obj : World.getInstance().getObjects())
		{
			if (obj instanceof GrandBoss)
			{
				Npc npc = (Npc) obj;
				if (!npc.isAlikeDead())
				{
					// Check if this boss should be displayed based on configuration
					if (!RaidInfoConfig.shouldShowGrandBoss(npc.getNpcId()))
						continue;
					
					Monster monster = (Monster) npc;
					BossInfo info = new BossInfo();
					info.npcId = npc.getNpcId();
					info.name = npc.getName();
					info.level = npc.getStatus().getLevel();
					info.hpPercent = (monster.getStatus().getHp() / monster.getStatus().getMaxHp()) * 100.0;
					bosses.add(info);
				}
			}
		}
		
		Collections.sort(bosses, Comparator.comparingInt(b -> b.level));
		Collections.reverse(bosses);
		
		return bosses;
	}
	
	private List<BossInfo> getRaidBosses()
	{
		List<BossInfo> bosses = new ArrayList<>();
		
		for (net.sf.l2j.gameserver.model.WorldObject obj : World.getInstance().getObjects())
		{
			if (obj instanceof RaidBoss && !(obj instanceof GrandBoss))
			{
				Npc npc = (Npc) obj;
				if (!npc.isAlikeDead())
				{
					// Check if this boss should be displayed based on configuration
					if (!RaidInfoConfig.shouldShowRaidBoss(npc.getNpcId()))
						continue;
					
					Monster monster = (Monster) npc;
					BossInfo info = new BossInfo();
					info.npcId = npc.getNpcId();
					info.name = npc.getName();
					info.level = npc.getStatus().getLevel();
					info.hpPercent = (monster.getStatus().getHp() / monster.getStatus().getMaxHp()) * 100.0;
					bosses.add(info);
				}
			}
		}
		
		Collections.sort(bosses, Comparator.comparingInt(b -> b.level));
		Collections.reverse(bosses);
		
		return bosses;
	}
	
	public static void showBossDrops(Player player, int npcId)
	{
		NpcTemplate template = NpcData.getInstance().getTemplate(npcId);
		if (template == null)
		{
			player.sendMessage("Boss not found!");
			return;
		}
		
		String html = HtmCache.getInstance().getHtm("data/html/mods/menu/RaidInfoDrops.htm");
		if (html == null)
		{
			html = generateDropsHtml(player, template);
		}
		else
		{
			html = html.replace("%bossname%", template.getName());
			html = html.replace("%drops%", generateDropsTable(template));
		}
		
		NpcHtmlMessage msg = new NpcHtmlMessage(0);
		msg.setHtml(html);
		player.sendPacket(msg);
	}
	
	private static String generateDropsHtml(Player player, NpcTemplate template)
	{
		StringBuilder html = new StringBuilder();
		html.append("<html>");
		html.append("\t<body>");
		html.append("\t\t<center>");
		html.append("\t\t\t<table width=\"256\">");
		html.append("\t\t\t\t<tr><td width=\"256\" align=\"center\"><font color=\"LEVEL\">").append(template.getName()).append(" - Drops</font></td></tr>");
		html.append("\t\t\t</table>");
		html.append("\t\t\t<br>");
		html.append(generateDropsTable(template));
		html.append("\t\t\t<br>");
		html.append("\t\t\t<br>");
		html.append("\t\t\t<button action=\"bypass -h _raidinfo\" value=\"Back\" width=204 height=20 back=\"sek.cbui81\" fore=\"sek.cbui82\">");
		html.append("\t\t</center>");
		html.append("\t</body>");
		html.append("</html>");
		return html.toString();
	}
	
	private static String generateDropsTable(NpcTemplate template)
	{
		StringBuilder html = new StringBuilder();
		html.append("\t\t\t<table width=\"224\" bgcolor=\"000000\">");
		html.append("\t\t\t\t<tr>");
		html.append("\t\t\t\t\t<td width=\"30\" align=\"center\"><font color=\"A9A9A9\">Icon</font></td>");
		html.append("\t\t\t\t\t<td width=\"120\" align=\"left\"><font color=\"A9A9A9\">Item Name</font></td>");
		html.append("\t\t\t\t\t<td width=\"35\" align=\"center\"><font color=\"A9A9A9\">Min</font></td>");
		html.append("\t\t\t\t\t<td width=\"35\" align=\"center\"><font color=\"A9A9A9\">Max</font></td>");
		html.append("\t\t\t\t\t<td width=\"60\" align=\"center\"><font color=\"A9A9A9\">Chance</font></td>");
		html.append("\t\t\t\t</tr>");
		html.append("\t\t\t</table>");
		
		List<DropCategory> categories = template.getDropData();
		if (categories == null || categories.isEmpty())
		{
			html.append("\t\t\t<br>");
			html.append("\t\t\t<table width=\"224\" bgcolor=\"000000\">");
			html.append("\t\t\t\t<tr><td width=\"224\" align=\"center\"><font color=\"AAAAAA\">No drops available</font></td></tr>");
			html.append("\t\t\t</table>");
		}
		else
		{
			boolean hasDrops = false;
			int dropCount = 0;
			final int MAX_DROPS = RaidInfoConfig.MAX_DROPS_DISPLAY > 0 
				? RaidInfoConfig.MAX_DROPS_DISPLAY 
				: Integer.MAX_VALUE; // Limit drops to avoid HTML being too long
			
			for (DropCategory category : categories)
			{
				// Skip spoil and herb drops, only show regular drops
				if (category.getDropType() != net.sf.l2j.gameserver.enums.DropType.DROP)
					continue;
				
				for (DropData drop : category)
				{
					if (dropCount >= MAX_DROPS)
						break;
					
					hasDrops = true;
					String itemName = "Unknown";
					String itemIcon = "icon.skill0000";
					
					try
					{
						net.sf.l2j.gameserver.model.item.kind.Item item = ItemData.getInstance().getTemplate(drop.itemId());
						if (item != null)
						{
							itemName = item.getName();
							itemIcon = IconTable.getIcon(drop.itemId());
						}
					}
					catch (Exception e)
					{
						// Ignore
					}
					
					double chance = drop.chance();
					String chanceStr = chance >= 100.0 ? "100%" : String.format("%.2f%%", chance);
					
					html.append("\t\t\t<br>");
					html.append("\t\t\t<table width=\"224\" bgcolor=\"000000\">");
					html.append("\t\t\t\t<tr>");
					html.append("\t\t\t\t\t<td width=\"30\" align=\"center\"><img src=\"").append(itemIcon).append("\" width=24 height=24></td>");
					html.append("\t\t\t\t\t<td width=\"120\" align=\"left\">").append(itemName).append("</td>");
					html.append("\t\t\t\t\t<td width=\"35\" align=\"center\">").append(drop.minDrop()).append("</td>");
					html.append("\t\t\t\t\t<td width=\"35\" align=\"center\">").append(drop.maxDrop()).append("</td>");
					html.append("\t\t\t\t\t<td width=\"60\" align=\"center\"><font color=\"FF9900\">").append(chanceStr).append("</font></td>");
					html.append("\t\t\t\t</tr>");
					html.append("\t\t\t</table>");
					
					dropCount++;
				}
				
				if (dropCount >= MAX_DROPS)
					break;
			}
			
			if (!hasDrops)
			{
				html.append("\t\t\t<br>");
				html.append("\t\t\t<table width=\"224\" bgcolor=\"000000\">");
				html.append("\t\t\t\t<tr><td width=\"224\" align=\"center\"><font color=\"AAAAAA\">No drops available</font></td></tr>");
				html.append("\t\t\t</table>");
			}
			else if (dropCount >= MAX_DROPS)
			{
				html.append("\t\t\t<br>");
				html.append("\t\t\t<table width=\"224\" bgcolor=\"000000\">");
				html.append("\t\t\t\t<tr><td width=\"224\" align=\"center\"><font color=\"AAAAAA\">... showing first ").append(MAX_DROPS).append(" drops</font></td></tr>");
				html.append("\t\t\t</table>");
			}
		}
		
		return html.toString();
	}
	
	private static class BossInfo
	{
		int npcId;
		String name;
		int level;
		double hpPercent;
	}
}

