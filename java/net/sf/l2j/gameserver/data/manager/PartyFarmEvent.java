package net.sf.l2j.gameserver.data.manager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;

import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.commons.pool.ThreadPool;

import net.sf.l2j.gameserver.data.xml.NpcData;
import net.sf.l2j.gameserver.data.xml.PartyFarmData;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.model.holder.PTFarmConfig;
import net.sf.l2j.gameserver.model.holder.PTFarmHolder;
import net.sf.l2j.gameserver.model.spawn.Spawn;

public class PartyFarmEvent
{
	private static final CLogger LOGGER = new CLogger(PartyFarmEvent.class.getName());
	
	private static ScheduledFuture<?> _eventChecker;
	private static boolean _isRunning;
	private static final List<Spawn> _activeSpawns = Collections.synchronizedList(new ArrayList<>());
	private static final Set<Integer> _partyFarmNpcIds = Collections.synchronizedSet(new HashSet<>());
	private static String _lastEventTime;
	
	public static void start()
	{
		if (_eventChecker == null || _eventChecker.isCancelled())
			_eventChecker = ThreadPool.scheduleAtFixedRate(PartyFarmEvent::checkAndStartEvent, 500, 1000);
	}
	
	private static void checkAndStartEvent()
	{
		Calendar cal = Calendar.getInstance();
		int currentDay = cal.get(Calendar.DAY_OF_WEEK) - 1; // 0 = domingo, 1 = segunda, etc.
		
		PTFarmConfig config = PartyFarmData.getInstance().getConfig();
		
		if (config == null || !config.isEnabled() || !config.getDays().contains(currentDay))
			return;
		
		Date now = new Date();
		String nowStr = new SimpleDateFormat("HH:mm").format(now);
		
		for (String time : config.getTimes())
		{
			if (nowStr.equals(time) && !_isRunning && !nowStr.equals(_lastEventTime))
			{
				_isRunning = true;
				_lastEventTime = nowStr;
				World.announceToOnlinePlayers("The Party Farm will start in " + config.getPreparation() + " minutes!", true);
				ThreadPool.schedule(PartyFarmEvent::spawnMobs, 1000L * 60 * config.getPreparation());
				unSpawn();
				break;
			}
		}
	}
	
	private static void spawnMobs()
	{
		World.announceToOnlinePlayers("The Party Farm has started! Good drops!", true);
		
		List<PTFarmHolder> spawns = PartyFarmData.getInstance().getSpawns("partyfarm");
		PTFarmConfig config = PartyFarmData.getInstance().getConfig();
		
		for (PTFarmHolder holder : spawns)
		{
			try
			{
				final NpcTemplate template = NpcData.getInstance().getTemplate(holder.getNpcId());
				if (template == null)
				{
					LOGGER.warn("Template not found for npcId: {}", holder.getNpcId());
					continue;
				}
				
				Spawn spawn = new Spawn(template);
				
				// Use exact location from XML
				spawn.setLoc(holder.getX(), holder.getY(), holder.getZ(), 0);
				
				// Set respawn configuration
				spawn.setRespawnDelay(config.getRespawnDelay());
				spawn.setRespawnRandom(config.getRespawnRandom());
				
				Npc npc = spawn.doSpawn(false, null);
				if (npc != null)
				{
					// Add ObjectId for legacy compatibility
					_partyFarmNpcIds.add(npc.getObjectId());
					// Add spawn to active spawns list (this is the main way to identify Party Farm NPCs)
					_activeSpawns.add(spawn);
				}
			}
			catch (Exception e)
			{
				LOGGER.error("Failed to spawn NPC for Party Farm event.", e);
			}
		}
		
		World.announceToOnlinePlayers("The Party Farm event ends in " + config.getDuration() + " minutes!", true);
		ThreadPool.schedule(PartyFarmEvent::endEvent, 1000L * 60 * config.getDuration());
	}
	
	private static void endEvent()
	{
		World.announceToOnlinePlayers("The Party Farm event has ended!", false);
		unSpawn();
		_activeSpawns.clear();
		_partyFarmNpcIds.clear();
		_isRunning = false;
		_lastEventTime = "";
	}
	
	private static void unSpawn()
	{
		for (Spawn spawn : _activeSpawns)
		{
			if (spawn != null)
			{
				try
				{
					spawn.doDelete();
				}
				catch (Exception e)
				{
					LOGGER.error("Failed to delete spawn.", e);
				}
			}
		}
	}
	
	public static boolean isRunning()
	{
		return _isRunning;
	}
	
	public static String lastEvent()
	{
		return _lastEventTime;
	}
	
	public static void reset()
	{
		if (_eventChecker != null)
		{
			_eventChecker.cancel(false);
			_eventChecker = null;
		}
		unSpawn();
		_activeSpawns.clear();
		_partyFarmNpcIds.clear();
		_isRunning = false;
		_lastEventTime = "";
	}
	
	/**
	 * Check if an NPC is from the Party Farm event.
	 * @param npc The NPC to check.
	 * @return True if the NPC is from Party Farm event.
	 */
	public static boolean isPartyFarmNpc(Npc npc)
	{
		if (npc == null || npc.getSpawn() == null)
			return false;
		
		// Check if the spawn is in our active spawns list
		return _activeSpawns.contains(npc.getSpawn());
	}
	
	/**
	 * Check if an NPC ObjectId is from the Party Farm event (legacy method for compatibility).
	 * @param npcId The ObjectId of the NPC to check.
	 * @return True if the NPC is from Party Farm event.
	 */
	public static boolean isPartyFarmNpc(int npcId)
	{
		return _partyFarmNpcIds.contains(npcId);
	}
}

