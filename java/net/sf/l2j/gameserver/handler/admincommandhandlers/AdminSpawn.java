package net.sf.l2j.gameserver.handler.admincommandhandlers;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.List;
import java.util.StringTokenizer;

import net.sf.l2j.commons.data.Pagination;
import net.sf.l2j.commons.lang.StringUtil;
import net.sf.l2j.commons.logging.CLogger;

import net.sf.l2j.gameserver.data.manager.CustomSpawnManager;
import net.sf.l2j.gameserver.data.manager.FenceManager;
import net.sf.l2j.gameserver.data.manager.SpawnManager;
import net.sf.l2j.gameserver.data.xml.AdminData;
import net.sf.l2j.gameserver.data.xml.NpcData;
import net.sf.l2j.gameserver.handler.IAdminCommandHandler;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.WorldObject;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.instance.Fence;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.model.location.SpawnLocation;
import net.sf.l2j.gameserver.model.spawn.ASpawn;
import net.sf.l2j.gameserver.model.spawn.Spawn;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;
import net.sf.l2j.gameserver.network.serverpackets.SystemMessage;

public class AdminSpawn implements IAdminCommandHandler
{
	private static final CLogger LOGGER = new CLogger(AdminSpawn.class.getName());
	private static final String OTHER_XML_FOLDER = "./data/xml/spawnlist/custom";
		
	private static final String[] ADMIN_COMMANDS =
	{
		"admin_list_spawns",
		"admin_spawn",
		"admin_delete",
		"admin_unspawnall",
		"admin_respawnall",
		"admin_spawnfence",
		"admin_deletefence",
		"admin_listfence"
	};
	
	@Override
	public void useAdminCommand(String command, Player player)
	{
		if (command.startsWith("admin_list_spawns"))
		{
			final StringTokenizer st = new StringTokenizer(command, " ");
			st.nextToken();
			
			int npcId = 0;
			
			final String entry = (st.hasMoreTokens()) ? st.nextToken() : null;
			final int page = (st.hasMoreTokens()) ? Integer.parseInt(st.nextToken()) : 1;
			
			if (entry == null)
			{
				final Npc npc = getTarget(Npc.class, player, false);
				if (npc == null)
				{
					player.sendPacket(SystemMessageId.INVALID_TARGET);
					return;
				}
				
				npcId = npc.getNpcId();
			}
			else if (StringUtil.isDigit(entry))
				npcId = Integer.parseInt(entry);
			else
			{
				final NpcTemplate template = NpcData.getInstance().getTemplateByName(entry);
				if (template != null)
					npcId = template.getNpcId();
			}
			
			if (npcId == 0)
			{
				player.sendPacket(SystemMessageId.INVALID_TARGET);
				return;
			}
			
			int row = 0 + (8 * (page - 1));
			
			// Generate data.
			final Pagination<Npc> list = new Pagination<>(World.getInstance().getNpcs(npcId).stream(), page, PAGE_LIMIT_8);
			list.append("<html><body>");
			
			for (Npc npc : list)
			{
				list.append((row % 2) == 0 ? "<table width=280 height=41 bgcolor=000000><tr>" : "<table width=280 height=41><tr>");
				list.append("<td><a action=\"bypass -h admin_teleport ", npc.getX(), " ", npc.getY(), " ", npc.getZ(), "\">", row);
				
				final ASpawn spawn = npc.getSpawn();
				if (spawn == null)
					list.append(" - (", npc.getPosition(), ")", "</a>");
				else
					list.append(" - ", spawn, "</a><br1>", spawn.getDescription());
				
				list.append("</td></tr></table><img src=\"L2UI.SquareGray\" width=280 height=1>");
				
				row++;
			}
			
			list.generateSpace(42);
			list.generatePages("bypass admin_list_spawns " + npcId + " %page%");
			list.append("</body></html>");
			
			final NpcHtmlMessage html = new NpcHtmlMessage(0);
			html.setHtml(list.getContent());
			player.sendPacket(html);
		}
		else if (command.startsWith("admin_unspawnall"))
		{
			World.toAllOnlinePlayers(SystemMessage.getSystemMessage(SystemMessageId.NPC_SERVER_NOT_OPERATING));
			SpawnManager.getInstance().despawn();
			World.getInstance().deleteVisibleNpcSpawns();
			AdminData.getInstance().broadcastMessageToGMs("NPCs' unspawn is now complete.");
		}
		else if (command.startsWith("admin_respawnall"))
		{
			// make sure all spawns are deleted
			SpawnManager.getInstance().despawn();
			World.getInstance().deleteVisibleNpcSpawns();
			
			// now respawn all
			NpcData.getInstance().reload();
			SpawnManager.getInstance().reload();
			AdminData.getInstance().broadcastMessageToGMs("NPCs' respawn is now complete.");
		}
		else if (command.startsWith("admin_spawnfence"))
		{
			StringTokenizer st = new StringTokenizer(command, " ");
			try
			{
				st.nextToken();
				int type = Integer.parseInt(st.nextToken());
				int sizeX = (Integer.parseInt(st.nextToken()) / 100) * 100;
				int sizeY = (Integer.parseInt(st.nextToken()) / 100) * 100;
				int height = 1;
				if (st.hasMoreTokens())
					height = Math.min(Integer.parseInt(st.nextToken()), 3);
				
				FenceManager.getInstance().addFence(player.getX(), player.getY(), player.getZ(), type, sizeX, sizeY, height);
				
				listFences(player);
			}
			catch (Exception e)
			{
				player.sendMessage("Usage: //spawnfence <type> <width> <length> [height]");
			}
		}
		else if (command.startsWith("admin_deletefence"))
		{
			StringTokenizer st = new StringTokenizer(command, " ");
			st.nextToken();
			try
			{
				final WorldObject worldObject = World.getInstance().getObject(Integer.parseInt(st.nextToken()));
				if (worldObject instanceof Fence fence)
				{
					FenceManager.getInstance().removeFence(fence);
					
					if (st.hasMoreTokens())
						listFences(player);
				}
				else
					player.sendPacket(SystemMessageId.INVALID_TARGET);
			}
			catch (Exception e)
			{
				player.sendMessage("Usage: //deletefence <objectId>");
			}
		}
		else if (command.startsWith("admin_listfence"))
			listFences(player);
		else if (command.startsWith("admin_spawn"))
		{
			StringTokenizer st = new StringTokenizer(command, " ");
			try
			{
				final String cmd = st.nextToken();
				final String idOrName = st.nextToken();
				final int respawnTime = (st.hasMoreTokens()) ? Integer.parseInt(st.nextToken()) : 60;
				
				final WorldObject targetWorldObject = getTarget(WorldObject.class, player, true);
				
				NpcTemplate template;
				
				// First parameter was an ID number
				if (idOrName.matches("[0-9]*"))
					template = NpcData.getInstance().getTemplate(Integer.parseInt(idOrName));
				// First parameter wasn't just numbers, so go by name not ID
				else
					template = NpcData.getInstance().getTemplateByName(idOrName.replace('_', ' '));
				
				try
				{
					final Spawn spawn = new Spawn(template);
					spawn.setLoc(targetWorldObject.getPosition());
					spawn.setRespawnDelay(respawnTime);
					spawn.doSpawn(false);
					addSpawn(spawn);
					
					player.sendMessage("You spawned " + template.getName() + ". - Cmd: " + cmd);
					
				}
				catch (Exception e)
				{
					player.sendPacket(SystemMessageId.APPLICANT_INFORMATION_INCORRECT);
				}
			}
			catch (Exception e)
			{
				sendFile(player, "spawns.htm");
			}
		}
		else if (command.startsWith("admin_delete"))
		{
			// Target must be a Npc.
			final WorldObject targetWorldObject = player.getTarget();
			if (!(targetWorldObject instanceof Npc targetNpc))
			{
				player.sendPacket(SystemMessageId.INVALID_TARGET);
				return;
			}
			
			// Get the spawn (can be Spawn or MultiSpawn)
			final ASpawn spawn = targetNpc.getSpawn();
			if (spawn == null)
			{
				player.sendPacket(SystemMessageId.INVALID_TARGET);
				return;
			}
			
			// Check if this is a custom spawn (from database)
			boolean isCustomSpawn = false;
			int customSpawnId = 0;
			
			// Check if spawn exists in CustomSpawnManager by comparing position and NPC ID
			final SpawnLocation spawnLoc = spawn.getSpawnLocation();
			for (CustomSpawnManager.CustomSpawnData data : CustomSpawnManager.getInstance().getAllSpawns())
			{
				// Compare by NPC ID and position
				if (data.npcId == targetNpc.getNpcId() &&
					data.x == spawnLoc.getX() &&
					data.y == spawnLoc.getY() &&
					data.z == spawnLoc.getZ())
				{
					isCustomSpawn = true;
					customSpawnId = data.id;
					break;
				}
			}
			
			// Delete from CustomSpawnManager if it's a custom spawn
			if (isCustomSpawn)
			{
				CustomSpawnManager.getInstance().deleteSpawn(customSpawnId);
				player.sendMessage("Custom spawn deleted from database: " + targetNpc.getName() + ".");
			}
			
			// Remove from XML file if it's a Spawn type (created via //spawn command)
			if (spawn instanceof Spawn)
			{
				removeSpawnFromXML((Spawn) spawn);
				
				// Delete the Spawn entry from SpawnManager
				SpawnManager.getInstance().deleteSpawn((Spawn) spawn);
			}
			else
			{
				// For MultiSpawn (from XML), try to remove from XML file
				// Create a temporary Spawn object to use the removal function
				try
				{
					final Spawn tempSpawn = new Spawn(targetNpc.getTemplate());
					tempSpawn.setLoc(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), spawnLoc.getHeading());
					removeSpawnFromXML(tempSpawn);
				}
				catch (Exception e)
				{
					LOGGER.warn("Could not create temp spawn for XML removal: " + e);
				}
			}
			
			// Delete the Npc.
			targetNpc.deleteMe();
			
			// Send Player log.
			player.sendMessage("You deleted " + targetNpc.getName() + ".");
		}
	}
	
	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
	
	private static void listFences(Player player)
	{
		final List<Fence> fences = FenceManager.getInstance().getFences();
		final StringBuilder sb = new StringBuilder();
		
		sb.append("<html><body>Total Fences: " + fences.size() + "<br><br>");
		for (Fence fence : fences)
			sb.append("<a action=\"bypass -h admin_deletefence " + fence.getObjectId() + " 1\">Fence: " + fence.getObjectId() + " [" + fence.getX() + " " + fence.getY() + " " + fence.getZ() + "]</a><br>");
		sb.append("</body></html>");
		
		final NpcHtmlMessage html = new NpcHtmlMessage(0);
		html.setHtml(sb.toString());
		player.sendPacket(html);
	}
		
		private static void addSpawn(Spawn spawn)
		{
			SpawnManager.getInstance().addSpawn(spawn);
			
			// Create output directory if it doesn't exist
			final File outputDirectory = new File(OTHER_XML_FOLDER);
			if (!outputDirectory.exists())
			{
				try
				{
					outputDirectory.mkdir();
				}
				catch (SecurityException se)
				{
					// empty
				}
			}
			
			// XML file for spawn
			final String name = spawn.getNpc().getName().replaceAll("(\\s|')+","").toLowerCase() + "_" + System.currentTimeMillis();
			final String npcMakerName = spawn.getNpc().getName().replaceAll("(\\s|')+","").toLowerCase() + "_" + System.nanoTime();
			final String fileName = spawn.getNpc().getName().replaceAll("(\\s|')+","").toLowerCase();
			
			final int x = ((spawn.getLocX() - World.WORLD_X_MIN) >> 15) + World.TILE_X_MIN;
			final int y = ((spawn.getLocY() - World.WORLD_Y_MIN) >> 15) + World.TILE_Y_MIN;
			final File spawnFile = new File(OTHER_XML_FOLDER + "/" + fileName + "_" + x + "_" + y + ".xml");
			
			// Write info to XML
			final String spawnId = String.valueOf(spawn.getNpcId());
			final String spawnLoc = String.valueOf(spawn.getLocX() + ";" + spawn.getLocY() + ";" + spawn.getLocZ() + ";" + spawn.getHeading());
			
			final String respawnDelay = spawn.calculateRespawnDelay() + "sec";
			
			if (spawnFile.exists()) // update
			{
				final File tempFile = new File(OTHER_XML_FOLDER + "/" + name + "_" + x + "_" + y + ".tmp");
				try (BufferedReader reader = new BufferedReader(new FileReader(spawnFile));
					BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile)))
				{
					String currentLine;
					while ((currentLine = reader.readLine()) != null)
					{
						if (currentLine.contains("</list>"))
						{
							writer.write("	<territory name=\"" + name + "\" minZ=\"" + (spawn.getLocZ()) + "\" maxZ=\"" + (spawn.getLocZ() + 16) + "\">\n");
							writer.write("		<node x=\"" + (spawn.getLocX() + 50) + "\" y=\"" + (spawn.getLocY() + 50) + "\" />\n");
							writer.write("		<node x=\"" + (spawn.getLocX() - 50) + "\" y=\"" + (spawn.getLocY() + 50) + "\" />\n");
							writer.write("		<node x=\"" + (spawn.getLocX() - 50) + "\" y=\"" + (spawn.getLocY() - 50) + "\" />\n");
							writer.write("		<node x=\"" + (spawn.getLocX() + 50) + "\" y=\"" + (spawn.getLocY() - 50) + "\" />\n");
							writer.write("	</territory>\n");
							writer.write("	<npcmaker name=\"" + npcMakerName + "\" territory=\"" + name + "\" maximumNpcs=\"" + 1 + "\">\n");
							writer.write("		<npc id=\"" + spawnId + "\" pos=\"" + spawnLoc + "\" total=\"" + 1 + "\" respawn=\"" + respawnDelay + "\" /> <!-- " + spawn.getNpc().getTemplate().getName() + " -->\n");
							writer.write("	</npcmaker>\n");
							writer.write(currentLine + "\n");
							continue;
						}
						writer.write(currentLine + "\n");
					}
					writer.close();
					reader.close();
					spawnFile.delete();
					tempFile.renameTo(spawnFile);
				}
				catch (Exception e)
				{
					LOGGER.warn("Could not store spawn in the spawn XML files: " + e);
				}
			}
			else // new file
			{
				try (BufferedWriter writer = new BufferedWriter(new FileWriter(spawnFile)))
				{
					writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
					writer.write("<list>\n");
					writer.write("	<territory name=\"" + name + "\" minZ=\"" + (spawn.getLocZ()) + "\" maxZ=\"" + (spawn.getLocZ() + 16) + "\">\n");
					writer.write("		<node x=\"" + (spawn.getLocX() + 50) + "\" y=\"" + (spawn.getLocY() + 50) + "\" />\n");
					writer.write("		<node x=\"" + (spawn.getLocX() - 50) + "\" y=\"" + (spawn.getLocY() + 50) + "\" />\n");
					writer.write("		<node x=\"" + (spawn.getLocX() - 50) + "\" y=\"" + (spawn.getLocY() - 50) + "\" />\n");
					writer.write("		<node x=\"" + (spawn.getLocX() + 50) + "\" y=\"" + (spawn.getLocY() - 50) + "\" />\n");
					writer.write("	</territory>\n");
					writer.write("	<npcmaker name=\"" + npcMakerName + "\" territory=\"" + name + "\" maximumNpcs=\"" + 1 + "\">\n");
					writer.write("		<npc id=\"" + spawnId + "\" pos=\"" + spawnLoc + "\" total=\"" + 1 + "\" respawn=\"" + respawnDelay + "\" /> <!-- " + spawn.getNpc().getTemplate().getName() + " -->\n");
					writer.write("	</npcmaker>\n");
					writer.write("</list>\n");
					writer.close();
				}
				catch (Exception e)
				{
					LOGGER.warn("Spawn " + spawn + " could not be added to the spawn XML files: " + e);
				}
			}
		}
		
		/**
		 * Remove spawn from XML file
		 */
		private static void removeSpawnFromXML(Spawn spawn)
		{
			try
			{
				// Get NPC name - handle case where NPC might be null (temporary spawn)
				String npcName;
				if (spawn.getNpc() != null)
					npcName = spawn.getNpc().getName();
				else
					npcName = spawn.getTemplate().getName();
				
				final int x = ((spawn.getLocX() - World.WORLD_X_MIN) >> 15) + World.TILE_X_MIN;
				final int y = ((spawn.getLocY() - World.WORLD_Y_MIN) >> 15) + World.TILE_Y_MIN;
				final String fileName = npcName.replaceAll("(\\s|')+","").toLowerCase();
				
				// Try multiple possible paths
				File customFolder = new File(OTHER_XML_FOLDER);
				if (!customFolder.exists())
				{
					// Try game/data path
					customFolder = new File("./game/data/xml/spawnlist/custom");
				}
				if (!customFolder.exists())
				{
					// Try data path
					customFolder = new File("./data/xml/spawnlist/custom");
				}
				
				if (!customFolder.exists())
				{
					LOGGER.warn("Custom spawn folder does not exist: " + OTHER_XML_FOLDER);
					return;
				}
				
				// Try the specific file first
				File spawnFile = new File(customFolder, fileName + "_" + x + "_" + y + ".xml");
				
				// If specific file doesn't exist, search all XML files in the folder
				if (!spawnFile.exists())
				{
					File[] xmlFiles = customFolder.listFiles((dir, name) -> name.endsWith(".xml"));
					if (xmlFiles != null)
					{
						for (File file : xmlFiles)
						{
							// Quick check: does this file contain our NPC ID?
							try (BufferedReader testReader = new BufferedReader(new FileReader(file)))
							{
								String testLine;
								boolean hasNpcId = false;
								while ((testLine = testReader.readLine()) != null)
								{
									if (testLine.contains("id=\"" + spawn.getNpcId() + "\""))
									{
										hasNpcId = true;
										break;
									}
								}
								if (hasNpcId)
								{
									spawnFile = file;
									break;
								}
							}
							catch (Exception e)
							{
								// Continue searching
							}
						}
					}
				}
				
				LOGGER.info("Attempting to remove spawn from XML: " + spawnFile.getAbsolutePath() + " (NPC: " + spawn.getNpcId() + " at " + spawn.getLocX() + "," + spawn.getLocY() + "," + spawn.getLocZ() + ")");
				
				if (!spawnFile.exists())
				{
					LOGGER.warn("Spawn XML file does not exist: " + spawnFile.getAbsolutePath());
					return;
				}
				
				// Read file and remove matching spawn
				// Create temp file in system temp directory to avoid SpawnManager loading it
				final File tempFile = File.createTempFile(spawnFile.getName(), ".tmp", new File(System.getProperty("java.io.tmpdir")));
				boolean found = false;
				
				try (BufferedReader reader = new BufferedReader(new FileReader(spawnFile));
					BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile)))
				{
					// Write XML header
					writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
					writer.write("<list>\n");
					
					String currentLine;
					java.util.Map<String, java.util.List<String>> territoryMap = new java.util.HashMap<>();
					java.util.List<String> currentTerritoryLines = null;
					java.util.List<String> npcMakerLines = new java.util.ArrayList<>();
					String currentTerritoryName = null;
					String npcMakerTerritoryName = null;
					boolean collectingTerritory = false;
					boolean collectingNpcMaker = false;
					int npcMakerDepth = 0;
					boolean inContent = false;
					
					while ((currentLine = reader.readLine()) != null)
					{
						String trimmedLine = currentLine.trim();
						
						// Skip XML header
						if (trimmedLine.startsWith("<?xml"))
							continue;
						
						// Skip opening list tags until we find actual content
						if (!inContent)
						{
							if (trimmedLine.equals("<list>"))
								continue;
							// Once we find content (territory or npcmaker), we're in content
							if (trimmedLine.startsWith("<territory") || trimmedLine.startsWith("<npcmaker"))
								inContent = true;
							else
								continue;
						}
						
						// Skip closing list tags
						if (trimmedLine.equals("</list>"))
							continue;
						
						// Check if we're entering a territory
						if (currentLine.contains("<territory"))
						{
							currentTerritoryLines = new java.util.ArrayList<>();
							collectingTerritory = true;
							
							// Extract territory name
							String line = currentLine.trim();
							if (line.contains("name=\""))
							{
								int nameStart = line.indexOf("name=\"") + 6;
								int nameEnd = line.indexOf("\"", nameStart);
								if (nameEnd > nameStart)
									currentTerritoryName = line.substring(nameStart, nameEnd);
							}
							
							currentTerritoryLines.add(currentLine);
							continue;
						}
						// Collect territory lines
						else if (collectingTerritory)
						{
							currentTerritoryLines.add(currentLine);
							
							if (currentLine.contains("</territory"))
							{
								collectingTerritory = false;
								// Store territory in map for later use
								if (currentTerritoryName != null)
								{
									territoryMap.put(currentTerritoryName, new java.util.ArrayList<>(currentTerritoryLines));
								}
								currentTerritoryLines = null;
								currentTerritoryName = null;
							}
							continue;
						}
						
						// Check if we're entering npcmaker
						if (currentLine.contains("<npcmaker") && !collectingNpcMaker)
						{
							collectingNpcMaker = true;
							npcMakerLines.clear();
							String line = currentLine.trim();
							// Extract territory name from npcmaker
							if (line.contains("territory=\""))
							{
								int terrStart = line.indexOf("territory=\"") + 11;
								int terrEnd = line.indexOf("\"", terrStart);
								if (terrEnd > terrStart)
									npcMakerTerritoryName = line.substring(terrStart, terrEnd);
							}
							npcMakerLines.add(currentLine);
							npcMakerDepth = 1;
							continue;
						}
						
						// Collect npcmaker lines
						if (collectingNpcMaker)
						{
							npcMakerLines.add(currentLine);
							
							if (currentLine.contains("<npcmaker"))
								npcMakerDepth++;
							else if (currentLine.contains("</npcmaker"))
							{
								npcMakerDepth--;
								if (npcMakerDepth == 0)
								{
									collectingNpcMaker = false;
									// Check if this npcmaker contains our spawn
									boolean containsOurSpawn = false;
									for (String npcLine : npcMakerLines)
									{
										if (npcLine.contains("<npc"))
										{
											String line = npcLine.trim();
											// Check if NPC ID matches
											if (line.contains("id=\"" + spawn.getNpcId() + "\""))
											{
												// Extract position from XML line
												if (line.contains("pos=\""))
												{
													int posStart = line.indexOf("pos=\"") + 5;
													int posEnd = line.indexOf("\"", posStart);
													if (posEnd > posStart)
													{
														String posStr = line.substring(posStart, posEnd);
														String[] posParts = posStr.split(";");
														
														if (posParts.length >= 3)
														{
															try
															{
																int xmlX = Integer.parseInt(posParts[0]);
																int xmlY = Integer.parseInt(posParts[1]);
																int xmlZ = Integer.parseInt(posParts[2]);
																
																// Compare positions with tolerance (spawns might have slight differences)
																int tolerance = 100; // Allow 100 units tolerance
																if (Math.abs(xmlX - spawn.getLocX()) <= tolerance &&
																	Math.abs(xmlY - spawn.getLocY()) <= tolerance &&
																	Math.abs(xmlZ - spawn.getLocZ()) <= tolerance)
																{
																	containsOurSpawn = true;
																	found = true;
																	LOGGER.info("Found matching spawn in XML: " + line);
																	break;
																}
															}
															catch (NumberFormatException e)
															{
																// Continue searching
															}
														}
													}
												}
											}
										}
									}
									
									// If this npcmaker contains our spawn, skip it and its territory
									if (containsOurSpawn)
									{
										// Remove territory from map so it won't be written
										if (npcMakerTerritoryName != null)
										{
											territoryMap.remove(npcMakerTerritoryName);
										}
										// Don't write npcmaker lines
										npcMakerLines.clear();
									}
									else
									{
										// Write territory first if it exists
										if (npcMakerTerritoryName != null)
										{
											java.util.List<String> terrLines = territoryMap.remove(npcMakerTerritoryName);
											if (terrLines != null)
											{
												for (String territoryLine : terrLines)
													writer.write(territoryLine + "\n");
											}
										}
										// Write npcmaker lines if it doesn't contain our spawn
										for (String npcMakerLine : npcMakerLines)
											writer.write(npcMakerLine + "\n");
										npcMakerLines.clear();
									}
								}
								continue;
							}
							continue;
						}
						
						// Write line if we're not collecting territory/npcmaker
						if (!collectingTerritory && !collectingNpcMaker)
							writer.write(currentLine + "\n");
					}
					
					// Write any remaining territories that weren't matched to npcmakers
					for (java.util.List<String> territoryLines : territoryMap.values())
					{
						for (String territoryLine : territoryLines)
							writer.write(territoryLine + "\n");
					}
					
					// Write closing list tag
					writer.write("</list>\n");
					writer.flush();
					
					// Close writer and reader before copying
					writer.close();
					reader.close();
					
					// Replace original file if we found and removed the spawn
					if (found)
					{
						// Copy temp file content to original file
						try (BufferedReader tempReader = new BufferedReader(new FileReader(tempFile));
							BufferedWriter finalWriter = new BufferedWriter(new FileWriter(spawnFile)))
						{
							String line;
							while ((line = tempReader.readLine()) != null)
							{
								finalWriter.write(line + "\n");
							}
							finalWriter.flush();
						}
						
						LOGGER.info("Successfully removed spawn from XML file: " + spawnFile.getName());
						
						// If file is now empty or only has header, delete it
						try (BufferedReader checkReader = new BufferedReader(new FileReader(spawnFile)))
						{
							boolean hasContent = false;
							String checkLine;
							while ((checkLine = checkReader.readLine()) != null)
							{
								if (checkLine.contains("<territory") || checkLine.contains("<npcmaker"))
								{
									hasContent = true;
									break;
								}
							}
							
							if (!hasContent)
							{
								spawnFile.delete();
								LOGGER.info("Deleted empty XML file: " + spawnFile.getName());
							}
						}
					}
					else
					{
						LOGGER.warn("Spawn not found in XML file: " + spawnFile.getName() + " (NPC ID: " + spawn.getNpcId() + ", Pos: " + spawn.getLocX() + "," + spawn.getLocY() + "," + spawn.getLocZ() + ")");
					}
				}
				catch (Exception e)
				{
					LOGGER.warn("Could not remove spawn from XML file: " + e);
				}
				finally
				{
					// Always try to delete temp file, even if there was an error
					// Wait a bit to ensure file handles are released
					try
					{
						Thread.sleep(100);
					}
					catch (InterruptedException ie)
					{
						// Ignore
					}
					
					if (tempFile.exists())
					{
						// Try multiple times to delete
						boolean deleted = false;
						for (int i = 0; i < 3; i++)
						{
							if (tempFile.delete())
							{
								deleted = true;
								break;
							}
							try
							{
								Thread.sleep(100);
							}
							catch (InterruptedException ie)
							{
								// Ignore
							}
						}
						
						if (!deleted)
							LOGGER.warn("Could not delete temp file after multiple attempts: " + tempFile.getAbsolutePath());
					}
				}
			}
			catch (Exception e)
			{
				LOGGER.warn("Error removing spawn from XML: " + e);
			}
		}
}