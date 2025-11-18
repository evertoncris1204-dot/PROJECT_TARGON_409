package net.sf.l2j.gameserver.network.serverpackets;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.data.manager.CursedWeaponManager;
import net.sf.l2j.gameserver.data.xml.DressMeData;
import net.sf.l2j.gameserver.enums.Paperdoll;
import net.sf.l2j.gameserver.enums.TeamType;
import net.sf.l2j.gameserver.enums.skills.AbnormalEffect;
import net.sf.l2j.gameserver.model.actor.Boat;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.Summon;
import net.sf.l2j.gameserver.model.actor.instance.Cubic;
import net.sf.l2j.gameserver.model.skin.SkinPackage;

public class CharInfo extends L2GameServerPacket
{
	private final Player _player;
	
	public CharInfo(Player player)
	{
		_player = player;
	}
	
	@Override
	protected final void writeImpl()
	{
		boolean canSeeInvis = false;
		
		if (!_player.getAppearance().isVisible())
		{
			final Player tmp = getClient().getPlayer();
			if (tmp != null && tmp.isGM())
				canSeeInvis = true;
		}
		
		writeC(0x03);
		
		writeLoc(_player.getPosition());
		
		final Boat boat = _player.getBoatInfo().getBoat();
		writeD((boat == null) ? 0 : boat.getObjectId());
		writeD(_player.getObjectId());
		writeS(_player.getName());
		writeD(_player.getRace().ordinal());
		writeD(_player.getAppearance().getSex().ordinal());
		writeD((_player.getClassIndex() == 0) ? _player.getClassId().getId() : _player.getBaseClass());
		
		writeD(_player.getInventory().getItemIdFrom(Paperdoll.HAIRALL));
		writeD(_player.getInventory().getItemIdFrom(Paperdoll.HEAD));
		
		if (Config.ALLOW_DRESS_ME_SYSTEM)
		{
			// Weapon skin
			if (_player.getWeaponSkinOption() > 0)
			{
				SkinPackage weaponSkin = DressMeData.getInstance().getWeaponSkinsPackage(_player.getWeaponSkinOption());
				if (weaponSkin != null && weaponSkin.getWeaponId() != 0)
					writeD(weaponSkin.getWeaponId());
				else
					writeD(_player.getInventory().getItemIdFrom(Paperdoll.RHAND));
			}
			else
				writeD(_player.getInventory().getItemIdFrom(Paperdoll.RHAND));
			
			// Shield skin
			if (_player.getShieldSkinOption() > 0)
			{
				SkinPackage shieldSkin = DressMeData.getInstance().getShieldSkinsPackage(_player.getShieldSkinOption());
				if (shieldSkin != null && shieldSkin.getShieldId() != 0)
					writeD(shieldSkin.getShieldId());
				else
					writeD(_player.getInventory().getItemIdFrom(Paperdoll.LHAND));
			}
			else
				writeD(_player.getInventory().getItemIdFrom(Paperdoll.LHAND));
			
			// Armor skin
			if (_player.getArmorSkinOption() > 0)
			{
				SkinPackage armorSkin = DressMeData.getInstance().getArmorSkinsPackage(_player.getArmorSkinOption());
				if (armorSkin != null)
				{
					writeD(armorSkin.getGlovesId() != 0 ? armorSkin.getGlovesId() : _player.getInventory().getItemIdFrom(Paperdoll.GLOVES));
					writeD(armorSkin.getChestId() != 0 ? armorSkin.getChestId() : _player.getInventory().getItemIdFrom(Paperdoll.CHEST));
					writeD(armorSkin.getLegsId() != 0 ? armorSkin.getLegsId() : _player.getInventory().getItemIdFrom(Paperdoll.LEGS));
					writeD(armorSkin.getFeetId() != 0 ? armorSkin.getFeetId() : _player.getInventory().getItemIdFrom(Paperdoll.FEET));
				}
				else
				{
					writeD(_player.getInventory().getItemIdFrom(Paperdoll.GLOVES));
					writeD(_player.getInventory().getItemIdFrom(Paperdoll.CHEST));
					writeD(_player.getInventory().getItemIdFrom(Paperdoll.LEGS));
					writeD(_player.getInventory().getItemIdFrom(Paperdoll.FEET));
				}
			}
			else
			{
				writeD(_player.getInventory().getItemIdFrom(Paperdoll.GLOVES));
				writeD(_player.getInventory().getItemIdFrom(Paperdoll.CHEST));
				writeD(_player.getInventory().getItemIdFrom(Paperdoll.LEGS));
				writeD(_player.getInventory().getItemIdFrom(Paperdoll.FEET));
			}
			
			writeD(_player.getInventory().getItemIdFrom(Paperdoll.CLOAK));
			
			// Weapon skin (duplicate)
			if (_player.getWeaponSkinOption() > 0)
			{
				SkinPackage weaponSkin = DressMeData.getInstance().getWeaponSkinsPackage(_player.getWeaponSkinOption());
				if (weaponSkin != null && weaponSkin.getWeaponId() != 0)
					writeD(weaponSkin.getWeaponId());
				else
					writeD(_player.getInventory().getItemIdFrom(Paperdoll.RHAND));
			}
			else
				writeD(_player.getInventory().getItemIdFrom(Paperdoll.RHAND));
			
			// Hair skin
			if (_player.getHairSkinOption() > 0)
			{
				SkinPackage hairSkin = DressMeData.getInstance().getHairSkinsPackage(_player.getHairSkinOption());
				if (hairSkin != null && hairSkin.getHairId() != 0)
					writeD(hairSkin.getHairId());
				else
					writeD(_player.getInventory().getItemIdFrom(Paperdoll.HAIR));
			}
			else
				writeD(_player.getInventory().getItemIdFrom(Paperdoll.HAIR));
			
			// Face skin
			if (_player.getFaceSkinOption() > 0)
			{
				SkinPackage faceSkin = DressMeData.getInstance().getFaceSkinsPackage(_player.getFaceSkinOption());
				if (faceSkin != null && faceSkin.getFaceId() != 0)
					writeD(faceSkin.getFaceId());
				else
					writeD(_player.getInventory().getItemIdFrom(Paperdoll.FACE));
			}
			else
				writeD(_player.getInventory().getItemIdFrom(Paperdoll.FACE));
		}
		else
		{
			writeD(_player.getInventory().getItemIdFrom(Paperdoll.RHAND));
			writeD(_player.getInventory().getItemIdFrom(Paperdoll.LHAND));
			writeD(_player.getInventory().getItemIdFrom(Paperdoll.GLOVES));
			writeD(_player.getInventory().getItemIdFrom(Paperdoll.CHEST));
			writeD(_player.getInventory().getItemIdFrom(Paperdoll.LEGS));
			writeD(_player.getInventory().getItemIdFrom(Paperdoll.FEET));
			writeD(_player.getInventory().getItemIdFrom(Paperdoll.CLOAK));
			writeD(_player.getInventory().getItemIdFrom(Paperdoll.RHAND));
			writeD(_player.getInventory().getItemIdFrom(Paperdoll.HAIR));
			writeD(_player.getInventory().getItemIdFrom(Paperdoll.FACE));
		}
		
		writeH(0x00);
		writeH(0x00);
		writeH(0x00);
		writeH(0x00);
		writeD(_player.getInventory().getAugmentationIdFrom(Paperdoll.RHAND));
		writeH(0x00);
		writeH(0x00);
		writeH(0x00);
		writeH(0x00);
		writeH(0x00);
		writeH(0x00);
		writeH(0x00);
		writeH(0x00);
		writeH(0x00);
		writeH(0x00);
		writeH(0x00);
		writeH(0x00);
		writeD(_player.getInventory().getAugmentationIdFrom(Paperdoll.LHAND));
		writeH(0x00);
		writeH(0x00);
		writeH(0x00);
		writeH(0x00);
		
		writeD(_player.getPvpFlag());
		writeD(_player.getKarma());
		writeD(_player.getStatus().getMAtkSpd());
		writeD(_player.getStatus().getPAtkSpd());
		writeD(_player.getPvpFlag());
		writeD(_player.getKarma());
		
		final int runSpd = _player.getStatus().getBaseRunSpeed();
		final int walkSpd = _player.getStatus().getBaseWalkSpeed();
		final int swimSpd = _player.getStatus().getBaseSwimSpeed();
		
		writeD(runSpd);
		writeD(walkSpd);
		writeD(swimSpd);
		writeD(swimSpd);
		writeD(runSpd);
		writeD(walkSpd);
		writeD((_player.isFlying()) ? runSpd : 0);
		writeD((_player.isFlying()) ? walkSpd : 0);
		
		writeF(_player.getStatus().getMovementSpeedMultiplier());
		writeF(_player.getStatus().getAttackSpeedMultiplier());
		
		final Summon summon = _player.getSummon();
		if (_player.isMounted() && summon != null)
		{
			writeF(summon.getCollisionRadius());
			writeF(summon.getCollisionHeight());
		}
		else
		{
			writeF(_player.getCollisionRadius());
			writeF(_player.getCollisionHeight());
		}
		
		writeD(_player.getAppearance().getHairStyle());
		writeD(_player.getAppearance().getHairColor());
		writeD(_player.getAppearance().getFace());
		
		writeS((canSeeInvis) ? "Invisible" : _player.getTitle());
		
		writeD(_player.getClanId());
		writeD(_player.getClanCrestId());
		writeD(_player.getAllyId());
		writeD(_player.getAllyCrestId());
		
		writeD(0);
		
		writeC((_player.isSitting()) ? 0 : 1);
		writeC((_player.isRunning()) ? 1 : 0);
		writeC((_player.isInCombat()) ? 1 : 0);
		writeC((_player.isAlikeDead()) ? 1 : 0);
		writeC((!canSeeInvis && !_player.getAppearance().isVisible()) ? 1 : 0);
		
		writeC(_player.getMountType());
		writeC(_player.getOperateType().getId());
		
		writeH(_player.getCubicList().size());
		for (final Cubic cubic : _player.getCubicList())
			writeH(cubic.getId());
		
		writeC((_player.isInPartyMatchRoom()) ? 1 : 0);
		writeD((canSeeInvis) ? (_player.getAbnormalEffect() | AbnormalEffect.STEALTH.getMask()) : _player.getAbnormalEffect());
		writeC(_player.getRecomLeft());
		writeH(_player.getRecomHave());
		writeD(_player.getClassId().getId());
		writeD(_player.getStatus().getMaxCp());
		writeD((int) _player.getStatus().getCp());
		writeC((_player.isMounted()) ? 0 : _player.getEnchantEffect());
		writeC((Config.PLAYER_SPAWN_PROTECTION > 0 && _player.isSpawnProtected()) ? TeamType.BLUE.getId() : _player.getTeam().getId());
		writeD(_player.getClanCrestLargeId());
		writeC((_player.isNoble()) ? 1 : 0);
		writeC((_player.isHero() || (_player.isGM() && Config.GM_HERO_AURA)) ? 1 : 0);
		writeC((_player.isFishing()) ? 1 : 0);
		writeLoc(_player.getFishingStance().getLoc());
		writeD(_player.getAppearance().getNameColor());
		writeD(_player.getHeading());
		writeD(_player.getPledgeClass());
		writeD(_player.getPledgeType());
		writeD(_player.getAppearance().getTitleColor());
		writeD(CursedWeaponManager.getInstance().getCurrentStage(_player.getCursedWeaponEquippedId()));
	}
}