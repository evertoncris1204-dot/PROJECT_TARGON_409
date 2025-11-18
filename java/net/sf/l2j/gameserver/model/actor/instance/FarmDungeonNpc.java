package net.sf.l2j.gameserver.model.actor.instance;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.data.cache.HtmCache;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;

import dev.farmDungeon.FarmDungeonInstance;

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
		else if (command.startsWith("farmdungeon_use_preserved"))
		{
			if (FarmDungeonInstance.getInstance().usePreservedTime(player))
			{
				player.sendMessage("You have re-entered the farm dungeon using your preserved time!");
			}
			else
			{
				player.sendMessage("You don't have any preserved time to use.");
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
		if (html == null || html.isEmpty())
		{
			html = getDefaultHtml();
		}
		
		// Replace placeholders
		boolean hasInstance = FarmDungeonInstance.getInstance().hasActiveInstance(player);
		long remainingTime = FarmDungeonInstance.getInstance().getRemainingTime(player);
		int preservedTime = FarmDungeonInstance.getInstance().getPreservedTime(player);
		
		// Get item name
		String itemName = "Adena";
		try
		{
			net.sf.l2j.gameserver.data.xml.ItemData itemData = net.sf.l2j.gameserver.data.xml.ItemData.getInstance();
			net.sf.l2j.gameserver.model.item.kind.Item item = itemData.getTemplate(Config.FARM_DUNGEON_PAYMENT_ITEM_ID);
			if (item != null)
				itemName = item.getName();
		}
		catch (Exception e)
		{
			// Use default name
		}
		
		// Calculate costs for different durations (using item count)
		int cost30 = (Config.FARM_DUNGEON_PAYMENT_ITEM_COUNT_PER_HOUR * 30) / 60;
		int cost60 = Config.FARM_DUNGEON_PAYMENT_ITEM_COUNT_PER_HOUR;
		int cost120 = (Config.FARM_DUNGEON_PAYMENT_ITEM_COUNT_PER_HOUR * 120) / 60;
		
		// Replace all placeholders
		html = html.replace("%hasInstance%", hasInstance ? "Yes" : "No");
		html = html.replace("%remainingTime%", hasInstance ? formatTime(remainingTime) : "N/A");
		html = html.replace("%costPerHour%", String.valueOf(Config.FARM_DUNGEON_PAYMENT_ITEM_COUNT_PER_HOUR) + " " + itemName);
		html = html.replace("%minDuration%", String.valueOf(Config.FARM_DUNGEON_MIN_DURATION));
		html = html.replace("%maxDuration%", String.valueOf(Config.FARM_DUNGEON_MAX_DURATION));
		html = html.replace("%cost30min%", String.valueOf(cost30) + " " + itemName);
		html = html.replace("%cost60min%", String.valueOf(cost60) + " " + itemName);
		html = html.replace("%cost120min%", String.valueOf(cost120) + " " + itemName);
		html = html.replace("%itemName%", itemName);
		html = html.replace("%preservedTime%", preservedTime > 0 ? String.valueOf(preservedTime) + " minutes" : "None");
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
		
		// Add preserved time button if player has preserved time
		if (preservedTime > 0)
		{
			html = html.replace("%preservedButton%", "<button value=\"Use Preserved Time (" + preservedTime + " min)\" action=\"bypass -h npc_" + getObjectId() + "_farmdungeon_use_preserved\" width=200 height=25 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_CT1.Button_DF\">");
		}
		else
		{
			html = html.replace("%preservedButton%", "");
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
			"<table width=300 bgcolor=5A5A5A>" +
			"<tr><td width=150>Active Instance:</td><td width=150><center>%hasInstance%</center></td></tr>" +
			"<tr><td width=150>Remaining Time:</td><td width=150><center>%remainingTime%</center></td></tr>" +
			"</table><br>" +
			"<font color=\"LEVEL\">Rent a private dungeon instance for farming!</font><br>" +
			"You will have your own private dungeon where you can farm without interference.<br><br>" +
			"<table width=300 bgcolor=5A5A5A>" +
			"<tr><td width=100>Duration</td><td width=100>Cost</td><td width=100>Action</td></tr>" +
			"<tr><td>30 minutes</td><td>%cost30min%</td><td><center><button value=\"Buy\" action=\"bypass -h npc_%npcId%_farmdungeon_buy 30\" width=65 height=19 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_CT1.Button_DF\"></center></td></tr>" +
			"<tr><td>60 minutes</td><td>%cost60min%</td><td><center><button value=\"Buy\" action=\"bypass -h npc_%npcId%_farmdungeon_buy 60\" width=65 height=19 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_CT1.Button_DF\"></center></td></tr>" +
			"<tr><td>120 minutes</td><td>%cost120min%</td><td><center><button value=\"Buy\" action=\"bypass -h npc_%npcId%_farmdungeon_buy 120\" width=65 height=19 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_CT1.Button_DF\"></center></td></tr>" +
			"</table><br>" +
			"<center>%leaveButton%</center><br>" +
			"<table width=300 bgcolor=5A5A5A>" +
			"<tr><td width=150>Preserved Time:</td><td width=150><center>%preservedTime%</center></td></tr>" +
			"</table><br>" +
			"<center>%preservedButton%</center><br>" +
			"<font color=\"LEVEL\">Rules:</font><br>" +
			"- Minimum duration: %minDuration% minutes<br>" +
			"- Maximum duration: %maxDuration% minutes<br>" +
			"- Cost per hour: %costPerHour%<br>" +
			"- You will be teleported back when time expires<br>" +
			"- Only you can enter your instance<br>" +
			"</body></html>";
	}
}

