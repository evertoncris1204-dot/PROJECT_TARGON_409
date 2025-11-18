package Base.AutoFarm;

import net.sf.l2j.Config;
import net.sf.l2j.commons.math.MathUtil;
import net.sf.l2j.commons.pool.ConnectionPool;
import net.sf.l2j.commons.pool.ThreadPool;
import net.sf.l2j.commons.random.Rnd;

import net.sf.l2j.gameserver.enums.ShortcutType;
import net.sf.l2j.gameserver.enums.TeamType;
import net.sf.l2j.gameserver.enums.skills.SkillTargetType;
import net.sf.l2j.gameserver.enums.skills.SkillType;
import net.sf.l2j.gameserver.geoengine.GeoEngine;
import net.sf.l2j.gameserver.handler.IItemHandler;
import net.sf.l2j.gameserver.handler.ItemHandler;
import net.sf.l2j.gameserver.handler.voicedcommandhandlers.AutoFarm;
import net.sf.l2j.gameserver.model.Shortcut;
import net.sf.l2j.gameserver.model.WorldObject;
import net.sf.l2j.gameserver.model.WorldRegion;

import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.Summon;

import net.sf.l2j.gameserver.model.actor.instance.Chest;
import net.sf.l2j.gameserver.model.actor.instance.Monster;
import net.sf.l2j.gameserver.model.actor.instance.Pet;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.ActionFailed;
import net.sf.l2j.gameserver.network.serverpackets.ExServerPrimitive;

import java.awt.Color;
import net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage;
import net.sf.l2j.gameserver.network.serverpackets.SystemMessage;
import net.sf.l2j.gameserver.skills.AbstractEffect;
import net.sf.l2j.gameserver.skills.L2Skill;
import net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage.SMPOS;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AutofarmPlayerRoutine
{
	private final Player player;
	private ScheduledFuture<?> _task;
	private ScheduledFuture<?> _interfaceUpdateTask;
	private Creature committedTarget = null;
	private ExServerPrimitive _radiusVisual;
	private int _lastRadiusVisualUpdate = 0;
	private String _currentTargetName = "None";
	private String _currentStatus = "Idle";
	private boolean _radiusCircleVisible = true; // Default to visible when autofarm starts
	private int _fixedCenterX = 0; // Fixed center X position for radius circle
	private int _fixedCenterY = 0; // Fixed center Y position for radius circle
	private int _fixedCenterZ = 0; // Fixed center Z position for radius circle

	public AutofarmPlayerRoutine(Player player)
	{
		this.player = player;
	}

	public boolean start()
	{
		if (_task != null && running())
		{
			player.sendMessage("Auto Farm is already running.");
			return true;
		}
		
		// Safety check
		if (player == null)
			return false;
		
		if (!player.isOnline())
		{
			player.sendMessage("Cannot start Auto Farm: Player is not online.");
			return false;
		}
		
		// Clean up any old task reference
		if (_task != null)
		{
			try
			{
				_task.cancel(false);
			}
			catch (Exception e)
			{
				// Ignore
			}
			_task = null;
		}
		
		// Check if IP is already in use by another character (not this one)
		try
		{
			if (isIpAllowedForPlayer(player.getIP(), player.getObjectId()))
			{
				player.sendMessage("You can only use Auto Farm with one IP at a time.");
				return false;
			}
		}
		catch (Exception e)
		{
			// If IP check fails, continue anyway (might be database issue)
			e.printStackTrace();
		}
		
		// Remove old entry if exists (in case of restart)
		try
		{
			removeIpEntry(player.getObjectId());
		}
		catch (Exception e)
		{
			// Ignore if removal fails
		}
		
		try
		{
			_task = ThreadPool.scheduleAtFixedRate(() -> executeRoutine(), 450, 450);
			
			if (_task == null)
			{
				player.sendMessage("Failed to start Auto Farm: Could not create task.");
				return false;
			}
			
			// Clear summon request fields periodically to prevent any ConfirmDlg from being sent
			ThreadPool.scheduleAtFixedRate(() -> {
				if (player != null && player.isOnline() && running())
				{
					try
					{
						java.lang.reflect.Field field = player.getClass().getDeclaredField("_summonTargetRequest");
						field.setAccessible(true);
						field.set(player, null);
						
						field = player.getClass().getDeclaredField("_summonSkillRequest");
						field.setAccessible(true);
						field.set(player, null);
					}
					catch (Exception e)
					{
						// Ignore reflection errors
					}
				}
			}, 1000, 1000); // Clear every second
			
			// Removed interface update task to prevent automatic popup dialog
			// _interfaceUpdateTask = ThreadPool.scheduleAtFixedRate(() -> updateInterface(), 2000, 2000);
			// System.out.println("[DEBUG] AutofarmPlayerRoutine.start() - Interface update task created");
			
			// Removed screen message as requested by user
			// player.sendPacket(new ExShowScreenMessage("Auto Farming Activated...", 5*1000, SMPOS.TOP_CENTER, false));
			try
			{
				player.sendPacket(new SystemMessage(SystemMessageId.AUTO_FARM_ACTIVATED));
			}
			catch (Exception e)
			{
				// SystemMessageId might not exist, ignore
			}
			// Removed setTeam() to prevent teleport confirmation dialog
			// player.setTeam(player.isMageClass() ? TeamType.BLUE : TeamType.RED);
			
			// Clear any pending teleport requests to prevent confirmation dialogs
			try
			{
				// Clear summon target request directly
				java.lang.reflect.Field field = player.getClass().getDeclaredField("_summonTargetRequest");
				field.setAccessible(true);
				field.set(player, null);
				
				field = player.getClass().getDeclaredField("_summonSkillRequest");
				field.setAccessible(true);
				field.set(player, null);
			}
			catch (Exception e)
			{
				// Fallback: use public method
				try
				{
					player.teleportRequest(null, null);
				}
				catch (Exception e2)
				{
					// Ignore if clearing fails
				}
			}
			
			try
			{
				insertIpEntry(player.getObjectId(), player.getIP());
			}
			catch (Exception e)
			{
				// If IP insertion fails, continue anyway
			}
			
			// Removed broadcastUserInfo() temporarily to prevent teleport confirmation dialog
			// player.broadcastUserInfo();
			player.sendMessage("Auto Farm started successfully!");
			
			// Save fixed center position when autofarm starts
			_fixedCenterX = player.getX();
			_fixedCenterY = player.getY();
			_fixedCenterZ = player.getZ();
			
			// Draw radius circle if visible
			_radiusCircleVisible = true; // Reset visibility when starting
			if (_radiusCircleVisible)
			{
				drawRadiusCircle();
			}
			
			// Removed automatic interface update to prevent popup dialog
			// updateInterface();
			
			return true;
		}
		catch (Exception e)
		{
			player.sendMessage("Error starting Auto Farm: " + e.getMessage());
			e.printStackTrace();
			_task = null;
			return false;
		}
	}
	
	public void stop()
	{
		if (_task != null)
		{
			removeIpEntry(player.getObjectId());
			_task.cancel(false);
			_task = null;
			committedTarget = null;
			
			// Stop interface update task
			if (_interfaceUpdateTask != null)
			{
				_interfaceUpdateTask.cancel(false);
				_interfaceUpdateTask = null;
			}
			
			// Remove radius circle
			removeRadiusCircle();
			
			// Reset visibility state
			_radiusCircleVisible = true; // Reset to default for next start
			
			// Reset fixed center position
			_fixedCenterX = 0;
			_fixedCenterY = 0;
			_fixedCenterZ = 0;
			
			// Reset status
			_currentTargetName = "None";
			_currentStatus = "Stopped";
			
			if (player != null)
			{
				player.sendPacket(new ExShowScreenMessage("Auto Farming Deactivated...", 5*1000, SMPOS.TOP_CENTER, false));
				try
				{
					player.sendPacket(new SystemMessage(SystemMessageId.AUTO_FARM_DESACTIVATED));
				}
				catch (Exception e)
				{
					// SystemMessageId might not exist, ignore
				}
				// Removed setTeam() to prevent teleport confirmation dialog
				// player.setTeam(TeamType.NONE);
				player.broadcastUserInfo();
				
				// Update interface one last time
				updateInterface();
			}
		}
	}
	
	public boolean running()
	{
		return _task != null && !_task.isCancelled() && !_task.isDone();
	}
	
	
	// Method to insert the player's IP into the Auto_Farm_Ip table
	private void insertIpEntry(int charId, String ip)
	{
		try (Connection con = ConnectionPool.getConnection())
		{
			// First remove any existing entry for this character
			removeIpEntry(charId);
			
			// Then insert the new entry
			String insertSql = "INSERT INTO Auto_Farm_Ip (char_id, ip) VALUES (?, ?)";
			try (PreparedStatement insertStatement = con.prepareStatement(insertSql))
			{
				insertStatement.setInt(1, charId);
				insertStatement.setString(2, ip);
				insertStatement.executeUpdate();
			}
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}
	}
	
	public static boolean isIpAllowed(String ip)
	{
		try (Connection con = ConnectionPool.getConnection())
		{
			String selectSql = "SELECT * FROM Auto_Farm_Ip WHERE ip = ?";
			try (PreparedStatement selectStatement = con.prepareStatement(selectSql))
			{
				selectStatement.setString(1, ip);
				try (ResultSet result = selectStatement.executeQuery())
				{
					return result.next();
				}
			}
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}
		return false; // In case of error or if there's no match
	}
	
	public static boolean isIpAllowedForPlayer(String ip, int charId)
	{
		try (Connection con = ConnectionPool.getConnection())
		{
			String selectSql = "SELECT * FROM Auto_Farm_Ip WHERE ip = ? AND char_id != ?";
			try (PreparedStatement selectStatement = con.prepareStatement(selectSql))
			{
				selectStatement.setString(1, ip);
				selectStatement.setInt(2, charId);
				try (ResultSet result = selectStatement.executeQuery())
				{
					return result.next();
				}
			}
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}
		return false;
	}

	// Método para eliminar la entrada del jugador en la tabla Auto_Farm_Ip
	public static void removeIpEntry(int charId)
	{
		try (Connection con = ConnectionPool.getConnection())
		{
			String deleteSql = "DELETE FROM Auto_Farm_Ip WHERE char_id = ?";
			try (PreparedStatement deleteStatement = con.prepareStatement(deleteSql))
			{
				deleteStatement.setInt(1, charId);
				deleteStatement.executeUpdate();
			}
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}
	}
	
	
	
	public void executeRoutine()
	{
		// Safety checks
		if (player == null || !player.isOnline() || player.isDead())
		{
			stop();
			if (player != null)
				player.setAutoFarm(false);
			return;
		}
		
		// Check if player is moving, attacking, or disabled
		if (player.isMoving() || player.isAllSkillsDisabled() || player.isAttackingNow() || player.isOutOfControl())
			return;
		
		// Check buff protection
		if (player.isNoBuffProtected() && player.getAllEffects().length <= 8)
		{
			player.sendMessage("You don't have buffs to use autofarm.");
			player.broadcastUserInfo();
			stop();
			player.setAutoFarm(false);
			AutoFarm.showAutoFarm(player);
			return;
		}
		
		calculatePotions();
		checkSpoil();
		targetEligibleCreature();
		
		// Update target name for interface
		if (committedTarget != null && committedTarget instanceof Monster)
		{
			_currentTargetName = committedTarget.getName();
			_currentStatus = "Hunting";
		}
		else
		{
			_currentTargetName = "None";
			_currentStatus = "Searching";
		}
		
		if (player.isMageClass())
		{
			useAppropriateSpell(); // Prioritize spell usage for mages
		}
		else if (shotcutsContainAttack())
		{
			attack(); // If non-mage class and has attack action in shortcuts
		}
		else
		{
			useAppropriateSpell(); // If non-mage class and no attack action, use spells
		}
		
		checkSpoil();
		useAppropriateSpell();
		
		// Update radius circle visual every 2 seconds
		int currentTime = (int)(System.currentTimeMillis() / 1000);
		if (currentTime - _lastRadiusVisualUpdate >= 2)
		{
			updateRadiusCircle();
			_lastRadiusVisualUpdate = currentTime;
		}
	}

	private void attack()
	{
		Boolean shortcutsContainAttack = shotcutsContainAttack();
		
		if (shortcutsContainAttack)
			physicalAttack();
	}

	private void useAppropriateSpell()
	{
		L2Skill chanceSkill = nextAvailableSkill(getChanceSpells(), AutofarmSpellType.Chance);

		if (chanceSkill != null)
		{
			doSkill(chanceSkill, false);
			return;
		}

		L2Skill lowLifeSkill = nextAvailableSkill(getLowLifeSpells(), AutofarmSpellType.LowLife);

		if (lowLifeSkill != null)
		{
			doSkill(lowLifeSkill, true);
			return;
		}

		L2Skill attackSkill = nextAvailableSkill(getAttackSpells(), AutofarmSpellType.Attack);

		if (attackSkill != null)
		{
			doSkill(attackSkill, false);
			return;
		}
	}

	public L2Skill nextAvailableSkill(List<Integer> skillIds, AutofarmSpellType spellType)
	{
		for (Integer skillId : skillIds)
		{
			L2Skill skill = player.getSkill(skillId);

			if (skill == null)
				continue;
			
			if (skill.getSkillType() == SkillType.SIGNET || skill.getSkillType() == SkillType.SIGNET_CASTTIME)
				continue;

			if (player.isSkillDisabled(skill))
				continue;

			if (isSpoil(skillId))
			{
				if (monsterIsAlreadySpoiled())
				{
					continue;
				}
				return skill;
			}
			
			if (spellType == AutofarmSpellType.Chance && getMonsterTarget() != null)
			{
				if (getMonsterTarget().getFirstEffect(skillId) == null)
					return skill;
				continue;
			}

			if (spellType == AutofarmSpellType.LowLife && getHpPercentage() > player.getHealPercent())
				break;

			return skill;
		}

		return null;
	}
	
	private void checkSpoil()
	{
		Monster target = getMonsterTarget();
		if (target != null && canBeSweepedByMe() && target.isDead())
		{
			L2Skill sweeper = player.getSkill(42);
			if (sweeper == null)
				return;
			
			doSkill(sweeper, false);
		}
	}

	private Double getHpPercentage()
	{
		return player.getStatus().getHp() * 100.0f / player.getStatus().getMaxHp();
	}

	private Double percentageMpIsLessThan()
	{
		return player.getStatus().getMp() * 100.0f / player.getStatus().getMaxMp();
	}

	private Double percentageHpIsLessThan()
	{
		return player.getStatus().getHp() * 100.0f / player.getStatus().getMaxHp();
	}

	private List<Integer> getAttackSpells()
	{
		return getSpellsInSlots(AutofarmConstants.attackSlots);
	}

	private List<Integer> getSpellsInSlots(List<Integer> attackSlots)
	{
		return Arrays.stream(player.getShortcutList().getShortcuts()).filter(shortcut -> shortcut.getPage() == player.getPage() && shortcut.getType() == ShortcutType.SKILL && attackSlots.contains(shortcut.getSlot())).map(Shortcut::getId).collect(Collectors.toList());
	}

	private List<Integer> getChanceSpells()
	{
		return getSpellsInSlots(AutofarmConstants.chanceSlots);
	}

	private List<Integer> getLowLifeSpells()
	{
		return getSpellsInSlots(AutofarmConstants.lowLifeSlots);
	}

	private boolean shotcutsContainAttack()
	{
		return Arrays.stream(player.getShortcutList().getShortcuts()).anyMatch(shortcut -> shortcut.getPage() == player.getPage() && shortcut.getType() == ShortcutType.ACTION && (shortcut.getId() == 2 || player.isSummonAttack() && shortcut.getId() == 22));
	}
	
	
	
	private boolean monsterIsAlreadySpoiled()
	{
		Monster target = getMonsterTarget();
		return target != null && target.getSpoilState().isSpoiled();
	}
	
	private static boolean isSpoil(Integer skillId)
	{
		return skillId == 254 || skillId == 302;
	}
	
	private boolean canBeSweepedByMe()
	{
		Monster target = getMonsterTarget();
		return target != null && target.isDead() && target.getSpoilState().isSweepable();
	}
	

	
	private void doSkill(L2Skill skill, boolean isSelfSkill)
	{
		final WorldObject target = player.getTarget();
		if (skill == null || !(target instanceof Creature))
			return;
		
		if (skill.getSkillType() == SkillType.RECALL && !Config.KARMA_PLAYER_CAN_TELEPORT && player.getKarma() > 0)
		{
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}

		if (skill.isToggle() && player.isMounted())
		{
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}

		if (player.isOutOfControl())
		{
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		
		
		if (isNecessarySkill(skill))
			player.getAI().tryToCast(isSelfSkill ? player : (Creature) target, skill);
	}
	
	
	private boolean isNecessarySkill(L2Skill skill)
	{
		if (skill == null)
			return false;
		
		final WorldObject target = player.getTarget();
		if (target instanceof Monster)
		{
			final Monster monster = (Monster) target;
			if (skill.getSkillType() == SkillType.SPOIL && monster.getSpoilState().isSpoiled())
				return false;
			
			List<AbstractEffect> effects = Arrays.stream(monster.getAllEffects()).filter(e -> e.getSkill().isDebuff()).collect(Collectors.toList());
			if (effects != null && !effects.isEmpty() && effects.stream().anyMatch(e -> e.getSkill().getId() == skill.getId()))
				return false;
			
			if (!monster.isDead() && skill.getTargetType() == SkillTargetType.CORPSE_MOB)
				return false;
			
			return true;
		}
		return false;
	}

	private void physicalAttack()
	{
		if (!(player.getTarget() instanceof Monster))
			return;

		Monster target = (Monster) player.getTarget();
		
		if (target == null || target.isDead())
			return;

		if (!player.isMageClass())
		{
			if (GeoEngine.getInstance().canSeeTarget(player, target))
			{
				if (target.canAutoAttack(player))
				{
					player.getAI().tryToAttack(target);
					player.onActionRequest();
				}
				else
				{
					// Move closer if can't attack
					player.getAI().tryToFollow(target, false);
				}

				// Handle summon attack
				if (player.isSummonAttack() && player.getSummon() != null)
				{
					Summon activeSummon = player.getSummon();
					// Siege Golem's - skip
					if (activeSummon.getNpcId() >= 14702 && activeSummon.getNpcId() <= 14798 || activeSummon.getNpcId() >= 14839 && activeSummon.getNpcId() <= 14869)
						return;

					activeSummon.setTarget(target);
					activeSummon.getAI().tryToAttack(target);

					int[] summonAttackSkills = {4261, 4068, 4137, 4260, 4708, 4709, 4710, 4712, 5135, 5138, 5141, 5442, 5444, 6095, 6096, 6041, 6044};
					if (Rnd.get(100) < player.getSummonSkillPercent())
					{
						for (int skillId : summonAttackSkills)
						{
							useMagicSkillBySummon(skillId, target);
						}
					}
				}
			}
			else
			{
				// Can't see target, try to follow
				player.getAI().tryToFollow(target, false);
			}
		}
		else
		{
			// Mage class - handle summon attack
			if (player.isSummonAttack() && player.getSummon() != null)
			{
				Summon activeSummon = player.getSummon();
				// Siege Golem's - skip
				if (activeSummon.getNpcId() >= 14702 && activeSummon.getNpcId() <= 14798 || activeSummon.getNpcId() >= 14839 && activeSummon.getNpcId() <= 14869)
					return;

				activeSummon.setTarget(target);
				activeSummon.getAI().tryToAttack(target);

				int[] summonAttackSkills = {4261, 4068, 4137, 4260, 4708, 4709, 4710, 4712, 5135, 5138, 5141, 5442, 5444, 6095, 6096, 6041, 6044};
				if (Rnd.get(100) < player.getSummonSkillPercent())
				{
					for (int skillId : summonAttackSkills)
					{
						useMagicSkillBySummon(skillId, target);
					}
				}
			}
		}
	}

	private void useMagicSkillBySummon(int skillId, WorldObject target)
	{
		// No owner, or owner in shop mode.
		if (player == null || player.isInStoreMode())
			return;
		
		final Summon activeSummon = player.getSummon();
		if (activeSummon == null)
			return;
		
		// Pet which is 20 levels higher than owner.
		if (activeSummon instanceof Pet && activeSummon.getStatus().getLevel() - player.getStatus().getLevel() > 20)
		{
			player.sendPacket(SystemMessageId.PET_TOO_HIGH_TO_CONTROL);
			return;
		}
		
		// Out of control pet.
		if (activeSummon.isOutOfControl())
		{
			player.sendPacket(SystemMessageId.PET_REFUSING_ORDER);
			return;
		}
		
		// Verify if the launched skill is mastered by the summon.
		final L2Skill skill = activeSummon.getSkill(skillId);
		if (skill == null)
			return;
		
		// Can't launch offensive skills on owner.
		if (skill.isOffensive() && player == target)
			return;
		
		activeSummon.setTarget(target);
		activeSummon.getAI().tryToCast(committedTarget, skill);
	}



	public void targetEligibleCreature()
	{
		// If player has no target, select a new one
		if (player.getTarget() == null)
		{
			selectNewTarget();
			return;
		}

		// If we have a committed target, check its status
		if (committedTarget != null)
		{
			// Target is dead, select new one
			if (committedTarget.isDead())
			{
				committedTarget = null;
				player.setTarget(null);
				selectNewTarget();
				return;
			}
			
			// Check if we can still see the target
			if (!GeoEngine.getInstance().canSeeTarget(player, committedTarget))
			{
				committedTarget = null;
				player.setTarget(null);
				selectNewTarget();
				return;
			}
			
			// Target is valid, attack it
			if (!committedTarget.isDead() && GeoEngine.getInstance().canSeeTarget(player, committedTarget))
			{
				// Target is set, continue with attack logic
				return;
			}
		}
		
		// No committed target or target is invalid, select new one
		if (committedTarget == null || committedTarget.isDead())
		{
			selectNewTarget();
		}
	}


	// Function to select a new target
	private void selectNewTarget()
	{
		if (player == null)
			return;
		
		// Use fixed center position if available, otherwise use player position
		int centerX = (_fixedCenterX != 0) ? _fixedCenterX : player.getX();
		int centerY = (_fixedCenterY != 0) ? _fixedCenterY : player.getY();
		int centerZ = (_fixedCenterZ != 0) ? _fixedCenterZ : player.getZ();
		net.sf.l2j.gameserver.model.location.Location fixedLocation = new net.sf.l2j.gameserver.model.location.Location(centerX, centerY, centerZ);
			
		List<Monster> targets = getKnownMonstersInRadius(player, player.getRadius(), fixedLocation, creature -> 
			creature != null && 
			!creature.isDead() && 
			!creature.isRaidRelated() && 
			!creature.isRaidBoss() && 
			!(creature instanceof Chest) &&
			!player.ignoredMonsterContain(creature.getNpcId()) &&
			GeoEngine.getInstance().canSeeTarget(player, creature) &&
			!(player.isAntiKsProtected() && creature.getTarget() != null && creature.getTarget() != player && creature.getTarget() != player.getSummon())
		);

		if (targets.isEmpty())
		{
			// No targets found, clear target
			committedTarget = null;
			if (player.getTarget() != null && player.getTarget() instanceof Monster)
				player.setTarget(null);
			return;
		}

		// Find closest target to fixed center position
		final int finalCenterX = centerX;
		final int finalCenterY = centerY;
		Monster closestTarget = targets.stream()
			.min((o1, o2) -> {
				// Calculate 2D distance from fixed center to each target
				long dx1 = (long) finalCenterX - o1.getX();
				long dy1 = (long) finalCenterY - o1.getY();
				double dist1 = Math.sqrt(dx1 * dx1 + dy1 * dy1);
				
				long dx2 = (long) finalCenterX - o2.getX();
				long dy2 = (long) finalCenterY - o2.getY();
				double dist2 = Math.sqrt(dx2 * dx2 + dy2 * dy2);
				
				return Double.compare(dist1, dist2);
			})
			.orElse(null);

		if (closestTarget != null)
		{
			committedTarget = closestTarget;
			player.setTarget(closestTarget);
		}
	}




	public final static List<Monster> getKnownMonstersInRadius(Player player, int radius, Function<Monster, Boolean> condition)
	{
		return getKnownMonstersInRadius(player, radius, null, condition);
	}
	
	public final static List<Monster> getKnownMonstersInRadius(Player player, int radius, net.sf.l2j.gameserver.model.location.Location centerLocation, Function<Monster, Boolean> condition)
	{
		final WorldRegion region = player.getRegion();
		if (region == null)
			return Collections.emptyList();

		final List<Monster> result = new ArrayList<>();
		
		// Use fixed center location if provided, otherwise use player position
		net.sf.l2j.gameserver.model.location.Location checkLocation = (centerLocation != null) ? centerLocation : new net.sf.l2j.gameserver.model.location.Location(player.getX(), player.getY(), player.getZ());

		for (WorldRegion reg : region.getSurroundingRegions())
		{
			for (WorldObject obj : reg.getObjects())
			{
				if (!(obj instanceof Monster) || !MathUtil.checkIfInRange(radius, obj, checkLocation, true) || !condition.apply((Monster) obj))
					continue;

				result.add((Monster) obj);
			}
		}

		return result;
	}

	public Monster getMonsterTarget()
	{
		if(!(player.getTarget() instanceof Monster))
		{
			return null;
		}

		return (Monster)player.getTarget();
	}

	

	private void calculatePotions()
	{
		if (percentageHpIsLessThan() < player.getHpPotionPercentage())
			forceUseItem(1539);

		if (percentageMpIsLessThan() < player.getMpPotionPercentage())
			forceUseItem(728);
	}

	private void forceUseItem(int itemId)
	{
		final ItemInstance potion = player.getInventory().getItemByItemId(itemId);
		if (potion == null)
			return;

		final IItemHandler handler = ItemHandler.getInstance().getHandler(potion.getEtcItem());
		if (handler != null)
			handler.useItem(player, potion, false);
	}
	
	/**
	 * Draws a circle around the player showing the autofarm radius.
	 */
	private void drawRadiusCircle()
	{
		if (player == null || !player.isOnline())
			return;
		
		// Get or create the debug packet
		_radiusVisual = player.getDebugPacket("AUTOFARM_RADIUS");
		_radiusVisual.reset();
		
		int radius = player.getRadius();
		// Use fixed center position (saved when autofarm started) instead of current player position
		int centerX = _fixedCenterX;
		int centerY = _fixedCenterY;
		int centerZ = _fixedCenterZ;
		
		// Draw circle using lines (approximate circle with many small lines)
		// Use more segments for smoother circle
		int segments = Math.max(64, Math.min(128, radius / 30));
		double angleStep = 2 * Math.PI / segments;
		
		Color circleColor = new Color(0, 255, 0, 255); // Green color
		
		for (int i = 0; i < segments; i++)
		{
			double angle1 = i * angleStep;
			double angle2 = (i + 1) * angleStep;
			
			int x1 = (int)(centerX + radius * Math.cos(angle1));
			int y1 = (int)(centerY + radius * Math.sin(angle1));
			int x2 = (int)(centerX + radius * Math.cos(angle2));
			int y2 = (int)(centerY + radius * Math.sin(angle2));
			
			_radiusVisual.addLine(circleColor, x1, y1, centerZ, x2, y2, centerZ);
		}
		
		// Send the visual to the player
		_radiusVisual.sendTo(player);
	}
	
	/**
	 * Removes the radius circle visual.
	 */
	private void removeRadiusCircle()
	{
		if (player == null || !player.isOnline())
			return;
		
		// Get the debug packet from player (it may have been recreated)
		ExServerPrimitive visual = player.getDebugPacket("AUTOFARM_RADIUS");
		if (visual != null)
		{
			visual.reset();
			visual.sendTo(player);
		}
		
		// Clear our reference
		_radiusVisual = null;
	}
	
	/**
	 * Updates the radius circle visual (call when radius changes).
	 */
	public void updateRadiusCircle()
	{
		if (running() && player != null && player.isOnline() && _radiusCircleVisible)
		{
			drawRadiusCircle();
		}
	}
	
	/**
	 * Shows the radius circle if autofarm is running.
	 */
	public void showRadiusCircle()
	{
		_radiusCircleVisible = true;
		if (running() && player != null && player.isOnline())
		{
			drawRadiusCircle();
		}
	}
	
	/**
	 * Hides the radius circle.
	 */
	public void hideRadiusCircle()
	{
		_radiusCircleVisible = false;
		if (player != null && player.isOnline())
		{
			removeRadiusCircle();
		}
	}
	
	/**
	 * Checks if the radius circle is currently visible.
	 */
	public boolean isRadiusCircleVisible()
	{
		return _radiusCircleVisible;
	}
	
	/**
	 * Updates the interface with current autofarm status.
	 */
	private void updateInterface()
	{
		if (!running() || player == null || !player.isOnline())
			return;
		
		// Only update if player has the interface open (check if they recently opened it)
		// For now, we'll update it periodically
		try
		{
			AutoFarm.showAutoFarm(player);
		}
		catch (Exception e)
		{
			// Ignore errors
		}
	}
	
	/**
	 * Gets the current target name for interface display.
	 */
	public String getCurrentTargetName()
	{
		return _currentTargetName;
	}
	
	/**
	 * Gets the current status for interface display.
	 */
	public String getCurrentStatus()
	{
		return _currentStatus;
	}
	
	/**
	 * Gets the number of monsters in range.
	 */
	public int getMonstersInRange()
	{
		if (player == null || !running())
			return 0;
		
		try
		{
			// Use fixed center position if available, otherwise use player position
			int centerX = (_fixedCenterX != 0) ? _fixedCenterX : player.getX();
			int centerY = (_fixedCenterY != 0) ? _fixedCenterY : player.getY();
			int centerZ = (_fixedCenterZ != 0) ? _fixedCenterZ : player.getZ();
			net.sf.l2j.gameserver.model.location.Location fixedLocation = new net.sf.l2j.gameserver.model.location.Location(centerX, centerY, centerZ);
			
			List<Monster> targets = getKnownMonstersInRadius(player, player.getRadius(), fixedLocation, 
				creature -> GeoEngine.getInstance().canMoveToTarget(centerX, centerY, centerZ, 
					creature.getX(), creature.getY(), creature.getZ()) && 
					!player.ignoredMonsterContain(creature.getNpcId()) && 
					!creature.isRaidRelated() && 
					!creature.isRaidBoss() && 
					!creature.isDead() && 
					!(creature instanceof Chest) && 
					!(player.isAntiKsProtected() && creature.getTarget() != null && 
						creature.getTarget() != player && creature.getTarget() != player.getSummon()));
			return targets.size();
		}
		catch (Exception e)
		{
			return 0;
		}
	}
}

