package dev.farmDungeon;

import net.sf.l2j.Config;
import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.gameserver.data.manager.SpawnManager;
import net.sf.l2j.gameserver.data.xml.NpcData;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.model.location.SpawnLocation;
import net.sf.l2j.gameserver.model.spawn.Spawn;
import net.sf.l2j.gameserver.model.spawn.SpawnData;

/**
 * Farm Dungeon Manager
 * Handles NPC spawning and system initialization
 */
public class FarmDungeonManager
{
	private static final CLogger LOGGER = new CLogger(FarmDungeonManager.class.getName());
	
	private Spawn farmDungeonNpcSpawn = null;
	
	public void initialize()
	{
		if (!Config.ALLOW_FARM_DUNGEON_INSTANCE)
		{
			LOGGER.info("Farm Dungeon Instance System is disabled.");
			return;
		}
		
		spawnFarmDungeonNpc();
		LOGGER.info("Farm Dungeon Instance System initialized.");
	}
	
	private void spawnFarmDungeonNpc()
	{
		try
		{
			NpcTemplate template = NpcData.getInstance().getTemplate(Config.FARM_DUNGEON_NPC_ID);
			if (template == null)
			{
				LOGGER.warn("Farm Dungeon: NPC template not found! NPC ID: " + Config.FARM_DUNGEON_NPC_ID);
				return;
			}
			
			Spawn spawn = new Spawn(template);
			spawn.setLoc(Config.FARM_DUNGEON_NPC_LOCATION.getX(), Config.FARM_DUNGEON_NPC_LOCATION.getY(), Config.FARM_DUNGEON_NPC_LOCATION.getZ(), 0);
			spawn.setRespawnDelay(0);
			
			// Create SpawnData using reflection
			try
			{
				java.lang.reflect.Field spawnDataField = net.sf.l2j.gameserver.model.spawn.ASpawn.class.getDeclaredField("_spawnData");
				spawnDataField.setAccessible(true);
				SpawnData spawnData = new SpawnData("FarmDungeon_" + Config.FARM_DUNGEON_NPC_ID);
				spawnData.set(Config.FARM_DUNGEON_NPC_LOCATION.getX(), Config.FARM_DUNGEON_NPC_LOCATION.getY(), Config.FARM_DUNGEON_NPC_LOCATION.getZ(), 0);
				spawnData.setStatus((byte) 1);
				spawnData.setDBValue(0);
				spawnDataField.set(spawn, spawnData);
			}
			catch (Exception e)
			{
				LOGGER.warn("Farm Dungeon: Could not set SpawnData: " + e.getMessage());
			}
			
			spawn.doSpawn(false, null);
			if (spawn.getNpc() == null)
			{
				LOGGER.warn("Farm Dungeon: Failed to spawn NPC.");
				return;
			}
			
			farmDungeonNpcSpawn = spawn;
		}
		catch (Exception e)
		{
			LOGGER.warn("Farm Dungeon: Failed to spawn NPC: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	public void despawnNpc()
	{
		if (farmDungeonNpcSpawn != null && farmDungeonNpcSpawn.getNpc() != null)
		{
			farmDungeonNpcSpawn.getNpc().deleteMe();
			farmDungeonNpcSpawn.doDelete();
			farmDungeonNpcSpawn = null;
		}
	}
	
	private static FarmDungeonManager _instance;
	
	public static FarmDungeonManager getInstance()
	{
		if (_instance == null)
			_instance = new FarmDungeonManager();
		return _instance;
	}
}

