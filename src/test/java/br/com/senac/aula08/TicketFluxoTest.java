package br.com.senac.aula08;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import br.com.senac.aula08.ia.IaException;
import br.com.senac.aula08.ia.IaProvider;
import br.com.senac.aula08.ia.MensagemChat;
import br.com.senac.aula08.ia.RespostaIA;
import br.com.senac.aula08.model.Ticket.Prioridade;
import br.com.senac.aula08.service.ClassificadorRegras;

/**
 * Testes de verificacao dos 6 incrementos.
 *
 * Nao chamam nenhuma API de verdade: o provedor "fake" abaixo substitui a IA,
 * entao os testes rodam offline, de graca e sempre com o mesmo resultado.
 * O provedor real continua no projeto - so nao e usado aqui (ia.provedor=fake).
 */
@SpringBootTest(properties = {
        "ia.provedor=fake",
        "ia.cache.ativo=true",
        "ia.cache.validade-minutos=60"
})
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TicketFluxoTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ClassificadorRegras regras;

    /** Liga/desliga a "queda" da IA dentro de um teste. */
    static boolean iaForaDoAr = false;

    // ------------------------------------------------------------------ provedor simulado
    @TestConfiguration
    static class ProvedorFakeConfig {
        @Bean
        IaProvider provedorFake() {
            return new IaProvider() {
                @Override
                public String nome() {
                    return "fake";
                }

                @Override
                public RespostaIA conversar(List<MensagemChat> historico, String systemPrompt) {
                    if (iaForaDoAr) {
                        throw new IaException(503, "Provedor indisponivel (simulado)");
                    }
                    String ultima = historico.get(historico.size() - 1).conteudo();
                    // Se for o prompt de classificacao, devolve so a palavra (como pede o prompt).
                    if (ultima.startsWith("Classifique a mensagem")) {
                        return new RespostaIA("fake", "fake-1", "suporte", 10, 1, 5);
                    }
                    return new RespostaIA("fake", "fake-1",
                            "Resposta simulada da Tec para: " + ultima, 42, 17, 1234);
                }

                @Override
                public String perguntarComImagem(byte[] imagem, String mimeType, String prompt) {
                    if (iaForaDoAr) {
                        throw new IaException(503, "Provedor indisponivel (simulado)");
                    }
                    return "Notebook com a tela trincada no canto inferior direito. Dano aparente moderado.";
                }
            };
        }
    }

    // ================================================================== Incremento 3 (unitario)
    @Test
    @Order(1)
    void classificadorDeRegrasSegueOsExemplosDoEnunciado() {
        Assertions.assertEquals("suporte", regras.categoria("Meu notebook nao liga, e urgente!"));
        Assertions.assertEquals(Prioridade.ALTA, regras.prioridade("Meu notebook nao liga, e urgente!"));

        Assertions.assertEquals("orcamento", regras.categoria("Quanto custa trocar a bateria?"));
        Assertions.assertEquals(Prioridade.BAIXA, regras.prioridade("Quanto custa trocar a bateria?"));

        Assertions.assertEquals("prazo", regras.categoria("Qual o prazo de entrega do conserto?"));
        Assertions.assertEquals("garantia", regras.categoria("O aparelho ainda esta na garantia?"));
        Assertions.assertEquals("outros", regras.categoria("Bom dia!"));
    }

    // ================================================================== Incrementos 1, 2 e 3
    @Test
    @Order(2)
    void abrirTicketChamaAIaClassificaEPersiste() throws Exception {
        criarUsuario("Maria Silva", "maria@email.com");

        mvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON)
                .content("{\"usuarioId\":1,\"texto\":\"Meu notebook nao liga, e urgente, preciso para trabalhar!\"}"))
            .andExpect(status().isCreated())                                   // Incremento 1
            .andExpect(jsonPath("$.id", is(1)))
            .andExpect(jsonPath("$.origem", is("IA")))
            .andExpect(jsonPath("$.resposta", containsString("Resposta simulada")))
            .andExpect(jsonPath("$.tokensEntrada", is(42)))                    // Incremento 2: gravou tokens
            .andExpect(jsonPath("$.tempoMs", is(1234)))
            .andExpect(jsonPath("$.categoriaRegras", is("suporte")))           // Incremento 3
            .andExpect(jsonPath("$.categoriaIa", is("suporte")))
            .andExpect(jsonPath("$.prioridade", is("ALTA")));

        // Incremento 2: consulta por id e historico do usuario
        mvc.perform(get("/api/tickets/1")).andExpect(status().isOk())
            .andExpect(jsonPath("$.usuarioId", is(1)));

        mvc.perform(get("/api/usuarios/1/tickets")).andExpect(status().isOk())
            .andExpect(jsonPath("$.length()", is(1)));
    }

    @Test
    @Order(3)
    void ticketInexistenteDevolve404EUsuarioInexistenteTambem() throws Exception {
        mvc.perform(get("/api/tickets/9999")).andExpect(status().isNotFound());

        mvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON)
                .content("{\"usuarioId\":9999,\"texto\":\"teste\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    @Order(4)
    void textoVazioNaoPassaNaValidacao() throws Exception {
        mvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON)
                .content("{\"usuarioId\":1,\"texto\":\"   \"}"))
            .andExpect(status().isBadRequest());                               // Incremento 6: @Valid
    }

    // ================================================================== Incremento 5 - cache
    @Test
    @Order(5)
    void mesmaPerguntaComAcentoEMaiusculaBateNoCache() throws Exception {
        mvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON)
                .content("{\"usuarioId\":1,\"texto\":\"Voces trocam tela de celular?\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.origem", is("IA")));

        // mesma pergunta, escrita de outro jeito -> texto normalizado igual -> CACHE
        mvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON)
                .content("{\"usuarioId\":1,\"texto\":\"VOCÊS TROCAM TELA DE CELULAR!!!\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.origem", is("CACHE")))
            .andExpect(jsonPath("$.tokensEntrada", is(0)))                     // cache nao gasta token
            .andExpect(jsonPath("$.resposta", containsString("Resposta simulada")));
    }

    // ================================================================== Incremento 5 - fallback
    @Test
    @Order(6)
    void quandoAIaCaiOTicketEGravadoComFallbackEOEndpointContinua201() throws Exception {
        iaForaDoAr = true;
        try {
            mvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"usuarioId\":1,\"texto\":\"O computador esta fazendo barulho estranho\"}"))
                .andExpect(status().isCreated())                               // nao virou 500 nem 503
                .andExpect(jsonPath("$.origem", is("FALLBACK")))
                .andExpect(jsonPath("$.resposta", containsString("nossa equipe vai retornar")))
                .andExpect(jsonPath("$.categoriaRegras", is("suporte")));       // regras continuam funcionando
        } finally {
            iaForaDoAr = false;
        }
    }

    // ================================================================== Incremento 4 - foto
    @Test
    @Order(7)
    void fotoValidaGravaADescricaoEArquivoErradoDevolve415() throws Exception {
        MockMultipartFile png = new MockMultipartFile("imagem", "tela.png", "image/png", new byte[]{1, 2, 3, 4});
        mvc.perform(multipart("/api/tickets/1/foto").file(png))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.descricaoAnexo", containsString("tela trincada")));

        MockMultipartFile xml = new MockMultipartFile("imagem", "pom.xml", "application/xml", new byte[]{1, 2});
        mvc.perform(multipart("/api/tickets/1/foto").file(xml))
            .andExpect(status().isUnsupportedMediaType());                     // 415

        MockMultipartFile vazio = new MockMultipartFile("imagem", "vazio.png", "image/png", new byte[0]);
        mvc.perform(multipart("/api/tickets/1/foto").file(vazio))
            .andExpect(status().isBadRequest());                               // 400

        mvc.perform(multipart("/api/tickets/9999/foto").file(png))
            .andExpect(status().isNotFound());                                 // 404
    }

    @Test
    @Order(8)
    void seAIaFalharNaImagemOEndpointNaoQuebra() throws Exception {
        iaForaDoAr = true;
        try {
            MockMultipartFile png = new MockMultipartFile("imagem", "t.png", "image/png", new byte[]{9, 9, 9});
            mvc.perform(multipart("/api/tickets/1/foto").file(png))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descricaoAnexo", containsString("indisponivel")));
        } finally {
            iaForaDoAr = false;
        }
    }

    // ================================================================== Incremento 5 - metricas
    @Test
    @Order(9)
    void metricasSaoCoerentes() throws Exception {
        mvc.perform(get("/api/metricas")).andExpect(status().isOk())
            .andExpect(jsonPath("$.totalTickets", greaterThanOrEqualTo(4)))
            .andExpect(jsonPath("$.respondidosPeloCache", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.fallbacks", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.taxaCacheHitPercentual", greaterThanOrEqualTo(0.0)))
            .andExpect(jsonPath("$.totalTokens", greaterThanOrEqualTo(0)))
            .andExpect(jsonPath("$.prioridadeAlta", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.concordanciaRegrasIaPercentual", greaterThanOrEqualTo(0.0)));
    }

    // ------------------------------------------------------------------ apoio
    private void criarUsuario(String nome, String email) throws Exception {
        mvc.perform(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"" + nome + "\",\"email\":\"" + email + "\"}"))
            .andExpect(status().isCreated());
    }
}
