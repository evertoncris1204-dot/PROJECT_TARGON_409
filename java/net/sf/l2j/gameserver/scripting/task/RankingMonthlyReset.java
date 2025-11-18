package net.sf.l2j.gameserver.scripting.task;

import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.gameserver.data.manager.RankingManager;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.scripting.ScheduledQuest;

/**
 * Monthly Ranking Reset and Reward Distribution
 * Resets monthly rankings and distributes rewards on the 1st of each month at 00:00
 */
public final class RankingMonthlyReset extends ScheduledQuest
{
	private static final CLogger LOGGER = new CLogger(RankingMonthlyReset.class.getName());
	
	public RankingMonthlyReset()
	{
		super(-1, "task");
	}
	
	@Override
	public final void onStart()
	{
		LOGGER.info("Starting monthly ranking reset and reward distribution...");
		
		try
		{
			RankingManager rankingManager = RankingManager.getInstance();
			
			// Distribute rewards first (based on previous month's rankings)
			rankingManager.distributeRewards("monthly");
			
			// Reset monthly rankings
			rankingManager.resetRankings("monthly");
			
			// Announce to all online players
			World.announceToOnlinePlayers("Monthly rankings have been reset! New rewards are available for top players!", false);
			
			LOGGER.info("Monthly ranking reset and reward distribution completed successfully.");
		}
		catch (Exception e)
		{
			LOGGER.error("Error during monthly ranking reset", e);
		}
	}
	
	@Override
	public final void onEnd()
	{
		// Do nothing.
	}
}

