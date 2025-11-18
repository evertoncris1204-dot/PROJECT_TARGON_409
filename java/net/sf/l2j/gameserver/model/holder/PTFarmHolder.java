package net.sf.l2j.gameserver.model.holder;

import net.sf.l2j.commons.data.StatSet;

public class PTFarmHolder
{
	private final int _npcId;
	private final int _x;
	private final int _y;
	private final int _z;
	
	public PTFarmHolder(StatSet set)
	{
		_npcId = set.getInteger("npcId");
		_x = set.getInteger("x");
		_y = set.getInteger("y");
		_z = set.getInteger("z");
	}
	
	public int getNpcId()
	{
		return _npcId;
	}
	
	public int getX()
	{
		return _x;
	}
	
	public int getY()
	{
		return _y;
	}
	
	public int getZ()
	{
		return _z;
	}
}

