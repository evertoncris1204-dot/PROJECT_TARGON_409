package net.sf.l2j.gameserver.model.entity.Tournament.model;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

import net.sf.l2j.commons.data.StatSet;
import net.sf.l2j.commons.math.MathUtil;
import net.sf.l2j.commons.pool.ThreadPool;
import net.sf.l2j.gameserver.data.xml.DoorData;
import net.sf.l2j.gameserver.model.actor.instance.Door;
import net.sf.l2j.gameserver.model.entity.Tournament.enums.TournamentFightType;
import net.sf.l2j.gameserver.model.location.Location;

/**
 * @author Rouxy
 */

public class TournamentArena
{
	private final int id;
	private List<Location> teamOneLocation = new ArrayList<>();
	private List<Location> teamTwoLocation = new ArrayList<>();
	private List<Integer> doorIds = new ArrayList<>();
	private int time;
	private ScheduledFuture<?> doorCheckTask;
	
	private final List<TournamentFightType> types = new ArrayList<>();
	
	public TournamentArena(StatSet set, List<Location> teamOneLocation, List<Location> teamTwoLocation, List<Integer> doorIds)
	{
		String fTypes = set.getString("types");
		for (String type : fTypes.split(";"))
		{
			try
			{
				TournamentFightType fightType = TournamentFightType.valueOf(type);
				if (fightType != null)
					types.add(fightType);
			}
			catch (IllegalArgumentException e)
			{
				// Invalid fight type, skip
			}
		}
		id = set.getInteger("id");
		this.teamOneLocation = teamOneLocation;
		this.teamTwoLocation = teamTwoLocation;
		this.doorIds = doorIds != null ? doorIds : new ArrayList<>();
	}
	
	public List<Location> getTeamOneLocation()
	{
		return teamOneLocation;
	}
	
	public void setTeamOneLocation(List<Location> teamOneLocation)
	{
		this.teamOneLocation = teamOneLocation;
	}
	
	public List<Location> getTeamTwoLocation()
	{
		return teamTwoLocation;
	}
	
	public void setTeamTwoLocation(List<Location> teamTwoLocation)
	{
		this.teamTwoLocation = teamTwoLocation;
	}
	
	public int getTime()
	{
		return time;
	}
	
	public void setTime(int time)
	{
		this.time = time;
	}
	
	public int getId()
	{
		return id;
	}
	
	public List<TournamentFightType> getTypes()
	{
		return types;
	}
	
	/**
	 * Get list of door IDs for this arena
	 */
	public List<Integer> getDoorIds()
	{
		return doorIds;
	}
	
	/**
	 * Close all doors in this arena permanently
	 * This method ensures doors stay closed regardless of event state
	 */
	public void closeDoors()
	{
		if (doorIds.isEmpty())
		{
			// Auto-detect doors near arena center
			Location centerLoc = getArenaCenter();
			if (centerLoc != null)
			{
				closeDoorsNearLocation(centerLoc, 2000); // 2000 radius
			}
		}
		else
		{
			// Close specified doors
			for (Integer doorId : doorIds)
			{
				Door door = DoorData.getInstance().getDoor(doorId);
				if (door != null && door.isOpened())
				{
					door.closeMe();
				}
			}
		}
		
		// Cancel any existing door check task
		if (doorCheckTask != null)
		{
			doorCheckTask.cancel(false);
		}
		
		// Schedule periodic check to ensure doors remain closed permanently
		// This prevents doors from being opened by other systems
		doorCheckTask = ThreadPool.scheduleAtFixedRate(() -> {
			// Re-close doors if they were opened
			if (doorIds.isEmpty())
			{
				Location centerLoc = getArenaCenter();
				if (centerLoc != null)
				{
					closeDoorsNearLocation(centerLoc, 2000);
				}
			}
			else
			{
				for (Integer doorId : doorIds)
				{
					Door door = DoorData.getInstance().getDoor(doorId);
					if (door != null && door.isOpened())
					{
						door.closeMe();
					}
				}
			}
		}, 5000, 10000); // Check every 10 seconds, starting after 5 seconds
	}
	
	/**
	 * Open all doors in this arena
	 * NOTE: This method is kept for potential future use, but doors are kept closed by default
	 */
	public void openDoors()
	{
		// Doors remain closed - not opening them
		// This method is kept for potential future use if needed
	}
	
	/**
	 * Get the center location of the arena (average of team locations)
	 */
	private Location getArenaCenter()
	{
		if (teamOneLocation.isEmpty() && teamTwoLocation.isEmpty())
			return null;
		
		int totalX = 0, totalY = 0, totalZ = 0;
		int count = 0;
		
		for (Location loc : teamOneLocation)
		{
			totalX += loc.getX();
			totalY += loc.getY();
			totalZ += loc.getZ();
			count++;
		}
		
		for (Location loc : teamTwoLocation)
		{
			totalX += loc.getX();
			totalY += loc.getY();
			totalZ += loc.getZ();
			count++;
		}
		
		if (count == 0)
			return null;
		
		return new Location(totalX / count, totalY / count, totalZ / count);
	}
	
	/**
	 * Close doors near a specific location
	 */
	private void closeDoorsNearLocation(Location center, int radius)
	{
		for (Door door : DoorData.getInstance().getDoors())
		{
			if (door != null && door.isOpened() && MathUtil.checkIfInRange(radius, door, center, true))
			{
				door.closeMe();
			}
		}
	}
	
	/**
	 * Open doors near a specific location
	 */
	private void openDoorsNearLocation(Location center, int radius)
	{
		for (Door door : DoorData.getInstance().getDoors())
		{
			if (door != null && !door.isOpened() && MathUtil.checkIfInRange(radius, door, center, true))
			{
				door.openMe();
			}
		}
	}
	
}

