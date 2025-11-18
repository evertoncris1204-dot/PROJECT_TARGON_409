package net.sf.l2j.gameserver.network.clientpackets;

import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.network.serverpackets.ActionFailed;
import net.sf.l2j.gameserver.network.serverpackets.RecipeShopSellList;
import net.sf.l2j.gameserver.util.sellBuffEngine.BuffShopManager;
import net.sf.l2j.gameserver.util.sellBuffEngine.BuffShopUIManager;
import net.sf.l2j.gameserver.util.sellBuffEngine.ShopObject;

public final class RequestRecipeShopManagePrev extends L2GameClientPacket
{
	@Override
	protected void readImpl()
	{
		// Do nothing.
	}
	
	@Override
	protected void runImpl()
	{
		final Player player = getClient().getPlayer();
		if (player == null)
			return;
		
		// Player shouldn't be able to set stores if he/she is alike dead (dead or fake death)
		if (player.isAlikeDead())
		{
			sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		if (!(player.getTarget() instanceof Player targetPlayer))
			return;
		
		// Verifica se é uma loja de buffs
		if (BuffShopManager.getInstance().getSellers().containsKey(targetPlayer.getObjectId()))
		{
			Integer ownerId = BuffShopManager.getInstance().getSellers().get(targetPlayer.getObjectId());
			if (ownerId != null)
			{
				ShopObject shop = BuffShopManager.getInstance().getShops().get(ownerId);
				if (shop != null)
				{
					// Abre a janela do BuffShop ao invés da janela padrão de Recipe Shop
					BuffShopUIManager.getInstance().showPublicShopWindow(player, targetPlayer, shop, 1, 1);
					return;
				}
			}
		}
		
		// Se não for loja de buffs, usa o comportamento padrão
		player.sendPacket(new RecipeShopSellList(player, targetPlayer));
	}
}