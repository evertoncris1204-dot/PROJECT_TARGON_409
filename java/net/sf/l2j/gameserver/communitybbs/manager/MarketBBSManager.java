package net.sf.l2j.gameserver.communitybbs.manager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import net.sf.l2j.commons.pool.ConnectionPool;
import net.sf.l2j.commons.lang.StringUtil;

import net.sf.l2j.config.MarketConfig;
import net.sf.l2j.gameserver.data.cache.HtmCache;
import net.sf.l2j.gameserver.data.xml.IconTable;
import net.sf.l2j.gameserver.enums.SayType;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.Playable;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance;
import net.sf.l2j.gameserver.network.serverpackets.CreatureSay;

public class MarketBBSManager extends BaseBBSManager
{
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
	
	protected MarketBBSManager()
	{
	}
	
	@Override
	public void parseCmd(String command, Player player)
	{
		if (command.equals("_bbsmarket"))
		{
			showAuctionList(player, 0, "", "NONE", "ALL");
		}
		else if (command.startsWith("_bbsmarket;"))
		{
			StringTokenizer st = new StringTokenizer(command, ";");
			st.nextToken(); // Skip "_bbsmarket"
			
			String action = st.hasMoreTokens() ? st.nextToken() : "";
			
			if (action.isEmpty() || action.equals("list"))
			{
				int page = st.hasMoreTokens() ? Integer.parseInt(st.nextToken()) : 0;
				String itemName = st.hasMoreTokens() ? st.nextToken() : "";
				String grade = st.hasMoreTokens() ? st.nextToken() : "NONE";
				String category = st.hasMoreTokens() ? st.nextToken() : "ALL";
				showAuctionList(player, page, itemName, grade, category);
			}
			else if (action.equals("create"))
			{
				showCreateAuction(player);
			}
			else if (action.equals("my"))
			{
				showMyAuction(player);
			}
			else if (action.equals("buy"))
			{
				if (st.hasMoreTokens())
				{
					int listingId = Integer.parseInt(st.nextToken());
					showBuyConfirmation(player, listingId);
				}
			}
			else if (action.equals("buyconfirm"))
			{
				if (st.hasMoreTokens())
				{
					int listingId = Integer.parseInt(st.nextToken());
					buyItem(player, listingId);
				}
			}
			else if (action.equals("sellitem"))
			{
				if (st.hasMoreTokens())
				{
					int itemObjectId = Integer.parseInt(st.nextToken());
					showSellPriceInput(player, itemObjectId);
				}
			}
			else if (action.equals("createlisting"))
			{
				if (st.hasMoreTokens())
				{
					try
					{
						String itemObjectIdStr = st.nextToken();
						
						int itemObjectId = Integer.parseInt(itemObjectIdStr);
						
						String quantityStr = st.hasMoreTokens() ? st.nextToken() : null;
						String priceStr = st.hasMoreTokens() ? st.nextToken() : null;
						
						long count = 1;
						long price = 0;
						
						// Parse quantity
						if (quantityStr != null && !quantityStr.isEmpty() && !quantityStr.startsWith("$"))
						{
							try
							{
								count = Long.parseLong(quantityStr);
							}
							catch (NumberFormatException e)
							{
								// Use default count of 1
								count = 1;
							}
						}
						
						// Parse price
						if (priceStr != null && !priceStr.isEmpty() && !priceStr.startsWith("$"))
						{
							try
							{
								price = Long.parseLong(priceStr);
							}
							catch (NumberFormatException e)
							{
								player.sendMessage("Invalid price format: " + priceStr);
								return;
							}
						}
						
						if (price > 0)
						{
							createListing(player, itemObjectId, price, count);
						}
						else
						{
							player.sendMessage("Price must be greater than 0.");
						}
					}
					catch (NumberFormatException e)
					{
						player.sendMessage("Invalid parameters: " + e.getMessage());
						e.printStackTrace();
					}
				}
				else
				{
					player.sendMessage("Missing parameters for createlisting.");
				}
			}
			else if (action.equals("cancel"))
			{
				if (st.hasMoreTokens())
				{
					int listingId = Integer.parseInt(st.nextToken());
					cancelListing(player, listingId);
				}
			}
			else
			{
				showAuctionList(player, 0, "", "NONE", "ALL");
			}
		}
		else
		{
			super.parseCmd(command, player);
		}
	}
	
	@Override
	protected String getFolder()
	{
		return "market/";
	}
	
	@Override
	public void parseWrite(String ar1, String ar2, String ar3, String ar4, String ar5, Player player)
	{
		if (ar1.equals("createlisting"))
		{
			try
			{
				// ar2 should be itemObjectId, ar3 should be quantity, ar4 should be price
				int itemObjectId = 0;
				long count = 1;
				long price = 0;
				
				// Try to parse itemObjectId from ar2
				if (ar2 != null && !ar2.isEmpty() && !ar2.startsWith("$") && !ar2.equals("_"))
				{
					try
					{
						itemObjectId = Integer.parseInt(ar2);
					}
					catch (NumberFormatException e)
					{
						player.sendMessage("Invalid item ID: " + ar2);
						return;
					}
				}
				else
				{
					player.sendMessage("Item ID is missing. Received: " + ar2);
					return;
				}
				
				// Try to parse quantity from ar3
				// Format: "Write Market createlisting itemObjectId quantity price"
				// The client replaces variables, so ar3 should contain the quantity value
				if (ar3 != null && !ar3.isEmpty() && !ar3.equals("quantity") && !ar3.equals("price") && !ar3.equals("_"))
				{
					try
					{
						count = Long.parseLong(ar3);
					}
					catch (NumberFormatException e)
					{
						// Use default count of 1 if ar3 is the variable name
						count = 1;
					}
				}
				
				// Try to parse price from ar4
				// The client replaces variables, so ar4 should contain the price value
				if (ar4 != null && !ar4.isEmpty() && !ar4.equals("quantity") && !ar4.equals("price") && !ar4.equals("_"))
				{
					try
					{
						price = Long.parseLong(ar4);
					}
					catch (NumberFormatException e)
					{
						player.sendMessage("Invalid price: " + ar4);
						return;
					}
				}
				else
				{
					player.sendMessage("Price is missing. Received ar3: " + ar3 + ", ar4: " + ar4 + ", ar5: " + ar5);
					return;
				}
				
				if (price > 0 && itemObjectId > 0)
				{
					createListing(player, itemObjectId, price, count);
				}
				else
				{
					player.sendMessage("Invalid parameters. Price: " + price + ", ItemID: " + itemObjectId);
				}
			}
			catch (Exception e)
			{
				player.sendMessage("Error: " + e.getMessage());
				e.printStackTrace();
			}
		}
		else
		{
			super.parseWrite(ar1, ar2, ar3, ar4, ar5, player);
		}
	}
	
	private void showAuctionList(Player player, int page, String itemNameFilter, String gradeFilter, String categoryFilter)
	{
		String html = HtmCache.getInstance().getHtm(CB_PATH + getFolder() + "auction_list.htm");
		if (html == null)
		{
			html = generateAuctionListHtml(player, page, itemNameFilter, gradeFilter, categoryFilter);
		}
		else
		{
			html = html.replace("%list%", generateAuctionListTable(player, page, itemNameFilter, gradeFilter, categoryFilter));
			html = html.replace("%itemname%", itemNameFilter);
			html = html.replace("%grade%", gradeFilter);
			html = html.replace("%category%", categoryFilter);
			html = html.replace("%page%", String.valueOf(page + 1));
		}
		
		separateAndSend(html, player);
	}
	
	private String generateAuctionListHtml(Player player, int page, String itemNameFilter, String gradeFilter, String categoryFilter)
	{
		StringBuilder html = new StringBuilder();
		html.append("<html><body><br><br>");
		html.append("<table width=610><tr><td width=10></td><td width=600 align=center>");
		html.append("<table width=600 bgcolor=000000><tr>");
		html.append("<td width=80><button value=\"Auction List\" action=\"bypass _bbsmarket;list;").append(page).append(";").append(itemNameFilter).append(";").append(gradeFilter).append(";").append(categoryFilter).append("\" width=100 height=21 back=\"L2UI.DefaultButton_click\" fore=\"L2UI.DefaultButton\"></td>");
		html.append("<td width=80><button value=\"Create Auction\" action=\"bypass _bbsmarket;create\" width=100 height=21 back=\"L2UI.DefaultButton\" fore=\"L2UI.DefaultButton\"></td>");
		html.append("<td width=80><button value=\"My Auction\" action=\"bypass _bbsmarket;my\" width=100 height=21 back=\"L2UI.DefaultButton\" fore=\"L2UI.DefaultButton\"></td>");
		html.append("</tr></table>");
		html.append("<table width=600><tr>");
		html.append("<td width=150>Item Name:</td>");
		html.append("<td width=200><edit var=\"itemname\" width=180></td>");
		html.append("<td width=50><button value=\"Search\" action=\"bypass _bbsmarket;list;0;$itemname;").append(gradeFilter).append(";").append(categoryFilter).append("\" width=60 height=21 back=\"L2UI.DefaultButton_click\" fore=\"L2UI.DefaultButton\"></td>");
		html.append("</tr></table>");
		html.append("<table width=600><tr>");
		html.append("<td width=150>Grade:</td>");
		html.append("<td width=200><combobox width=180 height=21 var=\"grade\" list=\"NONE;D;C;B;A;S\" value=\"").append(gradeFilter).append("\"></td>");
		html.append("<td width=50><button value=\"APPLY\" action=\"bypass _bbsmarket;list;").append(page).append(";").append(itemNameFilter).append(";$grade;").append(categoryFilter).append("\" width=60 height=21 back=\"L2UI.DefaultButton_click\" fore=\"L2UI.DefaultButton\"></td>");
		html.append("</tr></table>");
		html.append("<table width=600><tr>");
		html.append("<td width=150>Category:</td>");
		html.append("<td width=200><combobox width=180 height=21 var=\"category\" list=\"ALL;Weapon;Armor;Accessory;Consumable;Material;Other\" value=\"").append(categoryFilter).append("\"></td>");
		html.append("<td width=50><button value=\"APPLY\" action=\"bypass _bbsmarket;list;").append(page).append(";").append(itemNameFilter).append(";").append(gradeFilter).append(";$category\" width=60 height=21 back=\"L2UI.DefaultButton_click\" fore=\"L2UI.DefaultButton\"></td>");
		html.append("</tr></table>");
		html.append("<table width=600 bgcolor=000000><tr>");
		html.append("<td width=40><font color=LEVEL>Icon</font></td>");
		html.append("<td width=160><font color=LEVEL>Item</font></td>");
		html.append("<td width=80><font color=LEVEL>Grade</font></td>");
		html.append("<td width=80><font color=LEVEL>Count</font></td>");
		html.append("<td width=120><font color=LEVEL>Sale Price</font></td>");
		html.append("<td width=120><font color=LEVEL>Seller</font></td>");
		html.append("</tr></table>");
		html.append(generateAuctionListTable(player, page, itemNameFilter, gradeFilter, categoryFilter));
		html.append("<table width=600><tr><td align=center>Page ").append(page + 1);
		if (page > 0)
		{
			html.append(" <a action=\"bypass _bbsmarket;list;").append(page - 1).append(";").append(itemNameFilter).append(";").append(gradeFilter).append(";").append(categoryFilter).append("\">PREV</a>");
		}
		if (getActiveListingsCount(page + 1, itemNameFilter, gradeFilter, categoryFilter) > 0)
		{
			html.append(" <a action=\"bypass _bbsmarket;list;").append(page + 1).append(";").append(itemNameFilter).append(";").append(gradeFilter).append(";").append(categoryFilter).append("\">NEXT</a>");
		}
		html.append("</td></tr></table>");
		html.append("</td></tr></table>");
		html.append("</body></html>");
		
		return html.toString();
	}
	
	private String generateAuctionListTable(Player player, int page, String itemNameFilter, String gradeFilter, String categoryFilter)
	{
		List<MarketListing> listings = getActiveListings(page, 10, itemNameFilter, gradeFilter, categoryFilter);
		StringBuilder html = new StringBuilder();
		html.append("<table width=600>");
		
		if (listings.isEmpty())
		{
			html.append("<tr><td colspan=\"6\" align=center>No items available in the market.</td></tr>");
		}
		else
		{
			for (MarketListing listing : listings)
			{
				String itemIcon = IconTable.getIcon(listing.getItemId());
				html.append("<tr>");
				html.append("<td width=40 align=center><img src=\"").append(itemIcon).append("\" width=32 height=32></td>");
				html.append("<td width=160><a action=\"bypass _bbsmarket;buy;").append(listing.getListingId()).append("\">").append(listing.getItemName()).append("</a></td>");
				html.append("<td width=80>").append(listing.getEnchant() > 0 ? "+" + listing.getEnchant() : "").append("</td>");
				html.append("<td width=80>").append(StringUtil.formatNumber(listing.getItemCount())).append("</td>");
				html.append("<td width=120><font color=FF9900>").append(StringUtil.formatNumber(listing.getPrice())).append("</font></td>");
				html.append("<td width=120>").append(listing.getSellerName()).append("</td>");
				html.append("</tr>");
			}
		}
		
		html.append("</table>");
		return html.toString();
	}
	
	private void showCreateAuction(Player player)
	{
		StringBuilder html = new StringBuilder();
		html.append("<html><body><br><br>");
		html.append("<table width=610><tr><td width=10></td><td width=600 align=center>");
		html.append("<table width=600 bgcolor=000000><tr>");
		html.append("<td width=80><button value=\"Auction List\" action=\"bypass _bbsmarket;list;0;;NONE;ALL\" width=100 height=21 back=\"L2UI.DefaultButton\" fore=\"L2UI.DefaultButton\"></td>");
		html.append("<td width=80><button value=\"Create Auction\" action=\"bypass _bbsmarket;create\" width=100 height=21 back=\"L2UI.DefaultButton_click\" fore=\"L2UI.DefaultButton\"></td>");
		html.append("<td width=80><button value=\"My Auction\" action=\"bypass _bbsmarket;my\" width=100 height=21 back=\"L2UI.DefaultButton\" fore=\"L2UI.DefaultButton\"></td>");
		html.append("</tr></table>");
		html.append("<table width=600 bgcolor=000000><tr>");
		html.append("<td width=40><font color=LEVEL>Icon</font></td>");
		html.append("<td width=110><font color=LEVEL>Item</font></td>");
		html.append("<td width=80><font color=LEVEL>Enchant</font></td>");
		html.append("<td width=100><font color=LEVEL>Quantity</font></td>");
		html.append("<td width=270><font color=LEVEL>Action</font></td>");
		html.append("</tr></table>");
		html.append("<table width=600>");
		
		int count = 0;
		for (ItemInstance item : player.getInventory().getItems())
		{
			if ((!MarketConfig.ALLOW_EQUIPPED_ITEMS && item.isEquipped()) || 
				(!MarketConfig.ALLOW_QUEST_ITEMS && item.isQuestItem()) || 
				isCurrencyItem(item.getItemId()))
				continue;
			
			String itemIcon = IconTable.getIcon(item.getItemId());
			html.append("<tr>");
			html.append("<td width=40 align=center><img src=\"").append(itemIcon).append("\" width=32 height=32></td>");
			html.append("<td width=110>").append(item.getItem().getName()).append("</td>");
			html.append("<td width=80>").append(item.getEnchantLevel() > 0 ? "+" + item.getEnchantLevel() : "").append("</td>");
			html.append("<td width=100>").append(StringUtil.formatNumber(item.getCount())).append("</td>");
			html.append("<td width=270><button value=\"Sell\" action=\"bypass _bbsmarket;sellitem;").append(item.getObjectId()).append("\" width=60 height=21 back=\"L2UI.DefaultButton_click\" fore=\"L2UI.DefaultButton\"></td>");
			html.append("</tr>");
			
			count++;
			if (count >= 20)
				break;
		}
		
		if (count == 0)
		{
			html.append("<tr><td colspan=\"4\" align=center>No items available to sell.</td></tr>");
		}
		
		html.append("</table>");
		html.append("</td></tr></table>");
		html.append("</body></html>");
		
		separateAndSend(html.toString(), player);
	}
	
	private void showSellPriceInput(Player player, int itemObjectId)
	{
		ItemInstance item = player.getInventory().getItemByObjectId(itemObjectId);
		if (item == null || (!MarketConfig.ALLOW_EQUIPPED_ITEMS && item.isEquipped()) || 
			(!MarketConfig.ALLOW_QUEST_ITEMS && item.isQuestItem()) || 
			isCurrencyItem(item.getItemId()))
		{
			player.sendMessage("Invalid item.");
			return;
		}
		
		StringBuilder html = new StringBuilder();
		html.append("<html><body><br><br>");
		html.append("<table width=610><tr><td width=10></td><td width=600 align=center>");
		html.append("<table width=600 bgcolor=000000><tr>");
		html.append("<td width=200>Item: ").append(item.getItem().getName()).append("</td>");
		html.append("<td width=200>Available: ").append(StringUtil.formatNumber(item.getCount())).append("</td>");
		html.append("</tr></table>");
		html.append("<table width=600><tr>");
		html.append("<td width=150>Price (").append(getCurrencyName()).append("):</td>");
		html.append("<td width=200><edit var=\"price\" width=180></td>");
		html.append("</tr></table>");
		html.append("<table width=600><tr>");
		html.append("<td width=150>Quantity:</td>");
		html.append("<td width=200><edit var=\"quantity\" width=180></td>");
		html.append("</tr></table>");
		html.append("<table width=600><tr>");
		html.append("<td width=300 align=center><button value=\"List Item\" action=\"Write Market createlisting ").append(itemObjectId).append(" quantity price\" width=100 height=21 back=\"L2UI.DefaultButton_click\" fore=\"L2UI.DefaultButton\"></td>");
		html.append("<td width=300 align=center><a action=\"bypass _bbsmarket;create\">Cancel</a></td>");
		html.append("</tr></table>");
		html.append("<font color=FF0000>Note: Items will be listed for ").append(MarketConfig.LISTING_DURATION_DAYS).append(" days. Commission: ").append((int)(MarketConfig.COMMISSION_RATE * 100)).append("%</font>");
		html.append("</td></tr></table>");
		html.append("</body></html>");
		
		separateAndSend(html.toString(), player);
	}
	
	private void showMyAuction(Player player)
	{
		List<MarketListing> listings = getPlayerListings(player.getObjectId());
		
		StringBuilder html = new StringBuilder();
		html.append("<html><body><br><br>");
		html.append("<table width=610><tr><td width=10></td><td width=600 align=center>");
		html.append("<table width=600 bgcolor=000000><tr>");
		html.append("<td width=80><button value=\"Auction List\" action=\"bypass _bbsmarket;list;0;;NONE;ALL\" width=100 height=21 back=\"L2UI.DefaultButton\" fore=\"L2UI.DefaultButton\"></td>");
		html.append("<td width=80><button value=\"Create Auction\" action=\"bypass _bbsmarket;create\" width=100 height=21 back=\"L2UI.DefaultButton\" fore=\"L2UI.DefaultButton\"></td>");
		html.append("<td width=80><button value=\"My Auction\" action=\"bypass _bbsmarket;my\" width=100 height=21 back=\"L2UI.DefaultButton_click\" fore=\"L2UI.DefaultButton\"></td>");
		html.append("</tr></table>");
		html.append("<table width=600 bgcolor=000000><tr>");
		html.append("<td width=40><font color=LEVEL>Icon</font></td>");
		html.append("<td width=110><font color=LEVEL>Item</font></td>");
		html.append("<td width=80><font color=LEVEL>Enchant</font></td>");
		html.append("<td width=80><font color=LEVEL>Quantity</font></td>");
		html.append("<td width=120><font color=LEVEL>Price</font></td>");
		html.append("<td width=100><font color=LEVEL>Expires</font></td>");
		html.append("<td width=70><font color=LEVEL>Action</font></td>");
		html.append("</tr></table>");
		html.append("<table width=600>");
		
		if (listings.isEmpty())
		{
			html.append("<tr><td colspan=\"7\" align=center>You have no active listings.</td></tr>");
		}
		else
		{
			for (MarketListing listing : listings)
			{
				long expiresIn = listing.getExpiresAt() - System.currentTimeMillis();
				String expiresStr = expiresIn > 0 ? (expiresIn / (24 * 60 * 60 * 1000)) + " days" : "Expired";
				String itemIcon = IconTable.getIcon(listing.getItemId());
				
				html.append("<tr>");
				html.append("<td width=40 align=center><img src=\"").append(itemIcon).append("\" width=32 height=32></td>");
				html.append("<td width=110>").append(listing.getItemName()).append("</td>");
				html.append("<td width=80>").append(listing.getEnchant() > 0 ? "+" + listing.getEnchant() : "").append("</td>");
				html.append("<td width=80>").append(StringUtil.formatNumber(listing.getItemCount())).append("</td>");
				html.append("<td width=120>").append(StringUtil.formatNumber(listing.getPrice())).append(" ").append(getCurrencyName()).append("</td>");
				html.append("<td width=100>").append(expiresStr).append("</td>");
				html.append("<td width=70><button value=\"Cancel\" action=\"bypass _bbsmarket;cancel;").append(listing.getListingId()).append("\" width=60 height=21 back=\"L2UI.DefaultButton_click\" fore=\"L2UI.DefaultButton\"></td>");
				html.append("</tr>");
			}
		}
		
		html.append("</table>");
		html.append("</td></tr></table>");
		html.append("</body></html>");
		
		separateAndSend(html.toString(), player);
	}
	
	// Reuse methods from Market.java (you can refactor these to a shared utility class)
	private void createListing(Player player, int itemObjectId, long price, long count)
	{
		ItemInstance item = player.getInventory().getItemByObjectId(itemObjectId);
		if (item == null || (!MarketConfig.ALLOW_EQUIPPED_ITEMS && item.isEquipped()) || 
			(!MarketConfig.ALLOW_QUEST_ITEMS && item.isQuestItem()) || 
			isCurrencyItem(item.getItemId()))
		{
			player.sendMessage("Invalid item.");
			return;
		}
		
		if (count <= 0 || count > item.getCount())
		{
			player.sendMessage("Invalid quantity.");
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
		
		if (MarketConfig.MAX_LISTINGS_PER_PLAYER > 0 && getPlayerActiveListingsCount(player.getObjectId()) >= MarketConfig.MAX_LISTINGS_PER_PLAYER)
		{
			player.sendMessage("You can only have " + MarketConfig.MAX_LISTINGS_PER_PLAYER + " active listings at a time.");
			return;
		}
		
		if (!MarketConfig.ALLOW_ENCHANTED_ITEMS && item.getEnchantLevel() > 0)
		{
			player.sendMessage("You cannot sell enchanted items.");
			return;
		}
		
		int itemId = item.getItemId();
		String itemName = item.getItem().getName();
		int enchantLevel = item.getEnchantLevel();
		
		try (Connection con = ConnectionPool.getConnection())
		{
			String insertSql = "INSERT INTO market_listings (seller_id, seller_name, item_object_id, item_id, item_name, item_count, item_enchant, price, created_at, expires_at, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)";
			try (PreparedStatement ps = con.prepareStatement(insertSql))
			{
				long currentTime = System.currentTimeMillis();
				long expiresAt = currentTime + MarketConfig.getListingDuration();
				
				ps.setInt(1, player.getObjectId());
				ps.setString(2, player.getName());
				ps.setInt(3, itemObjectId);
				ps.setInt(4, itemId);
				ps.setString(5, itemName);
				ps.setLong(6, count);
				ps.setInt(7, enchantLevel);
				ps.setLong(8, price);
				ps.setLong(9, currentTime);
				ps.setLong(10, expiresAt);
				
				ps.executeUpdate();
			}
			
			if (count >= item.getCount())
			{
				player.destroyItem(item, false);
			}
			else
			{
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
			
			showMyAuction(player);
		}
		catch (SQLException e)
		{
			player.sendMessage("Error creating listing: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	private void showBuyConfirmation(Player player, int listingId)
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
		
		StringBuilder html = new StringBuilder();
		html.append("<html><body><br><br>");
		html.append("<table width=610><tr><td width=10></td><td width=600 align=center>");
		html.append("<table width=600 bgcolor=000000><tr>");
		html.append("<td width=200 align=center><font color=LEVEL>Confirm Purchase</font></td>");
		html.append("</tr></table>");
		html.append("<table width=600><tr>");
		html.append("<td width=150><font color=B09878>Item:</font></td>");
		html.append("<td width=450>").append(listing.getItemName()).append(listing.getEnchant() > 0 ? " +" + listing.getEnchant() : "").append("</td>");
		html.append("</tr></table>");
		html.append("<table width=600><tr>");
		html.append("<td width=150><font color=B09878>Quantity:</font></td>");
		html.append("<td width=450>").append(StringUtil.formatNumber(listing.getItemCount())).append("</td>");
		html.append("</tr></table>");
		html.append("<table width=600><tr>");
		html.append("<td width=150><font color=B09878>Price:</font></td>");
		html.append("<td width=450><font color=FF9900>").append(StringUtil.formatNumber(listing.getPrice())).append(" ").append(getCurrencyName()).append("</font></td>");
		html.append("</tr></table>");
		html.append("<table width=600><tr>");
		html.append("<td width=150><font color=B09878>Seller:</font></td>");
		html.append("<td width=450>").append(listing.getSellerName()).append("</td>");
		html.append("</tr></table>");
		html.append("<table width=600><tr>");
		html.append("<td width=300 align=center><button value=\"Confirm Purchase\" action=\"bypass _bbsmarket;buyconfirm;").append(listingId).append("\" width=120 height=21 back=\"L2UI.DefaultButton_click\" fore=\"L2UI.DefaultButton\"></td>");
		html.append("<td width=300 align=center><a action=\"bypass _bbsmarket;list;0;;NONE;ALL\">Cancel</a></td>");
		html.append("</tr></table>");
		html.append("</td></tr></table>");
		html.append("</body></html>");
		
		separateAndSend(html.toString(), player);
	}
	
	private void buyItem(Player player, int listingId)
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
		
		try (Connection con = ConnectionPool.getConnection())
		{
			con.setAutoCommit(false);
			
			try
			{
				if (!reduceCurrency(player, listing.getPrice()))
				{
					player.sendMessage("You don't have enough " + getCurrencyName() + ".");
					con.rollback();
					return;
				}
				
				long commission = (long)(listing.getPrice() * MarketConfig.COMMISSION_RATE);
				long sellerReceives = listing.getPrice() - commission;
				
				Player seller = World.getInstance().getPlayer(listing.getSellerId());
				if (seller != null && seller.isOnline())
				{
					addCurrency(seller, sellerReceives);
					seller.sendMessage("Your item " + listing.getItemName() + " was sold for " + StringUtil.formatNumber(sellerReceives) + " " + getCurrencyName() + " (commission: " + StringUtil.formatNumber(commission) + ").");
				}
				
				ItemInstance newItem = player.addItem(listing.getItemId(), (int)listing.getItemCount(), false);
				if (newItem != null && listing.getEnchant() > 0)
				{
					newItem.setEnchantLevel(listing.getEnchant(), player);
				}
				
				String updateSql = "UPDATE market_listings SET status = 0 WHERE listing_id = ?";
				try (PreparedStatement ps = con.prepareStatement(updateSql))
				{
					ps.setInt(1, listingId);
					ps.executeUpdate();
				}
				
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
				showAuctionList(player, 0, "", "NONE", "ALL");
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
	
	private void cancelListing(Player player, int listingId)
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
			ItemInstance item = player.addItem(listing.getItemId(), (int)listing.getItemCount(), false);
			if (item != null && listing.getEnchant() > 0)
			{
				item.setEnchantLevel(listing.getEnchant(), player);
			}
			
			String updateSql = "UPDATE market_listings SET status = 0 WHERE listing_id = ?";
			try (PreparedStatement ps = con.prepareStatement(updateSql))
			{
				ps.setInt(1, listingId);
				ps.executeUpdate();
			}
			
			player.sendMessage("Listing cancelled. Item returned to your inventory.");
			showMyAuction(player);
		}
		catch (SQLException e)
		{
			player.sendMessage("Error cancelling listing: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	// Database helper methods
	private List<MarketListing> getActiveListings(int page, int pageSize, String itemNameFilter, String gradeFilter, String categoryFilter)
	{
		List<MarketListing> listings = new ArrayList<>();
		
		try (Connection con = ConnectionPool.getConnection())
		{
			StringBuilder sql = new StringBuilder("SELECT * FROM market_listings WHERE status = 1 AND expires_at > ?");
			if (itemNameFilter != null && !itemNameFilter.isEmpty())
			{
				sql.append(" AND item_name LIKE ?");
			}
			sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
			
			try (PreparedStatement ps = con.prepareStatement(sql.toString()))
			{
				int paramIndex = 1;
				ps.setLong(paramIndex++, System.currentTimeMillis());
				if (itemNameFilter != null && !itemNameFilter.isEmpty())
				{
					ps.setString(paramIndex++, "%" + itemNameFilter + "%");
				}
				ps.setInt(paramIndex++, pageSize);
				ps.setInt(paramIndex++, page * pageSize);
				
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
	
	private int getActiveListingsCount(int page, String itemNameFilter, String gradeFilter, String categoryFilter)
	{
		try (Connection con = ConnectionPool.getConnection())
		{
			StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM market_listings WHERE status = 1 AND expires_at > ?");
			if (itemNameFilter != null && !itemNameFilter.isEmpty())
			{
				sql.append(" AND item_name LIKE ?");
			}
			
			try (PreparedStatement ps = con.prepareStatement(sql.toString()))
			{
				int paramIndex = 1;
				ps.setLong(paramIndex++, System.currentTimeMillis());
				if (itemNameFilter != null && !itemNameFilter.isEmpty())
				{
					ps.setString(paramIndex++, "%" + itemNameFilter + "%");
				}
				
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
	
	private MarketListing getListing(int listingId)
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
	
	private List<MarketListing> getPlayerListings(int playerId)
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
	
	private int getPlayerActiveListingsCount(int playerId)
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
	
	private void expireListing(int listingId)
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
	
	public static MarketBBSManager getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private static class SingletonHolder
	{
		protected static final MarketBBSManager INSTANCE = new MarketBBSManager();
	}
}

