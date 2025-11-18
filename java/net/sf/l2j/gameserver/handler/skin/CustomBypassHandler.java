package net.sf.l2j.gameserver.handler.skin;

import java.util.HashMap;
import java.util.Map;

import net.sf.l2j.gameserver.handler.voicedcommandhandlers.VoicedDressMe;
import net.sf.l2j.gameserver.model.actor.Player;

public class CustomBypassHandler
{
	private static CustomBypassHandler _instance = null;
	private final Map<String, ICustomByPassHandler> _handlers;
	
	private CustomBypassHandler()
	{
		_handlers = new HashMap<>();
		
		registerCustomBypassHandler(new VoicedDressMe());
	}
	
	public static CustomBypassHandler getInstance()
	{
		if (_instance == null)
		{
			_instance = new CustomBypassHandler();
		}
		
		return _instance;
	}
	
	public void registerCustomBypassHandler(final ICustomByPassHandler handler)
	{
		for (final String s : handler.getByPassCommands())
		{
			_handlers.put(s, handler);
		}
	}
	
	public void handleBypass(final Player player, final String command)
	{
		if (!command.startsWith("custom_"))
			return;
		
		String cmd = "";
		String params = "";
		final int iPos = command.indexOf(" ");
		if (iPos != -1)
		{
			cmd = command.substring(0, iPos);
			params = command.substring(iPos + 1);
		}
		else
		{
			cmd = command;
		}
		final ICustomByPassHandler ch = _handlers.get(cmd);
		if (ch != null)
		{
			ch.handleCommand(cmd, player, params);
		}
	}
}

