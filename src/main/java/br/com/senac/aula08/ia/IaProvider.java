package br.com.senac.aula08.ia;

import java.util.List;

/**
 * Contrato comum dos tres provedores. O resto da aplicacao (Service,
 * Controller) so conhece esta interface - trocar de IA vira uma linha.
 */
public interface IaProvider {

    /** Nome curto usado na URL/JSON: "claude", "openai" ou "gemini". */
    String nome();

    /**
     * Envia o historico completo (ultima mensagem = pergunta atual) e um
     * system prompt opcional (pode ser null) e devolve a resposta.
     */
    RespostaIA conversar(List<MensagemChat> historico, String systemPrompt);

    /**
     * Incremento 4 (Aula 06): pergunta sobre uma IMAGEM (base64) - usado para
     * descrever a foto do problema anexada ao ticket.
     */
    String perguntarComImagem(byte[] imagem, String mimeType, String prompt);

    default RespostaIA perguntar(String pergunta) {
        return conversar(List.of(MensagemChat.usuario(pergunta)), null);
    }
}
