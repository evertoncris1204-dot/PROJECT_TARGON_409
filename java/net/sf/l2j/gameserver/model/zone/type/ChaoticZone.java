package net.sf.l2j.gameserver.model.zone.type;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.data.SkillTable;
import net.sf.l2j.gameserver.enums.ZoneId;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.zone.type.subtype.ZoneType;
import net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage;
import net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage.SMPOS;
import net.sf.l2j.gameserver.skills.L2Skill;

/**
 * A zone extending {@link ZoneType}, used for chaotic PvP zones.
 * Players automatically get PvP flag, can receive noblesse blessing, and have restrictions on restart/logout/store.
 */
public class ChaoticZone extends ZoneType
{
	private static final boolean GIVE_NOBLESSE = Config.GIVE_NOBLESSE;
	private static final boolean REVIVE_NOBLESSE = Config.REVIVE_NOBLESSE;
	private static final boolean REVIVE_HEAL = Config.REVIVE_HEAL;
	
	private static L2Skill noblesse = null;
	
	public ChaoticZone(int id)
	{
		super(id);
		if (noblesse == null)
			noblesse = SkillTable.getInstance().getInfo(1323, 1);
	}
	
	@Override
	protected void onEnter(Creature creature)
	{
		creature.setInsideZone(ZoneId.CHAOTIC, true);
		creature.setInsideZone(ZoneId.DANGER_AREA, true);
		
		if (creature instanceof Player player)
		{
			if (player.getPvpFlag() == 0)
				player.updatePvPFlag(1);
			
			if (GIVE_NOBLESSE && noblesse != null)
				noblesse.getEffects(player, player);
			
			player.sendPacket(new ExShowScreenMessage("You entered a Chaotic Zone", 10000, SMPOS.TOP_CENTER, false));
			player.sendMessage("You entered a Chaotic Zone");
		}
	}
	
	@Override
	protected void onExit(Creature creature)
	{
		creature.setInsideZone(ZoneId.CHAOTIC, false);
		creature.setInsideZone(ZoneId.DANGER_AREA, false);
		
		if (creature instanceof Player player)
		{
			if (player.getPvpFlag() == 1)
				player.updatePvPFlag(0);
			player.sendPacket(new ExShowScreenMessage("You left a Chaotic Zone", 10000, SMPOS.TOP_CENTER, false));
			player.sendMessage("You left a Chaotic Zone");
		}
	}
}

