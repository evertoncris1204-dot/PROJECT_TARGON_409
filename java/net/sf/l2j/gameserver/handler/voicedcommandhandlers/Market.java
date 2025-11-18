package net.sf.l2j.gameserver.handler.voicedcommandhandlers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.StringTokenizer;

import net.sf.l2j.commons.pool.ConnectionPool;
import net.sf.l2j.commons.lang.StringUtil;

import net.sf.l2j.config.MarketConfig;
import net.sf.l2j.gameserver.handler.IVoicedCommandHandler;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.Playable;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;

/**
 * Market System - Player to Player Trading System
 * Allows players to buy and sell items through a market interface
 */
public class Market implements IVoicedCommandHandler
{
	private static final String[] VOICED_COMMANDS = 
	{
		"market",
		"marketlist",
		"marketsell",
		"marketsellitem",
		"marketbuy",
		"marketbuyconfirm",
		"marketmy",
		"marketcancel",
		"marketcreate"
	};
	
	// Market configuration is loaded from MarketConfig
	
	/**
	 * Helper method to check if item is currency
	 */
	private static boolean isCurrencyItem(int itemId)
	{
		return itemId == MarketConfig.MARKET_CURRENCY_ITEM_ID;
	}
	
	/**
	 * Helper method to get currency amount from player
	 */
	private static long getCurrencyAmount(Player player)
	{
		if (MarketConfig.MARKET_CURRENCY_ITEM_ID == 57) // Adena
		{
			return player.getInventory().getAdena();
		}
		else
		{
			ItemInstance currencyItem = player.getInventory().getItemByItemId(MarketConfig.MARKET_CURRENCY_ITEM_ID);
			return currencyItem != null ? currencyItem.getCount() : 0;
		}
	}
	
	/**
	 * Helper method to reduce currency from player
	 */
	private static boolean reduceCurrency(Player player, long amount)
	{
		if (MarketConfig.MARKET_CURRENCY_ITEM_ID == 57) // Adena
		{
			if (amount > Integer.MAX_VALUE)
				return false;
			return player.reduceAdena((int)amount, false);
		}
		else
		{
			return player.destroyItemByItemId(MarketConfig.MARKET_CURRENCY_ITEM_ID, (int)amount, false);
		}
	}
	
	/**
	 * Helper method to add currency to player
	 */
	private static void addCurrency(Player player, long amount)
	{
		if (MarketConfig.MARKET_CURRENCY_ITEM_ID == 57) // Adena
		{
			if (amount <= Integer.MAX_VALUE)
				player.addAdena((int)amount, false);
		}
		else
		{
			player.addItem(MarketConfig.MARKET_CURRENCY_ITEM_ID, (int)amount, false);
		}
	}
	
	/**
	 * Helper method to get currency name
	 */
	private static String getCurrencyName()
	{
		if (MarketConfig.MARKET_CURRENCY_ITEM_ID == 57)
			return "Adena";
		// Try to get item name from ItemData
		try
		{
			net.sf.l2j.gameserver.data.xml.ItemData itemData = net.sf.l2j.gameserver.data.xml.ItemData.getInstance();
			net.sf.l2j.gameserver.model.item.kind.Item item = itemData.getTemplate(MarketConfig.MARKET_CURRENCY_ITEM_ID);
			if (item != null)
				return item.getName();
		}
		catch (Exception e)
		{
			// Ignore
		}
		return "Currency";
	}
	
	@Override
	public boolean useVoicedCommand(String command, Player player, String params)
	{
		if (command.startsWith("market"))
		{
			StringTokenizer st = new StringTokenizer(command, " ");
			String cmd = st.hasMoreTokens() ? st.nextToken() : command;
			
			if (cmd.equals("market") || cmd.equals("marketlist"))
			{
				int page = 0;
				if (st.hasMoreTokens())
				{
					try
					{
						page = Integer.parseInt(st.nextToken());
					}
					catch (NumberFormatException e)
					{
						// Use default page 0
					}
				}
				showMarketList(player, page);
			}
			else if (cmd.equals("marketsell"))
			{
				showSellMenu(player);
			}
			else if (cmd.equals("marketsellitem"))
			{
				if (st.hasMoreTokens())
				{
					try
					{
						int itemObjectId = Integer.parseInt(st.nextToken());
						showSellPriceInput(player, itemObjectId);
					}
					catch (NumberFormatException e)
					{
						player.sendMessage("Invalid item ID.");
					}
				}
			}
			else if (cmd.equals("marketcreate"))
			{
				if (st.hasMoreTokens())
				{
					try
					{
						int itemObjectId = Integer.parseInt(st.nextToken());
						
						// Parse remaining tokens - could be count and price, or just price
						String token1 = st.hasMoreTokens() ? st.nextToken() : null;
						String token2 = st.hasMoreTokens() ? st.nextToken() : null;
						
						long count = 1;
						long price = 0;
						
						// Try to determine which is count and which is price
						if (token1 != null && !token1.isEmpty() && !token1.startsWith("$"))
						{
							try
							{
								long val1 = Long.parseLong(token1);
								if (token2 != null && !token2.isEmpty() && !token2.startsWith("$"))
								{
									// Both tokens present - first is count, second is price
									count = val1;
									price = Long.parseLong(token2);
								}
								else
								{
									// Only one token - assume it's price
									price = val1;
								}
							}
							catch (NumberFormatException e)
							{
								player.sendMessage("Invalid number format: " + token1);
								return false;
							}
						}
						
						if (price > 0)
						{
							createListing(player, itemObjectId, price, count);
						}
						else
						{
							player.sendMessage("Invalid price.");
						}
					}
					catch (NumberFormatException e)
					{
						player.sendMessage("Invalid parameters: " + e.getMessage());
					}
				}
				else
				{
					player.sendMessage("Missing parameters for marketcreate command.");
				}
			}
			else if (cmd.equals("marketbuy"))
			{
				if (st.hasMoreTokens())
				{
					try
					{
						int listingId = Integer.parseInt(st.nextToken());
						showBuyConfirmation(player, listingId);
					}
					catch (NumberFormatException e)
					{
						player.sendMessage("Invalid listing ID.");
					}
				}
			}
			else if (cmd.equals("marketbuyconfirm"))
			{
				if (st.hasMoreTokens())
				{
					try
					{
						int listingId = Integer.parseInt(st.nextToken());
						buyItem(player, listingId);
					}
					catch (NumberFormatException e)
					{
						player.sendMessage("Invalid listing ID.");
					}
				}
			}
			else if (cmd.equals("marketmy"))
			{
				showMyListings(player);
			}
			else if (cmd.equals("marketcancel"))
			{
				if (st.hasMoreTokens())
				{
					try
					{
						int listingId = Integer.parseInt(st.nextToken());
						cancelListing(player, listingId);
					}
					catch (NumberFormatException e)
					{
						player.sendMessage("Invalid listing ID.");
					}
				}
			}
		}
		
		return false;
	}
	
	/**
	 * Shows the market listing page
	 */
	public static void showMarketList(Player player, int page)
	{
		NpcHtmlMessage html = new NpcHtmlMessage(0);
		html.setFile("data/html/mods/menu/Market.htm");
		html.disableValidation();
		
		// Get active listings
		List<MarketListing> listings = getActiveListings(page, 10);
		
		StringBuilder listHtml = new StringBuilder();
		if (listings.isEmpty())
		{
			listHtml.append("<tr><td colspan=\"6\" align=\"center\">No items available in the market.</td></tr>");
		}
		else
		{
			for (MarketListing listing : listings)
			{
				listHtml.append("<tr>");
				listHtml.append("<td>").append(listing.getItemName()).append("</td>");
				listHtml.append("<td>").append(listing.getEnchant() > 0 ? "+" + listing.getEnchant() : "").append("</td>");
				listHtml.append("<td>").append(StringUtil.formatNumber(listing.getItemCount())).append("</td>");
				listHtml.append("<td>").append(listing.getSellerName()).append("</td>");
				listHtml.append("<td>").append(StringUtil.formatNumber(listing.getPrice())).append(" ").append(getCurrencyName()).append("</td>");
				listHtml.append("<td><button value=\"Buy\" action=\"bypass _marketbuy ").append(listing.getListingId()).append("\" width=60 height=21 back=\"L2UI.DefaultButton_click\" fore=\"L2UI.DefaultButton\"></td>");
				listHtml.append("</tr>");
			}
		}
		
		html.replace("%list%", listHtml.toString());
		html.replace("%page%", String.valueOf(page + 1));
		
		// Pagination
		StringBuilder pagination = new StringBuilder();
		if (page > 0)
		{
			pagination.append("<a action=\"bypass _marketlist ").append(page - 1).append("\">Previous</a> ");
		}
		pagination.append("Page ").append(page + 1);
		if (listings.size() == 10)
		{
			pagination.append(" <a action=\"bypass _marketlist ").append(page + 1).append("\">Next</a>");
		}
		html.replace("%pagination%", pagination.toString());
		
		player.sendPacket(html);
	}
	
	/**
	 * Shows the sell menu
	 */
	public static void showSellMenu(Player player)
	{
		NpcHtmlMessage html = new NpcHtmlMessage(0);
		html.setFile("data/html/mods/menu/MarketSell.htm");
		html.disableValidation();
		
		// Get player's items (excluding equipped items)
		StringBuilder itemsHtml = new StringBuilder();
		int count = 0;
		
		for (ItemInstance item : player.getInventory().getItems())
		{
			if ((!MarketConfig.ALLOW_EQUIPPED_ITEMS && item.isEquipped()) || 
				(!MarketConfig.ALLOW_QUEST_ITEMS && item.isQuestItem()) || 
				isCurrencyItem(item.getItemId())) // Skip currency item
				continue;
			
			itemsHtml.append("<tr>");
			itemsHtml.append("<td>").append(item.getItem().getName()).append("</td>");
			itemsHtml.append("<td>").append(item.getEnchantLevel() > 0 ? "+" + item.getEnchantLevel() : "").append("</td>");
			itemsHtml.append("<td>").append(StringUtil.formatNumber(item.getCount())).append("</td>");
			itemsHtml.append("<td><button value=\"Sell\" action=\"bypass _marketsellitem ").append(item.getObjectId()).append("\" width=60 height=21 back=\"L2UI.DefaultButton_click\" fore=\"L2UI.DefaultButton\"></td>");
			itemsHtml.append("</tr>");
			
			count++;
			if (count >= 20) // Limit to 20 items per page
				break;
		}
		
		if (itemsHtml.length() == 0)
		{
			itemsHtml.append("<tr><td colspan=\"4\" align=\"center\">No items available to sell.</td></tr>");
		}
		
		html.replace("%items%", itemsHtml.toString());
		player.sendPacket(html);
	}
	
	/**
	 * Shows sell price input dialog
	 */
	public static void showSellPriceInput(Player player, int itemObjectId)
	{
		ItemInstance item = player.getInventory().getItemByObjectId(itemObjectId);
		if (item == null || (!MarketConfig.ALLOW_EQUIPPED_ITEMS && item.isEquipped()) || 
			(!MarketConfig.ALLOW_QUEST_ITEMS && item.isQuestItem()) || 
			isCurrencyItem(item.getItemId()))
		{
			player.sendMessage("Invalid item.");
			return;
		}
		
		NpcHtmlMessage html = new NpcHtmlMessage(0);
		html.setFile("data/html/mods/menu/MarketSellPrice.htm");
		html.disableValidation();
		html.replace("%itemname%", item.getItem().getName());
		html.replace("%itemcount%", StringUtil.formatNumber(item.getCount()));
		html.replace("%itemobjectid%", String.valueOf(itemObjectId));
		player.sendPacket(html);
	}
	
	/**
	 * Creates a new listing
	 */
	public static void createListing(Player player, int itemObjectId, long price, long count)
	{
		ItemInstance item = player.getInventory().getItemByObjectId(itemObjectId);
		if (item == null)
		{
			player.sendMessage("Item not found in inventory.");
			return;
		}
		
		if ((!MarketConfig.ALLOW_EQUIPPED_ITEMS && item.isEquipped()) || 
			(!MarketConfig.ALLOW_QUEST_ITEMS && item.isQuestItem()) || 
			isCurrencyItem(item.getItemId()))
		{
			player.sendMessage("You cannot sell this item.");
			return;
		}
		
		if (!MarketConfig.ALLOW_ENCHANTED_ITEMS && item.getEnchantLevel() > 0)
		{
			player.sendMessage("You cannot sell enchanted items.");
			return;
		}
		
		if (count <= 0 || count > item.getCount())
		{
			player.sendMessage("Invalid quantity. Available: " + item.getCount());
			return;
		}
		
		if (price < MarketConfig.MIN_LISTING_PRICE)
		{
			player.sendMessage("Price must be at least " + StringUtil.formatNumber(MarketConfig.MIN_LISTING_PRICE) + " " + getCurrencyName() + ".");
			return;
		}
		
		if (MarketConfig.MAX_LISTING_PRICE > 0 && price > MarketConfig.MAX_LISTING_PRICE)
		{
			player.sendMessage("Price cannot exceed " + StringUtil.formatNumber(MarketConfig.MAX_LISTING_PRICE) + " " + getCurrencyName() + ".");
			return;
		}
		
		// Check if player has too many active listings
		if (MarketConfig.MAX_LISTINGS_PER_PLAYER > 0 && getPlayerActiveListingsCount(player.getObjectId()) >= MarketConfig.MAX_LISTINGS_PER_PLAYER)
		{
			player.sendMessage("You can only have " + MarketConfig.MAX_LISTINGS_PER_PLAYER + " active listings at a time.");
			return;
		}
		
		// Store item data before modifying inventory
		int itemId = item.getItemId();
		String itemName = item.getItem().getName();
		int enchantLevel = item.getEnchantLevel();
		
		try (Connection con = ConnectionPool.getConnection())
		{
			// Create listing first
			String insertSql = "INSERT INTO market_listings (seller_id, seller_name, item_object_id, item_id, item_name, item_count, item_enchant, price, created_at, expires_at, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)";
			try (PreparedStatement ps = con.prepareStatement(insertSql))
			{
				long currentTime = System.currentTimeMillis();
				long expiresAt = currentTime + MarketConfig.getListingDuration();
				
				ps.setInt(1, player.getObjectId());
				ps.setString(2, player.getName());
				ps.setInt(3, itemObjectId); // Store original object ID
				ps.setInt(4, itemId);
				ps.setString(5, itemName);
				ps.setLong(6, count);
				ps.setInt(7, enchantLevel);
				ps.setLong(8, price);
				ps.setLong(9, currentTime);
				ps.setLong(10, expiresAt);
				
				ps.executeUpdate();
			}
			
			// Remove item from inventory after successful listing creation
			if (count >= item.getCount())
			{
				// Remove entire item
				player.destroyItem(item, false);
			}
			else
			{
				// Reduce quantity
				player.destroyItem(item, (int)count, false);
			}
			
			player.sendMessage("Item listed successfully! Listing expires in " + MarketConfig.LISTING_DURATION_DAYS + " days.");
			
			// Announce to all players
			if (MarketConfig.SEND_GLOBAL_ANNOUNCEMENT)
			{
				String announcement = player.getName() + " added " + itemName + 
					(enchantLevel > 0 ? " +" + enchantLevel : "") + 
					" x" + StringUtil.formatNumber(count) + " to the Market for " + 
					StringUtil.formatNumber(price) + " " + getCurrencyName() + "!";
				World.announceToOnlinePlayers(announcement, false);
			}
			
			showMyListings(player);
		}
		catch (SQLException e)
		{
			player.sendMessage("Error creating listing: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	/**
	 * Shows buy confirmation dialog
	 */
	public static void showBuyConfirmation(Player player, int listingId)
	{
		MarketListing listing = getListing(listingId);
		if (listing == null || listing.getStatus() != 1)
		{
			player.sendMessage("Listing not found or no longer available.");
			return;
		}
		
		if (listing.getSellerId() == player.getObjectId())
		{
			player.sendMessage("You cannot buy your own listing.");
			return;
		}
		
		if (System.currentTimeMillis() > listing.getExpiresAt())
		{
			player.sendMessage("This listing has expired.");
			expireListing(listingId);
			return;
		}
		
		if (getCurrencyAmount(player) < listing.getPrice())
		{
			player.sendMessage("You don't have enough " + getCurrencyName() + ".");
			return;
		}
		
		if (!player.getInventory().validateCapacity(1))
		{
			player.sendMessage("Your inventory is full.");
			return;
		}
		
		NpcHtmlMessage html = new NpcHtmlMessage(0);
		html.setFile("data/html/mods/menu/MarketBuyConfirm.htm");
		html.disableValidation();
		html.replace("%itemname%", listing.getItemName());
		html.replace("%enchant%", listing.getEnchant() > 0 ? "+" + listing.getEnchant() : "");
		html.replace("%quantity%", StringUtil.formatNumber(listing.getItemCount()));
		html.replace("%price%", StringUtil.formatNumber(listing.getPrice()));
		html.replace("%seller%", listing.getSellerName());
		html.replace("%listingid%", String.valueOf(listingId));
		player.sendPacket(html);
	}
	
	/**
	 * Buys an item from the market (after confirmation)
	 */
	public static void buyItem(Player player, int listingId)
	{
		MarketListing listing = getListing(listingId);
		if (listing == null || listing.getStatus() != 1)
		{
			player.sendMessage("Listing not found or no longer available.");
			return;
		}
		
		if (listing.getSellerId() == player.getObjectId())
		{
			player.sendMessage("You cannot buy your own listing.");
			return;
		}
		
		if (System.currentTimeMillis() > listing.getExpiresAt())
		{
			player.sendMessage("This listing has expired.");
			expireListing(listingId);
			return;
		}
		
		if (getCurrencyAmount(player) < listing.getPrice())
		{
			player.sendMessage("You don't have enough " + getCurrencyName() + ".");
			return;
		}
		
		// Check inventory space
		if (!player.getInventory().validateCapacity(1))
		{
			player.sendMessage("Your inventory is full.");
			return;
		}
		
		try (Connection con = ConnectionPool.getConnection())
		{
			con.setAutoCommit(false);
			
			try
			{
				// Remove adena from buyer
				if (!reduceCurrency(player, listing.getPrice()))
				{
					player.sendMessage("You don't have enough " + getCurrencyName() + ".");
					con.rollback();
					return;
				}
				
				// Calculate commission
				long commission = (long)(listing.getPrice() * MarketConfig.COMMISSION_RATE);
				long sellerReceives = listing.getPrice() - commission;
				
				// Give currency to seller (if online) or store in database
				Player seller = World.getInstance().getPlayer(listing.getSellerId());
				
				if (seller != null && seller.isOnline())
				{
					addCurrency(seller, sellerReceives);
					seller.sendMessage("Your item " + listing.getItemName() + " was sold for " + StringUtil.formatNumber(sellerReceives) + " " + getCurrencyName() + " (commission: " + StringUtil.formatNumber(commission) + ").");
				}
				else
				{
					// Store payment for offline seller (would need a separate table or mail system)
					// For now, we'll just log it - in a full implementation, you'd store this in a pending_payments table
					// TODO: Implement offline payment system
				}
				
				// Create item for buyer
				ItemInstance newItem = player.addItem(listing.getItemId(), (int)listing.getItemCount(), false);
				if (newItem != null && listing.getEnchant() > 0)
				{
					newItem.setEnchantLevel(listing.getEnchant(), player);
				}
				
				// Mark listing as sold
				String updateSql = "UPDATE market_listings SET status = 0 WHERE listing_id = ?";
				try (PreparedStatement ps = con.prepareStatement(updateSql))
				{
					ps.setInt(1, listingId);
					ps.executeUpdate();
				}
				
				// Record transaction
				String insertSql = "INSERT INTO market_transactions (listing_id, seller_id, seller_name, buyer_id, buyer_name, item_id, item_name, item_count, price, commission, transaction_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
				try (PreparedStatement ps = con.prepareStatement(insertSql))
				{
					ps.setInt(1, listingId);
					ps.setInt(2, listing.getSellerId());
					ps.setString(3, listing.getSellerName());
					ps.setInt(4, player.getObjectId());
					ps.setString(5, player.getName());
					ps.setInt(6, listing.getItemId());
					ps.setString(7, listing.getItemName());
					ps.setLong(8, listing.getItemCount());
					ps.setLong(9, listing.getPrice());
					ps.setLong(10, commission);
					ps.setLong(11, System.currentTimeMillis());
					ps.executeUpdate();
				}
				
				con.commit();
				
				player.sendMessage("You bought " + listing.getItemName() + " for " + StringUtil.formatNumber(listing.getPrice()) + " " + getCurrencyName() + ".");
				showMarketList(player, 0);
			}
			catch (Exception e)
			{
				con.rollback();
				throw e;
			}
			finally
			{
				con.setAutoCommit(true);
			}
		}
		catch (SQLException e)
		{
			player.sendMessage("Error processing purchase: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	/**
	 * Shows player's active listings
	 */
	public static void showMyListings(Player player)
	{
		NpcHtmlMessage html = new NpcHtmlMessage(0);
		html.setFile("data/html/mods/menu/MarketMy.htm");
		html.disableValidation();
		
		List<MarketListing> listings = getPlayerListings(player.getObjectId());
		
		StringBuilder listHtml = new StringBuilder();
		if (listings.isEmpty())
		{
			listHtml.append("<tr><td colspan=\"6\" align=\"center\">You have no active listings.</td></tr>");
		}
		else
		{
			SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm");
			for (MarketListing listing : listings)
			{
				long expiresIn = listing.getExpiresAt() - System.currentTimeMillis();
				String expiresStr = expiresIn > 0 ? (expiresIn / (24 * 60 * 60 * 1000)) + " days" : "Expired";
				
				listHtml.append("<tr>");
				listHtml.append("<td>").append(listing.getItemName()).append("</td>");
				listHtml.append("<td>").append(listing.getEnchant() > 0 ? "+" + listing.getEnchant() : "").append("</td>");
				listHtml.append("<td>").append(StringUtil.formatNumber(listing.getItemCount())).append("</td>");
				listHtml.append("<td>").append(StringUtil.formatNumber(listing.getPrice())).append(" ").append(getCurrencyName()).append("</td>");
				listHtml.append("<td>").append(expiresStr).append("</td>");
				listHtml.append("<td><button value=\"Cancel\" action=\"bypass _marketcancel ").append(listing.getListingId()).append("\" width=60 height=21 back=\"L2UI.DefaultButton_click\" fore=\"L2UI.DefaultButton\"></td>");
				listHtml.append("</tr>");
			}
		}
		
		html.replace("%list%", listHtml.toString());
		player.sendPacket(html);
	}
	
	/**
	 * Cancels a listing and returns item to seller
	 */
	public static void cancelListing(Player player, int listingId)
	{
		MarketListing listing = getListing(listingId);
		if (listing == null || listing.getSellerId() != player.getObjectId())
		{
			player.sendMessage("Listing not found or you don't own this listing.");
			return;
		}
		
		if (listing.getStatus() != 1)
		{
			player.sendMessage("This listing is no longer active.");
			return;
		}
		
		try (Connection con = ConnectionPool.getConnection())
		{
			// Return item to seller
			ItemInstance item = player.addItem(listing.getItemId(), (int)listing.getItemCount(), false);
			if (item != null && listing.getEnchant() > 0)
			{
				item.setEnchantLevel(listing.getEnchant(), player);
			}
			
			// Mark listing as cancelled
			String updateSql = "UPDATE market_listings SET status = 0 WHERE listing_id = ?";
			try (PreparedStatement ps = con.prepareStatement(updateSql))
			{
				ps.setInt(1, listingId);
				ps.executeUpdate();
			}
			
			player.sendMessage("Listing cancelled. Item returned to your inventory.");
			showMyListings(player);
		}
		catch (SQLException e)
		{
			player.sendMessage("Error cancelling listing: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	// Database helper methods
	private static List<MarketListing> getActiveListings(int page, int pageSize)
	{
		List<MarketListing> listings = new ArrayList<>();
		
		try (Connection con = ConnectionPool.getConnection())
		{
			String sql = "SELECT * FROM market_listings WHERE status = 1 AND expires_at > ? ORDER BY created_at DESC LIMIT ? OFFSET ?";
			try (PreparedStatement ps = con.prepareStatement(sql))
			{
				ps.setLong(1, System.currentTimeMillis());
				ps.setInt(2, pageSize);
				ps.setInt(3, page * pageSize);
				
				try (ResultSet rs = ps.executeQuery())
				{
					while (rs.next())
					{
						listings.add(new MarketListing(rs));
					}
				}
			}
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}
		
		return listings;
	}
	
	private static MarketListing getListing(int listingId)
	{
		try (Connection con = ConnectionPool.getConnection())
		{
			String sql = "SELECT * FROM market_listings WHERE listing_id = ?";
			try (PreparedStatement ps = con.prepareStatement(sql))
			{
				ps.setInt(1, listingId);
				
				try (ResultSet rs = ps.executeQuery())
				{
					if (rs.next())
					{
						return new MarketListing(rs);
					}
				}
			}
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}
		
		return null;
	}
	
	private static List<MarketListing> getPlayerListings(int playerId)
	{
		List<MarketListing> listings = new ArrayList<>();
		
		try (Connection con = ConnectionPool.getConnection())
		{
			String sql = "SELECT * FROM market_listings WHERE seller_id = ? AND status = 1 ORDER BY created_at DESC";
			try (PreparedStatement ps = con.prepareStatement(sql))
			{
				ps.setInt(1, playerId);
				
				try (ResultSet rs = ps.executeQuery())
				{
					while (rs.next())
					{
						listings.add(new MarketListing(rs));
					}
				}
			}
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}
		
		return listings;
	}
	
	private static int getPlayerActiveListingsCount(int playerId)
	{
		try (Connection con = ConnectionPool.getConnection())
		{
			String sql = "SELECT COUNT(*) FROM market_listings WHERE seller_id = ? AND status = 1";
			try (PreparedStatement ps = con.prepareStatement(sql))
			{
				ps.setInt(1, playerId);
				
				try (ResultSet rs = ps.executeQuery())
				{
					if (rs.next())
					{
						return rs.getInt(1);
					}
				}
			}
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}
		
		return 0;
	}
	
	private static void expireListing(int listingId)
	{
		try (Connection con = ConnectionPool.getConnection())
		{
			String sql = "UPDATE market_listings SET status = 0 WHERE listing_id = ?";
			try (PreparedStatement ps = con.prepareStatement(sql))
			{
				ps.setInt(1, listingId);
				ps.executeUpdate();
			}
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}
	}
	
	/**
	 * MarketListing data class
	 */
	private static class MarketListing
	{
		private final int listingId;
		private final int sellerId;
		private final String sellerName;
		private final int itemObjectId;
		private final int itemId;
		private final String itemName;
		private final long itemCount;
		private final int enchant;
		private final long price;
		private final long createdAt;
		private final long expiresAt;
		private final int status;
		
		public MarketListing(ResultSet rs) throws SQLException
		{
			this.listingId = rs.getInt("listing_id");
			this.sellerId = rs.getInt("seller_id");
			this.sellerName = rs.getString("seller_name");
			this.itemObjectId = rs.getInt("item_object_id");
			this.itemId = rs.getInt("item_id");
			this.itemName = rs.getString("item_name");
			this.itemCount = rs.getLong("item_count");
			this.enchant = rs.getInt("item_enchant");
			this.price = rs.getLong("price");
			this.createdAt = rs.getLong("created_at");
			this.expiresAt = rs.getLong("expires_at");
			this.status = rs.getInt("status");
		}
		
		public int getListingId() { return listingId; }
		public int getSellerId() { return sellerId; }
		public String getSellerName() { return sellerName; }
		public int getItemObjectId() { return itemObjectId; }
		public int getItemId() { return itemId; }
		public String getItemName() { return itemName; }
		public long getItemCount() { return itemCount; }
		public int getEnchant() { return enchant; }
		public long getPrice() { return price; }
		public long getCreatedAt() { return createdAt; }
		public long getExpiresAt() { return expiresAt; }
		public int getStatus() { return status; }
	}
	
	@Override
	public String[] getVoicedCommandList()
	{
		return VOICED_COMMANDS;
	}
}

