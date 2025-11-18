package net.sf.l2j.gameserver.model.entity.Tournament;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.ScheduledFuture;
import java.util.logging.Logger;

import net.sf.l2j.Config;
import net.sf.l2j.commons.pool.ConnectionPool;
import net.sf.l2j.commons.pool.ThreadPool;
import net.sf.l2j.commons.random.Rnd;
import net.sf.l2j.gameserver.data.manager.SpawnManager;
import net.sf.l2j.gameserver.data.sql.PlayerInfoTable;
import net.sf.l2j.gameserver.data.xml.NpcData;
import net.sf.l2j.gameserver.enums.SayType;
import net.sf.l2j.gameserver.network.serverpackets.CreatureSay;
import net.sf.l2j.gameserver.handler.AdminCommandHandler;
import net.sf.l2j.gameserver.handler.VoicedCommandHandler;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.model.entity.Tournament.Commands.VoiceTournament;
import net.sf.l2j.gameserver.model.entity.Tournament.Data.TournamentArenaParser;
import net.sf.l2j.gameserver.model.entity.Tournament.enums.TournamentFightType;
import net.sf.l2j.gameserver.model.entity.Tournament.model.TournamentTeam;
import net.sf.l2j.gameserver.model.entity.Tournament.tasks.TournamentFight;
import net.sf.l2j.gameserver.model.entity.Tournament.tasks.TournamentSearchFights;
import net.sf.l2j.gameserver.model.location.Location;
import net.sf.l2j.gameserver.model.spawn.Spawn;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.ConfirmDlg;
import net.sf.l2j.gameserver.network.serverpackets.L2GameServerPacket;
import net.sf.l2j.gameserver.network.serverpackets.MagicSkillUse;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;
import net.sf.l2j.gameserver.model.memo.PlayerMemo;

/**
 * @author Rouxy
 */
public class TournamentManager
{
	private static final Logger _log = Logger.getLogger(TournamentManager.class.getName());
	private Map<TournamentTeam, TournamentFightType> registeredTournamentTeams = new HashMap<>();
	private Map<Integer, TournamentFight> currentFights = new HashMap<>();
	private Calendar nextEvent;
	private final SimpleDateFormat format = new SimpleDateFormat("HH:mm");
	private Spawn _npcSpawn;
	private boolean running;
	private ScheduledFuture<?> finishEventTask = null;
	private boolean tournamentTeleporting;
	private int allTimeFights = 0;
	
	public TournamentManager()
	{
		TournamentArenaParser.getInstance();
		
		// Ensure all arena doors are closed permanently
		TournamentArenaParser.getInstance().closeAllArenaDoors();
		
		ThreadPool.scheduleAtFixedRate(new TournamentSearchFights(TournamentFightType.F1X1), 0, Config.TOURNAMENT_TIME_SEARCH_FIGHTS * 1000);
		ThreadPool.scheduleAtFixedRate(new TournamentSearchFights(TournamentFightType.F2X2), 0, Config.TOURNAMENT_TIME_SEARCH_FIGHTS * 1000);
		ThreadPool.scheduleAtFixedRate(new TournamentSearchFights(TournamentFightType.F3X3), 0, Config.TOURNAMENT_TIME_SEARCH_FIGHTS * 1000);
		ThreadPool.scheduleAtFixedRate(new TournamentSearchFights(TournamentFightType.F4X4), 0, Config.TOURNAMENT_TIME_SEARCH_FIGHTS * 1000);
		ThreadPool.scheduleAtFixedRate(new TournamentSearchFights(TournamentFightType.F5X5), 0, Config.TOURNAMENT_TIME_SEARCH_FIGHTS * 1000);
		ThreadPool.scheduleAtFixedRate(new TournamentSearchFights(TournamentFightType.F9X9), 0, Config.TOURNAMENT_TIME_SEARCH_FIGHTS * 1000);
		VoicedCommandHandler.getInstance().registerHandler(new VoiceTournament());
		net.sf.l2j.gameserver.handler.BypassHandler.getInstance().registerHandlerPublic(new net.sf.l2j.gameserver.model.entity.Tournament.ByPasses.TournamentBypasses());
		// AdminTournament will be registered automatically via reflection from admincommandhandlers package
		
		// Ensure NPC is not spawned on server start
		_npcSpawn = null;
		setRunning(false);
		
		startCalculationOfNextEventTime();
	}
	
	private static void closeQuietly(Connection con, PreparedStatement stmt, ResultSet rs)
	{
		try
		{
			if (rs != null)
				rs.close();
		}
		catch (Exception e)
		{
		}
		try
		{
			if (stmt != null)
				stmt.close();
		}
		catch (Exception e)
		{
		}
		try
		{
			if (con != null)
				con.close();
		}
		catch (Exception e)
		{
		}
	}
	
	public static TournamentManager getInstance()
	{
		return SingleTonHolder._instance;
	}
	
	private static class SingleTonHolder
	{
		protected static TournamentManager _instance = new TournamentManager();
	}
	
	public String getNextTime()
	{
		if (nextEvent != null && nextEvent.getTime() != null)
		{
			return format.format(nextEvent.getTime());
		}
		return "Erro";
	}
	
	/**
	 * Get the Calendar object for the next Tournament event
	 * @return Calendar object representing the next event time, or null if not calculated
	 */
	public Calendar getNextEventTime()
	{
		return nextEvent;
	}
	
	public void startCalculationOfNextEventTime()
	{
		try
		{
			Calendar currentTime = Calendar.getInstance();
			Calendar testStartTime = null;
			long flush2 = 0L;
			long timeL = 0L;
			int count = 0;
			for (String timeOfDay : Config.TOURNAMENT_EVENT_INTERVAL_BY_TIME_OF_DAY)
			{
				testStartTime = Calendar.getInstance();
				testStartTime.setLenient(true);
				String[] splitTimeOfDay = timeOfDay.split(":");
				testStartTime.set(Calendar.HOUR_OF_DAY, Integer.parseInt(splitTimeOfDay[0]));
				testStartTime.set(Calendar.MINUTE, Integer.parseInt(splitTimeOfDay[1]));
				testStartTime.set(Calendar.SECOND, 0);
				if (testStartTime.getTimeInMillis() < currentTime.getTimeInMillis())
				{
					testStartTime.add(Calendar.DAY_OF_YEAR, 1);
				}
				timeL = testStartTime.getTimeInMillis() - currentTime.getTimeInMillis();
				if (count == 0)
				{
					flush2 = timeL;
					nextEvent = testStartTime;
				}
				if (timeL < flush2)
				{
					flush2 = timeL;
					nextEvent = testStartTime;
				}
				count++;
			}
			_log.info("[Tournament]: Next Event time: " + nextEvent.getTime().toString());
			ThreadPool.schedule(new StartEventTask(), flush2);
		}
		catch (Exception e)
		{
			System.out.println("[Tournament]: " + e);
		}
	}
	
	public static void toAllOnlinePlayers(L2GameServerPacket packet)
	{
		for (Player player : World.getInstance().getPlayers())
		{
			if (player.isOnline())
				player.sendPacket(packet);
		}
	}
	
	public void announceToAllOnlinePlayers(String text)
	{
		for (Player player : World.getInstance().getPlayers())
		{
			if (player.isOnline())
				player.sendMessage(text);
		}
	}
	
	/**
	 * Announce a message to all online players using CreatureSay (like other events)
	 * @param text The message to announce
	 * @param critical If true, uses CRITICAL_ANNOUNCE, otherwise uses ANNOUNCEMENT
	 */
	public void announce(String text, boolean critical)
	{
		SayType sayType = critical ? SayType.CRITICAL_ANNOUNCE : SayType.ANNOUNCEMENT;
		CreatureSay cs = new CreatureSay(0, sayType, "[Tournament]", text);
		for (Player player : World.getInstance().getPlayers())
		{
			if (player.isOnline())
				player.sendPacket(cs);
		}
	}
	
	class FinishEventTask implements Runnable
	{
		FinishEventTask()
		{
			
		}
		
		@Override
		public void run()
		{
			finishEvent();
			
		}
		
	}
	
	public void finishEvent()
	{
		_log.info("----------------------------------------------------------------------------");
		_log.info("[Tournament]: Event Finished.");
		_log.info("----------------------------------------------------------------------------");
		announce("Event Finished", false);
		announce("All fights have been stored", false);
		announce("Next event: " + getNextTime(), false);
		unspawnNpc();
		setRunning(false);
		if (getFinishEventTask() != null)
		{
			getFinishEventTask().cancel(true);
			finishEventTask = null;
		}
	}
	
	public void startEvent()
	{
		_log.info("----------------------------------------------------------------------------");
		_log.info("[Tournament]: Event Started.");
		_log.info("----------------------------------------------------------------------------");
		
		// Set running first, then spawn NPC
		setRunning(true);
		spawnNpcEvent();
		
		announce("Party and Non Event PvP", false);
		announce("Battles: 1x1 / 2x2 / 3x3 / 4x4 / 5x5 / 9x9", false);
		announce("Teleport in the GK to (Tournament) Zone", false);
		announce("Event duration: " + Config.TOURNAMENT_EVENT_DURATION + " minutes", false);
		setFinishEventTask(ThreadPool.schedule(new FinishEventTask(), Config.TOURNAMENT_EVENT_DURATION * 60 * 1000));
		for (Player player : World.getInstance().getPlayers())
		{
			askTeleport(player);
		}
	}
	
	class StartEventTask implements Runnable
	{
		@Override
		public void run()
		{
			startEvent();
			
		}
		
	}
	
	public void unspawnNpc()
	{
		if (_npcSpawn == null)
		{
			return;
		}
		
		try
		{
			// Cancel any scheduled respawn first
			if (_npcSpawn.getNpc() != null)
			{
				_npcSpawn.getNpc().cancelRespawn();
			}
			
			// Set respawn delay to 0 to prevent respawn
			_npcSpawn.setRespawnDelay(0);
			
			// Use doDelete() which properly handles NPC deletion and removes from SpawnManager
			_npcSpawn.doDelete();
			
			// Also remove from SpawnManager to be safe
			SpawnManager.getInstance().deleteSpawn(_npcSpawn);
			
			// Clear reference
			_npcSpawn = null;
			_log.info("[Tournament]: NPC unspawned successfully");
		}
		catch (Exception e)
		{
			_log.warning("[Tournament]: Error unspawning NPC: " + e.getMessage());
			e.printStackTrace();
			
			// Try alternative deletion method if doDelete() fails
			try
			{
				if (_npcSpawn != null)
				{
					if (_npcSpawn.getNpc() != null)
					{
						_npcSpawn.getNpc().cancelRespawn();
						_npcSpawn.getNpc().deleteMe();
					}
					SpawnManager.getInstance().deleteSpawn(_npcSpawn);
				}
			}
			catch (Exception e2)
			{
				_log.warning("[Tournament]: Error in alternative NPC deletion: " + e2.getMessage());
			}
			
			_npcSpawn = null;
		}
	}
	
	public void spawnNpcEvent()
	{
		// Only spawn NPC if event is running
		if (!isRunning())
		{
			_log.warning("[Tournament]: Attempted to spawn NPC but event is not running!");
			return;
		}
		
		// Remove any existing NPC first to avoid duplicates
		if (_npcSpawn != null)
		{
			_log.warning("[Tournament]: NPC already exists, removing old one before spawning new.");
			unspawnNpc();
		}
		
		NpcTemplate tmpl = NpcData.getInstance().getTemplate(Config.TOURNAMENT_NPC_ID);
		Location npcLoc = Config.TOURNAMENT_NPC_LOCATION;
		try
		{
			_npcSpawn = new Spawn(tmpl);
			
			_npcSpawn.setLoc(npcLoc.getX(), npcLoc.getY(), npcLoc.getZ(), Rnd.get(65535));
			// Set respawn delay to 0 to prevent automatic respawn when NPC is deleted
			_npcSpawn.setRespawnDelay(0);
			
			SpawnManager.getInstance().addSpawn(_npcSpawn);
			_npcSpawn.doSpawn(false);
			_npcSpawn.getNpc().getStatus().setHp(9.99999999E8D);
			_npcSpawn.getNpc().isAggressive();
			_npcSpawn.getNpc().decayMe();
			_npcSpawn.getNpc().spawnMe(_npcSpawn.getNpc().getX(), _npcSpawn.getNpc().getY(), _npcSpawn.getNpc().getZ());
			_npcSpawn.getNpc().broadcastPacket(new MagicSkillUse(_npcSpawn.getNpc(), _npcSpawn.getNpc(), 1034, 1, 1, 1));
			
			_log.info("[Tournament]: NPC spawned successfully at " + npcLoc.getX() + ", " + npcLoc.getY() + ", " + npcLoc.getZ());
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		
	}
	
	public void askJoinTeam(Player leader, Player target)
	{
		ConfirmDlg confirm = new ConfirmDlg(SystemMessageId.S1.getId());
		confirm.addString("Do you wish to join " + leader.getName() + "'s Tournament Team?");
		confirm.addTime(30000);
		target.setTournamentTeamRequesterId(leader.getObjectId());
		target.setTournamentTeamBeingInvited(true);
		target.sendPacket(confirm);
		leader.sendMessage(target.getName() + " was invited to your team.");
		
	}
	
	public void askTeleport(Player player)
	{
		ConfirmDlg confirm = new ConfirmDlg(SystemMessageId.S1.getId());
		confirm.addString("Do you wish to teleport to Tournament Zone?");
		confirm.addTime(30000);
		setTournamentTeleporting(true);
		player.setTournamentTeleportRequested(true);
		ThreadPool.schedule(new Runnable()
		{
			@Override
			public void run()
			{
				setTournamentTeleporting(false);
				if (player.isTournamentTeleportRequested())
				{
					player.setTournamentTeleportRequested(false);
				}
			}
		}, 30000);
		player.sendPacket(confirm);
	}
	
	public void handleTeleportAnswer(Player player, int answer)
	{
		if (!player.isTournamentTeleportRequested())
			return;
		
		player.setTournamentTeleportRequested(false);
		
		if (answer == 1) // Yes
		{
			if (!isRunning())
			{
				player.sendMessage("Tournament event is not running!");
				return;
			}
			
			// Save current location
			player.setLastX(player.getX());
			player.setLastY(player.getY());
			player.setLastZ(player.getZ());
			
			// Teleport to Tournament Zone
			player.teleportTo(Config.TOURNAMENT_ZONE_LOCATION, 0);
			player.sendMessage("You have been teleported to Tournament Zone!");
		}
		else // No
		{
			player.sendMessage("Teleport cancelled.");
		}
	}
	
	public void debugInfo(String text)
	{
		_log.info("[Tournament]: " + text);
	}
	
	public List<TournamentFight> getCurrentFights(TournamentFightType type)
	{
		List<TournamentFight> list = new ArrayList<>();
		for (Map.Entry<Integer, TournamentFight> entry : currentFights.entrySet())
		{
			if (entry.getValue().getFightType() == type)
				list.add(entry.getValue());
		}
		return list;
	}
	
	public TournamentFight getFight(int id)
	{
		return currentFights.get(id);
	}
	
	public Map<Integer, TournamentFight> getCurrentFights()
	{
		return currentFights;
	}
	
	public void setCurrentFights(Map<Integer, TournamentFight> currentFights)
	{
		this.currentFights = currentFights;
	}
	
	public void onDisconnect(Player player)
	{
		if (player.isInTournamentTeam())
		{
			TournamentTeam team = player.getTournamentTeam();
			team.getMembers().remove(player);
			team.sendMessage(player.getName() + " left the Tournament Team.");
			if (team.getMembers().size() <= 1)
			{
				team.disbandTeam();
				return;
			}
			if (team.isLeader(player))
			{
				Player newLeader = team.getMembers().get(Rnd.get(team.getMembers().size()));
				if (newLeader != null)
				{
					team.setLeader(newLeader);
					newLeader.sendMessage("You has became the new Tournament Team Leader");
				}
				team.sendMessage(newLeader.getName() + " has became the new Team Leader");
				
			}
		}
	}
	
	public void onKill(Creature killer, Player killed)
	{
		if (killed.isInTournamentMatch())
		{
			if (killer instanceof Player)
			{
				Player killerPlayer = killer.getActingPlayer();
				if (killerPlayer.isInTournamentMatch())
				{
					if (killerPlayer.getTournamentFightId() == killed.getTournamentFightId() && killed.getTournamentFightId() != 0)
					{
						TournamentFight fight = getFight(killed.getTournamentFightId());
						if (fight != null)
						{
							// add single kill to killer
							killerPlayer.addTournamentKill(killerPlayer.getTournamentFightType());
							killerPlayer.sendMessage("Killed Tournament Enemy: " + killed.getName());
							
							// Check if the killed player's team is defeated
							// Use a small delay to ensure death state is updated
							if (killed.getTournamentTeam() != null)
							{
								TournamentTeam killedTeam = killed.getTournamentTeam();
								ThreadPool.schedule(() ->
								{
									if (killedTeam.teamIsDefeated())
									{
										// Determine winner (the other team)
										TournamentTeam winner = null;
										if (fight.getTeamOne() == killedTeam)
										{
											winner = fight.getTeamTwo();
										}
										else if (fight.getTeamTwo() == killedTeam)
										{
											winner = fight.getTeamOne();
										}
										
										if (winner != null)
										{
											fight.finish(winner);
										}
										else
										{
											fight.finish();
										}
									}
								}, 100); // Small delay to ensure death state is updated
							}
						}
					}
				}
			}
		}
	}
	
	public List<TournamentTeam> getRegisteredTeamsByType(TournamentFightType type)
	{
		List<TournamentTeam> teams = new ArrayList<>();
		for (Map.Entry<TournamentTeam, TournamentFightType> entry : registeredTournamentTeams.entrySet())
		{
			if (entry.getValue().equals(type))
			{
				teams.add(entry.getKey());
			}
		}
		return teams;
	}
	
	/**
	 * @return the tournamentTeams
	 */
	public Map<TournamentTeam, TournamentFightType> getRegisteredTournamentTeams()
	{
		return registeredTournamentTeams;
	}
	
	/**
	 * @param tournamentTeams the tournamentTeams to set
	 */
	public void setTournamentTeams(Map<TournamentTeam, TournamentFightType> tournamentTeams)
	{
		this.registeredTournamentTeams = tournamentTeams;
	}
	
	public boolean isInTournamentMode(Player player)
	{
		for (Map.Entry<TournamentTeam, TournamentFightType> entry : registeredTournamentTeams.entrySet())
		{
			if (entry.getKey().getMembers().contains(player))
			{
				return true;
			}
			
		}
		return false;
	}
	
	// Npc html part
	
	public void showHtml(Player player, String page, TournamentFightType type)
	{
		NpcHtmlMessage htm = new NpcHtmlMessage(0);
		htm.setFile("data/html/mods/tournament/" + page + ".htm");
		
		htm.replace("%missingMembers%", getMembersMessageForFightType(player, type));
		htm.replace("%memberslist%", player.getTournamentTeam() != null ? generateMemberList(player.getTournamentTeam()) : "<br><font color=ff0000>You haven't a Tournament Team</font>");
		htm.replace("%inviteBoxRegButton%", getInviteBoxOrRegisterButton(player, type));
		htm.replace("%fightType%", type.equals(TournamentFightType.NONE) ? "" : type.name().substring(1).toLowerCase());
		
		// Fight Data
		for (Map.Entry<TournamentFightType, Integer> entry : player.getTournamentVictories().entrySet())
		{
			Integer value = player.getTournamentVictories().get(entry.getKey());
			if (value != null)
				htm.replace("%victories" + entry.getKey().name() + "%", value);
		}
		for (Map.Entry<TournamentFightType, Integer> entry : player.getTournamentDefeats().entrySet())
		{
			Integer value = player.getTournamentDefeats().get(entry.getKey());
			if (value != null)
				htm.replace("%defeats" + entry.getKey().name() + "%", value);
		}
		for (Map.Entry<TournamentFightType, Integer> entry : player.getTournamentTies().entrySet())
		{
			Integer value = player.getTournamentTies().get(entry.getKey());
			if (value != null)
				htm.replace("%ties" + entry.getKey().name() + "%", value);
		}
		for (Map.Entry<TournamentFightType, Integer> entry : player.getTournamentKills().entrySet())
		{
			Integer value = player.getTournamentKills().get(entry.getKey());
			if (value != null)
				htm.replace("%kills" + entry.getKey().name() + "%", value);
		}
		for (Map.Entry<TournamentFightType, Integer> entry : player.getTournamentDamage().entrySet())
		{
			Integer value = player.getTournamentDamage().get(entry.getKey());
			if (value != null)
				htm.replace("%damage" + entry.getKey().name() + "%", value);
		}
		for (Map.Entry<TournamentFightType, Integer> entry : player.getTournamentDamage().entrySet())
		{
			htm.replace("%dpf" + entry.getKey().name() + "%", getDamagePerFight(player, entry.getKey()));
		}
		for (Map.Entry<TournamentFightType, Integer> entry : player.getTournamentVictories().entrySet())
		{
			htm.replace("%fightsDone" + entry.getKey().name() + "%", player.getTournamentFightsDone(entry.getKey()));
		}
		for (Map.Entry<TournamentFightType, Integer> entry : player.getTournamentVictories().entrySet())
		{
			htm.replace("%teamsReg" + entry.getKey().name() + "%", registeredTournamentTeams.size());
		}
		for (Map.Entry<TournamentFightType, Integer> entry : player.getTournamentVictories().entrySet())
		{
			htm.replace("%activeFights" + entry.getKey().name() + "%", getCurrentFights(entry.getKey()).size());
		}
		
		htm.replace("%allTimeFights%", getAllTimeFights());
		htm.replace("%tourPoints%", player.getTournamentPoints());
		htm.replace("%killstotal%", player.getTotalTournamentKills());
		htm.replace("%totalDmg%", player.getTournamentTotalDamage());
		htm.replace("%playerName%", player.getName());
		htm.replace("%dpfTotal%", getDamagePerFight(player));
		htm.replace("%wdt%", getWinDefeatTie(player));
		htm.replace("%totalFights%", player.getTotalTournamentFightsDone());
		
		player.sendPacket(htm);
	}
	
	public String getInviteBoxOrRegisterButton(Player player, TournamentFightType type)
	{
		StringBuilder sb = new StringBuilder();
		if (getMissingMembersForFightType(player, type) == 0)
		{
			sb.append("<table width=300>");
			sb.append("<tr>");
			sb.append("<td align=center><font color=LEVEL> Your team is ready!!</font></td>");
			sb.append("</tr>");
			sb.append("</table>");
		}
		else
		{
			sb.append("<center>");
			sb.append("Type the name of your partner or use command: <br1><font color=994992>\".tournamentinvite playername\"</font>");
			sb.append("</center>");
			sb.append("<table width=300>");
			sb.append("<tr>");
			sb.append("<td>Player Name</td>");
			sb.append("<td><edit var=\"playerName\" width=120 height=15></td>");
			sb.append("<td><button value=\"Invite\" action=\"bypass -h bp_inviteTournamentMember $playerName\" width=45 height=15 back=\"sek.cbui94\" fore=\"sek.cbui92\"></td>");
			sb.append("</tr>");
			sb.append("</table>");
		}
		
		return sb.toString();
	}
	
	public String getMembersMessageForFightType(Player player, TournamentFightType type)
	{
		if (!player.isInTournamentTeam())
		{
			return "<br><font color=ff0000>You haven't a Tournament Team</font>";
		}
		if (type != TournamentFightType.NONE)
		{
			return "<br>You need to invite <font color=LEVEL>" + getMissingMembersForFightType(player, type) + "</font> to register " + type.name().substring(1).toLowerCase() + " fights.";
		}
		return "";
	}
	
	public int getMissingMembersForFightType(Player player, TournamentFightType type)
	{
		int membersCount = 0;
		if (!player.isInTournamentTeam())
		{
			return -1;
		}
		membersCount = player.getTournamentTeam().getMembers().size();
		switch (type)
		{
			case F1X1:
				return 0;
			case F2X2:
				return 2 - membersCount;
			case F3X3:
				return 3 - membersCount;
			case F4X4:
				return 4 - membersCount;
			case F5X5:
				return 5 - membersCount;
			case F9X9:
				return 9 - membersCount;
			default:
				return -1;
		}
	}
	
	public String generateMemberList(TournamentTeam team)
	{
		StringBuilder sb = new StringBuilder();
		int bgcolor = 0;
		for (Player member : team.getMembers())
		{
			sb.append("<img src=\"Sek.cbui371\" width=300 height=1>");
			if (bgcolor % 2 == 0)
				sb.append("<table width=315  bgcolor=090000>");
			else
				sb.append("<table width=315 bgcolor=000000>");
			
			sb.append("<tr>");
			sb.append("<td fixwidth=50></td>");
			sb.append("<td align=center>");
			sb.append("<font color=LEVEL>" + member.getName() + "</font>");
			sb.append("</td>");
			sb.append("<td fixwidth=50></td>");
			sb.append("</tr>");
			sb.append("</table>");
			bgcolor++;
		}
		
		return sb.toString();
	}
	
	public void onPlayerEnter(Player player)
	{
		// catch data from memo
		loadTournamentData(player);
		
		// check data and insert if have no result for all types
		for (Map.Entry<TournamentFightType, Integer> entry : player.getTournamentVictories().entrySet())
		{
			checkData(player, entry.getKey());
		}
	}
	
	public void checkData(Player player, TournamentFightType type)
	{
		
		Connection con = null;
		PreparedStatement offline = null;
		ResultSet rs = null;
		try
		{
			con = ConnectionPool.getConnection();
			offline = con.prepareStatement("SELECT * FROM tournament_player_data WHERE obj_id=? AND fight_type=?");
			offline.setInt(1, player.getObjectId());
			offline.setString(2, type.name());
			rs = offline.executeQuery();
			boolean hasResult = rs.next();
			if (!hasResult)
			{
				insertData(player, type);
			}
			
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		finally
		{
			closeQuietly(con, offline, rs);
		}
		
	}
	
	public void insertData(Player player, TournamentFightType type)
	{
		
		Connection con = null;
		PreparedStatement offline = null;
		ResultSet rs = null;
		try
		{
			con = ConnectionPool.getConnection();
			offline = con.prepareStatement("REPLACE INTO tournament_player_data (obj_id, fight_type, fights_done, victories, defeats, ties, kills, damage, wdt, dpf) VALUES (?,?,?,?,?,?,?,?,?,?)");
			offline.setInt(1, player.getObjectId());
			offline.setString(2, type.name());
			offline.setInt(3, player.getTournamentFightsDone(type));
			Integer victories = player.getTournamentVictories().get(type);
			Integer defeats = player.getTournamentDefeats().get(type);
			Integer ties = player.getTournamentTies().get(type);
			Integer kills = player.getTournamentKills().get(type);
			Integer damage = player.getTournamentDamage().get(type);
			offline.setInt(4, victories != null ? victories : 0);
			offline.setInt(5, defeats != null ? defeats : 0);
			offline.setInt(6, ties != null ? ties : 0);
			offline.setInt(7, kills != null ? kills : 0);
			offline.setInt(8, damage != null ? damage : 0);
			offline.setString(9, "" + getWinDefeatTie(player, type));
			offline.setInt(10, getDamagePerFight(player, type));
			offline.execute();
			
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		finally
		{
			closeQuietly(con, offline, rs);
		}
		
	}
	
	public void updateData(Player player, TournamentFightType type)
	{
		
		Connection con = null;
		PreparedStatement offline = null;
		ResultSet rs = null;
		try
		{
			con = ConnectionPool.getConnection();
			offline = con.prepareStatement("UPDATE tournament_player_data SET fights_done=?, victories=?, defeats=?, ties=?, kills=?, damage=?, wdt=?, dpf=? WHERE obj_id=? AND fight_type=?");
			offline.setInt(1, player.getTournamentFightsDone(type));
			Integer victories = player.getTournamentVictories().get(type);
			Integer defeats = player.getTournamentDefeats().get(type);
			Integer ties = player.getTournamentTies().get(type);
			Integer kills = player.getTournamentKills().get(type);
			Integer damage = player.getTournamentDamage().get(type);
			offline.setInt(2, victories != null ? victories : 0);
			offline.setInt(3, defeats != null ? defeats : 0);
			offline.setInt(4, ties != null ? ties : 0);
			offline.setInt(5, kills != null ? kills : 0);
			offline.setInt(6, damage != null ? damage : 0);
			offline.setString(7, "" + getWinDefeatTie(player, type));
			offline.setInt(8, getDamagePerFight(player, type));
			offline.setInt(9, player.getObjectId());
			offline.setString(10, type.name());
			offline.execute();
			
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		finally
		{
			closeQuietly(con, offline, rs);
		}
		
	}
	
	public void initializeTournamentMaps(Player player)
	{
		player.getTournamentDamage().put(TournamentFightType.F1X1, 0);
		player.getTournamentDamage().put(TournamentFightType.F2X2, 0);
		player.getTournamentDamage().put(TournamentFightType.F3X3, 0);
		player.getTournamentDamage().put(TournamentFightType.F4X4, 0);
		player.getTournamentDamage().put(TournamentFightType.F5X5, 0);
		player.getTournamentDamage().put(TournamentFightType.F9X9, 0);
		
		player.getTournamentDefeats().put(TournamentFightType.F1X1, 0);
		player.getTournamentDefeats().put(TournamentFightType.F2X2, 0);
		player.getTournamentDefeats().put(TournamentFightType.F3X3, 0);
		player.getTournamentDefeats().put(TournamentFightType.F4X4, 0);
		player.getTournamentDefeats().put(TournamentFightType.F5X5, 0);
		player.getTournamentDefeats().put(TournamentFightType.F9X9, 0);
		
		player.getTournamentVictories().put(TournamentFightType.F1X1, 0);
		player.getTournamentVictories().put(TournamentFightType.F2X2, 0);
		player.getTournamentVictories().put(TournamentFightType.F3X3, 0);
		player.getTournamentVictories().put(TournamentFightType.F4X4, 0);
		player.getTournamentVictories().put(TournamentFightType.F5X5, 0);
		player.getTournamentVictories().put(TournamentFightType.F9X9, 0);
		
		player.getTournamentTies().put(TournamentFightType.F1X1, 0);
		player.getTournamentTies().put(TournamentFightType.F2X2, 0);
		player.getTournamentTies().put(TournamentFightType.F3X3, 0);
		player.getTournamentTies().put(TournamentFightType.F4X4, 0);
		player.getTournamentTies().put(TournamentFightType.F5X5, 0);
		player.getTournamentTies().put(TournamentFightType.F9X9, 0);
		
		player.getTournamentKills().put(TournamentFightType.F1X1, 0);
		player.getTournamentKills().put(TournamentFightType.F2X2, 0);
		player.getTournamentKills().put(TournamentFightType.F3X3, 0);
		player.getTournamentKills().put(TournamentFightType.F4X4, 0);
		player.getTournamentKills().put(TournamentFightType.F5X5, 0);
		player.getTournamentKills().put(TournamentFightType.F9X9, 0);
	}
	
	// store/load fights methods
	public void loadTournamentData(Player player)
	{
		initializeTournamentMaps(player);
		Connection con = null;
		PreparedStatement offline = null;
		ResultSet rs = null;
		try
		{
			con = ConnectionPool.getConnection();
			offline = con.prepareStatement("SELECT * FROM character_memo_alt WHERE obj_id =?");
			offline.setInt(1, player.getObjectId());
			rs = offline.executeQuery();
			
			while (rs.next())
			{
				if (rs.getString("name").startsWith("Tournament"))
				{
					StringTokenizer st = new StringTokenizer(rs.getString("name"), "-");
					st.nextToken(); // "Tournament"
					switch (st.nextToken())
					{
						case "Victories":
							if (st.hasMoreTokens())
							{
								try
								{
									player.getTournamentVictories().put(TournamentFightType.valueOf(st.nextToken()), rs.getInt("value"));
								}
								catch (IllegalArgumentException e)
								{
									// Invalid fight type, skip
								}
							}
							break;
						case "Defeats":
							if (st.hasMoreTokens())
							{
								try
								{
									player.getTournamentDefeats().put(TournamentFightType.valueOf(st.nextToken()), rs.getInt("value"));
								}
								catch (IllegalArgumentException e)
								{
									// Invalid fight type, skip
								}
							}
							break;
						case "Ties":
							if (st.hasMoreTokens())
							{
								try
								{
									player.getTournamentTies().put(TournamentFightType.valueOf(st.nextToken()), rs.getInt("value"));
								}
								catch (IllegalArgumentException e)
								{
									// Invalid fight type, skip
								}
							}
							break;
						case "Kills":
							if (st.hasMoreTokens())
							{
								try
								{
									player.getTournamentKills().put(TournamentFightType.valueOf(st.nextToken()), rs.getInt("value"));
								}
								catch (IllegalArgumentException e)
								{
									// Invalid fight type, skip
								}
							}
							break;
						
						case "Damage":
							if (st.hasMoreTokens())
							{
								try
								{
									player.getTournamentDamage().put(TournamentFightType.valueOf(st.nextToken()), rs.getInt("value"));
								}
								catch (IllegalArgumentException e)
								{
									// Invalid fight type, skip
								}
							}
							break;
						case "Points":
							player.setTournamentPoints(rs.getInt("value"));
							break;
					}
					
				}
				
			}
			
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		finally
		{
			closeQuietly(con, offline, rs);
		}
	}
	
	public int getTournamentPlayerFightsDone(int objectId, TournamentFightType type)
	{
		Connection con = null;
		PreparedStatement offline = null;
		ResultSet rs = null;
		int fights = 0;
		try
		{
			con = ConnectionPool.getConnection();
			offline = con.prepareStatement("SELECT * FROM character_memo_alt WHERE obj_id =?");
			offline.setInt(1, objectId);
			rs = offline.executeQuery();
			
			while (rs.next())
			{
				if (rs.getString("name").startsWith("Tournament"))
				{
					StringTokenizer st = new StringTokenizer(rs.getString("name"), "-");
					st.nextToken(); // "Tournament"
					String nextToken = st.nextToken();
					if (nextToken.equals("Victories") || nextToken.equals("Defeats") || nextToken.equals("Ties"))
					{
						if (st.hasMoreTokens())
						{
							String fightTypeStr = st.nextToken();
							if (fightTypeStr.equals(type.name()))
							{
								fights += rs.getInt("value");
							}
						}
					}
					
				}
				
			}
			
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		finally
		{
			closeQuietly(con, offline, rs);
		}
		return fights;
	}
	
	public void showPlayerTournamentData(Player player, int targetObjectId, TournamentFightType type, Map<TournamentFightType, Integer> tournamentKills, Map<TournamentFightType, Integer> tournamentVictories, Map<TournamentFightType, Integer> tournamentDefeats, Map<TournamentFightType, Integer> tournamentTies, Map<TournamentFightType, Integer> tournamentDamage)
	{
		NpcHtmlMessage htm = new NpcHtmlMessage(0);
		htm.setFile("data/html/mods/tournament/ranking/info/playerInfo" + type.name() + ".htm");
		
		// Fight Data
		for (Map.Entry<TournamentFightType, Integer> entry : tournamentVictories.entrySet())
		{
			Integer value = tournamentVictories.get(entry.getKey());
			if (value != null)
				htm.replace("%victories" + entry.getKey().name() + "%", value);
		}
		for (Map.Entry<TournamentFightType, Integer> entry : tournamentDefeats.entrySet())
		{
			Integer value = tournamentDefeats.get(entry.getKey());
			if (value != null)
				htm.replace("%defeats" + entry.getKey().name() + "%", value);
		}
		for (Map.Entry<TournamentFightType, Integer> entry : tournamentTies.entrySet())
		{
			Integer value = tournamentTies.get(entry.getKey());
			if (value != null)
				htm.replace("%ties" + entry.getKey().name() + "%", value);
		}
		for (Map.Entry<TournamentFightType, Integer> entry : tournamentKills.entrySet())
		{
			Integer value = tournamentKills.get(entry.getKey());
			if (value != null)
				htm.replace("%kills" + entry.getKey().name() + "%", value);
		}
		for (Map.Entry<TournamentFightType, Integer> entry : tournamentDamage.entrySet())
		{
			Integer value = tournamentDamage.get(entry.getKey());
			if (value != null)
				htm.replace("%damage" + entry.getKey().name() + "%", value);
		}
		for (Map.Entry<TournamentFightType, Integer> entry : tournamentDamage.entrySet())
		{
			htm.replace("%dpf" + entry.getKey().name() + "%", "Not Showing");
		}
		for (Map.Entry<TournamentFightType, Integer> entry : player.getTournamentVictories().entrySet())
		{
			htm.replace("%fightsDone" + entry.getKey().name() + "%", getTournamentPlayerFightsDone(targetObjectId, type));
		}
		
		htm.replace("%tourPoints%", player.getTournamentPoints());
		htm.replace("%killstotal%", player.getTotalTournamentKills());
		htm.replace("%totalDmg%", player.getTournamentTotalDamage());
		htm.replace("%playerName%", PlayerInfoTable.getInstance().getPlayerName(targetObjectId));
		htm.replace("%dpfTotal%", getDamagePerFight(player));
		htm.replace("%wdt%", getWinDefeatTie(player));
		htm.replace("%totalFights%", player.getTotalTournamentFightsDone());
		
		player.sendPacket(htm);
	}
	
	public void showPlayerRankingData(Player player, int targetObjectId, TournamentFightType type)
	{
		Map<TournamentFightType, Integer> tournamentKills = new HashMap<>();
		Map<TournamentFightType, Integer> tournamentVictories = new HashMap<>();
		Map<TournamentFightType, Integer> tournamentDefeats = new HashMap<>();
		Map<TournamentFightType, Integer> tournamentTies = new HashMap<>();
		Map<TournamentFightType, Integer> tournamentDamage = new HashMap<>();
		
		Connection con = null;
		PreparedStatement offline = null;
		ResultSet rs = null;
		try
		{
			con = ConnectionPool.getConnection();
			offline = con.prepareStatement("SELECT * FROM character_memo_alt WHERE obj_id =?");
			offline.setInt(1, targetObjectId);
			rs = offline.executeQuery();
			
			while (rs.next())
			{
				if (rs.getString("name").startsWith("Tournament"))
				{
					StringTokenizer st = new StringTokenizer(rs.getString("name"), "-");
					st.nextToken(); // "Tournament"
					switch (st.nextToken())
					{
						case "Victories":
							if (st.hasMoreTokens())
							{
								try
								{
									tournamentVictories.put(TournamentFightType.valueOf(st.nextToken()), rs.getInt("value"));
								}
								catch (IllegalArgumentException e)
								{
									// Invalid fight type, skip
								}
							}
							break;
						case "Defeats":
							if (st.hasMoreTokens())
							{
								try
								{
									tournamentDefeats.put(TournamentFightType.valueOf(st.nextToken()), rs.getInt("value"));
								}
								catch (IllegalArgumentException e)
								{
									// Invalid fight type, skip
								}
							}
							break;
						case "Ties":
							if (st.hasMoreTokens())
							{
								try
								{
									tournamentTies.put(TournamentFightType.valueOf(st.nextToken()), rs.getInt("value"));
								}
								catch (IllegalArgumentException e)
								{
									// Invalid fight type, skip
								}
							}
							break;
						case "Kills":
							if (st.hasMoreTokens())
							{
								try
								{
									tournamentKills.put(TournamentFightType.valueOf(st.nextToken()), rs.getInt("value"));
								}
								catch (IllegalArgumentException e)
								{
									// Invalid fight type, skip
								}
							}
							break;
						
						case "Damage":
							if (st.hasMoreTokens())
							{
								try
								{
									tournamentDamage.put(TournamentFightType.valueOf(st.nextToken()), rs.getInt("value"));
								}
								catch (IllegalArgumentException e)
								{
									// Invalid fight type, skip
								}
							}
							break;
						
					}
					
				}
				
			}
			
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		finally
		{
			closeQuietly(con, offline, rs);
		}
		
		showPlayerTournamentData(player, targetObjectId, type, tournamentKills, tournamentVictories, tournamentDefeats, tournamentTies, tournamentDamage);
	}
	
	class WDTRecord
	{
		String playerName;
		
	}
	
	public void storeTournamentData(Player player)
	{
		for (Map.Entry<TournamentFightType, Integer> entry : player.getTournamentVictories().entrySet())
		{
			player.getMemos().set("Tournament-Victories-" + entry.getKey().name(), String.valueOf(entry.getValue()));
		}
		for (Map.Entry<TournamentFightType, Integer> entry : player.getTournamentDefeats().entrySet())
		{
			player.getMemos().set("Tournament-Defeats-" + entry.getKey().name(), String.valueOf(entry.getValue()));
		}
		for (Map.Entry<TournamentFightType, Integer> entry : player.getTournamentTies().entrySet())
		{
			player.getMemos().set("Tournament-Ties-" + entry.getKey().name(), String.valueOf(entry.getValue()));
		}
		for (Map.Entry<TournamentFightType, Integer> entry : player.getTournamentKills().entrySet())
		{
			player.getMemos().set("Tournament-Kills-" + entry.getKey().name(), String.valueOf(entry.getValue()));
		}
		for (Map.Entry<TournamentFightType, Integer> entry : player.getTournamentDamage().entrySet())
		{
			player.getMemos().set("Tournament-Damage-" + entry.getKey().name(), String.valueOf(entry.getValue()));
		}
		for (Map.Entry<TournamentFightType, Integer> entry : player.getTournamentDamage().entrySet())
		{
			player.getMemos().set("Tournament-WDT-" + entry.getKey().name(), String.valueOf(getWinDefeatTie(player, entry.getKey())));
		}
		for (Map.Entry<TournamentFightType, Integer> entry : player.getTournamentDamage().entrySet())
		{
			player.getMemos().set("Tournament-DPF-" + entry.getKey().name(), String.valueOf(getDamagePerFight(player, entry.getKey())));
		}
		
		player.getMemos().set("Tournament-Points", String.valueOf(player.getTournamentPoints()));
		
	}
	
	/**
	 * @return the started
	 */
	public boolean isRunning()
	{
		return running;
	}
	
	/**
	 * @param started the started to set
	 */
	public void setRunning(boolean started)
	{
		this.running = started;
	}
	
	/**
	 * @return the finishEvent
	 */
	public ScheduledFuture<?> getFinishEventTask()
	{
		return finishEventTask;
	}
	
	/**
	 * @param finishEvent the finishEvent to set
	 */
	public void setFinishEventTask(ScheduledFuture<?> finishEvent)
	{
		this.finishEventTask = finishEvent;
	}
	
	/**
	 * @return the tournamentTeleporting
	 */
	public boolean isTournamentTeleporting()
	{
		return tournamentTeleporting;
	}
	
	/**
	 * @param tournamentTeleporting the tournamentTeleporting to set
	 */
	public void setTournamentTeleporting(boolean tournamentTeleporting)
	{
		this.tournamentTeleporting = tournamentTeleporting;
	}
	
	public double getDamagePerFight(int totalDamage, int totalFightsDone)
	{
		double dpf = 0;
		if (totalFightsDone == 0)
		{
			return 0;
		}
		dpf = (totalDamage / totalFightsDone * 1000);
		return dpf;
	}
	
	public int getDamagePerFight(Player player, TournamentFightType type)
	{
		int dpf = 0;
		int totalDamage = player.getTournamentDamage().getOrDefault(type, 0);
		int totalFightsDone = player.getTournamentFightsDone(type);
		if (totalFightsDone == 0)
		{
			return 0;
		}
		dpf = (totalDamage / totalFightsDone);
		return dpf;
	}
	
	public double getWinDefeatTie(int totalFightsDone, int totalVictories, int totalDefeats, int totalTies)
	{
		int ratioByFight = 1;
		double playerWDT = 0;
		if (totalFightsDone == 0)
		{
			return 0;
		}
		playerWDT = ratioByFight * (((3) * totalVictories) + ((-3) * totalDefeats) + (totalTies)) / totalFightsDone;
		return playerWDT;
	}
	
	public double getDamagePerFight(Player player)
	{
		double dpf = 0;
		int totalDamage = player.getTournamentTotalDamage();
		int totalFightsDone = player.getTotalTournamentFightsDone();
		if (totalFightsDone == 0)
		{
			return 0;
		}
		dpf = (totalDamage / totalFightsDone);
		return dpf;
	}
	
	public double getWinDefeatTie(Player player)
	{
		int ratioByFight = 1;
		double playerWDT = 0;
		int totalFightsDone = player.getTotalTournamentFightsDone();
		int totalVictories = 0;
		int totalDefeats = 0;
		int totalTies = 0;
		for (Integer victories : player.getTournamentVictories().values())
			totalVictories += victories;
		for (Integer defeats : player.getTournamentDefeats().values())
			totalDefeats += defeats;
		for (Integer ties : player.getTournamentTies().values())
			totalTies += ties;
		if (totalFightsDone == 0)
		{
			return 0;
		}
		playerWDT = ratioByFight * (((3) * totalVictories) + ((-3) * totalDefeats) + (totalTies)) / totalFightsDone;
		return playerWDT;
	}
	
	public double getWinDefeatTie(Player player, TournamentFightType type)
	{
		int ratioByFight = 1;
		double playerWDT = 0;
		int totalFightsDone = player.getTournamentFightsDone(type);
		Integer victories = player.getTournamentVictories().get(type);
		Integer defeats = player.getTournamentDefeats().get(type);
		Integer ties = player.getTournamentTies().get(type);
		int totalVictories = victories != null ? victories : 0;
		int totalDefeats = defeats != null ? defeats : 0;
		int totalTies = ties != null ? ties : 0;
		if (totalFightsDone == 0)
		{
			return 0;
		}
		playerWDT = ratioByFight * (((3) * totalVictories) + ((-3) * totalDefeats) + (totalTies)) / totalFightsDone;
		return playerWDT;
	}
	
	// RANKING
	class TourRankRecord
	{
		int pos;
		String playerName;
		String recordVal;
		
		public TourRankRecord(int pos, String playerName, String recordVal)
		{
			this.pos = pos + 1;
			this.playerName = playerName;
			this.recordVal = recordVal;
		}
	}
	
	public String generateRankingRecords(Player player, TournamentFightType type, LinkedList<TourRankRecord> records, String rankType)
	{
		StringBuilder sb = new StringBuilder();
		int bgColor = 1;
		for (TourRankRecord record : records)
		{
			if (record == null)
				continue;
			if (bgColor % 2 == 0)
				sb.append("<table width=300 bgcolor=000000>");
			else
				sb.append("<table width=300>");
			sb.append("<tr>");
			sb.append("<td align=center fixwidth=20>");
			sb.append(record.pos);
			sb.append("</td>");
			sb.append("<td fixwidth=5></td>");
			sb.append("<td align=center fixwidth=75>");
			sb.append(record.playerName);
			sb.append("</td>");
			sb.append("<td align=center fixwidth=50>");
			sb.append(record.recordVal);
			sb.append("</td>");
			sb.append("<td align=center fixwidth=50>");
			sb.append("<a action=\"bypass bp_checkTournamentPlayer " + record.playerName + " " + type.name() + "\"><font color=LEVEL>Check</font></a>");
			sb.append("</td>");
			sb.append("</tr>");
			sb.append("</table>");
			bgColor++;
			
		}
		return sb.toString();
	}
	
	public void showRanking(Player player, TournamentFightType fightType, String rankType)
	{
		NpcHtmlMessage htm = new NpcHtmlMessage(0);
		htm.setFile("data/html/mods/tournament/ranking/" + rankType + "/" + fightType.name() + ".htm");
		
		LinkedList<TourRankRecord> records = new LinkedList<>();
		int pos = 0;
		Connection con = null;
		PreparedStatement offline = null;
		ResultSet rs = null;
		try
		{
			con = ConnectionPool.getConnection();
			offline = con.prepareStatement("SELECT * FROM tournament_player_data WHERE fight_type=? ORDER BY " + rankType + " DESC LIMIT 10");
			offline.setString(1, fightType.name());
			rs = offline.executeQuery();
			while (rs.next())
			{
				
				records.add(new TourRankRecord(pos, PlayerInfoTable.getInstance().getPlayerName(rs.getInt("obj_id")), String.valueOf(rs.getInt(rankType))));
				pos++;
				
			}
			
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		finally
		{
			closeQuietly(con, offline, rs);
		}
		for (Map.Entry<TournamentFightType, Integer> entry : player.getTournamentVictories().entrySet())
			htm.replace("%ranking-" + rankType + entry.getKey() + "%", generateRankingRecords(player, fightType, records, rankType));
		player.sendPacket(htm);
	}
	
	/**
	 * @return the allTimeFights
	 */
	public int getAllTimeFights()
	{
		return allTimeFights;
	}
	
	/**
	 * @param allTimeFights the allTimeFights to set
	 */
	public void setAllTimeFights(int allTimeFights)
	{
		this.allTimeFights = allTimeFights;
	}
}

