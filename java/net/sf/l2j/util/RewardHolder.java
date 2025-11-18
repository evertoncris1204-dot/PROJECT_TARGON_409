package net.sf.l2j.util;

/**
 * @author Rouxy
 */
public class RewardHolder
{
	private final int itemId;
	private final int count;
	
	public RewardHolder(int itemId, int count)
	{
		this.itemId = itemId;
		this.count = count;
	}
	
	public int getItemId()
	{
		return itemId;
	}
	
	public int getCount()
	{
		return count;
	}
}

