package net.sf.l2j.gameserver.network.clientpackets;

import java.util.StringTokenizer;
import java.util.logging.Logger;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.communitybbs.CommunityBoard;
import net.sf.l2j.gameserver.data.manager.HeroManager;
import net.sf.l2j.gameserver.data.xml.AdminData;
import net.sf.l2j.gameserver.enums.FloodProtector;
import net.sf.l2j.gameserver.enums.actors.ClassId;
import net.sf.l2j.gameserver.handler.AdminCommandHandler;
import net.sf.l2j.gameserver.handler.IAdminCommandHandler;
import net.sf.l2j.gameserver.handler.IVoicedCommandHandler;
import net.sf.l2j.gameserver.handler.VoicedCommandHandler;
import net.sf.l2j.gameserver.handler.itemhandlers.ActiveItemSpecial;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.WorldObject;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.instance.OlympiadManagerNpc;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance;
import net.sf.l2j.gameserver.model.olympiad.OlympiadManager;
import net.sf.l2j.gameserver.data.xml.ItemData;
import net.sf.l2j.gameserver.enums.items.WeaponType;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.ActionFailed;
import net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;
import net.sf.l2j.gameserver.network.serverpackets.PlaySound;
import net.sf.l2j.commons.pool.ThreadPool;
import net.sf.l2j.gameserver.data.xml.DressMeData;
import net.sf.l2j.gameserver.data.xml.IconTable;
import net.sf.l2j.gameserver.handler.skin.CustomBypassHandler;
import net.sf.l2j.gameserver.model.skin.SkinPackage;
import net.sf.l2j.gameserver.model.item.kind.Item;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.sf.l2j.gameserver.util.sellBuffEngine.BuffShopBypassHandler;

public final class RequestBypassToServer extends L2GameClientPacket
{
	private static final Logger GMAUDIT_LOG = Logger.getLogger("gmaudit");
	private String _command;
	
	public static void showDressMeMainPage(Player player)
	{
		NpcHtmlMessage htm = new NpcHtmlMessage(1);
		htm.setFile("data/html/mods/dressme/index.htm");
		htm.replace("%time%", sdf.format(new Date(System.currentTimeMillis())));
		htm.replace("%dat%", (new SimpleDateFormat("dd/MM/yyyy")).format(new Date(System.currentTimeMillis())));
		
		player.sendPacket(htm);
	}
	
	static SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
	
	@Override
	protected void readImpl()
	{
		_command = readS();
	}
	
	// Adicione este metodo dentro da classe RequestBypassToServer
	private boolean isBuffShopManagerCommand(String fullCommand)
	{
		if (fullCommand == null || fullCommand.isEmpty())
		{
			return false;
		}
		
		// Remove "bypass -h " or "bypass " prefix if present
		String command = fullCommand;
		if (command.startsWith("bypass -h "))
			command = command.substring(10);
		else if (command.startsWith("bypass "))
			command = command.substring(7);
		
		String commandPart = command;
		int spaceIdx = command.indexOf(" ");
		if (spaceIdx != -1)
		{
			commandPart = command.substring(0, spaceIdx);
		}
		
		switch (commandPart)
		{
			case "index":
			case "setprice":
			case "settitle":
			case "setbuffs":
			case "close":
			case "setshop":
			case "list":
			case "add":
			case "del":
			case "startshop":
			case "stopshop":
			case "showShop":
			case "cast":
			case "remove_buff":
			case "remove_buffatt":
			case "manage_my_buffs":
			case "cast_confirm":
			case "shopskill":
			case "buy_skill":
			case "show_skill_shop":
				return true;
			default:
				return false;
		}
	}
	
	@Override
	protected void runImpl()
	{
		if (_command == null || _command.isEmpty())
			return;
		
		final Player player = getClient().getPlayer();
		if (player == null)
			return;
		
		if (!getClient().performAction(FloodProtector.SERVER_BYPASS))
			return;
		
		try
		{
			// Support for CTF commands without admin_ prefix
			if (_command.startsWith("ctf") && player.isGM())
			{
				_command = "admin_" + _command;
				handleAdminCommand(player);
			}
			// Support for Tournament command without admin_ prefix
			else if (_command.equals("tour") && player.isGM())
			{
				_command = "admin_tour";
				handleAdminCommand(player);
			}
			else if (_command.startsWith("admin_"))
				handleAdminCommand(player);
			else if (_command.startsWith("player_help "))
				handlePlayerHelp(player);
			else if (_command.startsWith("manor_menu_select?"))
				handleManor(player);
			// Handle autofarm commands BEFORE bbs commands to avoid conflicts
			// Process all "farm" commands (from client interface button)
			else if (_command.startsWith("farm "))
			{
				final Base.AutoFarm.AutofarmPlayerRoutine bot = player.getBot();
				
				if (bot == null)
				{
					player.sendMessage("Erro: Bot nao inicializado. Por favor, relogue.");
					return;
				}
				
				String[] parts = _command.split(" ");
				if (parts.length < 2)
				{
					net.sf.l2j.gameserver.handler.voicedcommandhandlers.AutoFarm.showAutoFarm(player);
					return;
				}
				
				String action = parts[1];
				
				// Handle farm on/off
				if (action.equals("on"))
				{
					if (!player.isAutoFarm())
					{
						// Set autofarm flag FIRST to block any dialogs immediately
						player.setAutoFarm(true);
						
						// Clear any pending teleport requests BEFORE starting autofarm
						try
						{
							player.teleportRequest(null, null);
						}
						catch (Exception e)
						{
							// Ignore
						}
						
						// Clear summon request fields directly using reflection BEFORE starting bot
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
							// Ignore
						}
						
						boolean started = bot.start();
						if (started && bot.running())
						{
							player.saveAutoFarmSettings();
							// bot.start() already sends the message
							// Don't show interface automatically to avoid popup
							return;
						}
						else
						{
							player.setAutoFarm(false);
							player.sendMessage("Erro ao iniciar AutoFarm.");
							return; // Don't show interface on error either
						}
					}
					else
					{
						// Already running
						return;
					}
				}
				else if (action.equals("off"))
				{
					if (player.isAutoFarm())
					{
						bot.stop();
						player.setAutoFarm(false);
						player.saveAutoFarmSettings();
						player.sendMessage("AutoFarm desativado.");
						// Don't show interface automatically to avoid popup
						return;
					}
					else
					{
						// Already stopped, don't show interface
						return;
					}
				}
				// Handle target range commands (Long/Short) - MUST be before inc_radius/dec_radius
				else if (action.equals("long") || action.equals("range_long") || action.equals("target_range_long") || 
				         _command.equals("farm long") || _command.equals("farm range_long") || _command.equals("farm target_range_long"))
				{
					player.setRadius(1200);
					player.saveAutoFarmSettings();
					// Update radius circle visual if autofarm is running
					if (bot.running())
						bot.updateRadiusCircle();
					player.sendPacket(new net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage("Target Range: Long (1200)", 3 * 1000, net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage.SMPOS.TOP_CENTER, false));
					// Removed showAutoFarm() to prevent automatic HTML popup
					return;
				}
				else if (action.equals("short") || action.equals("range_short") || action.equals("target_range_short") ||
				         _command.equals("farm short") || _command.equals("farm range_short") || _command.equals("farm target_range_short"))
				{
					player.setRadius(600);
					player.saveAutoFarmSettings();
					// Update radius circle visual if autofarm is running
					if (bot.running())
						bot.updateRadiusCircle();
					player.sendPacket(new net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage("Target Range: Short (600)", 3 * 1000, net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage.SMPOS.TOP_CENTER, false));
					// Removed showAutoFarm() to prevent automatic HTML popup
					return;
				}
				// Handle radius commands
				// Check if we should set fixed values based on current range
				else if (action.equals("inc_radius"))
				{
					int currentRadius = player.getRadius();
					// If range is close to short (600), set to long (1200) when increasing
					// Also check if already at long to prevent further increase
					if (currentRadius <= 700)
					{
						player.setRadius(1200);
						player.saveAutoFarmSettings();
						// Update radius circle visual if autofarm is running
						if (bot.running())
							bot.updateRadiusCircle();
						player.sendPacket(new net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage("Target Range: Long (1200)", 3 * 1000, net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage.SMPOS.TOP_CENTER, false));
						// Removed showAutoFarm() to prevent automatic HTML popup
						return; // Stop processing to prevent further changes
					}
					else if (currentRadius >= 1200)
					{
						// Already at maximum long range, don't increase further
						player.sendPacket(new net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage("Target Range: Long (1200) - Already at maximum", 2 * 1000, net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage.SMPOS.TOP_CENTER, false));
						return; // Stop processing
					}
					else
					{
						// Normal increment
						player.setRadius(currentRadius + 200);
						player.saveAutoFarmSettings();
						// Update radius circle visual if autofarm is running
						if (bot.running())
							bot.updateRadiusCircle();
						player.sendPacket(new net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage("Auto Farm Range: " + player.getRadius(), 3 * 1000, net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage.SMPOS.TOP_CENTER, false));
					}
				}
				else if (action.equals("dec_radius"))
				{
					int currentRadius = player.getRadius();
					// If range is close to long (1200), set to short (600) when decreasing
					// Also check if already at short to prevent further decrease
					if (currentRadius >= 1100)
					{
						player.setRadius(600);
						player.saveAutoFarmSettings();
						// Update radius circle visual if autofarm is running
						if (bot.running())
							bot.updateRadiusCircle();
						player.sendPacket(new net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage("Target Range: Short (600)", 3 * 1000, net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage.SMPOS.TOP_CENTER, false));
						// Removed showAutoFarm() to prevent automatic HTML popup
						return; // Stop processing to prevent further changes
					}
					else if (currentRadius <= 600)
					{
						// Already at minimum short range, don't decrease further
						player.sendPacket(new net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage("Target Range: Short (600) - Already at minimum", 2 * 1000, net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage.SMPOS.TOP_CENTER, false));
						return; // Stop processing
					}
					else
					{
						// Normal decrement
						player.setRadius(currentRadius - 200);
						player.saveAutoFarmSettings();
						// Update radius circle visual if autofarm is running
						if (bot.running())
							bot.updateRadiusCircle();
						player.sendPacket(new net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage("Auto Farm Range: " + player.getRadius(), 3 * 1000, net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage.SMPOS.TOP_CENTER, false));
					}
				}
				// Handle page commands
				else if (action.equals("inc_page"))
				{
					int newPage = player.getPage() + 1;
					if (newPage <= 9)
					{
						player.setPage(newPage);
						player.saveAutoFarmSettings();
						String[] pageStrings = {"F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10"};
						player.sendPacket(new net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage("Auto Farm Skill Bar " + pageStrings[newPage], 3 * 1000, net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage.SMPOS.TOP_CENTER, false));
					}
				}
				else if (action.equals("dec_page"))
				{
					int newPage = player.getPage() - 1;
					if (newPage >= 0)
					{
						player.setPage(newPage);
						player.saveAutoFarmSettings();
						String[] pageStrings = {"F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10"};
						player.sendPacket(new net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage("Auto Farm Skill Bar " + pageStrings[newPage], 3 * 1000, net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage.SMPOS.TOP_CENTER, false));
					}
				}
				// Handle heal commands
				else if (action.equals("inc_heal"))
				{
					player.setHealPercent(player.getHealPercent() + 10);
					player.saveAutoFarmSettings();
				}
				else if (action.equals("dec_heal"))
				{
					player.setHealPercent(player.getHealPercent() - 10);
					player.saveAutoFarmSettings();
				}
				// Handle HP potion commands
				else if (action.equals("inc_hp_pot"))
				{
					player.setHpPotionPercentage(player.getHpPotionPercentage() + 5);
					player.saveAutoFarmSettings();
				}
				else if (action.equals("dec_hp_pot"))
				{
					player.setHpPotionPercentage(player.getHpPotionPercentage() - 5);
					player.saveAutoFarmSettings();
				}
				// Handle MP potion commands
				else if (action.equals("inc_mp_pot"))
				{
					player.setMpPotionPercentage(player.getMpPotionPercentage() + 5);
					player.saveAutoFarmSettings();
				}
				else if (action.equals("dec_mp_pot"))
				{
					player.setMpPotionPercentage(player.getMpPotionPercentage() - 5);
					player.saveAutoFarmSettings();
				}
				// Handle summon skill commands
				else if (action.equals("inc_summonSkill"))
				{
					player.setSummonSkillPercent(player.getSummonSkillPercent() + 10);
					player.saveAutoFarmSettings();
				}
				else if (action.equals("dec_summonSkill"))
				{
					player.setSummonSkillPercent(player.getSummonSkillPercent() - 10);
					player.saveAutoFarmSettings();
				}
				// Handle toggle commands
				else if (action.equals("enableBuffProtect") || action.equals("toggleBuffProtect"))
				{
					player.setNoBuffProtection(!player.isNoBuffProtected());
					player.saveAutoFarmSettings();
					if (player.isNoBuffProtected())
					{
						player.sendPacket(new net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage("Auto Farm Buff Protect On", 3 * 1000, net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage.SMPOS.TOP_CENTER, false));
					}
					else
					{
						player.sendPacket(new net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage("Auto Farm Buff Protect Off", 3 * 1000, net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage.SMPOS.TOP_CENTER, false));
					}
				}
				else if (action.equals("enableAntiKs") || action.equals("enableRespectHunt") || action.equals("toggleAntiKs"))
				{
					player.setAntiKsProtection(!player.isAntiKsProtected());
					player.saveAutoFarmSettings();
					if (player.isAntiKsProtected())
					{
						player.sendPacket(new net.sf.l2j.gameserver.network.serverpackets.SystemMessage(net.sf.l2j.gameserver.network.SystemMessageId.ACTIVATE_RESPECT_HUNT));
						player.sendPacket(new net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage("Respect Hunt On", 3 * 1000, net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage.SMPOS.TOP_CENTER, false));
					}
					else
					{
						player.sendPacket(new net.sf.l2j.gameserver.network.serverpackets.SystemMessage(net.sf.l2j.gameserver.network.SystemMessageId.DESACTIVATE_RESPECT_HUNT));
						player.sendPacket(new net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage("Respect Hunt Off", 3 * 1000, net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage.SMPOS.TOP_CENTER, false));
					}
				}
				else if (action.equals("enableSummonAttack") || action.equals("toggleSummonAttack"))
				{
					player.setSummonAttack(!player.isSummonAttack());
					player.saveAutoFarmSettings();
					if (player.isSummonAttack())
					{
						player.sendPacket(new net.sf.l2j.gameserver.network.serverpackets.SystemMessage(net.sf.l2j.gameserver.network.SystemMessageId.ACTIVATE_SUMMON_ACTACK));
						player.sendPacket(new net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage("Auto Farm Summon Attack On", 3 * 1000, net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage.SMPOS.TOP_CENTER, false));
					}
					else
					{
						player.sendPacket(new net.sf.l2j.gameserver.network.serverpackets.SystemMessage(net.sf.l2j.gameserver.network.SystemMessageId.DESACTIVATE_SUMMON_ACTACK));
						player.sendPacket(new net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage("Auto Farm Summon Attack Off", 3 * 1000, net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage.SMPOS.TOP_CENTER, false));
					}
				}
				// Handle viewer command (toggle radius circle visibility)
				else if (action.equals("viwer") || action.equals("viewer") || action.equals("toggleViewer"))
				{
					// Toggle radius circle visibility
					if (bot.running())
					{
						// If autofarm is running, toggle the circle
						if (bot.isRadiusCircleVisible())
						{
							bot.hideRadiusCircle();
							player.sendPacket(new net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage("Radius Circle Hidden", 3 * 1000, net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage.SMPOS.TOP_CENTER, false));
						}
						else
						{
							bot.showRadiusCircle();
							player.sendPacket(new net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage("Radius Circle Visible", 3 * 1000, net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage.SMPOS.TOP_CENTER, false));
						}
					}
					else
					{
						player.sendMessage("AutoFarm must be running to toggle radius circle visibility.");
					}
				}
				// Handle assist command (probably opens settings)
				else if (action.equals("assist") || action.equals("settings") || action.equals("info"))
				{
					// Removed showAutoFarm() to prevent automatic HTML popup
					// net.sf.l2j.gameserver.handler.voicedcommandhandlers.AutoFarm.showAutoFarm(player);
					return;
				}
				
				// Don't show interface automatically for other commands to avoid popup
				// net.sf.l2j.gameserver.handler.voicedcommandhandlers.AutoFarm.showAutoFarm(player);
				return;
			}
			// Process _enableAutoFarm directly (from client interface button) - like old system
			else if (_command.equals("_enableAutoFarm") || _command.startsWith("_enableAutoFarm"))
			{
				final Base.AutoFarm.AutofarmPlayerRoutine bot = player.getBot();
				
				if (bot == null)
				{
					player.sendMessage("Erro: Bot nao inicializado. Por favor, relogue.");
					return;
				}
				
				if (player.isAutoFarm())
				{
					bot.stop();
					player.setAutoFarm(false);
					player.saveAutoFarmSettings();
				}
				else
				{
					bot.start();
					player.setAutoFarm(true);
					player.saveAutoFarmSettings();
				}
				
				net.sf.l2j.gameserver.handler.voicedcommandhandlers.AutoFarm.showAutoFarm(player);
				return;
			}
			// Process _autofarm directly (from HTML button) - like old system
			else if (_command.startsWith("_autofarm"))
			{
				final Base.AutoFarm.AutofarmPlayerRoutine bot = player.getBot();
				
				if (bot == null)
				{
					player.sendMessage("Erro: Bot nao inicializado. Por favor, relogue.");
					return;
				}
				
				if (player.isAutoFarm())
				{
					bot.stop();
					player.setAutoFarm(false);
					player.saveAutoFarmSettings();
				}
				else
				{
					bot.start();
					player.setAutoFarm(true);
					player.saveAutoFarmSettings();
				}
				
				net.sf.l2j.gameserver.handler.voicedcommandhandlers.AutoFarm.showAutoFarm(player);
				return;
			}
			// Process other autofarm commands through voiced command handler
			else if (_command.startsWith("_radiusAutoFarm") || _command.startsWith("_pageAutoFarm") ||
			         _command.startsWith("_enableBuffProtect") || _command.startsWith("_healAutoFarm") ||
			         _command.startsWith("_hpAutoFarm") || _command.startsWith("_mpAutoFarm") ||
			         _command.startsWith("_enableAntiKs") || _command.startsWith("_enableRespectHunt") ||
			         _command.startsWith("_enableSummonAttack") || _command.startsWith("_summonSkillAutoFarm") ||
			         _command.startsWith("_ignoreMonster") || _command.startsWith("_activeMonster") ||
			         _command.startsWith("_infosettings"))
			{
				handleVoicedCommand(player);
				return; // Important: return after handling to avoid processing by other handlers
			}
			// Handle market commands
			else if (_command.startsWith("_market"))
				handleVoicedCommand(player);
			// Handle raidinfo commands
			else if (_command.startsWith("_raidinfo"))
				handleRaidInfoCommand(player);
			else if (_command.startsWith("bbs_") || _command.startsWith("_bbs") || _command.startsWith("_friend") || _command.startsWith("_mail") || _command.startsWith("_block"))
				CommunityBoard.getInstance().handleCommands(getClient(), _command);
			else if (_command.startsWith("Quest "))
				handleQuest(player);
			else if (_command.startsWith("_match") || _command.startsWith("_diary"))
				handleHero(player);
			else if (_command.startsWith("arenachange"))
				handleOlympiad(player);
			else if (_command.startsWith("name_change "))
			{
				String newName = _command.substring(12).trim();
				ActiveItemSpecial.processNameChange(player, newName, null);
			}
			else if (_command.startsWith("class_index_select"))
				handleClassChange(player);
			// Handle voiced_ commands from menu
			else if (_command.startsWith("voiced_"))
				handleVoicedMenuCommand(player);
			// Handle bp_ commands (bypass commands)
			else if (_command.startsWith("bp_"))
				handleBypassCommand(player);
			else if (_command.startsWith("dressme"))
			{
				if (!Config.ALLOW_DRESS_ME_IN_OLY && player.isInOlympiadMode())
				{
					player.sendMessage("DressMe can't be used on The Olympiad game.");
					return;
				}
				
				StringTokenizer st = new StringTokenizer(_command, " ");
				st.nextToken();
				if (!st.hasMoreTokens())
				{
					showDressMeMainPage(player);
					return;
				}
				int page = Integer.parseInt(st.nextToken());
				
				if (!st.hasMoreTokens())
				{
					showDressMeMainPage(player);
					return;
				}
				String next = st.nextToken();
				if (next.startsWith("skinlist"))
				{
					String type = st.nextToken();
					showSkinList(player, type, page);
				}
			else if (next.startsWith("myskinlist"))
			{
				String type = "all";
				if (st.hasMoreTokens())
				{
					String typeToken = st.nextToken();
					if (typeToken.equalsIgnoreCase("armor") || typeToken.equalsIgnoreCase("weapon") || typeToken.equalsIgnoreCase("shield"))
						type = typeToken.toLowerCase();
				}
				showMySkinList(player, page, type);
			}
				else if (next.equals("clean"))
				{
					String type = st.nextToken();
					// Reset skin options for the specified type
					switch (type.toLowerCase())
					{
						case "armor":
							player.setArmorSkinOption(0);
							break;
						case "weapon":
							player.setWeaponSkinOption(0);
							break;
						case "hair":
							player.setHairSkinOption(0);
							break;
						case "face":
							player.setFaceSkinOption(0);
							break;
						case "shield":
							player.setShieldSkinOption(0);
							break;
					}
					player.broadcastUserInfo();
					player.storeDressMeData();
					showMySkinList(player, page, "all");
				}
				else if (next.startsWith("buyskin"))
				{
					int skinId = Integer.parseInt(st.nextToken());
					String type = st.nextToken();
					int itemId = 0;
					if (st.hasMoreTokens())
						itemId = Integer.parseInt(st.nextToken());
					
					SkinPackage skinPackage = null;
					switch (type.toLowerCase())
					{
						case "armor":
							skinPackage = DressMeData.getInstance().getArmorSkinsPackage(skinId);
							if (skinPackage != null && player.hasArmorSkin(skinId))
							{
								player.sendMessage("You already own this skin.");
								player.sendPacket(new PlaySound("ItemSound3.sys_impossible"));
								showSkinList(player, type, page);
								return;
							}
							break;
						case "weapon":
							skinPackage = DressMeData.getInstance().getWeaponSkinsPackage(skinId);
							if (skinPackage != null && player.hasWeaponSkin(skinId))
							{
								player.sendMessage("You already own this skin.");
								player.sendPacket(new PlaySound("ItemSound3.sys_impossible"));
								showSkinList(player, type, page);
								return;
							}
							break;
						case "hair":
							skinPackage = DressMeData.getInstance().getHairSkinsPackage(skinId);
							if (skinPackage != null && player.hasHairSkin(skinId))
							{
								player.sendMessage("You already own this skin.");
								player.sendPacket(new PlaySound("ItemSound3.sys_impossible"));
								showSkinList(player, type, page);
								return;
							}
							break;
						case "face":
							skinPackage = DressMeData.getInstance().getFaceSkinsPackage(skinId);
							if (skinPackage != null && player.hasFaceSkin(skinId))
							{
								player.sendMessage("You already own this skin.");
								player.sendPacket(new PlaySound("ItemSound3.sys_impossible"));
								showSkinList(player, type, page);
								return;
							}
							break;
						case "shield":
							skinPackage = DressMeData.getInstance().getShieldSkinsPackage(skinId);
							if (skinPackage != null && player.hasShieldSkin(skinId))
							{
								player.sendMessage("You already own this skin.");
								player.sendPacket(new PlaySound("ItemSound3.sys_impossible"));
								showSkinList(player, type, page);
								return;
							}
							break;
					}
					
					if (skinPackage == null)
					{
						player.sendMessage("Skin not found.");
						showSkinList(player, type, page);
						return;
					}
					
					// Check if player has enough items/currency
					int priceId = skinPackage.getPriceId();
					int priceCount = skinPackage.getPriceCount();
					
					if (priceId > 0 && priceCount > 0)
					{
						long itemCount = player.getInventory().getItemCount(priceId);
						if (itemCount < priceCount)
						{
							player.sendMessage("You don't have enough " + getItemNameById(priceId) + ".");
							player.sendPacket(new ExShowScreenMessage("You don't have enough " + getItemNameById(priceId) + ".", 2000));
							player.sendPacket(new PlaySound("ItemSound3.sys_impossible"));
							showSkinList(player, type, page);
							return;
						}
						
						// Remove items - use reduceAdena for adena (ID 57), otherwise destroyItem
						boolean success = false;
						if (priceId == 57)
						{
							success = player.reduceAdena(priceCount, true);
						}
						else
						{
							ItemInstance item = player.getInventory().getItemByItemId(priceId);
							if (item != null)
								success = player.destroyItem(item, priceCount, true);
						}
						
						if (!success)
						{
							player.sendMessage("Failed to remove items.");
							showSkinList(player, type, page);
							return;
						}
					}
					
					// Add skin to player
					switch (type.toLowerCase())
					{
						case "armor":
							player.buyArmorSkin(skinId);
							// Auto-add corresponding hat if available
							final int hairSkinId = DressMeData.getInstance().getCorrespondingHairSkinId(skinId);
							if (hairSkinId > 0)
							{
								// buyHairSkin already checks if player has it, so safe to call
								final boolean wasNewHat = !player.hasHairSkin(hairSkinId);
								player.buyHairSkin(hairSkinId);
								
								// Only show message if it's a new hat
								if (wasNewHat)
								{
									final SkinPackage hairSkin = DressMeData.getInstance().getHairSkinsPackage(hairSkinId);
									if (hairSkin != null)
										player.sendMessage("You also received " + hairSkin.getName() + "!");
								}
							}
							break;
						case "weapon":
							player.buyWeaponSkin(skinId);
							break;
						case "hair":
							player.buyHairSkin(skinId);
							break;
						case "face":
							player.buyFaceSkin(skinId);
							break;
						case "shield":
							player.buyShieldSkin(skinId);
							break;
					}
					
					// Save dressme data immediately after purchase
					player.storeDressMeData();
					
					player.sendMessage("You have successfully purchased " + skinPackage.getName() + "!");
					player.sendPacket(new PlaySound("ItemSound3.sys_impossible"));
					showSkinList(player, type, page);
				}
				else if (next.startsWith("tryskin"))
				{
					int skinId = Integer.parseInt(st.nextToken());
					String type = st.nextToken();
					
					if (player.isTryingSkin())
					{
						player.sendMessage("You are already trying a skin.");
						player.sendPacket(new ExShowScreenMessage("You are already trying a skin.", 2000));
						player.sendPacket(new PlaySound("ItemSound3.sys_impossible"));
						showSkinList(player, type, page);
						return;
					}
					
					player.setIsTryingSkin(true);
					
					int oldArmorSkinId = player.getArmorSkinOption();
					int oldWeaponSkinId = player.getWeaponSkinOption();
					int oldHairSkinId = player.getHairSkinOption();
					int oldFaceSkinId = player.getFaceSkinOption();
					int oldShieldSkinId = player.getShieldSkinOption();
					
					// Track if hat was temporarily added for testing
					boolean hatTemporarilyAdded = false;
					int testedHairSkinId = 0;
					
					switch (type.toLowerCase())
					{
						case "armor":
							player.setArmorSkinOption(skinId);
							// Auto-apply corresponding hat if available (even if player doesn't own it yet)
							testedHairSkinId = DressMeData.getInstance().getCorrespondingHairSkinId(skinId);
							if (testedHairSkinId > 0)
							{
								// Temporarily add hat skin for testing if player doesn't have it
								if (!player.hasHairSkin(testedHairSkinId))
								{
									player.buyHairSkin(testedHairSkinId);
									hatTemporarilyAdded = true;
								}
								player.setHairSkinOption(testedHairSkinId);
							}
							else
							{
								// New armor skin doesn't have hat - remove current hat if it was from previous armor skin
								if (oldArmorSkinId > 0)
								{
									final int previousHairSkinId = DressMeData.getInstance().getCorrespondingHairSkinId(oldArmorSkinId);
									if (previousHairSkinId > 0 && oldHairSkinId == previousHairSkinId)
									{
										player.setHairSkinOption(0); // Remove hat from previous armor skin
									}
								}
							}
							break;
						case "weapon":
							player.setWeaponSkinOption(skinId);
							break;
						case "hair":
							player.setHairSkinOption(skinId);
							break;
						case "face":
							player.setFaceSkinOption(skinId);
							break;
						case "shield":
							player.setShieldSkinOption(skinId);
							break;
					}
					
					player.broadcastUserInfo();
					showSkinList(player, type, page);
					
					// Store values for the scheduled task
					final boolean finalHatTemporarilyAdded = hatTemporarilyAdded;
					final int finalTestedHairSkinId = testedHairSkinId;
					
					ThreadPool.schedule(() ->
					{
						switch (type.toLowerCase())
						{
							case "armor":
								// Remove temporarily added hat skin first
								if (finalHatTemporarilyAdded)
								{
									player.getHairSkins().removeIf(id -> id == finalTestedHairSkinId);
								}
								// Temporarily disable auto-hat application to restore exact old state
								// Restore armor skin first
								player.setArmorSkinOption(oldArmorSkinId);
								// Then restore the exact old hat state
								player.setHairSkinOption(oldHairSkinId);
								break;
							case "weapon":
								player.setWeaponSkinOption(oldWeaponSkinId);
								break;
							case "hair":
								player.setHairSkinOption(oldHairSkinId);
								break;
							case "face":
								player.setFaceSkinOption(oldFaceSkinId);
								break;
							case "shield":
								player.setShieldSkinOption(oldShieldSkinId);
								break;
						}
						
						player.broadcastUserInfo();
						player.setIsTryingSkin(false);
					}, 5000);
				}
				else if (next.startsWith("setskin"))
				{
					int id = Integer.parseInt(st.nextToken());
					String type = st.nextToken();
					
					boolean hasSkin = false;
					switch (type.toLowerCase())
					{
						case "armor":
							hasSkin = player.hasArmorSkin(id);
							if (hasSkin)
								player.setArmorSkinOption(id);
							break;
						case "weapon":
							hasSkin = player.hasWeaponSkin(id);
							if (hasSkin)
								player.setWeaponSkinOption(id);
							break;
						case "hair":
							hasSkin = player.hasHairSkin(id);
							if (hasSkin)
								player.setHairSkinOption(id);
							break;
						case "face":
							hasSkin = player.hasFaceSkin(id);
							if (hasSkin)
								player.setFaceSkinOption(id);
							break;
						case "shield":
							hasSkin = player.hasShieldSkin(id);
							if (hasSkin)
								player.setShieldSkinOption(id);
							break;
					}
					
					if (!hasSkin)
					{
						player.sendMessage("You don't own this skin.");
						player.sendPacket(new PlaySound("ItemSound3.sys_impossible"));
					}
					else
					{
						player.sendMessage("Skin applied successfully!");
						player.broadcastUserInfo();
						// Save dressme data immediately after applying skin
						player.storeDressMeData();
					}
					
					showMySkinList(player, page, "all");
				}
			}
			else if (_command.startsWith("npc_"))
				handleNpcBypass(player);
			// Handle custom bypass commands (like custom_dressme_back)
			else if (_command.startsWith("custom_"))
			{
				CustomBypassHandler.getInstance().handleBypass(player, _command);
			}
			// Handle BuffShop commands
			else if (isBuffShopManagerCommand(_command))
			{
				String bypass = _command;
				// Remove "bypass -h " prefix if present
				if (bypass.startsWith("bypass -h "))
					bypass = bypass.substring(10);
				else if (bypass.startsWith("bypass "))
					bypass = bypass.substring(7);
				BuffShopBypassHandler.getInstance().handleBypass(player, bypass);
			}
			// Generic handler for other voiced commands starting with _ (but not bbs, match, diary which are handled above)
			// BUT skip autofarm commands as they are handled above
			else if (_command.startsWith("_") && 
			         !_command.startsWith("_enableAutoFarm") && 
			         !_command.startsWith("_autofarm") &&
			         !_command.startsWith("_radiusAutoFarm") && 
			         !_command.startsWith("_pageAutoFarm") &&
			         !_command.startsWith("_enableBuffProtect") && 
			         !_command.startsWith("_healAutoFarm") &&
			         !_command.startsWith("_hpAutoFarm") && 
			         !_command.startsWith("_mpAutoFarm") &&
			         !_command.startsWith("_enableAntiKs") && 
			         !_command.startsWith("_enableRespectHunt") &&
			         !_command.startsWith("_enableSummonAttack") && 
			         !_command.startsWith("_summonSkillAutoFarm") &&
			         !_command.startsWith("_ignoreMonster") && 
			         !_command.startsWith("_activeMonster") &&
			         !_command.startsWith("_infosettings"))
				handleVoicedCommand(player);
		}
		catch (Exception e)
		{
			player.sendMessage("An error occurred processing your command.");
			e.printStackTrace();
		}
	}
	
	private void handleRaidInfoCommand(Player player)
	{
		if (_command.equals("_raidinfo"))
		{
			net.sf.l2j.gameserver.handler.voicedcommandhandlers.RaidInfo handler = 
				(net.sf.l2j.gameserver.handler.voicedcommandhandlers.RaidInfo) VoicedCommandHandler.getInstance().getHandler("raidinfo");
			if (handler != null)
			{
				handler.useVoicedCommand("raidinfo", player, null);
			}
		}
		else if (_command.startsWith("_raidinfo;"))
		{
			StringTokenizer st = new StringTokenizer(_command, ";");
			st.nextToken(); // Skip "_raidinfo"
			
			if (st.hasMoreTokens())
			{
				String action = st.nextToken();
				net.sf.l2j.gameserver.handler.voicedcommandhandlers.RaidInfo handler = 
					(net.sf.l2j.gameserver.handler.voicedcommandhandlers.RaidInfo) VoicedCommandHandler.getInstance().getHandler("raidinfo");
				
				if (handler != null)
				{
					if (action.equals("grandbosses"))
					{
						handler.showGrandBosses(player);
					}
					else if (action.equals("raidbosses"))
					{
						handler.showRaidBosses(player);
					}
					else if (action.equals("drops") && st.hasMoreTokens())
					{
						try
						{
							int npcId = Integer.parseInt(st.nextToken());
							net.sf.l2j.gameserver.handler.voicedcommandhandlers.RaidInfo.showBossDrops(player, npcId);
						}
						catch (NumberFormatException e)
						{
							player.sendMessage("Invalid boss ID.");
						}
					}
				}
			}
		}
	}
	
	private void handleVoicedMenuCommand(Player player)
	{
		net.sf.l2j.gameserver.handler.voicedcommandhandlers.VoicedMenu menuHandler = 
			(net.sf.l2j.gameserver.handler.voicedcommandhandlers.VoicedMenu) VoicedCommandHandler.getInstance().getHandler("menu");
		
		if (menuHandler == null)
			return;
		
		if (_command.equals("voiced_setPartyRefuse"))
		{
			menuHandler.setPartyRefuse(player);
		}
		else if (_command.equals("voiced_setTradeRefuse"))
		{
			menuHandler.setTradeRefuse(player);
		}
		else if (_command.equals("voiced_setbuffsRefuse"))
		{
			menuHandler.setBuffsRefuse(player);
		}
		else if (_command.equals("voiced_setMessageRefuse"))
		{
			menuHandler.setMessageRefuse(player);
		}
		else if (_command.equals("voiced_showRegisteHtml"))
		{
			menuHandler.showRegisteHtml(player);
		}
		else if (_command.equals("voiced_showInfoHtml"))
		{
			menuHandler.showInfoHtml(player);
		}
		else if (_command.equals("voiced_epic"))
		{
			menuHandler.showEpic(player);
		}
		else if (_command.equals("voiced_EventTime") || _command.equals("voiced_menu"))
		{
			if (_command.equals("voiced_EventTime"))
				menuHandler.showEventTime(player);
			else
				menuHandler.showMenu(player);
		}
		else if (_command.equals("voiced_dressme"))
		{
			showDressMeMainPage(player);
		}
		else if (_command.equals("voiced_autofarm"))
		{
			IVoicedCommandHandler handler = VoicedCommandHandler.getInstance().getHandler("autofarm");
			if (handler != null)
			{
				handler.useVoicedCommand("autofarm", player, null);
			}
		}
		else if (_command.equals("voiced_combine"))
		{
			menuHandler.showCombine(player);
		}
	}
	
	private void handleBypassCommand(Player player)
	{
		// Try to get handler from BypassHandler
		net.sf.l2j.gameserver.handler.BypassHandler bypassHandler = net.sf.l2j.gameserver.handler.BypassHandler.getInstance();
		
		// Try to find handler by matching command prefixes
		// The handlers are registered with full names like "bp_registerTournament1x1"
		net.sf.l2j.gameserver.handler.IBypassHandler handler = null;
		
		// List of known Tournament bypass commands to check
		String[] tournamentCommands = {
			"bp_checkTournamentPlayer",
			"bp_showTournamentPage",
			"bp_registerTournament1x1",
			"bp_removeTournamentParticipation",
			"bp_createTournamentTeam",
			"bp_registerTournament2x2",
			"bp_inviteTournamentMember",
			"bp_deleteTournamentTeam",
			"bp_tournamentTeamInfo",
			"bp_inviteTournamentPage",
			"bp_registerTournament3x3",
			"bp_registerTournament4x4",
			"bp_registerTournament5x5",
			"bp_registerTournament9x9",
			"bp_tournamentRanking",
			"bp_leaveTournamentTeam"
		};
		
		// Check if command matches any registered handler
		for (String cmd : tournamentCommands)
		{
			if (_command.startsWith(cmd))
			{
				handler = bypassHandler.getHandler(cmd);
				if (handler != null)
					break;
			}
		}
		
		if (handler != null)
		{
			String command = _command.substring(3); // Remove "bp_" prefix for handler
			handler.handleBypass(command, player);
			return;
		}
		
		// Fallback for specific commands
		if (_command.equals("bp_showDailyRewardsBoard"))
		{
			// TODO: Implement daily rewards board if needed
			player.sendMessage("Daily Reward Manager function not implemented yet.");
		}
	}
	
	private void handleVoicedCommand(Player player)
	{
		// Remove the underscore prefix for handler lookup
		String command = _command.substring(1);
		
		// Extract command and parameters
		StringTokenizer st = new StringTokenizer(command, " ");
		String cmd = st.hasMoreTokens() ? st.nextToken() : command;
		String params = st.hasMoreTokens() ? command.substring(cmd.length() + 1) : "";
		
		// Special handling for enableAutoFarm - try both with and without underscore
		if (cmd.equals("enableAutoFarm") || _command.startsWith("_enableAutoFarm"))
		{
			IVoicedCommandHandler handler = VoicedCommandHandler.getInstance().getHandler("enableAutoFarm");
			if (handler == null)
			{
				handler = VoicedCommandHandler.getInstance().getHandler("autofarm");
			}
			
			if (handler != null)
			{
				// Pass the full command with underscore prefix preserved for AutoFarm handler
				handler.useVoicedCommand(_command, player, params);
				return;
			}
		}
		
		// Get the handler for this command
		final IVoicedCommandHandler handler = VoicedCommandHandler.getInstance().getHandler(cmd);
		if (handler != null)
		{
			// Pass the full command with underscore prefix preserved for AutoFarm handler
			// This is important because AutoFarm handler checks for commands with underscore
			boolean result = handler.useVoicedCommand(_command, player, params);
			
			// If handler returned false, try without underscore as fallback
			if (!result)
			{
				String commandWithoutUnderscore = cmd + (params.isEmpty() ? "" : " " + params);
				handler.useVoicedCommand(commandWithoutUnderscore, player, params);
			}
		}
	}
	
	private void handleAdminCommand(Player player)
	{
		String command = _command.split(" ")[0];
		final IAdminCommandHandler ach = AdminCommandHandler.getInstance().getHandler(command);
		if (ach == null)
		{
			if (player.isGM())
				player.sendMessage("The command " + command.substring(6) + " doesn't exist.");
			return;
		}
		
		if (!AdminData.getInstance().hasAccess(command, player.getAccessLevel()))
		{
			player.sendMessage("You don't have the access rights to use this command.");
			return;
		}
		
		if (Config.GMAUDIT)
			GMAUDIT_LOG.info(player.getName() + " [" + player.getObjectId() + "] used '" + _command + "' command on: " + ((player.getTarget() != null) ? player.getTarget().getName() : "none"));
		
		ach.useAdminCommand(_command, player);
	}
	
	private void handlePlayerHelp(Player player)
	{
		final String path = _command.substring(12);
		if (path.contains(".."))
			return;
		
		final StringTokenizer st = new StringTokenizer(path);
		final String[] cmd = st.nextToken().split("#");
		
		final NpcHtmlMessage html = new NpcHtmlMessage(0);
		html.setFile("data/html/help/" + cmd[0]);
		html.disableValidation();
		player.sendPacket(html);
	}
	
	private void handleNpcBypass(Player player)
	{
		if (!player.validateBypass(_command))
			return;
		
		int endOfId = _command.indexOf('_', 5);
		String id = endOfId > 0 ? _command.substring(4, endOfId) : _command.substring(4);
		
		try
		{
			final WorldObject object = World.getInstance().getObject(Integer.parseInt(id));
			if (object instanceof Npc npc && endOfId > 0 && player.getAI().canDoInteract(npc))
				npc.onBypassFeedback(player, _command.substring(endOfId + 1));
			
			player.sendPacket(ActionFailed.STATIC_PACKET);
		}
		catch (NumberFormatException ignored)
		{
		}
	}
	
	private void handleManor(Player player)
	{
		WorldObject object = player.getTarget();
		if (object instanceof Npc targetNpc)
			targetNpc.onBypassFeedback(player, _command);
	}
	
	private void handleQuest(Player player)
	{
		if (!player.validateBypass(_command))
			return;
		
		String[] str = _command.substring(6).trim().split(" ", 2);
		if (str.length == 1)
			player.getQuestList().processQuestEvent(str[0], "");
		else
			player.getQuestList().processQuestEvent(str[0], str[1]);
	}
	
	private void handleHero(Player player)
	{
		String params = _command.substring(_command.indexOf("?") + 1);
		StringTokenizer st = new StringTokenizer(params, "&");
		int heroclass = Integer.parseInt(st.nextToken().split("=")[1]);
		int heropage = Integer.parseInt(st.nextToken().split("=")[1]);
		int heroid = HeroManager.getInstance().getHeroByClass(heroclass);
		
		if (_command.startsWith("_match") && heroid > 0)
			HeroManager.getInstance().showHeroFights(player, heroclass, heroid, heropage);
		else if (_command.startsWith("_diary") && heroid > 0)
			HeroManager.getInstance().showHeroDiary(player, heroclass, heroid, heropage);
	}
	
	private void handleOlympiad(Player player)
	{
		final boolean isManager = player.getCurrentFolk() instanceof OlympiadManagerNpc;
		
		if (!isManager && (!player.isInObserverMode() || player.isInOlympiadMode() || player.getOlympiadGameId() < 0))
			return;
		
		if (OlympiadManager.getInstance().isRegisteredInComp(player))
		{
			player.sendPacket(SystemMessageId.WHILE_YOU_ARE_ON_THE_WAITING_LIST_YOU_ARE_NOT_ALLOWED_TO_WATCH_THE_GAME);
			return;
		}
		
		final int arenaId = Integer.parseInt(_command.substring(12).trim());
		player.enterOlympiadObserverMode(arenaId);
	}
	
	private void handleClassChange(Player player)
	{
		String[] cmd = _command.split(" ");
		if (cmd.length < 2)
		{
			player.sendMessage("Invalid class selection.");
			return;
		}
		
		String classParam = cmd[1];
		ItemInstance item = player.getInventory().getItemByItemId(ActiveItemSpecial.CHANGE_CLASS);
		if (item == null)
		{
			player.sendMessage("You don't have the required item.");
			return;
		}
		
		try
		{
			int newClassId = Integer.parseInt(classParam);
			ActiveItemSpecial.applyClassChange(player, newClassId, item);
		}
		catch (NumberFormatException e)
		{
			int newClassId = getClassIdByName(classParam);
			if (newClassId == -1)
			{
				player.sendMessage("Invalid class name.");
				return;
			}
			ActiveItemSpecial.applyClassChange(player, newClassId, item);
		}
	}
	
	private int getClassIdByName(String className)
	{
		for (ClassId cid : ClassId.values())
		{
			if (cid.name().equalsIgnoreCase(className))
				return cid.getId();
		}
		return -1;
	}
	
	private static void showSkinList(Player player, String type, int page)
	{
		NpcHtmlMessage html = new NpcHtmlMessage(1);
		html.setFile("data/html/mods/dressme/allskins.htm");
		html.replace("%time%", sdf.format(new Date(System.currentTimeMillis())));
		html.replace("%dat%", (new SimpleDateFormat("dd/MM/yyyy")).format(new Date(System.currentTimeMillis())));
		
		final int ITEMS_PER_PAGE = 8;
		int myPage = 1;
		int i = 0;
		int shown = 0;
		boolean hasMore = false;
		int itemId = 0;
		
		final StringBuilder sb = new StringBuilder();
		
		List<SkinPackage> tempList = new ArrayList<>();
		switch (type.toLowerCase())
		{
			case "armor":
				tempList = new ArrayList<>(DressMeData.getInstance().getArmorSkinOptions().values());
				break;
			case "weapon":
				tempList = new ArrayList<>(DressMeData.getInstance().getWeaponSkinOptions().values());
				break;
			case "hair":
				tempList = new ArrayList<>(DressMeData.getInstance().getHairSkinOptions().values());
				break;
			case "face":
				tempList = new ArrayList<>(DressMeData.getInstance().getFaceSkinOptions().values());
				break;
			case "shield":
				tempList = new ArrayList<>(DressMeData.getInstance().getShieldSkinOptions().values());
				break;
		}
		
		if (tempList != null && !tempList.isEmpty())
		{
			for (SkinPackage sp : tempList)
			{
				if (sp == null)
					continue;
				
				if (shown == ITEMS_PER_PAGE)
				{
					hasMore = true;
					break;
				}
				
				if (myPage != page)
				{
					i++;
					if (i == ITEMS_PER_PAGE)
					{
						myPage++;
						i = 0;
					}
					continue;
				}
				
				if (shown == ITEMS_PER_PAGE)
				{
					hasMore = true;
					break;
				}
				
				switch (type.toLowerCase())
				{
					case "armor":
						itemId = sp.getChestId();
						break;
					case "weapon":
						itemId = sp.getWeaponId();
						break;
					case "hair":
						itemId = sp.getHairId();
						break;
					case "face":
						itemId = sp.getFaceId();
						break;
					case "shield":
						itemId = sp.getShieldId();
						break;
				}
				
				Item item = ItemData.getInstance().getTemplate(itemId);
				String itemName = (item != null) ? item.getName() : "Unknown";
				String itemIcon = IconTable.getIcon(itemId);
				
				sb.append("<table border=0 cellspacing=0 cellpadding=2 height=36><tr>");
				sb.append("<td width=32 align=center><button width=32 height=32 back=").append(itemIcon).append(" fore=").append(itemIcon).append("></td>");
				sb.append("<td width=124>").append(sp.getName()).append("<br1> <font color=999999>Price:</font> <font color=339966>").append(getItemNameById(sp.getPriceId())).append("</font> (<font color=LEVEL>").append(sp.getPriceCount()).append("</font>)</td>");
				sb.append("<td align=center width=65><button value=\"Buy\" action=\"bypass -h dressme ").append(page).append(" buyskin ").append(sp.getId()).append(" ").append(type).append(" ").append(itemId).append("\" width=65 height=19 back=L2UI_ch3.smallbutton2_over fore=L2UI_ch3.smallbutton2></td>");
				sb.append("<td align=center width=65><button value=\"Try\" action=\"bypass -h dressme ").append(page).append(" tryskin ").append(sp.getId()).append(" ").append(type).append("\" width=65 height=19 back=L2UI_ch3.smallbutton2_over fore=L2UI_ch3.smallbutton2></td>");
				sb.append("</tr></table>");
				
				shown++;
			}
		}
		
		if (shown == 0 && page == 1)
		{
			sb.append("<center>No skins available for this category.</center>");
		}
		
		sb.append("<table width=300><tr>");
		sb.append("<td align=center width=70>").append(page > 1 ? "<button value=\"< PREV\" action=\"bypass -h dressme " + (page - 1) + " skinlist " + type + "\" width=65 height=19 back=L2UI_ch3.smallbutton2_over fore=L2UI_ch3.smallbutton2>" : "").append("</td>");
		sb.append("<td align=center width=140>Page: ").append(page).append("</td>");
		sb.append("<td align=center width=70>").append(hasMore ? "<button value=\"NEXT >\" action=\"bypass -h dressme " + (page + 1) + " skinlist " + type + "\" width=65 height=19 back=L2UI_ch3.smallbutton2_over fore=L2UI_ch3.smallbutton2>" : "").append("</td>");
		sb.append("</tr></table>");
		
		html.replace("%showList%", sb.toString());
		player.sendPacket(html);
	}
	
	private static void showMySkinList(Player player, int page, String filterType)
	{
		NpcHtmlMessage html = new NpcHtmlMessage(1);
		html.setFile("data/html/mods/dressme/myskins.htm");
		html.replace("%time%", sdf.format(new Date(System.currentTimeMillis())));
		html.replace("%dat%", (new SimpleDateFormat("dd/MM/yyyy")).format(new Date(System.currentTimeMillis())));
		
		final int ITEMS_PER_PAGE = 8;
		int itemId = 0;
		int myPage = 1;
		int i = 0;
		int shown = 0;
		boolean hasMore = false;
		
		final StringBuilder sb = new StringBuilder();
		
		// Get all skins that player owns (excluding hair and face - they are auto-applied with armor)
		List<SkinPackage> armors = new ArrayList<>(DressMeData.getInstance().getArmorSkinOptions().values()).stream()
			.filter(s -> player.hasArmorSkin(s.getId())).collect(Collectors.toList());
		List<SkinPackage> weapons = new ArrayList<>(DressMeData.getInstance().getWeaponSkinOptions().values()).stream()
			.filter(s -> player.hasWeaponSkin(s.getId())).collect(Collectors.toList());
		List<SkinPackage> shields = new ArrayList<>(DressMeData.getInstance().getShieldSkinOptions().values()).stream()
			.filter(s -> player.hasShieldSkin(s.getId())).collect(Collectors.toList());
		
		// Filter by type
		List<SkinPackage> filteredList = new ArrayList<>();
		if (filterType.equalsIgnoreCase("armor"))
		{
			filteredList.addAll(armors);
		}
		else if (filterType.equalsIgnoreCase("weapon"))
		{
			filteredList.addAll(weapons);
		}
		else if (filterType.equalsIgnoreCase("shield"))
		{
			filteredList.addAll(shields);
		}
		else
		{
			// Show all (default)
			filteredList.addAll(armors);
			filteredList.addAll(weapons);
			filteredList.addAll(shields);
		}
		
		if (!filteredList.isEmpty())
		{
			for (SkinPackage sp : filteredList)
			{
				if (sp == null)
					continue;
				
				if (shown == ITEMS_PER_PAGE)
				{
					hasMore = true;
					break;
				}
				
				if (myPage != page)
				{
					i++;
					if (i == ITEMS_PER_PAGE)
					{
						myPage++;
						i = 0;
					}
					continue;
				}
				
				if (shown == ITEMS_PER_PAGE)
				{
					hasMore = true;
					break;
				}
				
				switch (sp.getType().toLowerCase())
				{
					case "armor":
						itemId = sp.getChestId();
						break;
					case "weapon":
						itemId = sp.getWeaponId();
						break;
					case "hair":
						itemId = sp.getHairId();
						break;
					case "face":
						itemId = sp.getFaceId();
						break;
					case "shield":
						itemId = sp.getShieldId();
						break;
				}
				
				String itemIcon = IconTable.getIcon(itemId);
				
				sb.append("<table border=0 cellspacing=0 cellpadding=2 height=36><tr>");
				sb.append("<td width=32 align=center><button width=32 height=32 back=").append(itemIcon).append(" fore=").append(itemIcon).append("></td>");
				sb.append("<td width=124>").append(sp.getName()).append("</td>");
				sb.append("<td align=center width=65><button value=\"Equip\" action=\"bypass -h dressme ").append(page).append(" setskin ").append(sp.getId()).append(" ").append(sp.getType()).append("\" width=65 height=19 back=L2UI_ch3.smallbutton2_over fore=L2UI_ch3.smallbutton2></td>");
				sb.append("<td align=center width=65><button value=\"Remove\" action=\"bypass -h dressme ").append(page).append(" clean ").append(sp.getType()).append("\" width=65 height=19 back=L2UI_ch3.smallbutton2_over fore=L2UI_ch3.smallbutton2></td>");
				sb.append("</tr></table>");
				sb.append("<img src=\"L2UI.Squaregray\" width=\"300\" height=\"1\">");
				
				shown++;
			}
		}
		
		if (shown == 0 && page == 1)
		{
			String message = "You don't own any skins yet.";
			if (!filterType.equalsIgnoreCase("all"))
			{
				message = "You don't own any " + filterType + " skins yet.";
			}
			sb.append("<center>").append(message).append("</center>");
		}
		
		// Filter buttons
		sb.append("<br><table width=300><tr>");
		sb.append("<td align=center width=100>");
		if (filterType.equalsIgnoreCase("all"))
			sb.append("<button value=\"All\" action=\"bypass -h dressme 1 myskinlist all\" width=90 height=19 back=L2UI_ch3.Btn1_normalOn fore=L2UI_ch3.Btn1_normal>");
		else
			sb.append("<button value=\"All\" action=\"bypass -h dressme 1 myskinlist all\" width=90 height=19 back=L2UI_ch3.Btn1_normal fore=L2UI_ch3.Btn1_normal>");
		sb.append("</td>");
		sb.append("<td align=center width=100>");
		if (filterType.equalsIgnoreCase("armor"))
			sb.append("<button value=\"Armor\" action=\"bypass -h dressme 1 myskinlist armor\" width=90 height=19 back=L2UI_ch3.Btn1_normalOn fore=L2UI_ch3.Btn1_normal>");
		else
			sb.append("<button value=\"Armor\" action=\"bypass -h dressme 1 myskinlist armor\" width=90 height=19 back=L2UI_ch3.Btn1_normal fore=L2UI_ch3.Btn1_normal>");
		sb.append("</td>");
		sb.append("<td align=center width=100>");
		if (filterType.equalsIgnoreCase("weapon"))
			sb.append("<button value=\"Weapon\" action=\"bypass -h dressme 1 myskinlist weapon\" width=90 height=19 back=L2UI_ch3.Btn1_normalOn fore=L2UI_ch3.Btn1_normal>");
		else
			sb.append("<button value=\"Weapon\" action=\"bypass -h dressme 1 myskinlist weapon\" width=90 height=19 back=L2UI_ch3.Btn1_normal fore=L2UI_ch3.Btn1_normal>");
		sb.append("</td>");
		sb.append("</tr><tr>");
		sb.append("<td align=center colspan=3>");
		if (filterType.equalsIgnoreCase("shield"))
			sb.append("<button value=\"Shield\" action=\"bypass -h dressme 1 myskinlist shield\" width=90 height=19 back=L2UI_ch3.Btn1_normalOn fore=L2UI_ch3.Btn1_normal>");
		else
			sb.append("<button value=\"Shield\" action=\"bypass -h dressme 1 myskinlist shield\" width=90 height=19 back=L2UI_ch3.Btn1_normal fore=L2UI_ch3.Btn1_normal>");
		sb.append("</td>");
		sb.append("</tr></table>");
		
		// Pagination
		sb.append("<br><table width=300><tr>");
		sb.append("<td align=center width=70>").append(page > 1 ? "<button value=\"< PREV\" action=\"bypass -h dressme " + (page - 1) + " myskinlist " + filterType + "\" width=65 height=19 back=L2UI_ch3.smallbutton2_over fore=L2UI_ch3.smallbutton2>" : "").append("</td>");
		sb.append("<td align=center width=140>Page: ").append(page).append("</td>");
		sb.append("<td align=center width=70>").append(hasMore ? "<button value=\"NEXT >\" action=\"bypass -h dressme " + (page + 1) + " myskinlist " + filterType + "\" width=65 height=19 back=L2UI_ch3.smallbutton2_over fore=L2UI_ch3.smallbutton2>" : "").append("</td>");
		sb.append("</tr></table>");
		
		html.replace("%showList%", sb.toString());
		player.sendPacket(html);
	}
	
	public static String getItemNameById(int itemId)
	{
		if (itemId == 0)
			return "NoName";
		
		Item item = ItemData.getInstance().getTemplate(itemId);
		if (item == null)
			return "NoName";
		
		return item.getName();
	}
}
