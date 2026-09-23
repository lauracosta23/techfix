package br.com.senac.aula08.ia;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Codigo comum aos tres provedores: HttpClient nativo (Java 11+), timeout,
 * 1 retry em caso de timeout e traducao dos status HTTP em IaException.
 *
 * E exatamente o mesmo padrao dos exemplos da Aula 04 (GeradorTextoIA,
 * ClaudeChat, GeminiChat), so que agora reaproveitado por heranca.
 */
public abstract class ProviderBase implements IaProvider {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final ObjectMapper mapper;
    private final HttpClient client;
    private final Duration timeout;

    protected ProviderBase(ObjectMapper mapper, int timeoutSegundos) {
        this.mapper = mapper;
        this.timeout = Duration.ofSeconds(timeoutSegundos);
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /** Le a chave da variavel de ambiente e falha com mensagem clara se faltar. */
    protected String chave(String nomeVariavel) {
        String valor = System.getenv(nomeVariavel);
        if (valor == null || valor.isBlank()) {
            throw new IaException(500, "Variavel de ambiente " + nomeVariavel
                    + " nao configurada. No Eclipse: Run > Run Configurations > Environment.");
        }
        return valor;
    }

    /** Envia um POST JSON e devolve a arvore JSON da resposta (status 200). */
    protected JsonNode postJson(HttpRequest.Builder builder, Object corpo) {
        try {
            String json = mapper.writeValueAsString(corpo);
            HttpRequest request = builder
                    .timeout(timeout)
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = enviarComRetry(client, request);

            int status = response.statusCode();
            if (status == 200) {
                return mapper.readTree(response.body());
            }
            log.error("[{}] HTTP {} -> {}", nome(), status, response.body());
            throw new IaException(status, mensagemParaStatus(status));

        } catch (IOException e) {
            throw new IaException(502, "Falha de rede ao chamar " + nome() + ": " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IaException(500, "Chamada interrompida");
        }
    }

    /**
     * Status HTTP considerados temporarios: vale a pena tentar de novo.
     * 429 = limite/rate limit; 500/502/503/529 = provedor sobrecarregado
     * ou instavel (comum em modelos recem-lancados ou no free tier).
     */
    private static final java.util.Set<Integer> RETRYAVEIS = java.util.Set.of(429, 500, 502, 503, 529);

    /** Envia a requisicao com 1 retry em timeout e ate 2 retries (backoff) em status transitorio. */
    private HttpResponse<String> enviarComRetry(HttpClient client, HttpRequest request)
            throws IOException, InterruptedException {
        HttpResponse<String> response = null;
        for (int tentativa = 1; tentativa <= 3; tentativa++) {
            try {
                response = client.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (HttpTimeoutException e) {
                log.warn("[{}] timeout na tentativa {}, tentando de novo...", nome(), tentativa);
                continue;
            }
            if (response.statusCode() != 200 && RETRYAVEIS.contains(response.statusCode()) && tentativa < 3) {
                long esperaMs = 500L * tentativa; // backoff simples: 500ms, depois 1000ms
                log.warn("[{}] HTTP {} na tentativa {}, tentando de novo em {} ms...",
                        nome(), response.statusCode(), tentativa, esperaMs);
                Thread.sleep(esperaMs);
                continue;
            }
            return response;
        }
        // ultima tentativa (timeout na 3a vez): deixa estourar para o catch do metodo chamador
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String mensagemParaStatus(int status) {
        return switch (status) {
            case 400 -> "Requisicao invalida para " + nome() + " (confira o JSON/modelo).";
            case 401, 403 -> "Chave de API invalida ou sem permissao para " + nome() + ".";
            case 404 -> "Modelo nao encontrado em " + nome() + " (confira ia.*.modelo).";
            case 429 -> "Limite de uso ou credito esgotado em " + nome() + ".";
            case 500, 502, 503, 529 -> "Provedor " + nome() + " indisponivel no momento. Tente de novo.";
            default -> "Erro inesperado (" + status + ") em " + nome() + ".";
        };
    }

    protected static int intOuZero(JsonNode no, String campo) {
        return no != null && no.hasNonNull(campo) ? no.get(campo).asInt() : 0;
    }
}
