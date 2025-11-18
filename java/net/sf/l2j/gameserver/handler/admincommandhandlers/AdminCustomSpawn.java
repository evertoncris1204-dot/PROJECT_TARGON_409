package net.sf.l2j.gameserver.handler.admincommandhandlers;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.StringTokenizer;

import net.sf.l2j.commons.data.Pagination;
import net.sf.l2j.commons.lang.StringUtil;

import net.sf.l2j.gameserver.data.manager.CustomSpawnManager;
import net.sf.l2j.gameserver.data.xml.AdminData;
import net.sf.l2j.gameserver.data.xml.NpcData;
import net.sf.l2j.gameserver.handler.IAdminCommandHandler;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;
import net.sf.l2j.gameserver.network.SystemMessageId;

/**
 * Admin commands for Custom Spawnlist System
 */
public class AdminCustomSpawn implements IAdminCommandHandler
{
	private static final String[] ADMIN_COMMANDS =
	{
		"admin_customspawn",
		"admin_customspawn_add",
		"admin_customspawn_delete",
		"admin_customspawn_list",
		"admin_customspawn_reload"
	};
	
	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy HH:mm");
	
	@Override
	public void useAdminCommand(String command, Player player)
	{
		if (command.equals("admin_customspawn"))
		{
			showMainMenu(player);
		}
		else if (command.startsWith("admin_customspawn_add"))
		{
			StringTokenizer st = new StringTokenizer(command, " ");
			st.nextToken(); // Skip command
			
			if (!st.hasMoreTokens())
			{
				showAddMenu(player);
				return;
			}
			
			try
			{
				String npcIdOrName = st.nextToken();
				int npcId = 0;
				
				// Try to parse as ID first
				if (StringUtil.isDigit(npcIdOrName))
				{
					npcId = Integer.parseInt(npcIdOrName);
				}
				else
				{
					// Try to find by name
					NpcTemplate template = NpcData.getInstance().getTemplateByName(npcIdOrName.replace('_', ' '));
					if (template != null)
						npcId = template.getNpcId();
				}
				
				if (npcId == 0)
				{
					player.sendMessage("Invalid NPC ID or name: " + npcIdOrName);
					return;
				}
				
				int respawnDelay = st.hasMoreTokens() ? Integer.parseInt(st.nextToken()) : 60;
				int respawnRandom = st.hasMoreTokens() ? Integer.parseInt(st.nextToken()) : 0;
				String periodOfDay = st.hasMoreTokens() ? st.nextToken() : "ALL";
				
				// Get player's current location
				int x = player.getX();
				int y = player.getY();
				int z = player.getZ();
				int heading = player.getHeading();
				
				if (CustomSpawnManager.getInstance().addSpawn(npcId, x, y, z, heading, respawnDelay, respawnRandom, 0, periodOfDay, player.getName()))
				{
					player.sendMessage("Custom spawn added successfully!");
					showMainMenu(player);
				}
				else
				{
					player.sendMessage("Failed to add custom spawn.");
				}
			}
			catch (Exception e)
			{
				player.sendMessage("Usage: //customspawn_add <npcId|npcName> [respawnDelay] [respawnRandom] [periodOfDay]");
				player.sendMessage("Period options: ALL, DAY, NIGHT");
			}
		}
		else if (command.startsWith("admin_customspawn_delete"))
		{
			StringTokenizer st = new StringTokenizer(command, " ");
			st.nextToken(); // Skip command
			
			if (!st.hasMoreTokens())
			{
				player.sendMessage("Usage: //customspawn_delete <spawnId>");
				return;
			}
			
			try
			{
				int id = Integer.parseInt(st.nextToken());
				if (CustomSpawnManager.getInstance().deleteSpawn(id))
				{
					player.sendMessage("Custom spawn deleted successfully!");
					showMainMenu(player);
				}
				else
				{
					player.sendMessage("Failed to delete custom spawn. ID may not exist.");
				}
			}
			catch (Exception e)
			{
				player.sendMessage("Invalid spawn ID.");
			}
		}
		else if (command.startsWith("admin_customspawn_list"))
		{
			StringTokenizer st = new StringTokenizer(command, " ");
			st.nextToken(); // Skip command
			int page = st.hasMoreTokens() ? Integer.parseInt(st.nextToken()) : 1;
			
			showSpawnList(player, page);
		}
		else if (command.equals("admin_customspawn_reload"))
		{
			CustomSpawnManager.getInstance().load();
			player.sendMessage("Custom spawns reloaded!");
			showMainMenu(player);
		}
	}
	
	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
	
	private void showMainMenu(Player player)
	{
		StringBuilder html = new StringBuilder();
		html.append("<html><body>");
		html.append("<center><font color=\"LEVEL\">Custom Spawnlist Manager</font></center><br>");
		html.append("<table width=300>");
		html.append("<tr><td>Total Custom Spawns:</td><td>").append(CustomSpawnManager.getInstance().getAllSpawns().size()).append("</td></tr>");
		html.append("</table><br>");
		html.append("<center>");
		html.append("<button value=\"Add Spawn\" action=\"bypass -h admin_customspawn_add\" width=120 height=21 back=\"L2UI.DefaultButton_click\" fore=\"L2UI.DefaultButton\"><br>");
		html.append("<button value=\"List Spawns\" action=\"bypass -h admin_customspawn_list 1\" width=120 height=21 back=\"L2UI.DefaultButton_click\" fore=\"L2UI.DefaultButton\"><br>");
		html.append("<button value=\"Reload\" action=\"bypass -h admin_customspawn_reload\" width=120 height=21 back=\"L2UI.DefaultButton_click\" fore=\"L2UI.DefaultButton\">");
		html.append("</center>");
		html.append("</body></html>");
		
		NpcHtmlMessage msg = new NpcHtmlMessage(0);
		msg.setHtml(html.toString());
		player.sendPacket(msg);
	}
	
	private void showAddMenu(Player player)
	{
		StringBuilder html = new StringBuilder();
		html.append("<html><body>");
		html.append("<center><font color=\"LEVEL\">Add Custom Spawn</font></center><br>");
		html.append("<table width=300>");
		html.append("<tr><td>Current Position:</td></tr>");
		html.append("<tr><td>X: ").append(player.getX()).append("</td></tr>");
		html.append("<tr><td>Y: ").append(player.getY()).append("</td></tr>");
		html.append("<tr><td>Z: ").append(player.getZ()).append("</td></tr>");
		html.append("<tr><td>Heading: ").append(player.getHeading()).append("</td></tr>");
		html.append("</table><br>");
		html.append("<center>");
		html.append("Usage: //customspawn_add &lt;npcId|npcName&gt; [respawnDelay] [respawnRandom] [periodOfDay]<br>");
		html.append("<br>");
		html.append("Examples:<br>");
		html.append("//customspawn_add 12345 60 0 ALL<br>");
		html.append("//customspawn_add Orc 120 30 DAY<br>");
		html.append("<br>");
		html.append("Period options: ALL, DAY, NIGHT<br>");
		html.append("</center>");
		html.append("</body></html>");
		
		NpcHtmlMessage msg = new NpcHtmlMessage(0);
		msg.setHtml(html.toString());
		player.sendPacket(msg);
	}
	
	private void showSpawnList(Player player, int page)
	{
		List<CustomSpawnManager.CustomSpawnData> spawns = CustomSpawnManager.getInstance().getAllSpawns();
		Pagination<CustomSpawnManager.CustomSpawnData> pagination = new Pagination<>(spawns.stream(), page, 10);
		
		StringBuilder html = new StringBuilder();
		html.append("<html><body>");
		html.append("<center><font color=\"LEVEL\">Custom Spawn List</font></center><br>");
		html.append("<table width=600 bgcolor=000000>");
		html.append("<tr>");
		html.append("<td width=50 align=center>ID</td>");
		html.append("<td width=100 align=center>NPC ID</td>");
		html.append("<td width=150>NPC Name</td>");
		html.append("<td width=100 align=center>Location</td>");
		html.append("<td width=100 align=center>Respawn</td>");
		html.append("<td width=100 align=center>Actions</td>");
		html.append("</tr>");
		html.append("</table>");
		
		for (CustomSpawnManager.CustomSpawnData spawn : pagination)
		{
			html.append("<table width=600 bgcolor=000000>");
			html.append("<tr>");
			html.append("<td width=50 align=center>").append(spawn.id).append("</td>");
			html.append("<td width=100 align=center>").append(spawn.npcId).append("</td>");
			html.append("<td width=150>").append(spawn.npcName).append("</td>");
			html.append("<td width=100 align=center>").append(spawn.x).append(",").append(spawn.y).append(",").append(spawn.z).append("</td>");
			html.append("<td width=100 align=center>").append(spawn.respawnDelay).append("s</td>");
			html.append("<td width=100 align=center>");
			html.append("<button value=\"Delete\" action=\"bypass -h admin_customspawn_delete ").append(spawn.id).append("\" width=60 height=21 back=\"L2UI.DefaultButton_click\" fore=\"L2UI.DefaultButton\">");
			html.append("</td>");
			html.append("</tr>");
			html.append("</table>");
		}
		
		pagination.generateSpace(20);
		pagination.generatePages("bypass admin_customspawn_list %page%");
		
		html.append("<br><center>");
		html.append("<button value=\"Back\" action=\"bypass -h admin_customspawn\" width=100 height=21 back=\"L2UI.DefaultButton_click\" fore=\"L2UI.DefaultButton\">");
		html.append("</center>");
		html.append("</body></html>");
		
		NpcHtmlMessage msg = new NpcHtmlMessage(0);
		msg.setHtml(html.toString());
		player.sendPacket(msg);
	}
}

