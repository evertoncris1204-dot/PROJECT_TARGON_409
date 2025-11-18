package net.sf.l2j.gameserver.handler.voicedcommandhandlers;

import java.util.StringTokenizer;

import net.sf.l2j.commons.lang.StringUtil;

import net.sf.l2j.gameserver.handler.IVoicedCommandHandler;
import net.sf.l2j.gameserver.model.WorldObject;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.instance.Monster;

import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;
import net.sf.l2j.gameserver.network.serverpackets.SystemMessage;

import Base.AutoFarm.AutofarmPlayerRoutine;

import net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage.SMPOS;

public class AutoFarm implements IVoicedCommandHandler 
{
    private final String[] VOICED_COMMANDS = 
    {
    	"autofarm",
    	"farm",
    	"enableAutoFarm",
    	"radiusAutoFarm",
    	"pageAutoFarm",
    	"enableBuffProtect",
    	"healAutoFarm",
    	"hpAutoFarm",
    	"mpAutoFarm",
    	"enableAntiKs",
    	"enableSummonAttack",
    	"summonSkillAutoFarm",
    	"ignoreMonster",
    	"activeMonster"
    };

    @Override
    public boolean useVoicedCommand(final String command, final Player activeChar, final String args)
    {
		final AutofarmPlayerRoutine bot = activeChar.getBot();
		
		if (bot == null)
		{
			activeChar.sendMessage("AutoFarm system is not initialized. Please relog.");
			return false;
		}
		
		// Handle commands with underscore prefix (from client interface)
		String actualCommand = command;
		if (command.startsWith("_"))
		{
			actualCommand = command.substring(1); // Remove underscore
		}
		
		// Handle _infosettings command (opens settings window)
		if (command.equals("_infosettings") || command.equals("infosettings"))
		{
			showAutoFarm(activeChar);
			return true;
		}
		
		// Handle enableAutoFarm command FIRST (from interface button)
		if (command.equals("enableAutoFarm") || command.equals("_enableAutoFarm") || 
		    command.startsWith("enableAutoFarm") || command.startsWith("_enableAutoFarm"))
		{
			try
			{
				if (activeChar.isAutoFarm())
				{
					bot.stop();
					activeChar.setAutoFarm(false);
					activeChar.saveAutoFarmSettings();
					activeChar.sendMessage("AutoFarm desativado.");
				}
				else
				{
					// Verificar se o bot pode iniciar
					if (bot == null)
					{
						activeChar.sendMessage("Erro: Bot nao inicializado. Por favor, relogue.");
						return false;
					}
					
					boolean started = bot.start();
					
					// Verificar se realmente iniciou
					boolean isRunning = bot.running();
					
					if (started && isRunning)
					{
						activeChar.setAutoFarm(true);
						activeChar.saveAutoFarmSettings();
						// Não enviar mensagem aqui, o bot.start() já envia "Auto Farming Activated..."
					}
					else
					{
						activeChar.setAutoFarm(false);
						activeChar.sendMessage("Erro ao iniciar AutoFarm. started=" + started + ", running=" + isRunning);
					}
				}
			}
			catch (Exception e)
			{
				e.printStackTrace();
				activeChar.sendMessage("Erro ao processar comando AutoFarm: " + e.getMessage());
				return false;
			}

	    	// Removed automatic interface show to prevent popup dialog when starting autofarm
	    	// showAutoFarm(activeChar);
	    	return true;
		}
		
		// Handle _enableRespectHunt (same as enableAntiKs)
		if (command.equals("_enableRespectHunt") || command.equals("enableRespectHunt"))
		{
			activeChar.setAntiKsProtection(!activeChar.isAntiKsProtected());
			
			if(activeChar.isAntiKsProtected())
			{
				activeChar.sendPacket(new SystemMessage(SystemMessageId.ACTIVATE_RESPECT_HUNT));
				activeChar.sendPacket(new ExShowScreenMessage("Respect Hunt On" , 3*1000, SMPOS.TOP_CENTER, false));
			}
			else
			{
				activeChar.sendPacket(new SystemMessage(SystemMessageId.DESACTIVATE_RESPECT_HUNT));
				activeChar.sendPacket(new ExShowScreenMessage("Respect Hunt Off" , 3*1000, SMPOS.TOP_CENTER, false));
			}
			
			activeChar.saveAutoFarmSettings();
			showAutoFarm(activeChar);
			return true;
		}
		
		// Handle _autofarm command (toggle AutoFarm on/off)
		if (command.equals("_autofarm"))
		{
			try
			{
				if (activeChar.isAutoFarm())
				{
					bot.stop();
					activeChar.setAutoFarm(false);
					activeChar.saveAutoFarmSettings();
					activeChar.sendMessage("AutoFarm desativado.");
				}
				else
				{
					if (bot == null)
					{
						activeChar.sendMessage("Erro: Bot nao inicializado. Por favor, relogue.");
						return false;
					}
					
					boolean started = bot.start();
					
					if (started && bot.running())
					{
						activeChar.setAutoFarm(true);
						activeChar.saveAutoFarmSettings();
						// Não enviar mensagem aqui, o bot.start() já envia "Auto Farming Activated..."
					}
					else
					{
						activeChar.setAutoFarm(false);
						activeChar.sendMessage("Erro ao iniciar AutoFarm. Verifique se voce esta online e com buffs.");
					}
				}
			}
			catch (Exception e)
			{
				activeChar.sendMessage("Erro ao processar comando AutoFarm: " + e.getMessage());
				e.printStackTrace();
				return false;
			}
			
			showAutoFarm(activeChar);
			return true;
		}
		
		// Handle .farm command (opens autofarm HTML)
		if (command.equals("farm") || command.equals(".farm"))
		{
			showAutoFarm(activeChar);
			return true;
		}
		
    	if (actualCommand.startsWith("autofarm") || command.startsWith("autofarm"))
    		showAutoFarm(activeChar);
    	
		if (command.startsWith("radiusAutoFarm") || command.startsWith("_radiusAutoFarm"))
		{
			StringTokenizer st = new StringTokenizer(command, " ");
			st.nextToken();
			try
			{
				String param = st.nextToken();

				if (param.startsWith("inc_radius"))
				{
					activeChar.setRadius(activeChar.getRadius() + 200);
					// Update radius circle visual
					if (bot.running())
						bot.updateRadiusCircle();
					showAutoFarm(activeChar);
				}
				else if (param.startsWith("dec_radius"))
				{
					activeChar.setRadius(activeChar.getRadius() - 200);
					// Update radius circle visual
					if (bot.running())
						bot.updateRadiusCircle();
					showAutoFarm(activeChar);
				}
				activeChar.saveAutoFarmSettings();
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
		
		if (command.startsWith("pageAutoFarm") || command.startsWith("_pageAutoFarm"))
		{
			StringTokenizer st = new StringTokenizer(command, " ");
			st.nextToken();
			try
			{
				String param = st.nextToken();

				if (param.startsWith("inc_page"))
				{
					activeChar.setPage(activeChar.getPage() + 1);
					showAutoFarm(activeChar);
				}
				else if (param.startsWith("dec_page"))
				{
					activeChar.setPage(activeChar.getPage() - 1);
					showAutoFarm(activeChar);
				}
				activeChar.saveAutoFarmSettings();
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
		
		if (command.startsWith("healAutoFarm") || command.startsWith("_healAutoFarm"))
		{
			StringTokenizer st = new StringTokenizer(command, " ");
			st.nextToken();
			try
			{
				String param = st.nextToken();

				if (param.startsWith("inc_heal"))
				{
					activeChar.setHealPercent(activeChar.getHealPercent() + 10);
					showAutoFarm(activeChar);
				}
				else if (param.startsWith("dec_heal"))
				{
					activeChar.setHealPercent(activeChar.getHealPercent() - 10);
					showAutoFarm(activeChar);
				}
				activeChar.saveAutoFarmSettings();
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
		
		if (command.startsWith("hpAutoFarm") || command.startsWith("_hpAutoFarm"))
		{
			StringTokenizer st = new StringTokenizer(command, " ");
			st.nextToken();
			try
			{
				String param = st.nextToken();

				if (param.contains("inc_hp_pot"))
				{
					activeChar.setHpPotionPercentage(activeChar.getHpPotionPercentage() + 5);
					showAutoFarm(activeChar);
				}
				else if (param.contains("dec_hp_pot"))
				{
					activeChar.setHpPotionPercentage(activeChar.getHpPotionPercentage() - 5);
					showAutoFarm(activeChar);
				}
				activeChar.saveAutoFarmSettings();
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
		
		if (command.startsWith("mpAutoFarm") || command.startsWith("_mpAutoFarm"))
		{
			StringTokenizer st = new StringTokenizer(command, " ");
			st.nextToken();
			try
			{
				String param = st.nextToken();

				if (param.contains("inc_mp_pot"))
				{
					activeChar.setMpPotionPercentage(activeChar.getMpPotionPercentage() + 5);
					showAutoFarm(activeChar);
				}
				else if (param.contains("dec_mp_pot"))
				{
					activeChar.setMpPotionPercentage(activeChar.getMpPotionPercentage() - 5);
					showAutoFarm(activeChar);
				}
				activeChar.saveAutoFarmSettings();
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
		
		if (command.startsWith("enableBuffProtect") || command.startsWith("_enableBuffProtect"))
		{
			activeChar.setNoBuffProtection(!activeChar.isNoBuffProtected());
			showAutoFarm(activeChar);
			activeChar.saveAutoFarmSettings();
		}
		
		if (command.startsWith("enableAntiKs") || command.startsWith("_enableAntiKs"))
		{
			activeChar.setAntiKsProtection(!activeChar.isAntiKsProtected());
			
			if(activeChar.isAntiKsProtected())
			{
				activeChar.sendPacket(new SystemMessage(SystemMessageId.ACTIVATE_RESPECT_HUNT));
				activeChar.sendPacket(new ExShowScreenMessage("Respct Hunt On" , 3*1000, SMPOS.TOP_CENTER, false));
			}
			else
			{
				activeChar.sendPacket(new SystemMessage(SystemMessageId.DESACTIVATE_RESPECT_HUNT));
				activeChar.sendPacket(new ExShowScreenMessage("Respct Hunt Off" , 3*1000, SMPOS.TOP_CENTER, false));
			}
			
			activeChar.saveAutoFarmSettings();
			showAutoFarm(activeChar);
		}
		
		if (command.startsWith("enableSummonAttack") || command.startsWith("_enableSummonAttack"))
		{
			activeChar.setSummonAttack(!activeChar.isSummonAttack());
			if(activeChar.isSummonAttack())
			{
				activeChar.sendPacket(new SystemMessage(SystemMessageId.ACTIVATE_SUMMON_ACTACK));
				activeChar.sendPacket(new ExShowScreenMessage("Auto Farm Summon Attack On" , 3*1000, SMPOS.TOP_CENTER, false));
			}
			else
			{
				activeChar.sendPacket(new SystemMessage(SystemMessageId.DESACTIVATE_SUMMON_ACTACK));
				activeChar.sendPacket(new ExShowScreenMessage("Auto Farm Summon Attack Off" , 3*1000, SMPOS.TOP_CENTER, false));
			}
			activeChar.saveAutoFarmSettings();
			showAutoFarm(activeChar);
		}
		
		if (command.startsWith("summonSkillAutoFarm") || command.startsWith("_summonSkillAutoFarm"))
		{
			StringTokenizer st = new StringTokenizer(command, " ");
			st.nextToken();
			try
			{
				String param = st.nextToken();

				if (param.startsWith("inc_summonSkill"))
				{
					activeChar.setSummonSkillPercent(activeChar.getSummonSkillPercent() + 10);
					showAutoFarm(activeChar);
				}
				else if (param.startsWith("dec_summonSkill"))
				{
					activeChar.setSummonSkillPercent(activeChar.getSummonSkillPercent() - 10);
					showAutoFarm(activeChar);
				}
				activeChar.saveAutoFarmSettings();
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
		
		if (command.startsWith("ignoreMonster") || command.startsWith("_ignoreMonster"))
		{
			int monsterId = 0;
			WorldObject target = activeChar.getTarget();
			if (target instanceof Monster)
				monsterId = ((Monster) target).getNpcId();
			
			if (target == null)
			{
				activeChar.sendMessage("You dont have a target");
				return false;
			}
			
			activeChar.sendMessage(target.getName() + " has been added to the ignore list.");
			activeChar.ignoredMonster(monsterId);
		}
		
		if (command.startsWith("activeMonster") || command.startsWith("_activeMonster"))
		{
			int monsterId = 0;
			WorldObject target = activeChar.getTarget();
			if (target instanceof Monster)
				monsterId = ((Monster) target).getNpcId();
			
			if (target == null)
			{
				activeChar.sendMessage("You dont have a target");
				return false;
			}
			
			activeChar.sendMessage(target.getName() + " has been removed from the ignore list.");
			activeChar.activeMonster(monsterId);
		}

        return false;
    }
    
	private static final String ACTIVED = "<font color=00FF00>STARTED</font>";
	private static final String DESATIVED = "<font color=FF0000>STOPPED</font>";
	private static final String STOP = "STOP";
	private static final String START = "START";
	
	public static void showAutoFarm(Player activeChar)
	{
		NpcHtmlMessage html = new NpcHtmlMessage(0);
		
		html.setFile("data/html/mods/menu/AutoFarm.htm"); 
		html.replace("%player%", activeChar.getName());
		html.replace("%page%", StringUtil.formatNumber(activeChar.getPage() + 1));
		html.replace("%heal%", StringUtil.formatNumber(activeChar.getHealPercent()));
		html.replace("%radius%", StringUtil.formatNumber(activeChar.getRadius()));
		html.replace("%summonSkill%", StringUtil.formatNumber(activeChar.getSummonSkillPercent()));
		html.replace("%hpPotion%", StringUtil.formatNumber(activeChar.getHpPotionPercentage()));
		html.replace("%mpPotion%", StringUtil.formatNumber(activeChar.getMpPotionPercentage()));
		html.replace("%noBuff%", activeChar.isNoBuffProtected() ? "back=L2UI.CheckBox_checked fore=L2UI.CheckBox_checked" : "back=L2UI.CheckBox fore=L2UI.CheckBox");
		html.replace("%autofarm%", activeChar.isAutoFarm() ? ACTIVED : DESATIVED);
		html.replace("%button%", activeChar.isAutoFarm() ? STOP : START);
		
		activeChar.sendPacket(html);
	}

    @Override
    public String[] getVoicedCommandList() 
    {
        return VOICED_COMMANDS;
    }
}

