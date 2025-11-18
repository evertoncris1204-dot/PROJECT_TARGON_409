package net.sf.l2j.gameserver.scripting.task;

import java.util.Calendar;
import java.util.concurrent.ScheduledFuture;

import net.sf.l2j.Config;
import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.commons.pool.ThreadPool;
import net.sf.l2j.gameserver.enums.EventState;
import net.sf.l2j.gameserver.model.World;
import dev.tvtEvent.TvTEvent;

/**
 * Automatic TvT Event Starter
 * Starts TvT events automatically at configured times
 */
public final class TvTAutoStart
{
	private static final CLogger LOGGER = new CLogger(TvTAutoStart.class.getName());
	private static ScheduledFuture<?> _task;
	private static Calendar _nextEvent;
	
	public static void initialize()
	{
		if (Config.TVT_EVENT_INTERVAL == null || Config.TVT_EVENT_INTERVAL.length == 0)
		{
			LOGGER.info("TvT Auto Start: No event intervals configured. Auto-start disabled.");
			return;
		}
		
		calculateAndScheduleNextEvent();
		LOGGER.info("TvT Auto Start: Initialized. Next event scheduled.");
	}
	
	private static void calculateAndScheduleNextEvent()
	{
		try
		{
			Calendar currentTime = Calendar.getInstance();
			Calendar testStartTime = null;
			long minTime = Long.MAX_VALUE;
			Calendar nextEventTime = null;
			
			for (String timeOfDay : Config.TVT_EVENT_INTERVAL)
			{
				testStartTime = Calendar.getInstance();
				testStartTime.setLenient(true);
				String[] splitTimeOfDay = timeOfDay.split(":");
				testStartTime.set(Calendar.HOUR_OF_DAY, Integer.parseInt(splitTimeOfDay[0]));
				testStartTime.set(Calendar.MINUTE, splitTimeOfDay.length > 1 ? Integer.parseInt(splitTimeOfDay[1]) : 0);
				testStartTime.set(Calendar.SECOND, 0);
				testStartTime.set(Calendar.MILLISECOND, 0);
				
				// If the time is in the past, schedule for next day
				if (testStartTime.getTimeInMillis() < currentTime.getTimeInMillis())
				{
					testStartTime.add(Calendar.DAY_OF_MONTH, 1);
				}
				
				long timeUntil = testStartTime.getTimeInMillis() - currentTime.getTimeInMillis();
				if (timeUntil < minTime)
				{
					minTime = timeUntil;
					nextEventTime = testStartTime;
				}
			}
			
			if (nextEventTime != null)
			{
				_nextEvent = nextEventTime;
				LOGGER.info("TvT Auto Start: Next event scheduled for " + 
					nextEventTime.get(Calendar.DAY_OF_MONTH) + "/" + 
					(nextEventTime.get(Calendar.MONTH) + 1) + " at " +
					String.format("%02d:%02d", nextEventTime.get(Calendar.HOUR_OF_DAY), nextEventTime.get(Calendar.MINUTE)));
				
				// Cancel previous task if exists
				if (_task != null)
				{
					_task.cancel(false);
				}
				
				// Schedule the event start
				_task = ThreadPool.schedule(new StartTvTTask(), minTime);
			}
		}
		catch (Exception e)
		{
			LOGGER.error("TvT Auto Start: Error calculating next event time", e);
		}
	}
	
	private static class StartTvTTask implements Runnable
	{
		@Override
		public void run()
		{
			try
			{
				// Check if event is already active
				if (TvTEvent.getInstance().getState() != EventState.INACTIVE)
				{
					LOGGER.warn("TvT Auto Start: Cannot start event, event is already active!");
					// Reschedule for next interval
					calculateAndScheduleNextEvent();
					return;
				}
				
				LOGGER.info("TvT Auto Start: Starting automatic TvT event...");
				TvTEvent.getInstance().startRegistration();
				World.announceToOnlinePlayers("TvT Event: Automatic event started! Registration is now open!", false);
				
				// Schedule next event
				calculateAndScheduleNextEvent();
			}
			catch (Exception e)
			{
				LOGGER.error("TvT Auto Start: Error starting event", e);
				// Reschedule for next interval even on error
				calculateAndScheduleNextEvent();
			}
		}
	}
	
	public static void shutdown()
	{
		if (_task != null)
		{
			_task.cancel(false);
			_task = null;
		}
	}
	
	public static Calendar getNextEventTime()
	{
		return _nextEvent;
	}
}

