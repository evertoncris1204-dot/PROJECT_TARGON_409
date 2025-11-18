package net.sf.l2j.gameserver.handler.voicedcommandhandlers;

import net.sf.l2j.gameserver.handler.IVoicedCommandHandler;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;
import net.sf.l2j.gameserver.util.sellBuffEngine.BuffShopUIManager;

public class BuffManagerVCmd implements IVoicedCommandHandler
{
	private static final String[] VOICED_COMMANDS =
	{
		"rembuff",
		"buffshop"
	};
	
	@Override
	public boolean useVoicedCommand(String command, Player player, String params)
	{
		if (command.startsWith("rembuff"))
		{
			NpcHtmlMessage html = new NpcHtmlMessage(0);
			html.setFile("./data/html/BuffShop/index.htm");
			player.sendPacket(html);
		}
		else if (command.startsWith("buffshop"))
		{
			BuffShopUIManager.getInstance().showIndexWindow(player, null);
		}
		return true;
	}
	
	@Override
	public String[] getVoicedCommandList()
	{
		return VOICED_COMMANDS;
	}
}
