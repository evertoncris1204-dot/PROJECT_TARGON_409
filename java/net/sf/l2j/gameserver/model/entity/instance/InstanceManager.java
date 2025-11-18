package net.sf.l2j.gameserver.model.entity.instance;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.gameserver.idfactory.IdFactory;

/**
 * @author Rouxy
 */
public class InstanceManager
{
	private static final CLogger LOGGER = new CLogger(InstanceManager.class.getName());
	
	private final Map<Integer, Instance> _instances = new HashMap<>();
	private final AtomicInteger _instanceId = new AtomicInteger(1);
	
	public static InstanceManager getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private static class SingletonHolder
	{
		protected static final InstanceManager _instance = new InstanceManager();
	}
	
	public Instance createInstance()
	{
		int id = _instanceId.getAndIncrement();
		Instance instance = new Instance(id, "TournamentInstance-" + id, 0); // Template ID 0 for now
		_instances.put(id, instance);
		LOGGER.info("Created instance with ID: {}", id);
		return instance;
	}
	
	public Instance getInstance(int id)
	{
		return _instances.get(id);
	}
	
	public void deleteInstance(int id)
	{
		Instance instance = _instances.remove(id);
		if (instance != null)
		{
			instance.removeAllPlayers();
			LOGGER.info("Deleted instance with ID: {}", id);
		}
	}
	
	public Map<Integer, Instance> getInstances()
	{
		return _instances;
	}
}

