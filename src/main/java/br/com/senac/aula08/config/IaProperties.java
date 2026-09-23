package br.com.senac.aula08.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Propriedades "ia.*". Chaves ficam em variaveis de ambiente, nunca aqui. */
@ConfigurationProperties(prefix = "ia")
public record IaProperties(
        String provedor,
        Provedor claude,
        Provedor openai,
        Provedor gemini,
        int maxTokens,
        int timeoutSegundos,
        Historico historico,
        Cache cache) {

    public record Provedor(String modelo) {}

    public record Historico(int tamanho) {}

    public record Cache(boolean ativo, int validadeMinutos) {}
}
