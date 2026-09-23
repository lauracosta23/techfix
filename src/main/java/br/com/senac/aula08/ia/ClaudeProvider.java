package br.com.senac.aula08.ia;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import br.com.senac.aula08.config.IaProperties;

/**
 * Anthropic Claude - POST https://api.anthropic.com/v1/messages
 *
 * Particularidades (compare com os outros dois):
 *   - autenticacao pelo header "x-api-key" (nao e Bearer)
 *   - header obrigatorio "anthropic-version"
 *   - "max_tokens" e OBRIGATORIO
 *   - system prompt vai no campo "system" de nivel superior (nao dentro de messages)
 *   - o texto volta em content[0].text
 *
 * Chave: variavel de ambiente ANTHROPIC_API_KEY (console.anthropic.com).
 */
@Component
public class ClaudeProvider extends ProviderBase {

    private static final String ENDPOINT = "https://api.anthropic.com/v1/messages";

    private final IaProperties props;

    public ClaudeProvider(ObjectMapper mapper, IaProperties props) {
        super(mapper, props.timeoutSegundos());
        this.props = props;
    }

    @Override
    public String nome() {
        return "claude";
    }

    @Override
    public RespostaIA conversar(List<MensagemChat> historico, String systemPrompt) {
        String apiKey = chave("ANTHROPIC_API_KEY");
        String modelo = props.claude().modelo();

        ObjectNode corpo = mapper.createObjectNode();
        corpo.put("model", modelo);
        corpo.put("max_tokens", props.maxTokens());
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            corpo.put("system", systemPrompt);
        }
        ArrayNode messages = corpo.putArray("messages");
        for (MensagemChat m : historico) {
            ObjectNode no = messages.addObject();
            no.put("role", m.papel() == MensagemChat.Papel.USUARIO ? "user" : "assistant");
            no.put("content", m.conteudo());
        }

        HttpRequest.Builder req = HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT))
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01");

        long inicio = System.currentTimeMillis();
        JsonNode raiz = postJson(req, corpo);
        long tempo = System.currentTimeMillis() - inicio;

        String texto = raiz.path("content").path(0).path("text").asText("");
        JsonNode usage = raiz.get("usage");

        return new RespostaIA(nome(), modelo, texto,
                intOuZero(usage, "input_tokens"),
                intOuZero(usage, "output_tokens"),
                tempo);
    }
    @Override
    public String perguntarComImagem(byte[] imagem, String mimeType, String prompt) {
        String apiKey = chave("ANTHROPIC_API_KEY");
        ObjectNode corpo = mapper.createObjectNode();
        corpo.put("model", props.claude().modelo());
        corpo.put("max_tokens", props.maxTokens());
        ObjectNode msg = corpo.putArray("messages").addObject().put("role", "user");
        ArrayNode content = msg.putArray("content");
        ObjectNode source = content.addObject().put("type", "image").putObject("source");
        source.put("type", "base64");
        source.put("media_type", mimeType);
        source.put("data", java.util.Base64.getEncoder().encodeToString(imagem));
        content.addObject().put("type", "text").put("text", prompt);

        HttpRequest.Builder req = HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT))
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01");
        return postJson(req, corpo).path("content").path(0).path("text").asText("");
    }
}
