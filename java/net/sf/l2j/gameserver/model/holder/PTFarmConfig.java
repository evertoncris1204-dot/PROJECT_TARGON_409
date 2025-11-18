package net.sf.l2j.gameserver.model.holder;

import java.util.List;

public class PTFarmConfig
{
	private final boolean _enabled;
	private final int _duration;
	private final int _preparation;
	private final List<Integer> _days;
	private final List<String> _times;
	private final int _respawnDelay;
	private final int _respawnRandom;
	
	public PTFarmConfig(boolean enabled, int duration, int preparation, List<Integer> days, List<String> times, int respawnDelay, int respawnRandom)
	{
		_enabled = enabled;
		_duration = duration;
		_preparation = preparation;
		_days = days;
		_times = times;
		_respawnDelay = respawnDelay;
		_respawnRandom = respawnRandom;
	}
	
	public boolean isEnabled()
	{
		return _enabled;
	}
	
	public int getDuration()
	{
		return _duration;
	}
	
	public int getPreparation()
	{
		return _preparation;
	}
	
	public List<Integer> getDays()
	{
		return _days;
	}
	
	public List<String> getTimes()
	{
		return _times;
	}
	
	public int getRespawnDelay()
	{
		return _respawnDelay;
	}
	
	public int getRespawnRandom()
	{
		return _respawnRandom;
	}
}

