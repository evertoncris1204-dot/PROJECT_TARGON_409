package net.sf.l2j.gameserver.model.entity.events;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.data.xml.PartyFarmData;
import net.sf.l2j.gameserver.model.holder.PTFarmConfig;

/**
 * @author Jhon
 */
public class CheckNextEvent 
{
	private final SimpleDateFormat format = new SimpleDateFormat("HH:mm");
	
	public String getNextTvTTime()
	{
		if (getNextTvTEventTime().getTime() != null)
			return format.format(getNextTvTEventTime().getTime());
		
		return "Erro";
	}
	
	public String getNextPartyZoneTime()
	{
		if (getNextPartyZoneventTime().getTime() != null)
			return format.format(getNextPartyZoneventTime().getTime());
		
		return "Erro";
	}
	
	public String getNextTournamentTime()
	{
		Calendar nextTournament = getNextTournamentEventTime();
		if (nextTournament != null && nextTournament.getTime() != null)
			return format.format(nextTournament.getTime());
		
		return "Erro";
	}
	
	public Calendar getNextTvTEventTime()
	{
		try
		{
			Calendar currentTime = Calendar.getInstance();
			Calendar nextStartTime = null;
			Calendar testStartTime = null;
			String[] tvtIntervals = Config.TVT_EVENT_INTERVAL != null ? Config.TVT_EVENT_INTERVAL : new String[]{"20:00"};
			for (String timeOfDay : tvtIntervals)
			{
				// Creating a Calendar object from the specified interval value
				testStartTime = Calendar.getInstance();
				testStartTime.setLenient(true);
				String[] splitTimeOfDay = timeOfDay.split(":");
				testStartTime.set(Calendar.HOUR_OF_DAY, Integer.parseInt(splitTimeOfDay[0]));
				testStartTime.set(Calendar.MINUTE, Integer.parseInt(splitTimeOfDay[1]));
				// If the date is in the past, make it the next day (Example: Checking for "1:00", when the time is 23:57.)
				if (testStartTime.getTimeInMillis() < currentTime.getTimeInMillis())
				{
					testStartTime.add(Calendar.DAY_OF_MONTH, 1);
				}
				// Check for the test date to be the minimum (smallest in the specified list)
				if ((nextStartTime == null) || (testStartTime.getTimeInMillis() < nextStartTime.getTimeInMillis()))
				{
					nextStartTime = testStartTime;
				}
			}
			
			return nextStartTime;
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return null;
		}
	}
	

	public Calendar getNextPartyZoneventTime()
	{
		try
		{
			PTFarmConfig config = PartyFarmData.getInstance().getConfig();
			if (config == null || !config.isEnabled())
				return null;
			
			Calendar currentTime = Calendar.getInstance();
			Calendar nextStartTime = null;
			Calendar testStartTime = null;
			
			List<Integer> allowedDays = config.getDays();
			List<String> times = config.getTimes();
			
			// Check today and next 7 days
			for (int dayOffset = 0; dayOffset < 7; dayOffset++)
			{
				Calendar testDay = Calendar.getInstance();
				testDay.add(Calendar.DAY_OF_MONTH, dayOffset);
				int dayOfWeek = testDay.get(Calendar.DAY_OF_WEEK) - 1; // 0 = Sunday, 1 = Monday, etc.
				
				// Skip if this day is not allowed
				if (!allowedDays.contains(dayOfWeek))
					continue;
				
				// Check all times for this day
				for (String timeOfDay : times)
				{
					testStartTime = Calendar.getInstance();
					testStartTime.setLenient(true);
					testStartTime.add(Calendar.DAY_OF_MONTH, dayOffset);
					
					String[] splitTimeOfDay = timeOfDay.split(":");
					testStartTime.set(Calendar.HOUR_OF_DAY, Integer.parseInt(splitTimeOfDay[0].trim()));
					testStartTime.set(Calendar.MINUTE, Integer.parseInt(splitTimeOfDay[1].trim()));
					testStartTime.set(Calendar.SECOND, 0);
					testStartTime.set(Calendar.MILLISECOND, 0);
					
					// If the time is in the past and it's today, skip (already happened today)
					if (dayOffset == 0 && testStartTime.getTimeInMillis() < currentTime.getTimeInMillis())
						continue;
					
					// Check for the test date to be the minimum (smallest in the specified list)
					if ((nextStartTime == null) || (testStartTime.getTimeInMillis() < nextStartTime.getTimeInMillis()))
					{
						nextStartTime = (Calendar) testStartTime.clone();
					}
				}
			}
			
			return nextStartTime;
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return null;
		}
	}
	
	public Calendar getNextTournamentEventTime()
	{
		try
		{
			net.sf.l2j.gameserver.model.entity.Tournament.TournamentManager tournamentManager = net.sf.l2j.gameserver.model.entity.Tournament.TournamentManager.getInstance();
			if (tournamentManager == null)
				return null;
			
			// Get the next event time from TournamentManager
			return tournamentManager.getNextEventTime();
		}
		catch (Exception e)
		{
			// Fallback: calculate manually using Config
			try
			{
				Calendar currentTime = Calendar.getInstance();
				Calendar nextStartTime = null;
				Calendar testStartTime = null;
				String[] tournamentIntervals = Config.TOURNAMENT_EVENT_INTERVAL_BY_TIME_OF_DAY != null ? Config.TOURNAMENT_EVENT_INTERVAL_BY_TIME_OF_DAY : new String[]{"20:00"};
				for (String timeOfDay : tournamentIntervals)
				{
					testStartTime = Calendar.getInstance();
					testStartTime.setLenient(true);
					String[] splitTimeOfDay = timeOfDay.split(":");
					testStartTime.set(Calendar.HOUR_OF_DAY, Integer.parseInt(splitTimeOfDay[0]));
					testStartTime.set(Calendar.MINUTE, Integer.parseInt(splitTimeOfDay[1]));
					testStartTime.set(Calendar.SECOND, 0);
					testStartTime.set(Calendar.MILLISECOND, 0);
					// If the date is in the past, make it the next day
					if (testStartTime.getTimeInMillis() < currentTime.getTimeInMillis())
					{
						testStartTime.add(Calendar.DAY_OF_MONTH, 1);
					}
					// Check for the test date to be the minimum (smallest in the specified list)
					if ((nextStartTime == null) || (testStartTime.getTimeInMillis() < nextStartTime.getTimeInMillis()))
					{
						nextStartTime = testStartTime;
					}
				}
				
				return nextStartTime;
			}
			catch (Exception e2)
			{
				e2.printStackTrace();
				return null;
			}
		}
	}
	
	
	public static CheckNextEvent getInstance()
	{
		return SingletonHolder.INSTANCE;
	}

	private static class SingletonHolder
	{
		protected static final CheckNextEvent INSTANCE = new CheckNextEvent();
	}
}

