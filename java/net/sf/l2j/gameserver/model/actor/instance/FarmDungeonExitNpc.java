package net.sf.l2j.gameserver.model.actor.instance;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.data.cache.HtmCache;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;

import dev.farmDungeon.FarmDungeonInstance;

/**
 * Farm Dungeon Exit NPC
 * Allows players to leave the instance preserving remaining time
 */
public class FarmDungeonExitNpc extends Folk
{
	public FarmDungeonExitNpc(int objectId, NpcTemplate template)
	{
		super(objectId, template);
	}
	
	@Override
	public void onBypassFeedback(Player player, String command)
	{
		if (command.startsWith("farmdungeon_exit"))
		{
			if (!FarmDungeonInstance.getInstance().hasActiveInstance(player))
			{
				player.sendMessage("You are not in a farm dungeon instance.");
				showChatWindow(player, 0);
				return;
			}
			
			// Leave instance preserving remaining time
			int remainingMinutes = FarmDungeonInstance.getInstance().leaveInstancePreservingTime(player);
			
			if (remainingMinutes > 0)
			{
				player.sendMessage("You have left the farm dungeon instance. Remaining time: " + remainingMinutes + " minutes.");
				player.sendMessage("You can re-enter using the remaining time by talking to the Farm Dungeon Manager.");
			}
			else
			{
				player.sendMessage("You have left the farm dungeon instance.");
			}
			
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
		String html = HtmCache.getInstance().getHtm("data/html/mods/farmdungeon/exit.htm");
		if (html == null || html.isEmpty())
		{
			html = getDefaultHtml();
		}
		
		// Replace placeholders
		boolean hasInstance = FarmDungeonInstance.getInstance().hasActiveInstance(player);
		long remainingTime = FarmDungeonInstance.getInstance().getRemainingTime(player);
		
		html = html.replace("%hasInstance%", hasInstance ? "Yes" : "No");
		html = html.replace("%remainingTime%", hasInstance ? formatTime(remainingTime) : "N/A");
		html = html.replace("%npcId%", String.valueOf(getObjectId()));
		
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
			"<center><font color=\"LEVEL\">Farm Dungeon Exit</font></center><br>" +
			"<table width=300 bgcolor=5A5A5A>" +
			"<tr><td width=150>Active Instance:</td><td width=150><center>%hasInstance%</center></td></tr>" +
			"<tr><td width=150>Remaining Time:</td><td width=150><center>%remainingTime%</center></td></tr>" +
			"</table><br>" +
			"<font color=\"LEVEL\">Leave the farm dungeon instance</font><br>" +
			"Your remaining time will be preserved. You can re-enter later using the remaining time.<br><br>" +
			"<center>" +
			"<button value=\"Leave Instance\" action=\"bypass -h npc_%npcId%_farmdungeon_exit\" width=150 height=25 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_CT1.Button_DF\">" +
			"</center>" +
			"</body></html>";
	}
}

