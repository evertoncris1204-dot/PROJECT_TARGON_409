package net.sf.l2j.gameserver.handler;

import net.sf.l2j.gameserver.model.actor.Player;

public interface IVoicedCommandHandler {
    /**
     * Executa o comando de voz/bypass.
     * 
     * @param command O comando chamado (ex: "name_change")
     * @param player  O jogador que executou o comando
     * @param params  Parâmetros enviados (ex: novo nome)
     * @return true se executado com sucesso
     */
    boolean useVoicedCommand(String command, Player player, String params);

    /**
     * Retorna a lista de comandos que este handler processa.
     * 
     * @return array de comandos
     */
    String[] getVoicedCommandList();
}
