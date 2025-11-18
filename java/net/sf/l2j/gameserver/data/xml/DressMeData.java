package net.sf.l2j.gameserver.data.xml;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import net.sf.l2j.commons.data.xml.IXmlReader;
import net.sf.l2j.commons.data.StatSet;
import net.sf.l2j.gameserver.model.skin.SkinPackage;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public class DressMeData implements IXmlReader
{
	private final static Map<Integer, SkinPackage> _armorSkins = new HashMap<>();
	private final static Map<Integer, SkinPackage> _weaponSkins = new HashMap<>();
	private final static Map<Integer, SkinPackage> _hairSkins = new HashMap<>();
	private final static Map<Integer, SkinPackage> _faceSkins = new HashMap<>();
	private final static Map<Integer, SkinPackage> _shieldSkins = new HashMap<>();
	
	protected DressMeData()
	{
		load();
	}
	
	@Override
	public void load()
	{
		parseFile("./data/xml/dressme.xml");
		LOGGER.info("Loaded {} armor skins", _armorSkins.size());
		LOGGER.info("Loaded {} weapon skins", _weaponSkins.size());
		LOGGER.info("Loaded {} hair skins", _hairSkins.size());
		LOGGER.info("Loaded {} face skins", _faceSkins.size());
		LOGGER.info("Loaded {} shield skins", _shieldSkins.size());
	}
	
	@Override
	public void parseDocument(Document doc, Path path)
	{
		for (Node list = doc.getFirstChild(); list != null; list = list.getNextSibling())
		{
			if ("list".equalsIgnoreCase(list.getNodeName()))
			{
				for (Node skin = list.getFirstChild(); skin != null; skin = skin.getNextSibling())
				{
					if ("skin".equalsIgnoreCase(skin.getNodeName()))
					{
						final NamedNodeMap attrs = skin.getAttributes();
						
						String type = parseString(attrs, "type");
						
						final StatSet set = new StatSet();
						
						for (Node typeN = skin.getFirstChild(); typeN != null; typeN = typeN.getNextSibling())
						{
							if ("type".equalsIgnoreCase(typeN.getNodeName()))
							{
								final NamedNodeMap attrs2 = typeN.getAttributes();
								
								int id = parseInteger(attrs2, "id");
								String name = parseString(attrs2, "name");
								int weaponId = parseInteger(attrs2, "weaponId", 0);
								int shieldId = parseInteger(attrs2, "shieldId", 0);
								int chestId = parseInteger(attrs2, "chestId", 0);
								int hairId = parseInteger(attrs2, "hairId", 0);
								int faceId = parseInteger(attrs2, "faceId", 0);
								int legsId = parseInteger(attrs2, "legsId", 0);
								int glovesId = parseInteger(attrs2, "glovesId", 0);
								int feetId = parseInteger(attrs2, "feetId", 0);
								int priceId = parseInteger(attrs2, "priceId", 0);
								int priceCount = parseInteger(attrs2, "priceCount", 0);
								
								set.set("type", type);
								set.set("id", id);
								set.set("name", name);
								set.set("weaponId", weaponId);
								set.set("shieldId", shieldId);
								set.set("chestId", chestId);
								set.set("hairId", hairId);
								set.set("faceId", faceId);
								set.set("legsId", legsId);
								set.set("glovesId", glovesId);
								set.set("feetId", feetId);
								set.set("priceId", priceId);
								set.set("priceCount", priceCount);
								
								switch (type.toLowerCase())
								{
									case "armor":
										_armorSkins.put(id, new SkinPackage(set));
										break;
									case "weapon":
										_weaponSkins.put(id, new SkinPackage(set));
										break;
									case "hair":
										_hairSkins.put(id, new SkinPackage(set));
										break;
									case "face":
										_faceSkins.put(id, new SkinPackage(set));
										break;
									case "shield":
										_shieldSkins.put(id, new SkinPackage(set));
										break;
								}
							}
						}
					}
				}
			}
		}
	}
	
	public void reload()
	{
		_armorSkins.clear();
		_weaponSkins.clear();
		_hairSkins.clear();
		_faceSkins.clear();
		_shieldSkins.clear();
		
		load();
	}
	
	public SkinPackage getArmorSkinsPackage(int id)
	{
		return _armorSkins.get(id);
	}
	
	public Map<Integer, SkinPackage> getArmorSkinOptions()
	{
		return _armorSkins;
	}
	
	public SkinPackage getWeaponSkinsPackage(int id)
	{
		return _weaponSkins.get(id);
	}
	
	public Map<Integer, SkinPackage> getWeaponSkinOptions()
	{
		return _weaponSkins;
	}
	
	public SkinPackage getHairSkinsPackage(int id)
	{
		return _hairSkins.get(id);
	}
	
	public Map<Integer, SkinPackage> getHairSkinOptions()
	{
		return _hairSkins;
	}
	
	public SkinPackage getFaceSkinsPackage(int id)
	{
		return _faceSkins.get(id);
	}
	
	public Map<Integer, SkinPackage> getFaceSkinOptions()
	{
		return _faceSkins;
	}
	
	public SkinPackage getShieldSkinsPackage(int id)
	{
		return _shieldSkins.get(id);
	}
	
	public Map<Integer, SkinPackage> getShieldSkinOptions()
	{
		return _shieldSkins;
	}
	
	/**
	 * Finds the corresponding hair skin ID for a given armor skin ID.
	 * Maps armor skin chestId to hair skin hairId.
	 * @param armorSkinId The armor skin ID from dressme.xml
	 * @return The hair skin ID if found, or 0 if not found
	 */
	public int getCorrespondingHairSkinId(int armorSkinId)
	{
		final SkinPackage armorSkin = _armorSkins.get(armorSkinId);
		if (armorSkin == null)
			return 0;
		
		final int chestId = armorSkin.getChestId();
		if (chestId == 0)
			return 0;
		
		// Map armor chestId to hair hairId
		// 10100 -> 10101, 10103 -> 10104, 10105 -> 10106, etc.
		final int hairItemId = chestId + 1;
		
		// Find hair skin that uses this hairItemId
		for (Map.Entry<Integer, SkinPackage> entry : _hairSkins.entrySet())
		{
			if (entry.getValue().getHairId() == hairItemId)
				return entry.getKey();
		}
		
		return 0;
	}
	
	public static DressMeData getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private static class SingletonHolder
	{
		protected static final DressMeData _instance = new DressMeData();
	}
}

