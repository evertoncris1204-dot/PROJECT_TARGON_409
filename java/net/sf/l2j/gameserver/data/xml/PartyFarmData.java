package net.sf.l2j.gameserver.data.xml;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.l2j.Config;
import net.sf.l2j.commons.config.ExProperties;
import net.sf.l2j.commons.data.StatSet;
import net.sf.l2j.commons.data.xml.IXmlReader;
import net.sf.l2j.gameserver.data.manager.PartyFarmEvent;
import net.sf.l2j.gameserver.model.holder.PTFarmConfig;
import net.sf.l2j.gameserver.model.holder.PTFarmHolder;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

public class PartyFarmData implements IXmlReader
{
	private static final String PARTYFARM_PROPERTIES = "./config/partyfarm.properties";
	
	private final Map<String, List<PTFarmHolder>> _ptfarm = new HashMap<>();
	private PTFarmConfig _config;
	
	public PartyFarmData()
	{
		load();
		PartyFarmEvent.start();
	}
	
	public void reload()
	{
		PartyFarmEvent.reset();
		_ptfarm.clear();
		load();
	}
	
	@Override
	public void load()
	{
		loadProperties();
		parseFile("./data/xml/partyfarm.xml");
		LOGGER.info("Loaded {} Party Farm spawns.", _ptfarm.size());
	}
	
	private void loadProperties()
	{
		final ExProperties partyfarm = Config.initProperties(PARTYFARM_PROPERTIES);
		
		boolean enabled = partyfarm.getProperty("PartyFarmEnabled", true);
		int duration = partyfarm.getProperty("PartyFarmDuration", 30);
		int preparation = partyfarm.getProperty("PartyFarmPreparation", 5);
		
		String daysStr = partyfarm.getProperty("PartyFarmDays", "1,2,5");
		List<Integer> days = new ArrayList<>();
		for (String day : daysStr.split(","))
		{
			try
			{
				days.add(Integer.parseInt(day.trim()));
			}
			catch (NumberFormatException e)
			{
				LOGGER.warn("Invalid day value in partyfarm.properties: {}", day);
			}
		}
		
		String timesStr = partyfarm.getProperty("PartyFarmTimes", "11:10,15:00,17:20,21:15");
		List<String> times = new ArrayList<>();
		for (String time : timesStr.split(","))
			times.add(time.trim());
		
		int respawnDelay = partyfarm.getProperty("PartyFarmRespawnDelay", 60);
		int respawnRandom = partyfarm.getProperty("PartyFarmRespawnRandom", 30);
		
		_config = new PTFarmConfig(enabled, duration, preparation, days, times, respawnDelay, respawnRandom);
	}
	
	@Override
	public void parseDocument(Document doc, Path path)
	{
		forEach(doc, "partyfarm", eventsNode ->
		{
			forEach(eventsNode, "event", eventNode ->
			{
				StatSet set = parseAttributes(eventNode);
				String id = set.getString("name", "partyfarm");
				
				// Config is loaded from properties file, XML config is ignored
				// Only spawns are loaded from XML
				
				forEach(eventNode, "spawns", spawnsNode ->
				{
					forEach(spawnsNode, "spawn", spawnNode ->
					{
						StatSet spawnSet = parseAttributes(spawnNode);
						PTFarmHolder spawn = new PTFarmHolder(spawnSet);
						_ptfarm.computeIfAbsent(id, k -> new ArrayList<>()).add(spawn);
					});
				});
			});
		});
	}
	
	public List<PTFarmHolder> getSpawns(String eventId)
	{
		return _ptfarm.getOrDefault(eventId, new ArrayList<>());
	}
	
	public PTFarmConfig getConfig()
	{
		return _config;
	}
	
	public static PartyFarmData getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private static class SingletonHolder
	{
		protected static final PartyFarmData _instance = new PartyFarmData();
	}
	
	private static String getChildText(Node node, String tag)
	{
		Node child = getChild(node, tag);
		return (child != null) ? child.getTextContent().trim() : "";
	}
	
	private static Node getChild(Node node, String tag)
	{
		for (int i = 0; i < node.getChildNodes().getLength(); i++)
		{
			Node child = node.getChildNodes().item(i);
			if (IXmlReader.isNode(child) && tag.equals(child.getNodeName()))
				return child;
		}
		return null;
	}
}

