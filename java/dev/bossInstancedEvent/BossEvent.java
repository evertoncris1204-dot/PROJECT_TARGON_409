package dev.bossInstancedEvent;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
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
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.model.location.Location;
import net.sf.l2j.gameserver.enums.TeamType;
import net.sf.l2j.gameserver.model.location.SpawnLocation;
import net.sf.l2j.gameserver.model.spawn.Spawn;
import net.sf.l2j.gameserver.model.spawn.SpawnData;
import net.sf.l2j.gameserver.network.serverpackets.CreatureSay;
import net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage;
import net.sf.l2j.gameserver.network.serverpackets.MagicSkillUse;

/**
 * @author Zaun
 */
public class BossEvent
{
	private static final CLogger LOGGER = new CLogger(BossEvent.class.getName());
	
	public Spawn bossSpawn;
	public List<Location> locList = new ArrayList<>();
	public Location loc;
	public List<Integer> bossList = new ArrayList<>();
	public int bossId;
	public int objectId;
	public List<Player> eventPlayers = new ArrayList<>();
	private EventState state = EventState.INACTIVE;
	public boolean started = false;
	public boolean aborted = false;
	private Player lastAttacker = null;
	private Map<Integer, Integer> generalRewards = new HashMap<>();
	private Map<Integer, Integer> lastAttackerRewards = new HashMap<>();
	private Map<Integer, Integer> mainDamageDealerRewards = new HashMap<>();
	public ScheduledFuture<?> despawnBoss = null;
	public ScheduledFuture<?> countDownTask = null;
	private String bossName = "";
	public boolean bossKilled = false;
	public Spawn eventNpc = null;
	public long startTime;

	public BossEvent()
	{
		BossEventCMD handler = new BossEventCMD();
		net.sf.l2j.gameserver.handler.VoicedCommandHandler.getInstance().registerHandler(handler);
		NextBossEvent.getInstance().startCalculationOfNextEventTime();
		LOGGER.info("Boss Event loaded.");
	}

	public boolean addPlayer(Player player)
	{
		if (eventPlayers.add(player))
		{
			// Add visual effect to player (same as spawn protect - blue aura)
			applyEventEffect(player);
			return true;
		}
		return false;
	}

	public boolean removePlayer(Player player)
	{
		if (eventPlayers.remove(player))
		{
			// Remove visual effect from player
			removeEventEffect(player);
			return true;
		}
		return false;
	}
	
	/**
	 * Apply visual effect to event participant
	 * Uses the same system as spawn protect - sets team to BLUE for blue/red aura effect
	 */
	private void applyEventEffect(Player player)
	{
		if (player != null && player.isOnline())
		{
			// Set team to BLUE (same as spawn protect) - this creates the blue/red aura effect
			player.setTeam(TeamType.BLUE);
			// Broadcast the change so other players see the effect
			player.broadcastUserInfo();
		}
	}
	
	/**
	 * Remove visual effect from event participant
	 */
	private void removeEventEffect(Player player)
	{
		if (player != null && player.isOnline())
		{
			// Restore team to NONE (normal state)
			player.setTeam(TeamType.NONE);
			// Broadcast the change so other players see the effect removed
			player.broadcastUserInfo();
		}
	}
	
	/**
	 * Apply visual effects to all event participants
	 */
	public void applyEventEffectsToAll()
	{
		for (Player player : eventPlayers)
		{
			applyEventEffect(player);
		}
	}
	
	/**
	 * Remove visual effects from all event participants
	 */
	public void removeEventEffectsFromAll()
	{
		for (Player player : eventPlayers)
		{
			removeEventEffect(player);
		}
	}

	public boolean isRegistered(Player player)
	{
		return eventPlayers.contains(player);
	}

	class Registration implements Runnable
	{
		@Override
		public void run()
		{
			startRegistration();
		}
	}

	public void teleToTown()
	{
		// Remove visual effects before teleporting
		removeEventEffectsFromAll();
		
		for (Player p : eventPlayers)
		{
			p.teleportTo(new Location(83374, 148081, -3407), 0);
		}
		setState(EventState.INACTIVE);
	}

	public void delay(int delay)
	{
		try
		{
			Thread.sleep(delay);
		}
		catch (InterruptedException e)
		{
			e.printStackTrace();
		}
	}

	class Teleporting implements Runnable
	{
		Location teleTo;
		List<Player> toTeleport = new ArrayList<>();

		public Teleporting(List<Player> toTeleport, Location teleTo)
		{
			this.teleTo = teleTo;
			this.toTeleport = toTeleport;
		}

		@Override
		public void run()
		{
			if (eventPlayers.size() >= Config.BOSS_EVENT_MIN_PLAYERS)
			{
				despawnNpc(eventNpc);
				setState(EventState.STARTING);
				announce("Event Started!", false);
				announce("PvP protection is active between event participants!", true);
				startCountDown(Config.BOSS_EVENT_TIME_TO_TELEPORT_PLAYERS, true);

				for (Player p : toTeleport)
				{
					ThreadPool.schedule(new Runnable()
					{
						@Override
						public void run()
						{
							p.teleportTo(teleTo, 0);
						}
					}, Config.BOSS_EVENT_TIME_TO_TELEPORT_PLAYERS * 1000);
				}
				delay(Config.BOSS_EVENT_TIME_TO_TELEPORT_PLAYERS * 1000);
				setState(EventState.STARTING);
				startCountDown(Config.BOSS_EVENT_TIME_TO_WAIT, true);
				ThreadPool.schedule(new Fighting(bossId, teleTo), Config.BOSS_EVENT_TIME_TO_WAIT * 1000);
			}
			else
			{
				announce("Event was cancelled due to lack of participation!", false);
				removeEventEffectsFromAll();
				setState(EventState.INACTIVE);
				despawnNpc(eventNpc);
				eventPlayers.clear();
				objectId = 0;
			}
		}
	}

	public void reward(Player p, Map<Integer, Integer> rewardType)
	{
		for (Map.Entry<Integer, Integer> entry : rewardType.entrySet())
		{
			p.addItem(entry.getKey(), entry.getValue(), true);
		}
	}

	public void rewardPlayers()
	{
		for (Player p : eventPlayers)
		{
			if (p.getBossEventDamage() > Config.BOSS_EVENT_MIN_DAMAGE_TO_OBTAIN_REWARD)
			{
				reward(p, generalRewards);
			}
			else
			{
				p.sendPacket(new ExShowScreenMessage("You didn't caused min damage to receive rewards!", 5000, net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage.SMPOS.TOP_CENTER, false));
				p.sendMessage("You didn't caused min damage to receive rewards! Min. Damage: " + Config.BOSS_EVENT_MIN_DAMAGE_TO_OBTAIN_REWARD + ". Your Damage: " + p.getBossEventDamage());
			}
		}

		if (Config.BOSS_EVENT_REWARD_MAIN_DAMAGE_DEALER)
		{
			Player mainDamageDealer = getMainDamageDealer();
			if (mainDamageDealer != null)
			{
				reward(mainDamageDealer, mainDamageDealerRewards);
				mainDamageDealer.sendPacket(new CreatureSay(0, SayType.CRITICAL_ANNOUNCE, "[Boss Event]", "Congratulations, you was the damage dealer! So you will receive wonderful rewards."));
			}
		}
	}

	public void finishEvent()
	{
		started = false;
		NextBossEvent.getInstance().startCalculationOfNextEventTime();
		rewardPlayers();
		if (bossKilled) announce(bossName + " has been defeated!", false);
		if (Config.BOSS_EVENT_REWARD_LAST_ATTACKER)
		{
			if (lastAttacker != null)
			{
				announce("LastAttacker: " + lastAttacker.getName(), false);
			}
		}

		if (Config.BOSS_EVENT_REWARD_MAIN_DAMAGE_DEALER)
		{
			if (getMainDamageDealer() != null)
			{
				announce("Main Damage Dealer: " + getMainDamageDealer().getName() + ". Total Damage = " + getMainDamageDealer().getBossEventDamage(), false);
			}
		}
		ThreadPool.schedule(new Runnable()
		{
			@Override
			public void run()
			{
				removeEventEffectsFromAll();
				teleToTown();
				eventPlayers.clear();
			}
		}, Config.BOSS_EVENT_TIME_TO_TELEPORT_PLAYERS * 1000);

		setState(EventState.INACTIVATING);
		startCountDown(Config.BOSS_EVENT_TIME_TO_TELEPORT_PLAYERS, true);
		if (despawnBoss != null)
		{
			despawnBoss.cancel(false);
			despawnBoss = null;
		}
		objectId = 0;
	}

	class Fighting implements Runnable
	{
		int bossId;
		Location spawnLoc;

		public Fighting(int bossId, Location spawnLoc)
		{
			this.bossId = bossId;
			this.spawnLoc = spawnLoc;
		}

		@Override
		public void run()
		{
			if (spawnNpc(bossId, loc.getX(), loc.getY(), loc.getZ()))
			{
				if (bossSpawn.getNpc() == null)
				{
					LOGGER.warn("Boss Event: Boss spawn failed, NPC is null");
					return;
				}
				setState(EventState.STARTED);
				if (Config.BOSS_EVENT_TIME_ON_SCREEN)
				{
					startCountDown(Config.BOSS_EVENT_TIME_TO_DESPAWN_BOSS, true);
				}
				despawnBoss = ThreadPool.schedule(new DespawnBossTask(bossSpawn), Config.BOSS_EVENT_TIME_TO_DESPAWN_BOSS * 1000);
				objectId = bossSpawn.getNpc().getObjectId();
				for (Player p : eventPlayers)
				{
					p.sendPacket(new ExShowScreenMessage("Boss " + bossSpawn.getNpc().getName() + " has been spawned. Go and Defeat him!", 5000, net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage.SMPOS.TOP_CENTER, false));
				}
			}
		}
	}

	public void despawnNpc(Spawn spawn)
	{
		if (spawn != null && spawn.getNpc() != null)
		{
			spawn.getNpc().deleteMe();
			spawn.doDelete();
		}
	}

	class DespawnBossTask implements Runnable
	{
		Spawn spawn;

		public DespawnBossTask(Spawn spawn)
		{
			this.spawn = spawn;
		}

		@Override
		public void run()
		{
			if (spawn != null)
			{
				announceScreen("Your time is over " + spawn.getNpc().getName() + " returned to his home!", true);
				announce("Your time is over " + spawn.getNpc().getName() + " returned to his home!", true);
				announce("You will be teleported to town.", true);
				despawnNpc(spawn);
				ThreadPool.schedule(new Runnable()
				{
					@Override
					public void run()
					{
						removeEventEffectsFromAll();
						teleToTown();
						eventPlayers.clear();
						setState(EventState.INACTIVE);
						objectId = 0;
					}
				}, 10000);
			}
		}
	}

	public void startRegistration()
	{
		try
		{
			resetPlayersDamage();
			bossKilled = false;
			bossList = Config.BOSS_EVENT_ID;
			bossId = bossList.get(Rnd.get(bossList.size()));
			locList = Config.BOSS_EVENT_LOCATION;
			loc = locList.get(Rnd.get(locList.size()));
			if (NpcData.getInstance().getTemplate(bossId) != null)
			{
				startTime = System.currentTimeMillis() + Config.BOSS_EVENT_REGISTRATION_TIME * 1000;
				eventNpc = spawnEventNpc(Config.BOSS_EVENT_NPC_REGISTER_LOC.getX(), Config.BOSS_EVENT_NPC_REGISTER_LOC.getY(), Config.BOSS_EVENT_NPC_REGISTER_LOC.getZ());
				if (eventNpc == null)
				{
					LOGGER.warn("Boss Event: Failed to spawn registration NPC. Event cancelled.");
					setState(EventState.INACTIVE);
					return;
				}
				generalRewards = Config.BOSS_EVENT_GENERAL_REWARDS;
				lastAttackerRewards = Config.BOSS_EVENT_LAST_ATTACKER_REWARDS;
				mainDamageDealerRewards = Config.BOSS_EVENT_MAIN_DAMAGE_DEALER_REWARDS;
				started = true;
				aborted = false;
				bossName = NpcData.getInstance().getTemplate(bossId).getName();
				setState(EventState.PARTICIPATING);
				announce("Registration started!", false);
				announce("Joinable in giran or use command \".bossevent\" to register to event", false);
				startCountDown(Config.BOSS_EVENT_REGISTRATION_TIME, false);

				ThreadPool.schedule(new Teleporting(eventPlayers, loc), Config.BOSS_EVENT_REGISTRATION_TIME * 1000);
			}
			else
			{
				LOGGER.warn("Boss Event: cannot be started. Invalid BossId: " + bossList);
				return;
			}
		}
		catch (Exception e)
		{
			LOGGER.warn("[Boss Event]: Couldn't be started", e);
		}
	}

	public int timeInMillisToStart()
	{
		return (int) (startTime - System.currentTimeMillis()) / 1000;
	}

	public void startCountDownEnterWorld(Player player)
	{
		if (getState() == EventState.PARTICIPATING)
		{
			ThreadPool.schedule(new Countdown(player, timeInMillisToStart(), getState()), 0);
		}
	}

	public boolean spawnNpc(int npcId, int x, int y, int z)
	{
		NpcTemplate tmpl = NpcData.getInstance().getTemplate(npcId);
		if (tmpl == null)
		{
			LOGGER.warn("Boss Event: Boss template not found! NPC ID: " + npcId);
			return false;
		}
		try
		{
			bossSpawn = new Spawn(tmpl);
			bossSpawn.setLoc(x, y, z, Rnd.get(65535));
			bossSpawn.setRespawnDelay(0);
			
			// Create and set SpawnData to avoid NullPointerException in AI scripts
			try
			{
				Field spawnDataField = net.sf.l2j.gameserver.model.spawn.ASpawn.class.getDeclaredField("_spawnData");
				spawnDataField.setAccessible(true);
				SpawnData spawnData = new SpawnData("BossEvent_" + npcId);
				spawnData.set(x, y, z, Rnd.get(65535));
				spawnData.setStatus((byte) 1);
				spawnData.setDBValue(0);
				spawnDataField.set(bossSpawn, spawnData);
			}
			catch (Exception e)
			{
				LOGGER.warn("Boss Event: Failed to set SpawnData (this may cause issues with some bosses)", e);
			}
			
			bossSpawn.doSpawn(false, null);
			if (bossSpawn.getNpc() == null)
			{
				LOGGER.warn("Boss Event: Failed to spawn boss NPC ID: " + npcId);
				return false;
			}
			bossSpawn.getNpc().broadcastPacket(new MagicSkillUse(bossSpawn.getNpc(), bossSpawn.getNpc(), 1034, 1, 1, 1));
			return true;
		}
		catch (Exception e)
		{
			LOGGER.warn("Boss Event: Error spawning boss", e);
			return false;
		}
	}

	public void resetPlayersDamage()
	{
		for (Player p : World.getInstance().getPlayers())
		{
			p.setBossEventDamage(0);
		}
	}

	public Spawn spawnEventNpc(int x, int y, int z)
	{
		Spawn spawn = null;
		NpcTemplate tmpl = NpcData.getInstance().getTemplate(Config.BOSS_EVENT_REGISTRATION_NPC_ID);
		if (tmpl == null)
		{
			LOGGER.warn("Boss Event: Registration NPC template not found! NPC ID: " + Config.BOSS_EVENT_REGISTRATION_NPC_ID + ". Please check your NPC data files.");
			return null;
		}
		try
		{
			spawn = new Spawn(tmpl);
			spawn.setLoc(x, y, z, Rnd.get(65535));
			spawn.setRespawnDelay(0);
			spawn.doSpawn(false, null);
			if (spawn.getNpc() != null)
				spawn.getNpc().broadcastPacket(new MagicSkillUse(spawn.getNpc(), spawn.getNpc(), 1034, 1, 1, 1));
			return spawn;
		}
		catch (Exception e)
		{
			LOGGER.warn("Boss Event: Error spawning event NPC", e);
			return null;
		}
	}

	public final Player getMainDamageDealer()
	{
		int dmg = 0;
		Player mainDamageDealer = null;
		for (Player p : eventPlayers)
		{
			if (p.getBossEventDamage() > dmg)
			{
				dmg = p.getBossEventDamage();
				mainDamageDealer = p;
			}
		}
		return mainDamageDealer;
	}

	public static BossEvent getInstance()
	{
		return SingleTonHolder._instance;
	}

	private static class SingleTonHolder
	{
		protected static final BossEvent _instance = new BossEvent();
	}

	public void startCountDown(int time, boolean eventOnly)
	{
		Collection<Player> players = new ArrayList<>();
		players = eventOnly ? eventPlayers : World.getInstance().getPlayers();
		for (Player player : players)
		{
			ThreadPool.schedule(new Countdown(player, time, getState()), 0L);
		}
	}

	public void announce(String text, boolean eventOnly)
	{
		Collection<Player> players = new ArrayList<>();
		players = eventOnly ? eventPlayers : World.getInstance().getPlayers();
		for (Player player : players)
		{
			player.sendPacket(new CreatureSay(0, SayType.CRITICAL_ANNOUNCE, "[Boss Event]", text));
		}
	}

	public void announceScreen(String text, boolean eventOnly)
	{
		Collection<Player> players = new ArrayList<>();
		players = eventOnly ? eventPlayers : World.getInstance().getPlayers();
		for (Player player : players)
		{
			player.sendPacket(new ExShowScreenMessage(text, 4000, net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage.SMPOS.TOP_CENTER, false));
		}
	}

	public EventState getState()
	{
		return state;
	}

	public void setState(EventState state)
	{
		this.state = state;
	}

	public Player getLastAttacker()
	{
		return lastAttacker;
	}

	public void setLastAttacker(Player lastAttacker)
	{
		this.lastAttacker = lastAttacker;
	}

	protected class Countdown implements Runnable
	{
		private final Player _player;
		private final int _time;
		private String text = "";
		EventState evtState;

		public Countdown(Player player, int time, EventState evtState)
		{
			_time = time;
			_player = player;
			switch (evtState)
			{
			case PARTICIPATING:
				text = "Boss Event registration ends in: ";
				break;
			case STARTING:
				text = "You will be teleported to Boss Event in: ";
				break;
			case STARTED:
				text = "Boss will spawn in: ";
				break;
			case INACTIVATING:
				text = "You will be teleported to City in: ";
				break;
			}
			this.evtState = evtState;
		}

		@Override
		public void run()
		{
			if (getState() == EventState.INACTIVE)
			{
				return;
			}
			if (_player.isOnline())
			{
				switch (evtState)
				{
				case PARTICIPATING:
				case STARTING:
				case INACTIVATING:
					switch (_time)
					{
					case 60:
					case 120:
					case 180:
					case 240:
					case 300:
						_player.sendPacket(new CreatureSay(0, SayType.CRITICAL_ANNOUNCE, "[Boss Event]", text + _time / 60 + " minute(s)"));
						break;
					case 45:
					case 30:
					case 15:
					case 10:
					case 5:
					case 4:
					case 3:
					case 2:
					case 1:
						_player.sendPacket(new CreatureSay(0, SayType.CRITICAL_ANNOUNCE, "[Boss Event]", text + _time + " second(s)"));
						break;
					}
					if (_time > 1)
					{
						ThreadPool.schedule(new Countdown(_player, _time - 1, evtState), 1000L);
					}
					break;
				case STARTED:
					int minutes = _time / 60;
					int second = _time % 60;
					String timing = ((minutes < 10) ? ("0" + minutes) : minutes) + ":" + ((second < 10) ? ("0" + second) : second);

					_player.sendPacket(new ExShowScreenMessage("Time Left: " + timing, 1100, net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage.SMPOS.BOTTOM_RIGHT, true));
					if (_time > 1)
					{
						ThreadPool.schedule(new Countdown(_player, _time - 1, evtState), 1000L);
					}
					break;
				}
			}
		}
	}
}

