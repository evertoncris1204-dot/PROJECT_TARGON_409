package dev.farmDungeon;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import net.sf.l2j.Config;
import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.commons.pool.ThreadPool;
import net.sf.l2j.gameserver.data.xml.NpcData;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.model.location.Location;
import net.sf.l2j.gameserver.model.spawn.Spawn;
import net.sf.l2j.gameserver.model.spawn.SpawnData;
import net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage;

/**
 * Farm Dungeon Instance System
 * Allows players to rent a private dungeon instance for farming
 */
public class FarmDungeonInstance
{
	private static final CLogger LOGGER = new CLogger(FarmDungeonInstance.class.getName());
	
	private static FarmDungeonInstance _instance;
	
	// Map to store active instances: Player -> InstanceData
	private Map<Player, InstanceData> activeInstances = new ConcurrentHashMap<>();
	
	// Instance counter for unique instance IDs
	private int instanceCounter = 0;
	
	// Map to store instance IDs for players: Player -> InstanceId
	private Map<Player, Integer> playerInstances = new ConcurrentHashMap<>();
	
	// Map to store preserved time for players: Player -> RemainingMinutes
	private Map<Player, Integer> preservedTime = new ConcurrentHashMap<>();
	
	public static FarmDungeonInstance getInstance()
	{
		if (_instance == null)
			_instance = new FarmDungeonInstance();
		return _instance;
	}
	
	private FarmDungeonInstance()
	{
		LOGGER.info("Farm Dungeon Instance System loaded.");
	}
	
	/**
	 * Check if player has an active instance
	 */
	public boolean hasActiveInstance(Player player)
	{
		return activeInstances.containsKey(player);
	}
	
	/**
	 * Get remaining time for player's instance in seconds
	 */
	public long getRemainingTime(Player player)
	{
		InstanceData data = activeInstances.get(player);
		if (data == null)
			return 0;
		
		long elapsed = System.currentTimeMillis() - data.startTime;
		long remaining = (data.duration * 1000) - elapsed;
		return Math.max(0, remaining / 1000);
	}
	
	/**
	 * Create a new instance for player
	 */
	public boolean createInstance(Player player, int durationMinutes)
	{
		if (hasActiveInstance(player))
		{
			player.sendMessage("You already have an active farm dungeon instance!");
			return false;
		}
		
		// Calculate required item count
		int itemCount = (Config.FARM_DUNGEON_PAYMENT_ITEM_COUNT_PER_HOUR * durationMinutes) / 60;
		
		// Check if player has enough items
		if (player.getInventory().getItemCount(Config.FARM_DUNGEON_PAYMENT_ITEM_ID, -1) < itemCount)
		{
			String itemName = "item";
			try
			{
				net.sf.l2j.gameserver.data.xml.ItemData itemData = net.sf.l2j.gameserver.data.xml.ItemData.getInstance();
				net.sf.l2j.gameserver.model.item.kind.Item item = itemData.getTemplate(Config.FARM_DUNGEON_PAYMENT_ITEM_ID);
				if (item != null)
					itemName = item.getName();
			}
			catch (Exception e)
			{
				// Use default name
			}
			
			player.sendMessage("You don't have enough " + itemName + ". Required: " + itemCount + " for " + durationMinutes + " minutes.");
			return false;
		}
		
		// Charge player
		if (!player.destroyItemByItemId(Config.FARM_DUNGEON_PAYMENT_ITEM_ID, itemCount, true))
		{
			player.sendMessage("Failed to charge payment item.");
			return false;
		}
		
		// Generate unique instance ID
		int instanceId = instanceCounter++;
		
		// Create instance data
		InstanceData data = new InstanceData();
		data.startTime = System.currentTimeMillis();
		data.duration = durationMinutes * 60; // Convert to seconds
		data.entryLocation = new Location(player.getX(), player.getY(), player.getZ());
		data.spawnedMonsters = new ArrayList<>();
		data.instanceId = instanceId;
		data.exitNpcSpawn = null;
		
		activeInstances.put(player, data);
		playerInstances.put(player, instanceId);
		
		// Set instance ID on player (we'll add a method to Player for this)
		setPlayerInstanceId(player, instanceId);
		
		// Teleport player to dungeon (same location for all, but different instances)
		Location dungeonLoc = Config.FARM_DUNGEON_LOCATION;
		player.teleportTo(dungeonLoc.getX(), dungeonLoc.getY(), dungeonLoc.getZ(), 0);
		
		// Schedule cleanup after teleport (region update happens during teleport)
		ThreadPool.schedule(() -> player.cleanInstanceKnownList(), 500);
		
		// Spawn monsters in this instance (same location, but instance-specific)
		spawnMonsters(player, data, dungeonLoc);
		
		// Spawn exit NPC in this instance
		spawnExitNpc(player, data, dungeonLoc);
		
		// Start expiration task
		data.expirationTask = ThreadPool.schedule(new ExpirationTask(player), data.duration * 1000);
		
		// Start reminder task (warn at 5 minutes remaining)
		long reminderTime = Math.max(0, (data.duration - 300) * 1000);
		if (reminderTime > 0)
		{
			ThreadPool.schedule(new ReminderTask(player), reminderTime);
		}
		
		player.sendMessage("Farm Dungeon Instance created! Duration: " + durationMinutes + " minutes.");
		player.sendPacket(new ExShowScreenMessage("Farm Dungeon Instance: " + durationMinutes + " minutes", 5000, ExShowScreenMessage.SMPOS.TOP_CENTER, false));
		
		LOGGER.info("Player " + player.getName() + " created farm dungeon instance for " + durationMinutes + " minutes.");
		
		return true;
	}
	
	/**
	 * Spawn monsters for player's instance
	 */
	private void spawnMonsters(Player player, InstanceData data, Location dungeonLoc)
	{
		if (Config.FARM_DUNGEON_MONSTER_SPAWNS == null || Config.FARM_DUNGEON_MONSTER_SPAWNS.isEmpty())
			return;
		
		String[] spawns = Config.FARM_DUNGEON_MONSTER_SPAWNS.split(";");
		for (String spawnStr : spawns)
		{
			if (spawnStr == null || spawnStr.trim().isEmpty())
				continue;
			
			String[] parts = spawnStr.split(",");
			if (parts.length != 4)
			{
				LOGGER.warn("Invalid monster spawn format: " + spawnStr);
				continue;
			}
			
			try
			{
				int npcId = Integer.parseInt(parts[0].trim());
				int x = Integer.parseInt(parts[1].trim());
				int y = Integer.parseInt(parts[2].trim());
				int z = Integer.parseInt(parts[3].trim());
				
				NpcTemplate template = NpcData.getInstance().getTemplate(npcId);
				if (template == null)
				{
					LOGGER.warn("Monster template not found: " + npcId);
					continue;
				}
				
				Spawn spawn = new Spawn(template);
				spawn.setLoc(x, y, z, 0);
				spawn.setRespawnDelay(Config.FARM_DUNGEON_MONSTER_RESPAWN_DELAY);
				
				// Create SpawnData using reflection
				try
				{
					java.lang.reflect.Field spawnDataField = net.sf.l2j.gameserver.model.spawn.ASpawn.class.getDeclaredField("_spawnData");
					spawnDataField.setAccessible(true);
					SpawnData spawnData = new SpawnData("FarmDungeonMonster_Instance" + data.instanceId + "_" + player.getObjectId() + "_" + npcId + "_" + System.currentTimeMillis());
					spawnData.set(x, y, z, 0);
					spawnData.setStatus((byte) 1);
					spawnData.setDBValue(0);
					spawnDataField.set(spawn, spawnData);
				}
				catch (Exception e)
				{
					LOGGER.warn("Could not set SpawnData for monster: " + e.getMessage());
				}
				
				spawn.doSpawn(false, null);
				if (spawn.getNpc() != null)
				{
					// Set instance ID on monster so only players from same instance can see it
					setNpcInstanceId(spawn.getNpc(), data.instanceId);
					
					// Clean known list - remove this NPC from players not in same instance
					ThreadPool.schedule(() -> spawn.getNpc().cleanInstanceKnownList(), 100);
					
					// Make monster aggressive
					if (spawn.getNpc() instanceof net.sf.l2j.gameserver.model.actor.instance.Monster)
					{
						net.sf.l2j.gameserver.model.actor.instance.Monster monster = (net.sf.l2j.gameserver.model.actor.instance.Monster) spawn.getNpc();
						// Monsters are aggressive by default if they have aggroRange > 0 in template
						// Force aggressive behavior
						try
						{
							java.lang.reflect.Method setAggressive = monster.getClass().getMethod("setAggressive", boolean.class);
							setAggressive.invoke(monster, true);
						}
						catch (Exception e)
						{
							// Method might not exist, try alternative
							try
							{
								java.lang.reflect.Field aggressiveField = monster.getClass().getDeclaredField("_isAggressive");
								aggressiveField.setAccessible(true);
								aggressiveField.setBoolean(monster, true);
							}
							catch (Exception e2)
							{
								// If template has aggroRange > 0, monster will be aggressive anyway
								LOGGER.debug("Could not set aggressive flag directly, relying on template aggroRange");
							}
						}
					}
					
					data.spawnedMonsters.add(spawn);
				}
			}
			catch (NumberFormatException e)
			{
				LOGGER.warn("Invalid number in monster spawn: " + spawnStr);
			}
			catch (Exception e)
			{
				LOGGER.warn("Failed to spawn monster: " + spawnStr + " - " + e.getMessage());
			}
		}
	}
	
	/**
	 * Spawn exit NPC for player's instance
	 */
	private void spawnExitNpc(Player player, InstanceData data, Location dungeonLoc)
	{
		if (Config.FARM_DUNGEON_EXIT_NPC_ID <= 0)
			return;
		
		try
		{
			NpcTemplate template = NpcData.getInstance().getTemplate(Config.FARM_DUNGEON_EXIT_NPC_ID);
			if (template == null)
			{
				LOGGER.warn("Exit NPC template not found: " + Config.FARM_DUNGEON_EXIT_NPC_ID);
				return;
			}
			
			// Spawn exit NPC near the dungeon location (slightly offset)
			int exitX = dungeonLoc.getX() + 50;
			int exitY = dungeonLoc.getY() + 50;
			int exitZ = dungeonLoc.getZ();
			
			Spawn spawn = new Spawn(template);
			spawn.setLoc(exitX, exitY, exitZ, 0);
			spawn.setRespawnDelay(0); // No respawn needed
			
			// Create SpawnData using reflection
			try
			{
				java.lang.reflect.Field spawnDataField = net.sf.l2j.gameserver.model.spawn.ASpawn.class.getDeclaredField("_spawnData");
				spawnDataField.setAccessible(true);
				SpawnData spawnData = new SpawnData("FarmDungeonExitNPC_Instance" + data.instanceId + "_" + player.getObjectId());
				spawnData.set(exitX, exitY, exitZ, 0);
				spawnData.setStatus((byte) 1);
				spawnData.setDBValue(0);
				spawnDataField.set(spawn, spawnData);
			}
			catch (Exception e)
			{
				LOGGER.warn("Could not set SpawnData for exit NPC: " + e.getMessage());
			}
			
			spawn.doSpawn(false, null);
			if (spawn.getNpc() != null)
			{
				// Set instance ID on exit NPC
				setNpcInstanceId(spawn.getNpc(), data.instanceId);
				
				// Clean known list - remove this NPC from players not in same instance
				ThreadPool.schedule(() -> spawn.getNpc().cleanInstanceKnownList(), 100);
				data.exitNpcSpawn = spawn;
			}
		}
		catch (Exception e)
		{
			LOGGER.warn("Failed to spawn exit NPC: " + e.getMessage());
		}
	}
	
	/**
	 * Set instance ID on player
	 */
	private void setPlayerInstanceId(Player player, int instanceId)
	{
		player.setFarmDungeonInstanceId(instanceId);
	}
	
	/**
	 * Set instance ID on NPC
	 */
	private void setNpcInstanceId(net.sf.l2j.gameserver.model.actor.Npc npc, int instanceId)
	{
		npc.setFarmDungeonInstanceId(instanceId);
	}
	
	/**
	 * Get instance ID for player
	 */
	public int getPlayerInstanceId(Player player)
	{
		Integer instanceId = playerInstances.get(player);
		return instanceId != null ? instanceId : -1;
	}
	
	/**
	 * Check if two players are in the same instance
	 */
	public boolean areInSameInstance(Player player1, Player player2)
	{
		Integer id1 = playerInstances.get(player1);
		Integer id2 = playerInstances.get(player2);
		return id1 != null && id2 != null && id1.equals(id2);
	}
	
	/**
	 * Remove instance and teleport player back
	 */
	public void removeInstance(Player player, boolean expired)
	{
		InstanceData data = activeInstances.remove(player);
		if (data == null)
			return;
		
		// Cancel expiration task
		if (data.expirationTask != null)
		{
			data.expirationTask.cancel(false);
		}
		
		// Remove all spawned monsters
		if (data.spawnedMonsters != null)
		{
			for (Spawn spawn : data.spawnedMonsters)
			{
				if (spawn != null && spawn.getNpc() != null)
				{
					spawn.getNpc().deleteMe();
					spawn.doDelete();
				}
			}
			data.spawnedMonsters.clear();
		}
		
		// Remove exit NPC
		if (data.exitNpcSpawn != null && data.exitNpcSpawn.getNpc() != null)
		{
			data.exitNpcSpawn.getNpc().deleteMe();
			data.exitNpcSpawn.doDelete();
			data.exitNpcSpawn = null;
		}
		
		// Remove instance ID from player
		playerInstances.remove(player);
		clearPlayerInstanceId(player);
		
		// Reset instance counter if no active instances (to prevent overflow)
		if (activeInstances.isEmpty())
		{
			instanceCounter = 0;
		}
		
		// Teleport player back to entry location
		if (player != null && player.isOnline())
		{
			if (expired)
			{
				player.sendMessage("Your farm dungeon instance has expired!");
				player.sendPacket(new ExShowScreenMessage("Farm Dungeon Instance expired!", 5000, ExShowScreenMessage.SMPOS.TOP_CENTER, false));
			}
			else
			{
				player.sendMessage("You have left the farm dungeon instance.");
			}
			
			player.teleportTo(data.entryLocation.getX(), data.entryLocation.getY(), data.entryLocation.getZ(), 0);
		}
		
		LOGGER.info("Player " + (player != null ? player.getName() : "Unknown") + " removed from farm dungeon instance (expired: " + expired + ").");
	}
	
	/**
	 * Force remove instance (for admin commands, etc)
	 */
	public void forceRemoveInstance(Player player)
	{
		removeInstance(player, false);
	}
	
	/**
	 * Leave instance preserving remaining time (for NPC exit)
	 * Returns remaining time in minutes
	 */
	public int leaveInstancePreservingTime(Player player)
	{
		InstanceData data = activeInstances.get(player);
		if (data == null)
			return 0;
		
		// Calculate remaining time
		long elapsed = System.currentTimeMillis() - data.startTime;
		long remaining = (data.duration * 1000) - elapsed;
		int remainingMinutes = (int) Math.max(0, remaining / 60000);
		
		// Store preserved time
		if (remainingMinutes > 0)
		{
			preservedTime.put(player, remainingMinutes);
		}
		
		// Remove instance but don't expire it (preserve time)
		removeInstance(player, false);
		
		return remainingMinutes;
	}
	
	/**
	 * Get preserved time for player
	 */
	public int getPreservedTime(Player player)
	{
		Integer time = preservedTime.get(player);
		return time != null ? time : 0;
	}
	
	/**
	 * Use preserved time to create instance
	 */
	public boolean usePreservedTime(Player player)
	{
		Integer preservedMinutes = preservedTime.remove(player);
		if (preservedMinutes == null || preservedMinutes <= 0)
			return false;
		
		// Create instance using preserved time
		return createInstanceWithTime(player, preservedMinutes);
	}
	
	/**
	 * Create instance with specific time (internal method)
	 */
	private boolean createInstanceWithTime(Player player, int durationMinutes)
	{
		if (hasActiveInstance(player))
		{
			player.sendMessage("You already have an active farm dungeon instance!");
			return false;
		}
		
		// Generate unique instance ID
		int instanceId = instanceCounter++;
		
		// Create instance data
		InstanceData data = new InstanceData();
		data.startTime = System.currentTimeMillis();
		data.duration = durationMinutes * 60; // Convert to seconds
		data.entryLocation = new Location(player.getX(), player.getY(), player.getZ());
		data.spawnedMonsters = new ArrayList<>();
		data.instanceId = instanceId;
		data.exitNpcSpawn = null;
		
		activeInstances.put(player, data);
		playerInstances.put(player, instanceId);
		
		// Set instance ID on player
		setPlayerInstanceId(player, instanceId);
		
		// Teleport player to dungeon (same location for all, but different instances)
		Location dungeonLoc = Config.FARM_DUNGEON_LOCATION;
		player.teleportTo(dungeonLoc.getX(), dungeonLoc.getY(), dungeonLoc.getZ(), 0);
		
		// Schedule cleanup after teleport (region update happens during teleport)
		ThreadPool.schedule(() -> player.cleanInstanceKnownList(), 500);
		
		// Spawn monsters in this instance (same location, but instance-specific)
		spawnMonsters(player, data, dungeonLoc);
		
		// Spawn exit NPC in this instance
		spawnExitNpc(player, data, dungeonLoc);
		
		// Start expiration task
		data.expirationTask = ThreadPool.schedule(new ExpirationTask(player), data.duration * 1000);
		
		// Start reminder task (warn at 5 minutes remaining)
		long reminderTime = Math.max(0, (data.duration - 300) * 1000);
		if (reminderTime > 0)
		{
			ThreadPool.schedule(new ReminderTask(player), reminderTime);
		}
		
		player.sendMessage("Farm Dungeon Instance created! Duration: " + durationMinutes + " minutes.");
		player.sendPacket(new ExShowScreenMessage("Farm Dungeon Instance: " + durationMinutes + " minutes", 5000, ExShowScreenMessage.SMPOS.TOP_CENTER, false));
		
		LOGGER.info("Player " + player.getName() + " created farm dungeon instance for " + durationMinutes + " minutes.");
		
		return true;
	}
	
	/**
	 * Handle player logout - remove instance if active
	 */
	public void onPlayerLogout(Player player)
	{
		if (hasActiveInstance(player))
		{
			// Remove instance data but don't teleport (player is logging out)
			InstanceData data = activeInstances.remove(player);
			if (data != null)
			{
				if (data.expirationTask != null)
				{
					data.expirationTask.cancel(false);
				}
				
				// Remove all spawned monsters
				if (data.spawnedMonsters != null)
				{
					for (Spawn spawn : data.spawnedMonsters)
					{
						if (spawn != null && spawn.getNpc() != null)
						{
							spawn.getNpc().deleteMe();
							spawn.doDelete();
						}
					}
					data.spawnedMonsters.clear();
				}
				
				// Remove exit NPC
				if (data.exitNpcSpawn != null && data.exitNpcSpawn.getNpc() != null)
				{
					data.exitNpcSpawn.getNpc().deleteMe();
					data.exitNpcSpawn.doDelete();
				}
			}
			
			// Remove instance ID from player
			playerInstances.remove(player);
			
			// Note: Preserved time is kept even on logout
			
			// Reset instance counter if no active instances
			if (activeInstances.isEmpty())
			{
				instanceCounter = 0;
			}
			
			LOGGER.info("Player " + player.getName() + " logged out, farm dungeon instance removed.");
		}
	}
	
	/**
	 * Clear instance ID from player
	 */
	private void clearPlayerInstanceId(Player player)
	{
		player.setFarmDungeonInstanceId(-1);
	}
	
	/**
	 * Class to store instance data
	 */
	private static class InstanceData
	{
		long startTime;
		int duration; // in seconds
		Location entryLocation;
		ScheduledFuture<?> expirationTask;
		List<Spawn> spawnedMonsters; // List of spawned monsters for this instance
		Spawn exitNpcSpawn; // Exit NPC spawn for this instance
		int instanceId; // Unique instance ID
	}
	
	/**
	 * Task to handle instance expiration
	 */
	class ExpirationTask implements Runnable
	{
		private Player player;
		
		public ExpirationTask(Player player)
		{
			this.player = player;
		}
		
		@Override
		public void run()
		{
			if (player != null && player.isOnline() && hasActiveInstance(player))
			{
				removeInstance(player, true);
			}
		}
	}
	
	/**
	 * Task to remind player about expiration
	 */
	class ReminderTask implements Runnable
	{
		private Player player;
		
		public ReminderTask(Player player)
		{
			this.player = player;
		}
		
		@Override
		public void run()
		{
			if (player != null && player.isOnline() && hasActiveInstance(player))
			{
				long remaining = getRemainingTime(player);
				if (remaining > 0)
				{
					player.sendMessage("Farm Dungeon Instance: " + (remaining / 60) + " minutes remaining!");
					player.sendPacket(new ExShowScreenMessage("Farm Dungeon: " + (remaining / 60) + " minutes remaining!", 5000, ExShowScreenMessage.SMPOS.TOP_CENTER, false));
				}
			}
		}
	}
}

