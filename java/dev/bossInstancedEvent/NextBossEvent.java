package dev.bossInstancedEvent;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.concurrent.ScheduledFuture;

import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.commons.pool.ThreadPool;
import net.sf.l2j.Config;

public class NextBossEvent
{
	private static final CLogger LOGGER = new CLogger(NextBossEvent.class.getName());
	private Calendar nextEvent;
	private final SimpleDateFormat format = new SimpleDateFormat("HH:mm");
	public ScheduledFuture<?> task = null;
	
	public static NextBossEvent getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private static class SingletonHolder
	{
		protected static final NextBossEvent _instance = new NextBossEvent();
	}
	
	public String getNextTime()
	{
		if (nextEvent != null && nextEvent.getTime() != null)
		{
			return format.format(nextEvent.getTime());
		}
		return "Erro";
	}
	
	public void startCalculationOfNextEventTime()
	{
		try
		{
			Calendar currentTime = Calendar.getInstance();
			Calendar testStartTime = null;
			long flush2 = 0L;
			long timeL = 0L;
			int count = 0;
			for (String timeOfDay : Config.BOSS_EVENT_BY_TIME_OF_DAY)
			{
				testStartTime = Calendar.getInstance();
				testStartTime.setLenient(true);
				String[] splitTimeOfDay = timeOfDay.split(":");
				testStartTime.set(Calendar.HOUR_OF_DAY, Integer.parseInt(splitTimeOfDay[0]));
				testStartTime.set(Calendar.MINUTE, Integer.parseInt(splitTimeOfDay[1]));
				testStartTime.set(Calendar.SECOND, 0);
				if (testStartTime.getTimeInMillis() < currentTime.getTimeInMillis())
				{
					testStartTime.add(Calendar.DAY_OF_MONTH, 1);
				}
				timeL = testStartTime.getTimeInMillis() - currentTime.getTimeInMillis();
				if (count == 0)
				{
					flush2 = timeL;
					nextEvent = testStartTime;
				}
				if (timeL < flush2)
				{
					flush2 = timeL;
					nextEvent = testStartTime;
				}
				count++;
			}
			LOGGER.info("[Boss Event]: Next Event Time -> " + nextEvent.getTime().toString());
			ThreadPool.schedule(new StartEventTask(), flush2);
		}
		catch (Exception e)
		{
			LOGGER.warn("[Boss Event]: Error calculating next event time", e);
		}
	}
	
	class StartEventTask implements Runnable
	{
		@Override
		public void run()
		{
			LOGGER.info("----------------------------------------------------------------------------");
			LOGGER.info("[Boss Event]: Event Started.");
			LOGGER.info("----------------------------------------------------------------------------");
			BossEvent.getInstance().startRegistration();
		}
	}
}

