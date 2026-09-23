package br.com.senac.aula08.ia;

/**
 * Resposta padronizada, independente do provedor.
 *
 * Os campos de tokens vem do bloco "usage" (OpenAI/Claude) ou
 * "usageMetadata" (Gemini) - e a base de cobranca de todas as APIs.
 */
public record RespostaIA(
        String provedor,
        String modelo,
        String texto,
        int tokensEntrada,
        int tokensSaida,
        long tempoMs) {

    public int getTotalTokens() {
        return tokensEntrada + tokensSaida;
    }
}
