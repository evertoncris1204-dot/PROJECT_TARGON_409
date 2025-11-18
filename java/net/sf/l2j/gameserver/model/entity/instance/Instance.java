package net.sf.l2j.gameserver.model.entity.instance;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import net.sf.l2j.gameserver.model.actor.Player;

/**
 * @author Rouxy
 */
public class Instance
{
	private int _id;
	private String _name;
	private int _templateId;
	private List<Player> _players = new CopyOnWriteArrayList<>();
	
	public Instance(int id, String name, int templateId)
	{
		_id = id;
		_name = name;
		_templateId = templateId;
	}
	
	public int getId()
	{
		return _id;
	}
	
	public String getName()
	{
		return _name;
	}
	
	public int getTemplateId()
	{
		return _templateId;
	}
	
	public void addPlayer(Player player)
	{
		_players.add(player);
	}
	
	public void removePlayer(Player player)
	{
		_players.remove(player);
	}
	
	public List<Player> getPlayers()
	{
		return _players;
	}
	
	public void removeAllPlayers()
	{
		_players.clear();
	}
	
	public boolean isPlayerIn(int objectId)
	{
		for (Player player : _players)
		{
			if (player.getObjectId() == objectId)
				return true;
		}
		return false;
	}
	
	public List<Player> getPlayers(boolean includeOffline)
	{
		List<Player> players = new ArrayList<>();
		for (Player player : _players)
		{
			if (player.isOnline() || includeOffline)
				players.add(player);
		}
		return players;
	}
}

