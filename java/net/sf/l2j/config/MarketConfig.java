package net.sf.l2j.config;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

import net.sf.l2j.commons.logging.CLogger;

/**
 * Market System Configuration
 * Loads settings from market.properties file
 */
public class MarketConfig
{
	private static final CLogger LOGGER = new CLogger(MarketConfig.class.getName());
	private static final Properties _props = new Properties();
	
	// Configuration values
	public static int MARKET_CURRENCY_ITEM_ID = 57; // Adena
	public static double COMMISSION_RATE = 0.05; // 5%
	public static int LISTING_DURATION_DAYS = 7;
	public static int MAX_LISTINGS_PER_PLAYER = 10;
	public static boolean ENABLED = true;
	public static boolean ALLOW_ENCHANTED_ITEMS = true;
	public static boolean ALLOW_QUEST_ITEMS = false;
	public static boolean ALLOW_EQUIPPED_ITEMS = false;
	public static long MIN_LISTING_PRICE = 1;
	public static long MAX_LISTING_PRICE = 0; // 0 = no limit
	public static boolean SEND_GLOBAL_ANNOUNCEMENT = true;
	public static String ANNOUNCEMENT_COLOR = "FFFF00"; // Yellow
	
	static
	{
		load();
	}
	
	public static void load()
	{
		try (FileInputStream fis = new FileInputStream(new File("./config/market.properties")))
		{
			_props.load(fis);
			
			MARKET_CURRENCY_ITEM_ID = getIntProperty("MARKET_CURRENCY_ITEM_ID", 57);
			COMMISSION_RATE = getDoubleProperty("COMMISSION_RATE", 0.05);
			LISTING_DURATION_DAYS = getIntProperty("LISTING_DURATION_DAYS", 7);
			MAX_LISTINGS_PER_PLAYER = getIntProperty("MAX_LISTINGS_PER_PLAYER", 10);
			ENABLED = getBooleanProperty("ENABLED", true);
			ALLOW_ENCHANTED_ITEMS = getBooleanProperty("ALLOW_ENCHANTED_ITEMS", true);
			ALLOW_QUEST_ITEMS = getBooleanProperty("ALLOW_QUEST_ITEMS", false);
			ALLOW_EQUIPPED_ITEMS = getBooleanProperty("ALLOW_EQUIPPED_ITEMS", false);
			MIN_LISTING_PRICE = getLongProperty("MIN_LISTING_PRICE", 1);
			MAX_LISTING_PRICE = getLongProperty("MAX_LISTING_PRICE", 0);
			SEND_GLOBAL_ANNOUNCEMENT = getBooleanProperty("SEND_GLOBAL_ANNOUNCEMENT", true);
			ANNOUNCEMENT_COLOR = getProperty("ANNOUNCEMENT_COLOR", "FFFF00");
			
			LOGGER.info("Market System: market.properties loaded successfully.");
		}
		catch (Exception e)
		{
			LOGGER.error("Market System: failed to load market.properties, defaults will be used.", e);
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
			LOGGER.warn("Market System: Invalid integer value for '{}', using default: {}", key, defaultValue);
			return defaultValue;
		}
	}
	
	private static long getLongProperty(String key, long defaultValue)
	{
		try
		{
			return Long.parseLong(_props.getProperty(key, String.valueOf(defaultValue)));
		}
		catch (NumberFormatException e)
		{
			LOGGER.warn("Market System: Invalid long value for '{}', using default: {}", key, defaultValue);
			return defaultValue;
		}
	}
	
	private static double getDoubleProperty(String key, double defaultValue)
	{
		try
		{
			return Double.parseDouble(_props.getProperty(key, String.valueOf(defaultValue)));
		}
		catch (NumberFormatException e)
		{
			LOGGER.warn("Market System: Invalid double value for '{}', using default: {}", key, defaultValue);
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
			LOGGER.warn("Market System: Invalid boolean value for '{}', using default: {}", key, defaultValue);
			return defaultValue;
		}
	}
	
	/**
	 * Get listing duration in milliseconds
	 */
	public static long getListingDuration()
	{
		return LISTING_DURATION_DAYS * 24L * 60L * 60L * 1000L;
	}
}

