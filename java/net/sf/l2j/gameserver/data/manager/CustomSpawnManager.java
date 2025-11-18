package net.sf.l2j.gameserver.data.manager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.commons.pool.ConnectionPool;

import net.sf.l2j.gameserver.data.xml.NpcData;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.model.spawn.Spawn;

/**
 * Manager for custom spawns created by admins
 */
public class CustomSpawnManager
{
	private static final CLogger LOGGER = new CLogger(CustomSpawnManager.class.getName());
	
	private static final String LOAD_CUSTOM_SPAWNS = "SELECT * FROM custom_spawnlist WHERE enabled = 1";
	private static final String ADD_CUSTOM_SPAWN = "INSERT INTO custom_spawnlist (npc_id, npc_name, x, y, z, heading, respawn_delay, respawn_random, loc_id, period_of_day, created_by, created_at, enabled) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
	private static final String UPDATE_CUSTOM_SPAWN = "UPDATE custom_spawnlist SET npc_id = ?, npc_name = ?, x = ?, y = ?, z = ?, heading = ?, respawn_delay = ?, respawn_random = ?, loc_id = ?, period_of_day = ?, enabled = ? WHERE id = ?";
	private static final String DELETE_CUSTOM_SPAWN = "DELETE FROM custom_spawnlist WHERE id = ?";
	private static final String DISABLE_CUSTOM_SPAWN = "UPDATE custom_spawnlist SET enabled = 0 WHERE id = ?";
	
	private final ConcurrentMap<Integer, Spawn> _customSpawns = new ConcurrentHashMap<>();
	private final ConcurrentMap<Integer, CustomSpawnData> _spawnData = new ConcurrentHashMap<>();
	
	public static CustomSpawnManager getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private static class SingletonHolder
	{
		protected static final CustomSpawnManager INSTANCE = new CustomSpawnManager();
	}
	
	private CustomSpawnManager()
	{
		load();
	}
	
	/**
	 * Load all custom spawns from database
	 */
	public void load()
	{
		_customSpawns.clear();
		_spawnData.clear();
		
		try (Connection con = ConnectionPool.getConnection();
			PreparedStatement ps = con.prepareStatement(LOAD_CUSTOM_SPAWNS);
			ResultSet rs = ps.executeQuery())
		{
			int loaded = 0;
			while (rs.next())
			{
				try
				{
					int id = rs.getInt("id");
					int npcId = rs.getInt("npc_id");
					int x = rs.getInt("x");
					int y = rs.getInt("y");
					int z = rs.getInt("z");
					int heading = rs.getInt("heading");
					int respawnDelay = rs.getInt("respawn_delay");
					int respawnRandom = rs.getInt("respawn_random");
					String periodOfDay = rs.getString("period_of_day");
					
					// Check if period matches current time
					if (!isPeriodValid(periodOfDay))
						continue;
					
					NpcTemplate template = NpcData.getInstance().getTemplate(npcId);
					if (template == null)
					{
						LOGGER.warn("Custom Spawn: NPC template not found for ID: " + npcId);
						continue;
					}
					
					Spawn spawn = new Spawn(template);
					spawn.setLoc(x, y, z, heading);
					spawn.setRespawnDelay(respawnDelay);
					if (respawnRandom > 0)
						spawn.setRespawnRandom(respawnRandom);
					
					// Spawn the NPC (doSpawn automatically adds to SpawnManager)
					Npc npc = spawn.doSpawn(false);
					if (npc != null)
					{
						_customSpawns.put(id, spawn);
						_spawnData.put(id, new CustomSpawnData(rs));
						loaded++;
					}
				}
				catch (Exception e)
				{
					LOGGER.error("Custom Spawn: Error loading spawn ID " + rs.getInt("id"), e);
				}
			}
			
			LOGGER.info("Custom Spawn Manager: Loaded " + loaded + " custom spawns.");
		}
		catch (Exception e)
		{
			LOGGER.error("Custom Spawn Manager: Error loading custom spawns", e);
		}
	}
	
	/**
	 * Check if period of day is valid for current time
	 */
	private boolean isPeriodValid(String period)
	{
		if (period == null || period.equals("ALL"))
			return true;
		
		java.util.Calendar cal = java.util.Calendar.getInstance();
		int hour = cal.get(java.util.Calendar.HOUR_OF_DAY);
		
		switch (period.toUpperCase())
		{
			case "DAY":
				return hour >= 6 && hour < 18;
			case "NIGHT":
				return hour < 6 || hour >= 18;
			default:
				return true;
		}
	}
	
	/**
	 * Add a new custom spawn
	 */
	public boolean addSpawn(int npcId, int x, int y, int z, int heading, int respawnDelay, int respawnRandom, int locId, String periodOfDay, String createdBy)
	{
		try
		{
			NpcTemplate template = NpcData.getInstance().getTemplate(npcId);
			if (template == null)
				return false;
			
			String npcName = template.getName();
			
			try (Connection con = ConnectionPool.getConnection();
				PreparedStatement ps = con.prepareStatement(ADD_CUSTOM_SPAWN, PreparedStatement.RETURN_GENERATED_KEYS))
			{
				ps.setInt(1, npcId);
				ps.setString(2, npcName);
				ps.setInt(3, x);
				ps.setInt(4, y);
				ps.setInt(5, z);
				ps.setInt(6, heading);
				ps.setInt(7, respawnDelay);
				ps.setInt(8, respawnRandom);
				ps.setInt(9, locId);
				ps.setString(10, periodOfDay != null ? periodOfDay : "ALL");
				ps.setString(11, createdBy);
				ps.setLong(12, System.currentTimeMillis());
				ps.setInt(13, 1);
				
				ps.executeUpdate();
				
				ResultSet rs = ps.getGeneratedKeys();
				if (rs.next())
				{
					int id = rs.getInt(1);
					
					// Create and spawn the NPC
					Spawn spawn = new Spawn(template);
					spawn.setLoc(x, y, z, heading);
					spawn.setRespawnDelay(respawnDelay);
					if (respawnRandom > 0)
						spawn.setRespawnRandom(respawnRandom);
					
					Npc npc = spawn.doSpawn(false);
					if (npc != null)
					{
						_customSpawns.put(id, spawn);
						_spawnData.put(id, new CustomSpawnData(id, npcId, npcName, x, y, z, heading, respawnDelay, respawnRandom, locId, periodOfDay, createdBy, System.currentTimeMillis(), true));
						return true;
					}
				}
			}
		}
		catch (Exception e)
		{
			LOGGER.error("Custom Spawn Manager: Error adding spawn", e);
		}
		
		return false;
	}
	
	/**
	 * Delete a custom spawn
	 */
	public boolean deleteSpawn(int id)
	{
		Spawn spawn = _customSpawns.get(id);
		if (spawn != null)
		{
			if (spawn.getNpc() != null)
				spawn.getNpc().deleteMe();
			
			SpawnManager.getInstance().deleteSpawn(spawn);
			_customSpawns.remove(id);
			_spawnData.remove(id);
		}
		
		try (Connection con = ConnectionPool.getConnection();
			PreparedStatement ps = con.prepareStatement(DELETE_CUSTOM_SPAWN))
		{
			ps.setInt(1, id);
			ps.executeUpdate();
			return true;
		}
		catch (Exception e)
		{
			LOGGER.error("Custom Spawn Manager: Error deleting spawn ID " + id, e);
			return false;
		}
	}
	
	/**
	 * Disable a custom spawn (without deleting)
	 */
	public boolean disableSpawn(int id)
	{
		Spawn spawn = _customSpawns.get(id);
		if (spawn != null)
		{
			if (spawn.getNpc() != null)
				spawn.getNpc().deleteMe();
			
			SpawnManager.getInstance().deleteSpawn(spawn);
			_customSpawns.remove(id);
			CustomSpawnData data = _spawnData.get(id);
			if (data != null)
				data.enabled = false;
		}
		
		try (Connection con = ConnectionPool.getConnection();
			PreparedStatement ps = con.prepareStatement(DISABLE_CUSTOM_SPAWN))
		{
			ps.setInt(1, id);
			ps.executeUpdate();
			return true;
		}
		catch (Exception e)
		{
			LOGGER.error("Custom Spawn Manager: Error disabling spawn ID " + id, e);
			return false;
		}
	}
	
	/**
	 * Get all custom spawn data
	 */
	public List<CustomSpawnData> getAllSpawns()
	{
		return new ArrayList<>(_spawnData.values());
	}
	
	/**
	 * Get custom spawn data by ID
	 */
	public CustomSpawnData getSpawnData(int id)
	{
		return _spawnData.get(id);
	}
	
	/**
	 * Get spawn by ID
	 */
	public Spawn getSpawn(int id)
	{
		return _customSpawns.get(id);
	}
	
	/**
	 * Data class for custom spawn information
	 */
	public static class CustomSpawnData
	{
		public final int id;
		public final int npcId;
		public final String npcName;
		public final int x;
		public final int y;
		public final int z;
		public final int heading;
		public final int respawnDelay;
		public final int respawnRandom;
		public final int locId;
		public final String periodOfDay;
		public final String createdBy;
		public final long createdAt;
		public boolean enabled;
		
		public CustomSpawnData(ResultSet rs) throws Exception
		{
			this.id = rs.getInt("id");
			this.npcId = rs.getInt("npc_id");
			this.npcName = rs.getString("npc_name");
			this.x = rs.getInt("x");
			this.y = rs.getInt("y");
			this.z = rs.getInt("z");
			this.heading = rs.getInt("heading");
			this.respawnDelay = rs.getInt("respawn_delay");
			this.respawnRandom = rs.getInt("respawn_random");
			this.locId = rs.getInt("loc_id");
			this.periodOfDay = rs.getString("period_of_day");
			this.createdBy = rs.getString("created_by");
			this.createdAt = rs.getLong("created_at");
			this.enabled = rs.getInt("enabled") == 1;
		}
		
		public CustomSpawnData(int id, int npcId, String npcName, int x, int y, int z, int heading, int respawnDelay, int respawnRandom, int locId, String periodOfDay, String createdBy, long createdAt, boolean enabled)
		{
			this.id = id;
			this.npcId = npcId;
			this.npcName = npcName;
			this.x = x;
			this.y = y;
			this.z = z;
			this.heading = heading;
			this.respawnDelay = respawnDelay;
			this.respawnRandom = respawnRandom;
			this.locId = locId;
			this.periodOfDay = periodOfDay;
			this.createdBy = createdBy;
			this.createdAt = createdAt;
			this.enabled = enabled;
		}
	}
}

