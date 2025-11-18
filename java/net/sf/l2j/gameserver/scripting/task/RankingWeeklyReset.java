package net.sf.l2j.gameserver.scripting.task;

import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.gameserver.data.manager.RankingManager;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.scripting.ScheduledQuest;

/**
 * Weekly Ranking Reset and Reward Distribution
 * Resets weekly rankings and distributes rewards every Monday at 00:00
 */
public final class RankingWeeklyReset extends ScheduledQuest
{
	private static final CLogger LOGGER = new CLogger(RankingWeeklyReset.class.getName());
	
	public RankingWeeklyReset()
	{
		super(-1, "task");
	}
	
	@Override
	public final void onStart()
	{
		LOGGER.info("Starting weekly ranking reset and reward distribution...");
		
		try
		{
			RankingManager rankingManager = RankingManager.getInstance();
			
			// Distribute rewards first (based on previous week's rankings)
			rankingManager.distributeRewards("weekly");
			
			// Reset weekly rankings
			rankingManager.resetRankings("weekly");
			
			// Announce to all online players
			World.announceToOnlinePlayers("Weekly rankings have been reset! New rewards are available for top players!", false);
			
			LOGGER.info("Weekly ranking reset and reward distribution completed successfully.");
		}
		catch (Exception e)
		{
			LOGGER.error("Error during weekly ranking reset", e);
		}
	}
	
	@Override
	public final void onEnd()
	{
		// Do nothing.
	}
}

