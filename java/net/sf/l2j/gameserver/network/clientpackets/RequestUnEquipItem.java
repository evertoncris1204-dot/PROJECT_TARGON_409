package net.sf.l2j.gameserver.network.clientpackets;

import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance;
import net.sf.l2j.gameserver.model.item.kind.Item;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.SystemMessage;

public class RequestUnEquipItem extends L2GameClientPacket
{
	private int _slot;
	
	@Override
	protected void readImpl()
	{
		_slot = readD();
	}
	
	@Override
	protected void runImpl()
	{
		final Player player = getClient().getPlayer();
		if (player == null)
			return;
		
		// Prevent of unequiping a cursed weapon
		if (_slot == Item.SLOT_LR_HAND && player.isCursedWeaponEquipped())
			return;
		
		final ItemInstance item = player.getInventory().getItemFrom(_slot);
		if (item == null)
			return;
			
		// Prevent player from unequipping items in special conditions
		// Unequip item on advExt sends the error message if castingNow
		// This is rather stupid, since UseItem achieves the same effect and is allowed.
		if (player.getCast().isCastingNow() || player.isStunned() || player.isSleeping() || player.isParalyzed() || player.isAfraid() || player.isAlikeDead())
		{
			player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.S1_CANNOT_BE_USED).addItemName(item));
			return;
		}
		
		final ItemInstance[] unequipped = player.getInventory().unequipItemInBodySlotAndRecord(_slot);
		
		for (ItemInstance itm : unequipped)
		{
			itm.unChargeAllShots();
			
			// If removing a weapon, check if weapon skin should be removed
			if ((itm.getItem().getBodyPart() & net.sf.l2j.gameserver.model.item.kind.Item.SLOT_ALLWEAPON) != 0)
			{
				// Check if no weapon is equipped after removal (check both RHAND and LHAND slots)
				net.sf.l2j.gameserver.model.item.instance.ItemInstance rhandItem = player.getInventory().getItemFrom(net.sf.l2j.gameserver.enums.Paperdoll.RHAND);
				net.sf.l2j.gameserver.model.item.instance.ItemInstance lhandItem = player.getInventory().getItemFrom(net.sf.l2j.gameserver.enums.Paperdoll.LHAND);
				
				boolean hasWeapon = false;
				if (rhandItem != null && rhandItem.getItem() instanceof net.sf.l2j.gameserver.model.item.kind.Weapon)
				{
					net.sf.l2j.gameserver.model.item.kind.Weapon weapon = (net.sf.l2j.gameserver.model.item.kind.Weapon) rhandItem.getItem();
					if (weapon.getItemType() != net.sf.l2j.gameserver.enums.items.WeaponType.NONE && weapon.getItemType() != net.sf.l2j.gameserver.enums.items.WeaponType.FIST)
						hasWeapon = true;
				}
				if (!hasWeapon && lhandItem != null && lhandItem.getItem() instanceof net.sf.l2j.gameserver.model.item.kind.Weapon)
				{
					net.sf.l2j.gameserver.model.item.kind.Weapon weapon = (net.sf.l2j.gameserver.model.item.kind.Weapon) lhandItem.getItem();
					if (weapon.getItemType() != net.sf.l2j.gameserver.enums.items.WeaponType.NONE && weapon.getItemType() != net.sf.l2j.gameserver.enums.items.WeaponType.FIST)
						hasWeapon = true;
				}
				
				// If no weapon is equipped, remove weapon skin
				if (!hasWeapon && player.getWeaponSkinOption() > 0)
				{
					player.setWeaponSkinOption(0);
					player.storeDressMeData();
				}
			}
		}
		
		player.broadcastUserInfo();
		
		// this can be 0 if the user pressed the right mousebutton twice very fast
		if (unequipped.length > 0)
		{
			SystemMessage sm = null;
			if (unequipped[0].getEnchantLevel() > 0)
			{
				sm = SystemMessage.getSystemMessage(SystemMessageId.EQUIPMENT_S1_S2_REMOVED);
				sm.addNumber(unequipped[0].getEnchantLevel());
				sm.addItemName(unequipped[0]);
			}
			else
			{
				sm = SystemMessage.getSystemMessage(SystemMessageId.S1_DISARMED);
				sm.addItemName(unequipped[0]);
			}
			player.sendPacket(sm);
		}
	}
}