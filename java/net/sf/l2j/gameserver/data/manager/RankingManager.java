package net.sf.l2j.gameserver.data.manager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.commons.pool.ConnectionPool;

import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.actor.Player;

/**
 * Ranking Manager
 * Manages player rankings (PvP, PK, Level, PvE) and rewards
 */
public class RankingManager
{
	private static final CLogger LOGGER = new CLogger(RankingManager.class.getName());
	
	private static final String UPDATE_RANKING_DATA = "INSERT INTO ranking_data (player_id, player_name, pvp_kills, pk_kills, level, pve_kills, pve_exp, last_update) VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE pvp_kills=VALUES(pvp_kills), pk_kills=VALUES(pk_kills), level=VALUES(level), pve_kills=VALUES(pve_kills), pve_exp=VALUES(pve_exp), last_update=VALUES(last_update)";
	private static final String GET_RANKING_DATA = "SELECT * FROM ranking_data WHERE player_id = ?";
	private static final String UPDATE_PVE_STATS = "UPDATE ranking_data SET pve_kills = pve_kills + ?, last_update = ? WHERE player_id = ?";
	
	protected RankingManager()
	{
	}
	
	public static RankingManager getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private static class SingletonHolder
	{
		protected static final RankingManager INSTANCE = new RankingManager();
	}
	
	/**
	 * Update player ranking data from database
	 */
	public void updatePlayerRanking(Player player)
	{
		try (Connection con = ConnectionPool.getConnection();
			PreparedStatement ps = con.prepareStatement(UPDATE_RANKING_DATA))
		{
			ps.setInt(1, player.getObjectId());
			ps.setString(2, player.getName());
			ps.setInt(3, player.getPvpKills());
			ps.setInt(4, player.getPkKills());
			ps.setInt(5, player.getStatus().getLevel());
			ps.setLong(6, 0); // Will be updated separately
			ps.setLong(7, 0); // Will be updated separately
			ps.setLong(8, System.currentTimeMillis());
			
			ps.executeUpdate();
		}
		catch (SQLException e)
		{
			LOGGER.error("Error updating player ranking data for " + player.getName(), e);
		}
	}
	
	/**
	 * Update PvE stats (kills only)
	 */
	public void updatePvEStats(Player player)
	{
		try (Connection con = ConnectionPool.getConnection();
			PreparedStatement ps = con.prepareStatement(UPDATE_PVE_STATS))
		{
			ps.setLong(1, 1); // +1 kill
			ps.setLong(2, System.currentTimeMillis());
			ps.setInt(3, player.getObjectId());
			
			ps.executeUpdate();
		}
		catch (SQLException e)
		{
			LOGGER.error("Error updating PvE stats for " + player.getName(), e);
		}
	}
	
	/**
	 * Get top players for a specific ranking type
	 */
	public List<RankingEntry> getTopRankings(String rankingType, int limit)
	{
		List<RankingEntry> rankings = new ArrayList<>();
		
		String orderBy;
		switch (rankingType.toLowerCase())
		{
			case "pvp":
				orderBy = "rd.pvp_kills DESC";
				break;
			case "pk":
				orderBy = "rd.pk_kills DESC";
				break;
			case "pve":
				orderBy = "rd.pve_kills DESC";
				break;
			default:
				return rankings;
		}
		
		String sql = "SELECT player_id, player_name, pvp_kills, pk_kills, level, pve_kills, pve_exp FROM ranking_data rd ORDER BY " + orderBy + " LIMIT ?";
		
		try (Connection con = ConnectionPool.getConnection();
			PreparedStatement ps = con.prepareStatement(sql))
		{
			ps.setInt(1, limit);
			
			try (ResultSet rs = ps.executeQuery())
			{
				int rank = 1;
				while (rs.next())
				{
					RankingEntry entry = new RankingEntry();
					entry.playerId = rs.getInt("player_id");
					entry.playerName = rs.getString("player_name");
					entry.pvpKills = rs.getInt("pvp_kills");
					entry.pkKills = rs.getInt("pk_kills");
					entry.level = rs.getInt("level");
					entry.pveKills = rs.getLong("pve_kills");
					entry.pveExp = rs.getLong("pve_exp");
					entry.rank = rank++;
					
					rankings.add(entry);
				}
			}
		}
		catch (SQLException e)
		{
			LOGGER.error("Error getting top rankings for " + rankingType, e);
		}
		
		return rankings;
	}
	
	/**
	 * Calculate and update weekly/monthly ranks
	 */
	public void calculateRanks(String period) // "weekly" or "monthly"
	{
		try (Connection con = ConnectionPool.getConnection())
		{
			// Calculate PvP ranks
			updateRanks(con, period, "pvp", "rd.pvp_kills");
			
			// Calculate PK ranks
			updateRanks(con, period, "pk", "rd.pk_kills");
			
			// Calculate PvE ranks
			updateRanks(con, period, "pve", "rd.pve_kills");
		}
		catch (SQLException e)
		{
			LOGGER.error("Error calculating " + period + " ranks", e);
		}
	}
	
	private void updateRanks(Connection con, String period, String rankingType, String orderColumn) throws SQLException
	{
		String rankColumn = period + "_" + rankingType + "_rank";
		String resetColumn = "last_" + period + "_reset";
		
		String sql = "SELECT player_id FROM ranking_data ORDER BY " + orderColumn + " DESC";
		
		// Get all players ordered by the ranking column
		List<Integer> playerIds = new ArrayList<>();
		try (PreparedStatement ps = con.prepareStatement(sql))
		{
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
				{
					playerIds.add(rs.getInt("player_id"));
				}
			}
		}
		
		// Update ranks
		int rank = 1;
		for (int playerId : playerIds)
		{
			try (PreparedStatement ps = con.prepareStatement("UPDATE ranking_data SET " + rankColumn + " = ?, " + resetColumn + " = ? WHERE player_id = ?"))
			{
				ps.setInt(1, rank++);
				ps.setLong(2, System.currentTimeMillis());
				ps.setInt(3, playerId);
				ps.executeUpdate();
			}
		}
	}
	
	/**
	 * Distribute rewards for a period
	 */
	public void distributeRewards(String period) // "weekly" or "monthly"
	{
		try (Connection con = ConnectionPool.getConnection())
		{
			// Get reward configurations
			Map<String, List<RewardConfig>> rewards = getRewardConfigs(con, period);
			
			// Distribute rewards for each ranking type
			for (String rankingType : new String[]{"pvp", "pk", "pve"})
			{
				String rankColumn = period + "_" + rankingType + "_rank";
				String claimedColumn = period + "_reward_claimed";
				
				List<RewardConfig> typeRewards = rewards.get(rankingType);
				if (typeRewards == null || typeRewards.isEmpty())
					continue;
				
				// Get top players for this ranking type
				try (PreparedStatement ps = con.prepareStatement("SELECT player_id, player_name, " + rankColumn + " FROM ranking_data WHERE " + rankColumn + " > 0 AND " + rankColumn + " <= ? AND " + claimedColumn + " = 0 ORDER BY " + rankColumn + " ASC"))
				{
					int maxRank = typeRewards.size();
					ps.setInt(1, maxRank);
					
					try (ResultSet rs = ps.executeQuery())
					{
						while (rs.next())
						{
							int playerId = rs.getInt("player_id");
							String playerName = rs.getString("player_name");
							int rank = rs.getInt(rankColumn);
							
							// Find reward for this rank
							RewardConfig reward = null;
							for (RewardConfig r : typeRewards)
							{
								if (r.rankPosition == rank)
								{
									reward = r;
									break;
								}
							}
							
							if (reward != null && reward.enabled)
							{
								// Give reward to player
								Player player = World.getInstance().getPlayer(playerName);
								if (player != null && player.isOnline())
								{
									player.addItem(reward.itemId, (int)reward.itemCount, false);
									player.sendMessage("Congratulations! You received a " + period + " ranking reward for " + rankingType.toUpperCase() + " rank #" + rank + "!");
									LOGGER.info("Distributed " + period + " " + rankingType + " rank #" + rank + " reward to " + playerName);
								}
								else
								{
									// Store reward for offline player (would need a mail system or pending rewards table)
									LOGGER.info("Player " + playerName + " (ID: " + playerId + ") earned " + period + " " + rankingType + " rank #" + rank + " reward but is offline.");
								}
								
								// Mark reward as claimed
								try (PreparedStatement updatePs = con.prepareStatement("UPDATE ranking_data SET " + claimedColumn + " = 1 WHERE player_id = ?"))
								{
									updatePs.setInt(1, playerId);
									updatePs.executeUpdate();
								}
							}
						}
					}
				}
			}
		}
		catch (SQLException e)
		{
			LOGGER.error("Error distributing " + period + " rewards", e);
		}
	}
	
	/**
	 * Reset rankings and rewards for a period
	 */
	public void resetRankings(String period) // "weekly" or "monthly"
	{
		try (Connection con = ConnectionPool.getConnection())
		{
		String rankColumnPrefix = period + "_";
		String claimedColumn = period + "_reward_claimed";
		
		// Reset all ranks and claimed flags
		String sql = "UPDATE ranking_data SET " +
			rankColumnPrefix + "pvp_rank = 0, " +
			rankColumnPrefix + "pk_rank = 0, " +
			rankColumnPrefix + "pve_rank = 0, " +
			claimedColumn + " = 0";
			
			try (PreparedStatement ps = con.prepareStatement(sql))
			{
				ps.executeUpdate();
			}
			
			// Recalculate ranks
			calculateRanks(period);
		}
		catch (SQLException e)
		{
			LOGGER.error("Error resetting " + period + " rankings", e);
		}
	}
	
	private Map<String, List<RewardConfig>> getRewardConfigs(Connection con, String period) throws SQLException
	{
		Map<String, List<RewardConfig>> rewards = new HashMap<>();
		
		try (PreparedStatement ps = con.prepareStatement("SELECT ranking_type, rank_position, item_id, item_count FROM ranking_rewards WHERE reward_type = ? AND enabled = 1 ORDER BY ranking_type, rank_position"))
		{
			ps.setString(1, period);
			
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
				{
					String rankingType = rs.getString("ranking_type");
					RewardConfig config = new RewardConfig();
					config.rankPosition = rs.getInt("rank_position");
					config.itemId = rs.getInt("item_id");
					config.itemCount = rs.getLong("item_count");
					config.enabled = true;
					
					rewards.computeIfAbsent(rankingType, k -> new ArrayList<>()).add(config);
				}
			}
		}
		
		return rewards;
	}
	
	/**
	 * Ranking entry data class
	 */
	public static class RankingEntry
	{
		public int playerId;
		public String playerName;
		public int pvpKills;
		public int pkKills;
		public int level;
		public long pveKills;
		public long pveExp;
		public int rank;
	}
	
	/**
	 * Reward configuration data class
	 */
	private static class RewardConfig
	{
		public int rankPosition;
		public int itemId;
		public long itemCount;
		public boolean enabled;
	}
}

