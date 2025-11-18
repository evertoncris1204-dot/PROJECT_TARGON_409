package dev.tvtEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import net.sf.l2j.Config;
import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.commons.pool.ThreadPool;
import net.sf.l2j.commons.random.Rnd;
import net.sf.l2j.gameserver.data.manager.SpawnManager;
import net.sf.l2j.gameserver.data.xml.NpcData;
import net.sf.l2j.gameserver.enums.EventState;
import net.sf.l2j.gameserver.enums.SayType;
import net.sf.l2j.gameserver.enums.TeamType;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.model.location.Location;
import net.sf.l2j.gameserver.model.location.SpawnLocation;
import net.sf.l2j.gameserver.model.spawn.Spawn;
import net.sf.l2j.gameserver.model.spawn.SpawnData;
import net.sf.l2j.gameserver.network.serverpackets.CreatureSay;
import net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage;

/**
 * Team vs Team Event
 * @author Adapted from aCis Events
 */
public class TvTEvent
{
	private static final CLogger LOGGER = new CLogger(TvTEvent.class.getName());
	
	private static TvTEvent _instance;
	
	public Spawn eventNpc = null;
	public List<Player> eventPlayers = new ArrayList<>();
	public List<Player> teamBlue = new ArrayList<>();
	public List<Player> teamRed = new ArrayList<>();
	
	private EventState state = EventState.INACTIVE;
	public int blueScore = 0;
	public int redScore = 0;
	private int matchTime = 0;
	private ScheduledFuture<?> matchTask = null;
	private ScheduledFuture<?> countDownTask = null;
	
	private Map<Integer, Integer> winnerRewards = new HashMap<>();
	private Map<Integer, Integer> loserRewards = new HashMap<>();
	
	// K/D tracking
	private Map<Player, Integer> playerKills = new HashMap<>();
	private Map<Player, Integer> playerDeaths = new HashMap<>();
	private Map<Player, String> originalTitles = new HashMap<>();
	private Map<Player, Integer> originalKarma = new HashMap<>();
	private Map<Player, Byte> originalPvpFlags = new HashMap<>();
	private ScheduledFuture<?> pvpFlagCleanupTask = null;
	
	public static TvTEvent getInstance()
	{
		if (_instance == null)
			_instance = new TvTEvent();
		return _instance;
	}
	
	private TvTEvent()
	{
		// Register voiced command handler
		TvTEventCMD handler = new TvTEventCMD();
		net.sf.l2j.gameserver.handler.VoicedCommandHandler.getInstance().registerHandler(handler);
		
		LOGGER.info("TvT Event loaded.");
	}
	
	public void initialize()
	{
		// Load rewards after Config is loaded
		loadRewards();
	}
	
	public EventState getState()
	{
		return state;
	}
	
	public void setState(EventState newState)
	{
		state = newState;
	}
	
	public boolean addPlayer(Player player)
	{
		if (eventPlayers.add(player))
		{
			applyEventEffect(player);
			return true;
		}
		return false;
	}
	
	public boolean removePlayer(Player player)
	{
		if (eventPlayers.remove(player))
		{
			// Restore title immediately when player leaves event
			String originalTitle = originalTitles.get(player);
			if (originalTitle != null)
			{
				player.setTitle(originalTitle);
				player.broadcastUserInfo();
			}
			else
			{
				// Clear K/D title if present
				String currentTitle = player.getTitle();
				if (currentTitle != null && (currentTitle.startsWith("K:") || currentTitle.contains("D:")))
				{
					player.setTitle("");
					player.broadcastUserInfo();
				}
			}
			
			removeEventEffect(player);
			teamBlue.remove(player);
			teamRed.remove(player);
			return true;
		}
		return false;
	}
	
	public boolean isRegistered(Player player)
	{
		return eventPlayers.contains(player);
	}
	
	public int getTeam(Player player)
	{
		if (teamBlue.contains(player))
			return 1; // Blue
		if (teamRed.contains(player))
			return 2; // Red
		return 0;
	}
	
	/**
	 * Check if two players are enemies in the TvT event
	 * @param attacker The attacking player
	 * @param target The target player
	 * @return true if they are enemies (different teams)
	 */
	public boolean areEnemies(Player attacker, Player target)
	{
		if (state != EventState.STARTED)
			return false;
		
		if (!isRegistered(attacker) || !isRegistered(target))
			return false;
		
		int attackerTeam = getTeam(attacker);
		int targetTeam = getTeam(target);
		
		return attackerTeam > 0 && targetTeam > 0 && attackerTeam != targetTeam;
	}
	
	private void applyEventEffect(Player player)
	{
		if (player != null && player.isOnline())
		{
			// Store original title, karma and PvP flag (only if not already stored)
			if (!originalTitles.containsKey(player))
			{
				String currentTitle = player.getTitle();
				originalTitles.put(player, currentTitle != null ? currentTitle : "");
			}
			if (!originalKarma.containsKey(player))
			{
				originalKarma.put(player, player.getKarma());
			}
			if (!originalPvpFlags.containsKey(player))
			{
				originalPvpFlags.put(player, player.getPvpFlag());
			}
			
			// Set karma to 0 to prevent PK during event
			player.setKarma(0);
			
			// Remove PvP flag to prevent flag during event
			if (player.getPvpFlag() > 0)
			{
				player.setPvpFlag(0);
				net.sf.l2j.gameserver.taskmanager.PvpFlagTaskManager.getInstance().remove(player, true);
				player.broadcastUserInfo();
			}
			
			// Initialize K/D
			playerKills.put(player, 0);
			playerDeaths.put(player, 0);
			// Update title with K/D
			updatePlayerTitle(player);
		}
	}
	
	private void removeEventEffect(Player player)
	{
		if (player != null && player.isOnline())
		{
			// Restore original title (get without removing first)
			String originalTitle = originalTitles.get(player);
			if (originalTitle != null)
			{
				player.setTitle(originalTitle);
			}
			else
			{
				// If title wasn't stored, clear the K/D title
				String currentTitle = player.getTitle();
				if (currentTitle != null && (currentTitle.startsWith("K:") || currentTitle.contains("D:")))
				{
					player.setTitle("");
				}
			}
			
			// Restore original karma
			Integer originalKarmaValue = originalKarma.get(player);
			if (originalKarmaValue != null)
			{
				player.setKarma(originalKarmaValue);
			}
			
			// Restore original PvP flag
			Byte originalPvpFlag = originalPvpFlags.get(player);
			if (originalPvpFlag != null)
			{
				// Remove any active PvP flag task first
				net.sf.l2j.gameserver.taskmanager.PvpFlagTaskManager.getInstance().remove(player, true);
				// Restore original flag value
				player.setPvpFlag(originalPvpFlag);
			}
			else
			{
				// If flag wasn't stored, ensure it's removed
				if (player.getPvpFlag() > 0)
				{
					net.sf.l2j.gameserver.taskmanager.PvpFlagTaskManager.getInstance().remove(player, true);
					player.setPvpFlag(0);
				}
			}
			
			player.setTeam(TeamType.NONE);
			player.broadcastUserInfo();
		}
		
		// Clean up tracking maps (remove after restoring)
		originalTitles.remove(player);
		originalKarma.remove(player);
		originalPvpFlags.remove(player);
		playerKills.remove(player);
		playerDeaths.remove(player);
	}
	
	private void updatePlayerTitle(Player player)
	{
		if (player != null && player.isOnline() && isRegistered(player))
		{
			int kills = playerKills.getOrDefault(player, 0);
			int deaths = playerDeaths.getOrDefault(player, 0);
			String kdTitle = "K:" + kills + " D:" + deaths;
			player.setTitle(kdTitle);
			player.broadcastUserInfo();
		}
	}
	
	public void applyEventEffectsToAll()
	{
		for (Player player : eventPlayers)
		{
			applyEventEffect(player);
		}
	}
	
	public void removeEventEffectsFromAll()
	{
		for (Player player : eventPlayers)
		{
			removeEventEffect(player);
		}
	}
	
	public void startRegistration()
	{
		if (state != EventState.INACTIVE)
		{
			LOGGER.warn("TvT Event: Cannot start registration, event is already active!");
			return;
		}
		
		setState(EventState.PARTICIPATING);
		spawnEventNpc();
		announce("TvT Event registration started! Use .tvt or visit the Event Manager to register.", false);
		announce("Registration will last " + Config.TVT_EVENT_REGISTRATION_TIME / 60 + " minutes.", false);
		
		ThreadPool.schedule(new Registration(), Config.TVT_EVENT_REGISTRATION_TIME * 1000);
	}
	
	private void spawnEventNpc()
	{
		try
		{
			NpcTemplate template = NpcData.getInstance().getTemplate(Config.TVT_EVENT_REGISTRATION_NPC_ID);
			if (template == null)
			{
				LOGGER.warn("TvT Event: Registration NPC template not found! NPC ID: " + Config.TVT_EVENT_REGISTRATION_NPC_ID);
				return;
			}
			
			Spawn spawn = new Spawn(template);
			spawn.setLoc(Config.TVT_EVENT_NPC_REGISTER_LOC.getX(), Config.TVT_EVENT_NPC_REGISTER_LOC.getY(), Config.TVT_EVENT_NPC_REGISTER_LOC.getZ(), 0);
			spawn.setRespawnDelay(0);
			
			// Create SpawnData using reflection
			try
			{
				java.lang.reflect.Field spawnDataField = net.sf.l2j.gameserver.model.spawn.ASpawn.class.getDeclaredField("_spawnData");
				spawnDataField.setAccessible(true);
				SpawnData spawnData = new SpawnData("TvTEvent_" + Config.TVT_EVENT_REGISTRATION_NPC_ID);
				spawnData.set(Config.TVT_EVENT_NPC_REGISTER_LOC.getX(), Config.TVT_EVENT_NPC_REGISTER_LOC.getY(), Config.TVT_EVENT_NPC_REGISTER_LOC.getZ(), 0);
				spawnData.setStatus((byte) 1);
				spawnData.setDBValue(0);
				spawnDataField.set(spawn, spawnData);
			}
			catch (Exception e)
			{
				LOGGER.warn("TvT Event: Could not set SpawnData: " + e.getMessage());
			}
			
			spawn.doSpawn(false, null);
			if (spawn.getNpc() == null)
			{
				LOGGER.warn("TvT Event: Failed to spawn registration NPC.");
				return;
			}
			
			eventNpc = spawn;
		}
		catch (Exception e)
		{
			LOGGER.warn("TvT Event: Failed to spawn registration NPC: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	private void despawnNpc(Spawn spawn)
	{
		if (spawn != null && spawn.getNpc() != null)
		{
			spawn.getNpc().deleteMe();
			spawn.doDelete();
		}
	}
	
	class Registration implements Runnable
	{
		@Override
		public void run()
		{
			if (eventPlayers.size() < Config.TVT_EVENT_MIN_PLAYERS)
			{
				announce("TvT Event was cancelled due to lack of participation! Minimum players: " + Config.TVT_EVENT_MIN_PLAYERS, false);
				removeEventEffectsFromAll();
				setState(EventState.INACTIVE);
				despawnNpc(eventNpc);
				eventPlayers.clear();
				return;
			}
			
		// Divide players into teams
		divideIntoTeams();
		
		// Initialize K/D for all players
		for (Player player : eventPlayers)
		{
			playerKills.put(player, 0);
			playerDeaths.put(player, 0);
			originalTitles.put(player, player.getTitle());
			updatePlayerTitle(player);
		}
		
		announce("TvT Event: Teleporting players in " + Config.TVT_EVENT_TIME_TO_TELEPORT_PLAYERS + " seconds!", false);
		setState(EventState.STARTING);
		startCountDown(Config.TVT_EVENT_TIME_TO_TELEPORT_PLAYERS, true);
		
		ThreadPool.schedule(new Teleporting(), Config.TVT_EVENT_TIME_TO_TELEPORT_PLAYERS * 1000);
		}
	}
	
	private void divideIntoTeams()
	{
		teamBlue.clear();
		teamRed.clear();
		
		List<Player> tempList = new ArrayList<>(eventPlayers);
		
		// Shuffle players
		while (!tempList.isEmpty())
		{
			Player player = tempList.remove(Rnd.get(tempList.size()));
			
			// Balance teams
			if (teamBlue.size() <= teamRed.size())
				teamBlue.add(player);
			else
				teamRed.add(player);
		}
		
		// Set team colors (but no visual effect)
		for (Player player : teamBlue)
		{
			player.getAppearance().setNameColor(0, 0, 255); // Blue
			// Don't set team to avoid blue effect
		}
		
		for (Player player : teamRed)
		{
			player.getAppearance().setNameColor(255, 0, 0); // Red
			// Don't set team to avoid blue effect
		}
	}
	
	class Teleporting implements Runnable
	{
		@Override
		public void run()
		{
			despawnNpc(eventNpc);
			applyEventEffectsToAll();
			
			// Teleport Blue team
			Location blueLoc = Config.TVT_EVENT_BLUE_LOCATION.get(Rnd.get(Config.TVT_EVENT_BLUE_LOCATION.size()));
			for (Player player : teamBlue)
			{
				player.teleportTo(blueLoc, 0);
				player.getStatus().setMaxCpHpMp();
				// Block movement until match starts
				player.setIsImmobilized(true);
				player.abortAll(true);
			}
			
			// Teleport Red team
			Location redLoc = Config.TVT_EVENT_RED_LOCATION.get(Rnd.get(Config.TVT_EVENT_RED_LOCATION.size()));
			for (Player player : teamRed)
			{
				player.teleportTo(redLoc, 0);
				player.getStatus().setMaxCpHpMp();
				// Block movement until match starts
				player.setIsImmobilized(true);
				player.abortAll(true);
			}
			
			announce("TvT Event: Match starting in " + Config.TVT_EVENT_TIME_TO_WAIT + " seconds!", false);
			startCountDown(Config.TVT_EVENT_TIME_TO_WAIT, true);
			
			ThreadPool.schedule(new Fighting(), Config.TVT_EVENT_TIME_TO_WAIT * 1000);
		}
	}
	
	class Fighting implements Runnable
	{
		@Override
		public void run()
		{
			setState(EventState.STARTED);
			matchTime = Config.TVT_EVENT_MATCH_TIME;
			blueScore = 0;
			redScore = 0;
			
			// Unblock movement for all players and ensure no PvP flag
			for (Player player : eventPlayers)
			{
				if (player != null && player.isOnline())
				{
					player.setIsImmobilized(false);
					// Remove PvP flag if it exists
					if (player.getPvpFlag() > 0)
					{
						player.setPvpFlag(0);
						net.sf.l2j.gameserver.taskmanager.PvpFlagTaskManager.getInstance().remove(player, true);
					}
					player.broadcastUserInfo();
				}
			}
			
			// Update relations so players can attack each other without Control
			for (Player player : eventPlayers)
			{
				if (player != null && player.isOnline())
				{
					player.broadcastRelationsChanges();
				}
			}
			
			// Start periodic PvP flag cleanup task
			startPvpFlagCleanup();
			
			announce("TvT Event: Match started! Kill enemies to score points!", false);
			announce("Blue Team: " + teamBlue.size() + " players | Red Team: " + teamRed.size() + " players", false);
			
			// Start match timer
			matchTask = ThreadPool.schedule(new MatchTimer(), 1000);
		}
	}
	
	/**
	 * Starts a periodic task to remove PvP flags from all event players
	 */
	private void startPvpFlagCleanup()
	{
		if (pvpFlagCleanupTask != null)
			pvpFlagCleanupTask.cancel(false);
		
		pvpFlagCleanupTask = ThreadPool.schedule(new PvpFlagCleanup(), 1000);
	}
	
	/**
	 * Periodic task to remove PvP flags during the event
	 */
	class PvpFlagCleanup implements Runnable
	{
		@Override
		public void run()
		{
			if (state == EventState.STARTED)
			{
				// Remove PvP flag from all event players
				for (Player player : eventPlayers)
				{
					if (player != null && player.isOnline() && isRegistered(player))
					{
						if (player.getPvpFlag() > 0)
						{
							player.setPvpFlag(0);
							net.sf.l2j.gameserver.taskmanager.PvpFlagTaskManager.getInstance().remove(player, true);
							player.broadcastUserInfo();
						}
					}
				}
				
				// Schedule next cleanup in 2 seconds
				pvpFlagCleanupTask = ThreadPool.schedule(this, 2000);
			}
		}
	}
	
	class MatchTimer implements Runnable
	{
		@Override
		public void run()
		{
			if (matchTime > 0)
			{
				// Update scorebar every 30 seconds
				if (matchTime % 30 == 0 || matchTime <= 10)
				{
					updateScorebar();
				}
				
				matchTime--;
				matchTask = ThreadPool.schedule(this, 1000);
			}
			else
			{
				finishEvent();
			}
		}
	}
	
	private void updateScorebar()
	{
		String scorebar = "Blue: " + blueScore + " | Red: " + redScore + " | Time: " + (matchTime / 60) + ":" + String.format("%02d", matchTime % 60);
		
		for (Player player : eventPlayers)
		{
			player.sendPacket(new ExShowScreenMessage(scorebar, 3000, ExShowScreenMessage.SMPOS.TOP_CENTER, false));
		}
	}
	
	public void onKill(Player killer, Player victim)
	{
		if (state != EventState.STARTED)
			return;
		
		if (!isRegistered(killer) || !isRegistered(victim))
			return;
		
		int killerTeam = getTeam(killer);
		int victimTeam = getTeam(victim);
		
		// Only score if killing enemy team member
		if (killerTeam != victimTeam && killerTeam > 0 && victimTeam > 0)
		{
			// Remove PvP flag from killer immediately
			if (killer.getPvpFlag() > 0)
			{
				killer.setPvpFlag(0);
				net.sf.l2j.gameserver.taskmanager.PvpFlagTaskManager.getInstance().remove(killer, true);
				killer.broadcastUserInfo();
			}
			
			// Update K/D
			playerKills.put(killer, playerKills.getOrDefault(killer, 0) + 1);
			playerDeaths.put(victim, playerDeaths.getOrDefault(victim, 0) + 1);
			
			// Update titles
			updatePlayerTitle(killer);
			updatePlayerTitle(victim);
			
			if (killerTeam == 1) // Blue
			{
				blueScore++;
				announce("Blue Team scored! Blue: " + blueScore + " | Red: " + redScore, false);
			}
			else if (killerTeam == 2) // Red
			{
				redScore++;
				announce("Red Team scored! Blue: " + blueScore + " | Red: " + redScore, false);
			}
			
			// Resurrect victim after configured delay
			ThreadPool.schedule(new ResurrectTask(victim), Config.TVT_EVENT_RESURRECT_TIME * 1000);
		}
	}
	
	class ResurrectTask implements Runnable
	{
		private Player player;
		
		public ResurrectTask(Player player)
		{
			this.player = player;
		}
		
		@Override
		public void run()
		{
			if (player != null && player.isOnline() && isRegistered(player))
			{
				if (player.isDead())
				{
					player.doRevive();
					double maxHp = player.getStatus().getMaxHp();
					double maxMp = player.getStatus().getMaxMp();
					double maxCp = player.getStatus().getMaxCp();
					player.getStatus().setCpHpMp(maxCp * 0.5, maxHp * 0.5, maxMp * 0.5);
					
					// Teleport back to team position (prevents "to village" option)
					int team = getTeam(player);
					if (team == 1) // Blue
					{
						Location loc = Config.TVT_EVENT_BLUE_LOCATION.get(Rnd.get(Config.TVT_EVENT_BLUE_LOCATION.size()));
						player.teleportTo(loc, 0);
					}
					else if (team == 2) // Red
					{
						Location loc = Config.TVT_EVENT_RED_LOCATION.get(Rnd.get(Config.TVT_EVENT_RED_LOCATION.size()));
						player.teleportTo(loc, 0);
					}
					
					// Ensure karma is still 0 to prevent PK
					player.setKarma(0);
					
					// Remove PvP flag if it exists
					if (player.getPvpFlag() > 0)
					{
						player.setPvpFlag(0);
						net.sf.l2j.gameserver.taskmanager.PvpFlagTaskManager.getInstance().remove(player, true);
						player.broadcastUserInfo();
					}
				}
			}
		}
	}
	
	public void finishEvent()
	{
		if (matchTask != null)
		{
			matchTask.cancel(false);
			matchTask = null;
		}
		
		// Stop PvP flag cleanup task
		if (pvpFlagCleanupTask != null)
		{
			pvpFlagCleanupTask.cancel(false);
			pvpFlagCleanupTask = null;
		}
		
		setState(EventState.REWARDING);
		
		// Immobilize all players to prevent fighting during teleport delay
		for (Player player : eventPlayers)
		{
			if (player != null && player.isOnline())
			{
				player.setIsImmobilized(true);
				player.abortAll(true);
				player.sendPacket(new ExShowScreenMessage("Event ended! Teleporting back in 10 seconds...", 10000, ExShowScreenMessage.SMPOS.TOP_CENTER, false));
			}
		}
		
		// Determine winner
		int winnerTeam = 0;
		if (blueScore > redScore)
			winnerTeam = 1; // Blue
		else if (redScore > blueScore)
			winnerTeam = 2; // Red
		
		if (winnerTeam == 0)
		{
			announce("TvT Event: Match ended in a tie! Blue: " + blueScore + " | Red: " + redScore, false);
			// Reward both teams equally
			for (Player player : eventPlayers)
			{
				reward(player, winnerRewards);
			}
		}
		else
		{
			String winnerName = winnerTeam == 1 ? "Blue" : "Red";
			announce("TvT Event: " + winnerName + " Team won! Blue: " + blueScore + " | Red: " + redScore, false);
			
			// Reward winners
			List<Player> winners = winnerTeam == 1 ? teamBlue : teamRed;
			for (Player player : winners)
			{
				reward(player, winnerRewards);
				player.sendPacket(new CreatureSay(0, SayType.CRITICAL_ANNOUNCE, "[TvT Event]", "Congratulations! Your team won!"));
			}
			
			// Reward losers
			List<Player> losers = winnerTeam == 1 ? teamRed : teamBlue;
			for (Player player : losers)
			{
				reward(player, loserRewards);
			}
		}
		
		// Teleport back after delay
		ThreadPool.schedule(new TeleportBack(), 10000);
	}
	
	class TeleportBack implements Runnable
	{
		@Override
		public void run()
		{
			// Create a copy of the list to avoid concurrent modification
			List<Player> playersToTeleport = new ArrayList<>(eventPlayers);
			
			// Restore all effects (including titles) before teleporting
			removeEventEffectsFromAll();
			
			for (Player player : playersToTeleport)
			{
				if (player != null && player.isOnline())
				{
					// Ensure title is restored and not K/D title
					String currentTitle = player.getTitle();
					if (currentTitle != null && (currentTitle.startsWith("K:") || currentTitle.contains("D:")))
					{
						// Try to get original title from map (if still there)
						String originalTitle = originalTitles.get(player);
						if (originalTitle != null)
						{
							player.setTitle(originalTitle);
						}
						else
						{
							// Clear K/D title if no original found
							player.setTitle("");
						}
					}
					
					player.teleportTo(Config.TVT_EVENT_RETURN_LOCATION, 0);
					player.getAppearance().setNameColor(-1, -1, -1); // Reset color
					player.setTeam(TeamType.NONE);
					// Unblock movement after teleport
					player.setIsImmobilized(false);
					player.abortAll(true);
					player.broadcastUserInfo();
				}
			}
			
			// Clean up K/D tracking (should be empty already, but ensure cleanup)
			playerKills.clear();
			playerDeaths.clear();
			originalTitles.clear();
			originalKarma.clear();
			originalPvpFlags.clear();
			
			eventPlayers.clear();
			teamBlue.clear();
			teamRed.clear();
			setState(EventState.INACTIVE);
		}
	}
	
	private void reward(Player player, Map<Integer, Integer> rewards)
	{
		for (Map.Entry<Integer, Integer> entry : rewards.entrySet())
		{
			player.addItem(entry.getKey(), entry.getValue(), true);
		}
	}
	
	private void startCountDown(int time, boolean announce)
	{
		if (countDownTask != null)
			countDownTask.cancel(false);
		
		countDownTask = ThreadPool.schedule(new CountDown(time, announce), 1000);
	}
	
	class CountDown implements Runnable
	{
		private int time;
		private boolean announce;
		
		public CountDown(int time, boolean announce)
		{
			this.time = time;
			this.announce = announce;
		}
		
		@Override
		public void run()
		{
			if (time > 0)
			{
				if (announce && (time <= 10 || time % 30 == 0))
				{
					announce("TvT Event: " + time + " second(s) remaining!", false);
				}
				
				time--;
				countDownTask = ThreadPool.schedule(this, 1000);
			}
		}
	}
	
	private void announce(String text, boolean critical)
	{
		SayType sayType = critical ? SayType.CRITICAL_ANNOUNCE : SayType.ANNOUNCEMENT;
		CreatureSay cs = new CreatureSay(0, sayType, "[TvT Event]", text);
		for (Player player : World.getInstance().getPlayers())
		{
			player.sendPacket(cs);
		}
	}
	
	public void loadRewards()
	{
		winnerRewards.clear();
		loserRewards.clear();
		
		// Load winner rewards
		for (String reward : Config.TVT_EVENT_WINNER_REWARDS.split(";"))
		{
			String[] parts = reward.split(",");
			if (parts.length == 2)
			{
				winnerRewards.put(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
			}
		}
		
		// Load loser rewards
		for (String reward : Config.TVT_EVENT_LOSER_REWARDS.split(";"))
		{
			String[] parts = reward.split(",");
			if (parts.length == 2)
			{
				loserRewards.put(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
			}
		}
	}
}

