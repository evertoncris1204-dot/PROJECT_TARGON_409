package net.sf.l2j.gameserver.handler;

import net.sf.l2j.gameserver.handler.AbstractHandler;

public class BypassHandler extends AbstractHandler<String, IBypassHandler>
{
	protected BypassHandler()
	{
		super(IBypassHandler.class, "bypasses");
	}
	
	@Override
	protected void registerHandler(IBypassHandler handler)
	{
		for (String id : handler.getBypassHandlersList())
			_entries.put(id, handler);
	}
	
	public void registerHandlerPublic(IBypassHandler handler)
	{
		registerHandler(handler);
	}
	
	public static BypassHandler getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private static class SingletonHolder
	{
		protected static final BypassHandler INSTANCE = new BypassHandler();
	}
}

