package net.sf.l2j.gameserver.model.entity.Tournament.tasks;

import java.util.List;
import java.util.concurrent.ScheduledFuture;

import net.sf.l2j.commons.pool.ThreadPool;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.entity.Tournament.model.TournamentArena;
import net.sf.l2j.gameserver.model.entity.Tournament.model.TournamentTeam;
import net.sf.l2j.gameserver.model.location.Location;

public class TournamentTeleport implements Runnable
{
	
	private TournamentArena arena;
	private TournamentFight fight;
	private TournamentTeam teamOne;
	private TournamentTeam teamTwo;
	private ScheduledFuture<?> cleanupTask;
	
	public TournamentTeleport(TournamentFight fight, TournamentArena arena, TournamentTeam teamOne, TournamentTeam teamTwo)
	{
		this.fight = fight;
		this.arena = arena;
		this.teamOne = teamOne;
		this.teamTwo = teamTwo;
	}
	
	@Override
	public void run()
	{
		teleportTeamOne();
		teleportTeamTwo();
		teamOne.paralyze();
		teamTwo.paralyze();
		teamOne.screenMessage("Fight will start in " + Config.TOURNAMENT_FIGHT_START_TIME.get(fight.getFightType()) + " seconds");
		teamTwo.screenMessage("Fight will start in " + Config.TOURNAMENT_FIGHT_START_TIME.get(fight.getFightType()) + " seconds");
		
		// Schedule periodic cleanup of known list to ensure instance isolation
		cleanupTask = ThreadPool.scheduleAtFixedRate(new Runnable()
		{
			@Override
			public void run()
			{
				for (net.sf.l2j.gameserver.model.actor.Player player : teamOne.getMembers())
				{
					if (player.isOnline() && player.isInTournamentInstance())
						player.cleanInstanceKnownList();
				}
				for (net.sf.l2j.gameserver.model.actor.Player player : teamTwo.getMembers())
				{
					if (player.isOnline() && player.isInTournamentInstance())
						player.cleanInstanceKnownList();
				}
			}
		}, 1000, 2000); // Run every 2 seconds
		
		// Store cleanup task in fight for cancellation
		fight.setCleanupTask(cleanupTask);
		
		ThreadPool.schedule(new Unparalyze(), Config.TOURNAMENT_FIGHT_START_TIME.get(fight.getFightType()) * 1000);
		
	}
	
	class Unparalyze implements Runnable
	{
		
		@Override
		public void run()
		{
			teamOne.unparalyze();
			teamTwo.unparalyze();
			fight.setStarted(true);
			teamOne.screenMessage("Battle Started!");
			teamTwo.screenMessage("Battle Started!");
			
			// Close arena doors when battle starts
			if (arena != null)
			{
				arena.closeDoors();
			}
		}
		
	}
	
	public void teleportTeamOne()
	{
		int locIndex = 0;
		for (Player player : teamOne.getMembers())
		{
			if (!player.isOnline())
				continue;
			
			List<Location> locs = arena.getTeamOneLocation();
			if (locIndex >= locs.size())
			{
				fight.getTeamOne().sendMessage("Something goes wrong with locations of team one, please, contact and Admin.");
				fight.getTeamTwo().sendMessage("Something goes wrong with locations of team one, please, contact and Admin.");
				fight.finish();
				return;
			}
			Location loc = locs.get(locIndex);
			if (loc != null)
			{
				player.setLastX(player.getPosition().getX());
				player.setLastY(player.getPosition().getY());
				player.setLastZ(player.getPosition().getZ());
				
				// Add player to instance
				if (fight.getInstance() != null)
				{
					fight.getInstance().addPlayer(player);
					player.setTournamentInstanceId(fight.getInstance().getId());
				}
				
				player.teleportTo(loc.getX(), loc.getY(), loc.getZ(), 0);
				
				// Clean known list after teleport to hide players/NPCs from other instances
				net.sf.l2j.commons.pool.ThreadPool.schedule(() -> player.cleanInstanceKnownList(), 500);
				
				locIndex++;
			}
			else
			{
				fight.getTeamOne().sendMessage("Something goes wrong with locations of team one, please, contact and Admin.");
				fight.getTeamTwo().sendMessage("Something goes wrong with locations of team one, please, contact and Admin.");
				fight.finish();
				return;
			}
			
		}
	}
	
	public void teleportTeamTwo()
	{
		int locIndex = 0;
		for (Player player : teamTwo.getMembers())
		{
			if (!player.isOnline())
				continue;
			
			List<Location> locs = arena.getTeamTwoLocation();
			if (locIndex >= locs.size())
			{
				fight.getTeamOne().sendMessage("Something goes wrong with locations of team two, please, contact and Admin.");
				fight.getTeamTwo().sendMessage("Something goes wrong with locations of team two, please, contact and Admin.");
				fight.finish();
				return;
			}
			Location loc = locs.get(locIndex);
			if (loc != null)
			{
				player.setLastX(player.getPosition().getX());
				player.setLastY(player.getPosition().getY());
				player.setLastZ(player.getPosition().getZ());
				
				// Add player to instance
				if (fight.getInstance() != null)
				{
					fight.getInstance().addPlayer(player);
					player.setTournamentInstanceId(fight.getInstance().getId());
				}
				
				player.teleportTo(loc.getX(), loc.getY(), loc.getZ(), 0);
				
				// Clean known list after teleport to hide players/NPCs from other instances
				net.sf.l2j.commons.pool.ThreadPool.schedule(() -> player.cleanInstanceKnownList(), 500);
				
				locIndex++;
			}
			else
			{
				fight.getTeamOne().sendMessage("Something goes wrong with locations of team two, please, contact and Admin.");
				fight.getTeamTwo().sendMessage("Something goes wrong with locations of team two, please, contact and Admin.");
				fight.finish();
				return;
			}
			
		}
	}
	
	/**
	 * @return the arena
	 */
	public TournamentArena getArena()
	{
		return arena;
	}
	
	/**
	 * @param arena the arena to set
	 */
	public void setArena(TournamentArena arena)
	{
		this.arena = arena;
	}
	
	/**
	 * @return the teamOne
	 */
	public TournamentTeam getTeamOne()
	{
		return teamOne;
	}
	
	/**
	 * @param teamOne the teamOne to set
	 */
	public void setTeamOne(TournamentTeam teamOne)
	{
		this.teamOne = teamOne;
	}
	
	/**
	 * @return the teamTwo
	 */
	public TournamentTeam getTeamTwo()
	{
		return teamTwo;
	}
	
	/**
	 * @param teamTwo the teamTwo to set
	 */
	public void setTeamTwo(TournamentTeam teamTwo)
	{
		this.teamTwo = teamTwo;
	}
	
	public TournamentFight getFight()
	{
		return fight;
	}
	
	public void setFight(TournamentFight fight)
	{
		this.fight = fight;
	}
	
}

