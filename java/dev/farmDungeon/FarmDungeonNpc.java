package dev.farmDungeon;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.data.cache.HtmCache;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.instance.Folk;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;

/**
 * Farm Dungeon NPC
 * Sells access to private farm dungeon instances
 */
public class FarmDungeonNpc extends Folk
{
	public FarmDungeonNpc(int objectId, NpcTemplate template)
	{
		super(objectId, template);
	}
	
	@Override
	public void onBypassFeedback(Player player, String command)
	{
		if (command.startsWith("farmdungeon_buy"))
		{
			String[] params = command.split(" ");
			if (params.length >= 2)
			{
				try
				{
					int minutes = Integer.parseInt(params[1]);
					
					// Validate duration
					if (minutes < Config.FARM_DUNGEON_MIN_DURATION || minutes > Config.FARM_DUNGEON_MAX_DURATION)
					{
						player.sendMessage("Invalid duration. Minimum: " + Config.FARM_DUNGEON_MIN_DURATION + " minutes, Maximum: " + Config.FARM_DUNGEON_MAX_DURATION + " minutes.");
						showChatWindow(player, 0);
						return;
					}
					
					// Create instance
					if (FarmDungeonInstance.getInstance().createInstance(player, minutes))
					{
						// Instance created successfully, player was teleported
						return;
					}
				}
				catch (NumberFormatException e)
				{
					player.sendMessage("Invalid duration format.");
				}
			}
			showChatWindow(player, 0);
		}
		else if (command.startsWith("farmdungeon_leave"))
		{
			if (FarmDungeonInstance.getInstance().hasActiveInstance(player))
			{
				FarmDungeonInstance.getInstance().removeInstance(player, false);
			}
			else
			{
				player.sendMessage("You don't have an active farm dungeon instance.");
			}
			showChatWindow(player, 0);
		}
		else if (command.startsWith("farmdungeon_status"))
		{
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
		String html = HtmCache.getInstance().getHtm("data/html/mods/farmdungeon/index.htm");
		if (html == null)
		{
			html = getDefaultHtml();
		}
		
		// Replace placeholders
		boolean hasInstance = FarmDungeonInstance.getInstance().hasActiveInstance(player);
		long remainingTime = FarmDungeonInstance.getInstance().getRemainingTime(player);
		
		html = html.replace("%hasInstance%", hasInstance ? "Yes" : "No");
		html = html.replace("%remainingTime%", hasInstance ? formatTime(remainingTime) : "N/A");
		html = html.replace("%costPerHour%", String.valueOf(Config.FARM_DUNGEON_COST_PER_HOUR));
		html = html.replace("%minDuration%", String.valueOf(Config.FARM_DUNGEON_MIN_DURATION));
		html = html.replace("%maxDuration%", String.valueOf(Config.FARM_DUNGEON_MAX_DURATION));
		
		// Calculate costs for different durations
		long cost30 = (Config.FARM_DUNGEON_COST_PER_HOUR * 30) / 60;
		long cost60 = Config.FARM_DUNGEON_COST_PER_HOUR;
		long cost120 = (Config.FARM_DUNGEON_COST_PER_HOUR * 120) / 60;
		
		html = html.replace("%cost30min%", String.valueOf(cost30));
		html = html.replace("%cost60min%", String.valueOf(cost60));
		html = html.replace("%cost120min%", String.valueOf(cost120));
		
		// Replace NPC ID placeholder
		html = html.replace("%npcId%", String.valueOf(getObjectId()));
		
		// Add leave button if player has active instance
		if (hasInstance)
		{
			html = html.replace("%leaveButton%", "<button value=\"Leave Instance\" action=\"bypass -h npc_" + getObjectId() + "_farmdungeon_leave\" width=150 height=25 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_CT1.Button_DF\">");
		}
		else
		{
			html = html.replace("%leaveButton%", "");
		}
		
		NpcHtmlMessage htmlMsg = new NpcHtmlMessage(getObjectId());
		htmlMsg.setHtml(html);
		player.sendPacket(htmlMsg);
	}
	
	private String formatTime(long seconds)
	{
		if (seconds <= 0)
			return "0 minutes";
		
		long minutes = seconds / 60;
		long secs = seconds % 60;
		
		if (minutes > 0)
			return minutes + " minutes " + secs + " seconds";
		else
			return secs + " seconds";
	}
	
	private String getDefaultHtml()
	{
		return "<html><body>" +
			"<center><font color=\"LEVEL\">Farm Dungeon Instance</font></center><br>" +
			"<table width=300>" +
			"<tr><td>Active Instance:</td><td>%hasInstance%</td></tr>" +
			"<tr><td>Remaining Time:</td><td>%remainingTime%</td></tr>" +
			"</table><br>" +
			"<center>%leaveButton%</center>" +
			"</body></html>";
	}
}

