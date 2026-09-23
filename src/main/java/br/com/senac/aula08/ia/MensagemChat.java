package br.com.senac.aula08.ia;

/**
 * Uma mensagem da conversa, no formato "neutro" da nossa aplicacao.
 *
 * Cada provedor traduz isso para o seu proprio JSON:
 *   - OpenAI:  {"role":"user"|"assistant", "content":"..."}
 *   - Claude:  {"role":"user"|"assistant", "content":"..."}
 *   - Gemini:  {"role":"user"|"model",     "parts":[{"text":"..."}]}
 */
public record MensagemChat(Papel papel, String conteudo) {

    public enum Papel { USUARIO, ASSISTENTE }

    public static MensagemChat usuario(String texto) {
        return new MensagemChat(Papel.USUARIO, texto);
    }

    public static MensagemChat assistente(String texto) {
        return new MensagemChat(Papel.ASSISTENTE, texto);
    }
}
