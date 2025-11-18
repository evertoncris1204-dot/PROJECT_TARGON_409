package net.sf.l2j.config;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;

import net.sf.l2j.commons.logging.CLogger;

/**
 * RaidInfo System Configuration
 * Loads settings from raidinfo.properties file
 */
public class RaidInfoConfig
{
	private static final CLogger LOGGER = new CLogger(RaidInfoConfig.class.getName());
	private static final Properties _props = new Properties();
	
	// Configuration values
	public static boolean ENABLED = true;
	public static int MAX_GRAND_BOSSES_DISPLAY = 15;
	public static int MAX_RAID_BOSSES_DISPLAY = 15;
	public static int MAX_DROPS_DISPLAY = 50;
	public static Set<Integer> GRAND_BOSS_IDS = new HashSet<>();
	public static Set<Integer> RAID_BOSS_IDS = new HashSet<>();
	public static boolean USE_WHITELIST_MODE = true;
	
	static
	{
		load();
	}
	
	public static void load()
	{
		try (FileInputStream fis = new FileInputStream(new File("./config/raidinfo.properties")))
		{
			_props.load(fis);
			
			ENABLED = getBooleanProperty("ENABLED", true);
			MAX_GRAND_BOSSES_DISPLAY = getIntProperty("MAX_GRAND_BOSSES_DISPLAY", 15);
			MAX_RAID_BOSSES_DISPLAY = getIntProperty("MAX_RAID_BOSSES_DISPLAY", 15);
			MAX_DROPS_DISPLAY = getIntProperty("MAX_DROPS_DISPLAY", 50);
			USE_WHITELIST_MODE = getBooleanProperty("USE_WHITELIST_MODE", true);
			
			// Parse Grand Boss IDs
			GRAND_BOSS_IDS.clear();
			String grandBossIdsStr = getProperty("GRAND_BOSS_IDS", "");
			if (grandBossIdsStr != null && !grandBossIdsStr.trim().isEmpty())
			{
				StringTokenizer st = new StringTokenizer(grandBossIdsStr, ",");
				while (st.hasMoreTokens())
				{
					try
					{
						int npcId = Integer.parseInt(st.nextToken().trim());
						GRAND_BOSS_IDS.add(npcId);
					}
					catch (NumberFormatException e)
					{
						LOGGER.warn("RaidInfo System: Invalid Grand Boss ID in GRAND_BOSS_IDS, skipping.");
					}
				}
			}
			
			// Parse Raid Boss IDs
			RAID_BOSS_IDS.clear();
			String raidBossIdsStr = getProperty("RAID_BOSS_IDS", "");
			if (raidBossIdsStr != null && !raidBossIdsStr.trim().isEmpty())
			{
				StringTokenizer st = new StringTokenizer(raidBossIdsStr, ",");
				while (st.hasMoreTokens())
				{
					try
					{
						int npcId = Integer.parseInt(st.nextToken().trim());
						RAID_BOSS_IDS.add(npcId);
					}
					catch (NumberFormatException e)
					{
						LOGGER.warn("RaidInfo System: Invalid Raid Boss ID in RAID_BOSS_IDS, skipping.");
					}
				}
			}
			
			LOGGER.info("RaidInfo System: raidinfo.properties loaded successfully.");
			LOGGER.info("RaidInfo System: Grand Boss whitelist: {} bosses", GRAND_BOSS_IDS.size());
			LOGGER.info("RaidInfo System: Raid Boss whitelist: {} bosses", RAID_BOSS_IDS.size());
		}
		catch (Exception e)
		{
			LOGGER.error("RaidInfo System: failed to load raidinfo.properties, defaults will be used.", e);
		}
	}
	
	private static String getProperty(String key, String defaultValue)
	{
		return _props.getProperty(key, defaultValue);
	}
	
	private static int getIntProperty(String key, int defaultValue)
	{
		try
		{
			return Integer.parseInt(_props.getProperty(key, String.valueOf(defaultValue)));
		}
		catch (NumberFormatException e)
		{
			LOGGER.warn("RaidInfo System: Invalid integer value for '{}', using default: {}", key, defaultValue);
			return defaultValue;
		}
	}
	
	private static boolean getBooleanProperty(String key, boolean defaultValue)
	{
		try
		{
			return Boolean.parseBoolean(_props.getProperty(key, String.valueOf(defaultValue)));
		}
		catch (Exception e)
		{
			LOGGER.warn("RaidInfo System: Invalid boolean value for '{}', using default: {}", key, defaultValue);
			return defaultValue;
		}
	}
	
	/**
	 * Check if a Grand Boss should be displayed
	 */
	public static boolean shouldShowGrandBoss(int npcId)
	{
		if (!ENABLED)
			return false;
		
		if (GRAND_BOSS_IDS.isEmpty())
			return true; // No filter, show all
		
		if (USE_WHITELIST_MODE)
			return GRAND_BOSS_IDS.contains(npcId); // Only show if in whitelist
		else
			return !GRAND_BOSS_IDS.contains(npcId); // Show all except blacklist
	}
	
	/**
	 * Check if a Raid Boss should be displayed
	 */
	public static boolean shouldShowRaidBoss(int npcId)
	{
		if (!ENABLED)
			return false;
		
		if (RAID_BOSS_IDS.isEmpty())
			return true; // No filter, show all
		
		if (USE_WHITELIST_MODE)
			return RAID_BOSS_IDS.contains(npcId); // Only show if in whitelist
		else
			return !RAID_BOSS_IDS.contains(npcId); // Show all except blacklist
	}
}

