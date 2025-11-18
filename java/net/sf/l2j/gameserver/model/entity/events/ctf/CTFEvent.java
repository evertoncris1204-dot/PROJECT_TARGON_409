package net.sf.l2j.gameserver.model.entity.events.ctf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.commons.random.Rnd;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.data.cache.HtmCache;
import net.sf.l2j.gameserver.data.manager.SpawnManager;
import net.sf.l2j.gameserver.data.xml.DoorData;
import net.sf.l2j.gameserver.data.xml.NpcData;
import net.sf.l2j.gameserver.enums.EventState;
import net.sf.l2j.gameserver.enums.MessageType;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.Summon;
import net.sf.l2j.gameserver.model.actor.instance.Door;
import net.sf.l2j.gameserver.model.actor.instance.Pet;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.model.group.Party;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance;
import net.sf.l2j.gameserver.enums.Paperdoll;
import net.sf.l2j.gameserver.model.itemcontainer.PcInventory;
import net.sf.l2j.gameserver.model.olympiad.OlympiadManager;
import net.sf.l2j.gameserver.model.spawn.Spawn;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.clientpackets.Say2;
import net.sf.l2j.gameserver.network.serverpackets.ActionFailed;
import net.sf.l2j.gameserver.network.serverpackets.CreatureSay;
import net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;

import net.sf.l2j.gameserver.network.serverpackets.SystemMessage;

public class CTFEvent
{
	private static final CLogger LOGGER = new CLogger(CTFEvent.class.getName());
	
	/** html path **/
	private static final String htmlPath = "data/html/mods/events/ctf/";
	
	/**
	 * The teams of the CTFEvent<br>
	 */
	private static CTFEventTeam[] _teams = new CTFEventTeam[2];
	
	/**
	 * The state of the CTFEvent<br>
	 */
	private static EventState _state = EventState.INACTIVE;
	
	/**
	 * The spawn of the participation npc<br>
	 */
	private static Spawn _npcSpawn = null;
	
	/**
	 * the npc instance of the participation npc<br>
	 */
	private static Npc _lastNpcSpawn = null;
	
	/**
	 * The spawn of Team1 flag<br>
	 */
	private static Spawn _flag1Spawn = null;
	
	/**
	 * the npc instance Team1 flag<br>
	 */
	private static Npc _lastFlag1Spawn = null;
	
	/**
	 * The spawn of Team2 flag<br>
	 */
	private static Spawn _flag2Spawn = null;
	
	/**
	 * the npc instance of Team2 flag<br>
	 */
	private static Npc _lastFlag2Spawn = null;
	
	/**
	 * the Team 1 flag carrier Player<br>
	 */
	private static Player _team1Carrier = null;
	
	/**
	 * the Team 2 flag carrier Player<br>
	 */
	private static Player _team2Carrier = null;
	
	/**
	 * the Team 1 flag carrier right hand item<br>
	 */
	private static ItemInstance _team1CarrierRHand = null;
	
	/**
	 * the Team 2 flag carrier right hand item<br>
	 */
	private static ItemInstance _team2CarrierRHand = null;
	
	/**
	 * the Team 1 flag carrier left hand item<br>
	 */
	private static ItemInstance _team1CarrierLHand = null;
	
	/**
	 * the Team 2 flag carrier left hand item<br>
	 */
	private static ItemInstance _team2CarrierLHand = null;
	
	/**
	 * No instance of this class!<br>
	 */
	private CTFEvent()
	{
	}
	
	/**
	 * Teams initializing<br>
	 */
	public static void init()
	{
		_teams[0] = new CTFEventTeam(Config.CTF_EVENT_TEAM_1_NAME, Config.CTF_EVENT_TEAM_1_COORDINATES);
		_teams[1] = new CTFEventTeam(Config.CTF_EVENT_TEAM_2_NAME, Config.CTF_EVENT_TEAM_2_COORDINATES);
	}
	
	/**
	 * Starts the participation of the CTFEvent<br>
	 * 1. Get NpcTemplate by CTFConfig.CTF_EVENT_PARTICIPATION_NPC_ID<br>
	 * 2. Try to spawn a new npc of it<br>
	 * <br>
	 * @return boolean: true if success, otherwise false<br>
	 */
	public static boolean startParticipation()
	{
		if (Config.CTF_EVENT_PARTICIPATION_NPC_ID <= 0)
		{
			LOGGER.warn("CTFEventEngine: CTF_EVENT_PARTICIPATION_NPC_ID is not configured (must be > 0). Please set a valid NPC ID in capturetheflag.properties");
			return false;
		}
		
		NpcTemplate tmpl = NpcData.getInstance().getTemplate(Config.CTF_EVENT_PARTICIPATION_NPC_ID);
		
		if (tmpl == null)
		{
			LOGGER.warn("CTFEventEngine: EventManager is a NullPointer -> Invalid npc id in Configs? NPC ID: " + Config.CTF_EVENT_PARTICIPATION_NPC_ID);
			return false;
		}
		
		try
		{
			_npcSpawn = new Spawn(tmpl);
			_npcSpawn.setLoc(Config.CTF_EVENT_PARTICIPATION_NPC_COORDINATES[0], Config.CTF_EVENT_PARTICIPATION_NPC_COORDINATES[1], Config.CTF_EVENT_PARTICIPATION_NPC_COORDINATES[2], Config.CTF_EVENT_PARTICIPATION_NPC_COORDINATES.length > 3 ? Config.CTF_EVENT_PARTICIPATION_NPC_COORDINATES[3] : 0);
			_npcSpawn.setRespawnDelay(60000);
			
			_lastNpcSpawn = _npcSpawn.doSpawn(false, null);
		}
		catch (Exception e)
		{
			LOGGER.warn("CTFEventEngine: exception: " + e.getMessage(), e);
			return false;
		}
		
		setState(EventState.PARTICIPATING);
		return true;
	}
	
	private static int highestLevelPcInstanceOf(Map<Integer, Player> players)
	{
		int maxLevel = Integer.MIN_VALUE, maxLevelId = -1;
		for (Player player : players.values())
		{
			if (player.getStatus().getLevel() >= maxLevel)
			{
				maxLevel = player.getStatus().getLevel();
				maxLevelId = player.getObjectId();
			}
		}
		return maxLevelId;
	}
	
	/**
	 * Starts the CTFEvent fight<br>
	 * 1. Set state EventState.STARTING<br>
	 * 2. Close doors specified in Configs<br>
	 * 3. Abort if not enought participants(return false)<br>
	 * 4. Set state EventState.STARTED<br>
	 * 5. Teleport all participants to team spot<br>
	 * <br>
	 * @return boolean: true if success, otherwise false<br>
	 */
	public static boolean startFight()
	{
		// Set state to STARTING
		setState(EventState.STARTING);
		
		// Randomize and balance team distribution
		Map<Integer, Player> allParticipants = new HashMap<>();
		
		allParticipants.putAll(_teams[0].getParticipatedPlayers());
		allParticipants.putAll(_teams[1].getParticipatedPlayers());
		
		_teams[0].cleanMe();
		_teams[1].cleanMe();
		
		Player player;
		Iterator<Player> iter;
		if (needParticipationFee())
		{
			iter = allParticipants.values().iterator();
			while (iter.hasNext())
			{
				player = iter.next();
				if (!hasParticipationFee(player))
					iter.remove();
			}
		}
		
		int balance[] =
		{
			0,
			0
		}, priority = 0, highestLevelPlayerId;
		
		// TODO: allParticipants should be sorted by level instead of using highestLevelPcInstanceOf for every fetch
		while (!allParticipants.isEmpty())
		{
			// Priority team gets one player
			highestLevelPlayerId = highestLevelPcInstanceOf(allParticipants);
			player = allParticipants.remove(highestLevelPlayerId);
			
			if (player != null)
			{
				_teams[priority].addPlayer(player);
				balance[priority]++;
			}
			
			// Switch priority team
			priority = (priority == 0) ? 1 : 0;
		}
		
		// Abort if not enough participants
		if ((_teams[0].getParticipatedPlayerCount() < Config.CTF_EVENT_MIN_PLAYERS_IN_TEAMS) || (_teams[1].getParticipatedPlayerCount() < Config.CTF_EVENT_MIN_PLAYERS_IN_TEAMS))
		{
			// Cleanup of teams
			_teams[0].cleanMe();
			_teams[1].cleanMe();
			setState(EventState.INACTIVE);
			return false;
		}
		
		// Close doors specified in Configs for CTF
		closeDoors(Config.CTF_DOORS_IDS_TO_CLOSE);
		
		// Opens all doors specified in Configs for CTF
		openDoors(Config.CTF_DOORS_IDS_TO_OPEN);
		
		// Spawn flags
		spawnFlags();
		
		// Set state STARTED
		setState(EventState.STARTED);
		
		// Iterate over all teams
		for (CTFEventTeam team : _teams)
		{
			for (Player p : team.getParticipatedPlayers().values())
			{
				// Check for nullpointer
				if (p != null)
					new CTFEventTeleporter(p, team.getCoordinates(), false, false);
			}
		}
		
		return true;
	}
	
	/**
	 * Stops the CTFEvent fight<br>
	 * 1. Set state EventState.INACTIVATING<br>
	 * 2. Remove CTF npc from world<br>
	 * 3. Open doors specified in Configs<br>
	 * 4. Teleport all participants back to participation npc location<br>
	 * 5. Teams cleaning<br>
	 * 6. Set state EventState.INACTIVE<br>
	 */
	public static void stopFight()
	{
		// Set state INACTIVATING
		setState(EventState.INACTIVATING);
		
		// Unspawn event npc
		unSpawnNpc();
		
		// Unspawn flags
		unSpawnFlags();
		
		// Opens all doors specified in Configs for CTF
		openDoors(Config.CTF_DOORS_IDS_TO_CLOSE);
		
		// Closes all doors specified in Configs for CTF
		closeDoors(Config.CTF_DOORS_IDS_TO_OPEN);
		
		// Reset flag carriers
		if (_team1Carrier != null)
			removeFlagCarrier(_team1Carrier);
		
		if (_team2Carrier != null)
			removeFlagCarrier(_team2Carrier);
		
		// Iterate over all teams
		for (CTFEventTeam team : _teams)
		{
			for (Player p : team.getParticipatedPlayers().values())
			{
				// Check for nullpointer
				if (p != null)
					new CTFEventTeleporter(p, Config.CTF_EVENT_PARTICIPATION_NPC_COORDINATES, false, false); // Teleport back.
			}
		}
		
		// Cleanup of teams
		_teams[0].cleanMe();
		_teams[1].cleanMe();
		
		// Set state INACTIVE
		setState(EventState.INACTIVE);
	}
	
	/**
	 * Adds a player to a CTFEvent team<br>
	 * 1. Calculate the id of the team in which the player should be added<br>
	 * 2. Add the player to the calculated team<br>
	 * <br>
	 * @param player as Player<br>
	 * @return boolean: true if success, otherwise false<br>
	 */
	public static synchronized boolean addParticipant(Player player)
	{
		if (player == null)
			return false;
		
		byte teamId = 0;
		
		if (_teams[0].getParticipatedPlayerCount() == _teams[1].getParticipatedPlayerCount())
			teamId = (byte) (Rnd.get(2));
		else
			teamId = (byte) (_teams[0].getParticipatedPlayerCount() > _teams[1].getParticipatedPlayerCount() ? 1 : 0);
		
		return _teams[teamId].addPlayer(player);
	}
	
	/**
	 * Removes a player from a CTFEvent team<br>
	 * <br>
	 * @param player as Player<br>
	 * @return boolean: true if success, otherwise false<br>
	 */
	public static boolean removeParticipant(Player player)
	{
		if (player == null)
			return false;
		
		if (playerIsCarrier(player))
			removeFlagCarrier(player);
		
		_teams[0].removePlayer(player.getObjectId());
		_teams[1].removePlayer(player.getObjectId());
		
		return true;
	}
	
	/**
	 * Checks if a player is participant of the CTFEvent<br>
	 * <br>
	 * @param player as Player<br>
	 * @return boolean: true if player is participant, otherwise false<br>
	 */
	public static boolean isPlayerParticipant(Player player)
	{
		if (player == null)
			return false;
		
		return _teams[0].containsPlayer(player.getObjectId()) || _teams[1].containsPlayer(player.getObjectId());
	}
	
	/**
	 * Checks if a player needs a participation fee<br>
	 * <br>
	 * @param player as Player<br>
	 * @return boolean: true if player needs participation fee, otherwise false<br>
	 */
	private static boolean needParticipationFee()
	{
		return (Config.CTF_EVENT_PARTICIPATION_FEE[0] > 0) && (Config.CTF_EVENT_PARTICIPATION_FEE[1] > 0);
	}
	
	/**
	 * Checks if a player has the participation fee<br>
	 * <br>
	 * @param player as Player<br>
	 * @return boolean: true if player has participation fee, otherwise false<br>
	 */
	private static boolean hasParticipationFee(Player player)
	{
		if (player == null)
			return false;
		
		PcInventory inv = player.getInventory();
		
		if (inv.getItemByItemId(Config.CTF_EVENT_PARTICIPATION_FEE[0]) == null)
			return false;
		
		return inv.getItemByItemId(Config.CTF_EVENT_PARTICIPATION_FEE[0]).getCount() >= Config.CTF_EVENT_PARTICIPATION_FEE[1];
	}
	
	/**
	 * Removes the participation fee from a player<br>
	 * <br>
	 * @param player as Player<br>
	 */
	private static void removeParticipationFee(Player player)
	{
		if (player == null)
			return;
		
		PcInventory inv = player.getInventory();
		
		if (inv.getItemByItemId(Config.CTF_EVENT_PARTICIPATION_FEE[0]) == null)
			return;
		
		inv.destroyItemByItemId(Config.CTF_EVENT_PARTICIPATION_FEE[0], Config.CTF_EVENT_PARTICIPATION_FEE[1]);
	}
	
	/**
	 * Spawns the CTF npc<br>
	 */
	private static void spawnFlags()
	{
		try
		{
			// Spawn Team 1 Flag
			NpcTemplate tmpl1 = NpcData.getInstance().getTemplate(Config.CTF_EVENT_TEAM_1_HEADQUARTERS_ID);
			if (tmpl1 != null)
			{
				_flag1Spawn = new Spawn(tmpl1);
				_flag1Spawn.setLoc(Config.CTF_EVENT_TEAM_1_FLAG_COORDINATES[0], Config.CTF_EVENT_TEAM_1_FLAG_COORDINATES[1], Config.CTF_EVENT_TEAM_1_FLAG_COORDINATES[2], 0);
				_flag1Spawn.setRespawnDelay(60000);
				_lastFlag1Spawn = _flag1Spawn.doSpawn(false, null);
				if (_lastFlag1Spawn != null)
					_lastFlag1Spawn.setTitle(_teams[0].getName());
			}
			
			// Spawn Team 2 Flag
			NpcTemplate tmpl2 = NpcData.getInstance().getTemplate(Config.CTF_EVENT_TEAM_2_HEADQUARTERS_ID);
			if (tmpl2 != null)
			{
				_flag2Spawn = new Spawn(tmpl2);
				_flag2Spawn.setLoc(Config.CTF_EVENT_TEAM_2_FLAG_COORDINATES[0], Config.CTF_EVENT_TEAM_2_FLAG_COORDINATES[1], Config.CTF_EVENT_TEAM_2_FLAG_COORDINATES[2], 0);
				_flag2Spawn.setRespawnDelay(60000);
				_lastFlag2Spawn = _flag2Spawn.doSpawn(false, null);
				if (_lastFlag2Spawn != null)
					_lastFlag2Spawn.setTitle(_teams[1].getName());
			}
		}
		catch (Exception e)
		{
			LOGGER.warn("CTFEventEngine: exception while spawning flags: " + e.getMessage(), e);
		}
	}
	
	/**
	 * Unspawns the CTF npc<br>
	 */
	private static void unSpawnFlags()
	{
		if (_flag1Spawn != null && _lastFlag1Spawn != null)
		{
			_flag1Spawn.doDelete();
			_flag1Spawn = null;
			_lastFlag1Spawn = null;
		}
		
		if (_flag2Spawn != null && _lastFlag2Spawn != null)
		{
			_flag2Spawn.doDelete();
			_flag2Spawn = null;
			_lastFlag2Spawn = null;
		}
	}
	
	/**
	 * Unspawns the CTF npc<br>
	 */
	private static void unSpawnNpc()
	{
		if (_npcSpawn != null && _lastNpcSpawn != null)
		{
			_npcSpawn.doDelete();
			_npcSpawn = null;
			_lastNpcSpawn = null;
		}
	}
	
	/**
	 * Closes doors specified in Configs<br>
	 * <br>
	 * @param doors as int[]<br>
	 */
	private static void closeDoors(List<Integer> doors)
	{
		if (doors == null)
			return;
		
		for (int doorId : doors)
		{
			Door door = DoorData.getInstance().getDoor(doorId);
			if (door != null)
				door.closeMe();
		}
	}
	
	/**
	 * Opens doors specified in Configs<br>
	 * <br>
	 * @param doors as int[]<br>
	 */
	private static void openDoors(List<Integer> doors)
	{
		if (doors == null)
			return;
		
		for (int doorId : doors)
		{
			Door door = DoorData.getInstance().getDoor(doorId);
			if (door != null)
				door.openMe();
		}
	}
	
	/**
	 * Calculates rewards and returns results<br>
	 * <br>
	 * @return String: results of the event<br>
	 */
	public static String calculateRewards()
	{
		String results = "";
		
		// Set state REWARDING
		setState(EventState.REWARDING);
		
		// Get team points
		short team1Points = _teams[0].getPoints();
		short team2Points = _teams[1].getPoints();
		
		// Calculate rewards
		if (team1Points > team2Points)
		{
			results = "CTF Event: " + _teams[0].getName() + " team wins with " + team1Points + " points!";
			rewardTeam(_teams[0]);
		}
		else if (team2Points > team1Points)
		{
			results = "CTF Event: " + _teams[1].getName() + " team wins with " + team2Points + " points!";
			rewardTeam(_teams[1]);
		}
		else
		{
			results = "CTF Event: Event ended in a tie! Both teams have " + team1Points + " points!";
			if (Config.CTF_REWARD_TEAM_TIE)
			{
				rewardTeam(_teams[0]);
				rewardTeam(_teams[1]);
			}
		}
		
		return results;
	}
	
	/**
	 * Rewards a team<br>
	 * <br>
	 * @param team as CTFEventTeam<br>
	 */
	private static void rewardTeam(CTFEventTeam team)
	{
		if (team == null)
			return;
		
		for (Player player : team.getParticipatedPlayers().values())
		{
			if (player != null)
			{
				for (int[] reward : Config.CTF_EVENT_REWARDS)
				{
					player.addEarnedItem(reward[0], reward[1], true);
				}
			}
		}
	}
	
	/**
	 * Sets the CTFEvent state<br>
	 * <br>
	 * @param state as EventState<br>
	 */
	private static void setState(EventState state)
	{
		synchronized (_state)
		{
			_state = state;
		}
	}
	
	/**
	 * Is CTFEvent inactive?<br>
	 * <br>
	 * @return boolean: true if event is inactive(waiting for next event cycle), otherwise false<br>
	 */
	public static boolean isInactive()
	{
		boolean isInactive;
		
		synchronized (_state)
		{
			isInactive = _state == EventState.INACTIVE;
		}
		
		return isInactive;
	}
	
	/**
	 * Is CTFEvent in inactivating?<br>
	 * <br>
	 * @return boolean: true if event is in inactivating progress, otherwise false<br>
	 */
	public static boolean isInactivating()
	{
		boolean isInactivating;
		
		synchronized (_state)
		{
			isInactivating = _state == EventState.INACTIVATING;
		}
		
		return isInactivating;
	}
	
	/**
	 * Is CTFEvent in participation?<br>
	 * <br>
	 * @return boolean: true if event is in participation progress, otherwise false<br>
	 */
	public static boolean isParticipating()
	{
		boolean isParticipating;
		
		synchronized (_state)
		{
			isParticipating = _state == EventState.PARTICIPATING;
		}
		
		return isParticipating;
	}
	
	/**
	 * Is CTFEvent starting?<br>
	 * <br>
	 * @return boolean: true if event is starting up(setting up fighting spot, teleport players etc.), otherwise false<br>
	 */
	public static boolean isStarting()
	{
		boolean isStarting;
		
		synchronized (_state)
		{
			isStarting = _state == EventState.STARTING;
		}
		
		return isStarting;
	}
	
	/**
	 * Is CTFEvent started?<br>
	 * <br>
	 * @return boolean: true if event is started, otherwise false<br>
	 */
	public static boolean isStarted()
	{
		boolean isStarted;
		
		synchronized (_state)
		{
			isStarted = _state == EventState.STARTED;
		}
		
		return isStarted;
	}
	
	/**
	 * Is CTFEvent rewarding?<br>
	 * <br>
	 * @return boolean: true if event is currently rewarding, otherwise false<br>
	 */
	public static boolean isRewarding()
	{
		boolean isRewarding;
		
		synchronized (_state)
		{
			isRewarding = _state == EventState.REWARDING;
		}
		
		return isRewarding;
	}
	
	/**
	 * Returns the team id of a player, if player is not participant it returns -1
	 * @param objectId
	 * @return byte: team name of the given playerName, if not in event -1
	 */
	public static byte getParticipantTeamId(int objectId)
	{
		return (byte) (_teams[0].containsPlayer(objectId) ? 0 : (_teams[1].containsPlayer(objectId) ? 1 : -1));
	}
	
	/**
	 * Returns the team of a player, if player is not participant it returns null
	 * @param objectId
	 * @return CTFEventTeam: team of the given playerObjectId, if not in event null
	 */
	public static CTFEventTeam getParticipantTeam(int objectId)
	{
		return (_teams[0].containsPlayer(objectId) ? _teams[0] : (_teams[1].containsPlayer(objectId) ? _teams[1] : null));
	}
	
	/**
	 * Returns the enemy team of a player, if player is not participant it returns null
	 * @param objectId
	 * @return CTFEventTeam: enemy team of the given playerObjectId, if not in event null
	 */
	public static CTFEventTeam getParticipantEnemyTeam(int objectId)
	{
		return (_teams[0].containsPlayer(objectId) ? _teams[1] : (_teams[1].containsPlayer(objectId) ? _teams[0] : null));
	}
	
	/**
	 * Returns the team by index (0 or 1)
	 * @param index
	 * @return CTFEventTeam: team at the given index
	 */
	public static CTFEventTeam getTeamByIndex(int index)
	{
		if (index >= 0 && index < _teams.length)
			return _teams[index];
		return null;
	}
	
	/**
	 * Returns the team coordinates in which the player is in, if player is not in a team return null
	 * @param objectId
	 * @return int[]: coordinates of teams, 2 elements, index 0 for team 1 and index 1 for team 2
	 */
	public static int[] getParticipantTeamCoordinates(int objectId)
	{
		return _teams[0].containsPlayer(objectId) ? _teams[0].getCoordinates() : (_teams[1].containsPlayer(objectId) ? _teams[1].getCoordinates() : null);
	}
	
	/**
	 * Is given player participant of the event?
	 * @param objectId
	 * @return boolean: true if player is participant, ohterwise false
	 */
	public static boolean isPlayerParticipant(int objectId)
	{
		return _teams[0].containsPlayer(objectId) || _teams[1].containsPlayer(objectId);
	}
	
	/**
	 * Sets a player as flag carrier
	 * @param player
	 */
	public static void setTeamCarrier(Player player)
	{
		if (player == null)
			return;
		
		byte teamId = getParticipantTeamId(player.getObjectId());
		if (teamId == 0)
		{
			_team1Carrier = player;
			_team1CarrierRHand = player.getInventory().getItemFrom(Paperdoll.RHAND);
			_team1CarrierLHand = player.getInventory().getItemFrom(Paperdoll.LHAND);
		}
		else if (teamId == 1)
		{
			_team2Carrier = player;
			_team2CarrierRHand = player.getInventory().getItemFrom(Paperdoll.RHAND);
			_team2CarrierLHand = player.getInventory().getItemFrom(Paperdoll.LHAND);
		}
	}
	
	/**
	 * Removes flag carrier
	 * @param player
	 */
	public static void removeFlagCarrier(Player player)
	{
		if (player == null)
			return;
		
		byte teamId = getParticipantTeamId(player.getObjectId());
		if (teamId == 0 && _team1Carrier == player)
		{
			_team1Carrier = null;
			_team1CarrierRHand = null;
			_team1CarrierLHand = null;
			
			// Remove flag item
			ItemInstance flagItem = player.getInventory().getItemByItemId(Config.CTF_EVENT_TEAM_2_FLAG);
			if (flagItem != null)
				player.getInventory().destroyItem(flagItem);
			
			// Restore weapons
			if (_team1CarrierRHand != null)
				player.getInventory().equipItem(_team1CarrierRHand);
			if (_team1CarrierLHand != null)
				player.getInventory().equipItem(_team1CarrierLHand);
			
			// Items are automatically unblocked when unequipped
			player.broadcastUserInfo();
		}
		else if (teamId == 1 && _team2Carrier == player)
		{
			_team2Carrier = null;
			_team2CarrierRHand = null;
			_team2CarrierLHand = null;
			
			// Remove flag item
			ItemInstance flagItem = player.getInventory().getItemByItemId(Config.CTF_EVENT_TEAM_1_FLAG);
			if (flagItem != null)
				player.getInventory().destroyItem(flagItem);
			
			// Restore weapons
			if (_team2CarrierRHand != null)
				player.getInventory().equipItem(_team2CarrierRHand);
			if (_team2CarrierLHand != null)
				player.getInventory().equipItem(_team2CarrierLHand);
			
			// Items are automatically unblocked when unequipped
			player.broadcastUserInfo();
		}
	}
	
	/**
	 * Checks if player is flag carrier
	 * @param player
	 * @return boolean: true if player is flag carrier, otherwise false
	 */
	public static boolean playerIsCarrier(Player player)
	{
		if (player == null)
			return false;
		
		return (_team1Carrier == player) || (_team2Carrier == player);
	}
	
	/**
	 * Gets the team carrier
	 * @param player
	 * @return Player: team carrier, null if not found
	 */
	public static Player getTeamCarrier(Player player)
	{
		if (player == null)
			return null;
		
		byte teamId = getParticipantTeamId(player.getObjectId());
		if (teamId == 0)
			return _team2Carrier;
		else if (teamId == 1)
			return _team1Carrier;
		
		return null;
	}
	
	/**
	 * Gets the enemy carrier
	 * @param player
	 * @return Player: enemy carrier, null if not found
	 */
	public static Player getEnemyCarrier(Player player)
	{
		if (player == null)
			return null;
		
		byte teamId = getParticipantTeamId(player.getObjectId());
		if (teamId == 0)
			return _team1Carrier;
		else if (teamId == 1)
			return _team2Carrier;
		
		return null;
	}
	
	/**
	 * Gets the enemy team flag id
	 * @param player
	 * @return int: enemy team flag id
	 */
	public static int getEnemyTeamFlagId(Player player)
	{
		if (player == null)
			return 0;
		
		byte teamId = getParticipantTeamId(player.getObjectId());
		if (teamId == 0)
			return Config.CTF_EVENT_TEAM_2_FLAG;
		else if (teamId == 1)
			return Config.CTF_EVENT_TEAM_1_FLAG;
		
		return 0;
	}
	
	/**
	 * Sets carrier unequipped weapons (stored for later restore)
	 * @param player
	 * @param rHand
	 * @param lHand
	 */
	public static void setCarrierUnequippedWeapons(Player player, ItemInstance rHand, ItemInstance lHand)
	{
		if (player == null)
			return;
		
		byte teamId = getParticipantTeamId(player.getObjectId());
		if (teamId == 0)
		{
			_team1CarrierRHand = rHand;
			_team1CarrierLHand = lHand;
		}
		else if (teamId == 1)
		{
			_team2CarrierRHand = rHand;
			_team2CarrierLHand = lHand;
		}
	}
	
	/**
	 * Sends a system message to all participants
	 * @param message
	 */
	public static void sysMsgToAllParticipants(String message)
	{
		for (CTFEventTeam team : _teams)
		{
			for (Player player : team.getParticipatedPlayers().values())
			{
				if (player != null)
					player.sendMessage(message);
			}
		}
	}
	
	/**
	 * Broadcasts a screen message to all participants
	 * @param message
	 * @param time
	 */
	public static void broadcastScreenMessage(String message, int time)
	{
		for (CTFEventTeam team : _teams)
		{
			for (Player player : team.getParticipatedPlayers().values())
			{
				if (player != null)
					player.sendPacket(new ExShowScreenMessage(message, time * 1000));
			}
		}
	}
	
	/**
	 * Shows the participation HTML
	 * @param player
	 */
	public static void showParticipationHtml(Player player)
	{
		if (player == null)
			return;
		
		String htmContent = HtmCache.getInstance().getHtm(htmlPath + "participation.htm");
		if (htmContent == null)
			htmContent = "<html><body>CTF Event participation window is not available.</body></html>";
		
		// Get the NPC objectId if player is interacting with an NPC
		int npcId = 0;
		if (player.getTarget() != null && player.getTarget() instanceof net.sf.l2j.gameserver.model.actor.instance.EventManager)
		{
			npcId = player.getTarget().getObjectId();
		}
		else if (_lastNpcSpawn != null)
		{
			npcId = _lastNpcSpawn.getObjectId();
		}
		
		NpcHtmlMessage npcHtmlMessage = new NpcHtmlMessage(npcId);
		npcHtmlMessage.setHtml(htmContent);
		npcHtmlMessage.replace("%team1name%", _teams[0].getName());
		npcHtmlMessage.replace("%team1count%", String.valueOf(_teams[0].getParticipatedPlayerCount()));
		npcHtmlMessage.replace("%team2name%", _teams[1].getName());
		npcHtmlMessage.replace("%team2count%", String.valueOf(_teams[1].getParticipatedPlayerCount()));
		npcHtmlMessage.replace("%playername%", player.getName());
		player.sendPacket(npcHtmlMessage);
	}
	
	/**
	 * Handles bypass command
	 * @param command
	 * @param player
	 */
	public static void onBypass(String command, Player player)
	{
		if (player == null)
			return;
		
		if (command.startsWith("ctfjoin"))
		{
			if (!isParticipating())
			{
				player.sendMessage("CTF Event: Registration is not open.");
				return;
			}
			
			if (isPlayerParticipant(player))
			{
				player.sendMessage("CTF Event: You are already registered.");
				return;
			}
			
			if (player.getStatus().getLevel() < Config.CTF_EVENT_MIN_PLAYER_LEVEL || player.getStatus().getLevel() > Config.CTF_EVENT_MAX_PLAYER_LEVEL)
			{
				player.sendMessage("CTF Event: Your level is not suitable for this event.");
				return;
			}
			
			if (needParticipationFee() && !hasParticipationFee(player))
			{
				player.sendMessage("CTF Event: You don't have the participation fee.");
				return;
			}
			
			if (needParticipationFee())
				removeParticipationFee(player);
			
			if (addParticipant(player))
			{
				player.sendMessage("CTF Event: You have been registered.");
				showParticipationHtml(player);
			}
			else
			{
				player.sendMessage("CTF Event: Registration failed.");
			}
		}
		else if (command.startsWith("ctfleave"))
		{
			if (!isParticipating())
			{
				player.sendMessage("CTF Event: Registration is not open.");
				return;
			}
			
			if (!isPlayerParticipant(player))
			{
				player.sendMessage("CTF Event: You are not registered.");
				return;
			}
			
			if (removeParticipant(player))
			{
				player.sendMessage("CTF Event: You have been unregistered.");
				showParticipationHtml(player);
			}
		}
		else if (command.startsWith("ctfinfo"))
		{
			showParticipationHtml(player);
		}
	}
}

