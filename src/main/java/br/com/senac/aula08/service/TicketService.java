package br.com.senac.aula08.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.senac.aula08.config.IaProperties;
import br.com.senac.aula08.controller.RecursoNaoEncontradoException;
import br.com.senac.aula08.dto.Dtos.MetricasResponse;
import br.com.senac.aula08.dto.Dtos.TicketResponse;
import br.com.senac.aula08.ia.IaException;
import br.com.senac.aula08.ia.IaProvider;
import br.com.senac.aula08.ia.IaProviderFactory;
import br.com.senac.aula08.ia.MensagemChat;
import br.com.senac.aula08.ia.RespostaIA;
import br.com.senac.aula08.model.Ticket;
import br.com.senac.aula08.model.Ticket.Origem;
import br.com.senac.aula08.model.Ticket.Prioridade;
import br.com.senac.aula08.model.Usuario;
import br.com.senac.aula08.repository.TicketRepository;

/**
 * =====================================================================
 *  AVALIACAO FINAL - "TechFix Inteligente"
 * =====================================================================
 *
 * Toda a regra de negocio mora aqui. O Controller so recebe e devolve JSON;
 * o Repository so consulta o banco; o pacote ia/ so fala HTTP com o provedor.
 *
 * O fluxo completo de abrirTicket() e:
 *
 *   usuario (404 se nao existir)
 *      -> normaliza o texto
 *      -> classifica por REGRAS (local, de graca)                 [Incremento 3]
 *      -> procura no CACHE                                        [Incremento 5]
 *           HIT  -> reaproveita a resposta, origem CACHE
 *           MISS -> monta o contexto das ultimas conversas
 *                   -> chama a IA com o SYSTEM_PROMPT             [Incremento 1]
 *                   -> classifica tambem pela IA                  [Incremento 3]
 *                   -> se a IA cair: FALLBACK, sem derrubar nada  [Incremento 5]
 *      -> salva o ticket                                          [Incremento 2]
 */
@Service
public class TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);

    /**
     * INCREMENTO 1 - a "personalidade" e os limites da assistente.
     * Define nome, empresa, escopo, tom e a frase EXATA de recusa fora do escopo.
     */
    static final String SYSTEM_PROMPT = """
            Voce e a Tec, assistente virtual da TechFix, uma assistencia tecnica de celulares
            e notebooks em Blumenau/SC.

            Tom: simpatica, objetiva e sem girias. Responda em portugues do Brasil,
            em no maximo 4 frases curtas.

            Voce SO responde sobre assuntos da TechFix:
              - servicos: troca de tela, troca de bateria, formatacao, limpeza, recuperacao de dados
              - prazos: reparos simples ficam prontos no mesmo dia; os demais em ate 3 dias uteis
              - horario de atendimento: segunda a sexta, das 8h as 18h
              - formas de pagamento: Pix, dinheiro e cartao em ate 3x sem juros
              - garantia: 90 dias sobre o servico executado, mediante apresentacao da ordem de servico
              - cuidados basicos com o aparelho

            NUNCA invente precos nem prazos exatos de um aparelho especifico: diga que o
            orcamento e gratuito e feito na loja, apos a avaliacao tecnica.

            Se a pergunta for de qualquer outro assunto, responda EXATAMENTE:
            "Desculpe, so consigo ajudar com assuntos da TechFix e com cuidados com o seu
            aparelho. Posso ajudar com algo nesse sentido?"
            """;

    static final String RESPOSTA_FALLBACK = "No momento nao consigo responder automaticamente. "
            + "Seu ticket foi registrado e nossa equipe vai retornar em breve.";

    /** INCREMENTO 4 - limites do upload da foto. */
    private static final long TAMANHO_MAXIMO_BYTES = 8L * 1024 * 1024;   // 8 MB
    private static final Set<String> TIPOS_ACEITOS = Set.of("image/jpeg", "image/jpg", "image/png", "image/webp");
    private static final String AVISO_ANEXO_INDISPONIVEL =
            "Analise da imagem indisponivel no momento; a foto foi recebida e sera avaliada por um tecnico.";

    private static final String PROMPT_FOTO = """
            Voce e um tecnico de assistencia tecnica de celulares e notebooks.
            Descreva em ate 3 frases o problema visivel nesta foto do aparelho do cliente:
            qual e o aparelho, qual o dano aparente e o quanto ele parece grave.
            Nao invente informacao que nao esteja na imagem e nao estime precos.
            Se a imagem nao mostrar um aparelho eletronico, responda apenas:
            "A imagem enviada nao mostra um aparelho."
            """;

    private final UsuarioService usuarios;
    private final TicketRepository tickets;
    private final CacheService cache;
    private final ClassificadorRegras regras;
    private final IaProviderFactory fabrica;
    private final IaProperties props;

    public TicketService(UsuarioService usuarios, TicketRepository tickets, CacheService cache,
                         ClassificadorRegras regras, IaProviderFactory fabrica, IaProperties props) {
        this.usuarios = usuarios;
        this.tickets = tickets;
        this.cache = cache;
        this.regras = regras;
        this.fabrica = fabrica;
        this.props = props;
    }

    // =====================================================================
    // INCREMENTOS 1, 2, 3 e 5 - abrir um ticket
    // =====================================================================
    @Transactional
    public TicketResponse abrirTicket(Long usuarioId, String texto) {

        // 1) Usuario tem de existir: se nao existir, buscarEntidade lanca
        //    RecursoNaoEncontradoException e o GlobalExceptionHandler devolve 404.
        Usuario usuario = usuarios.buscarEntidade(usuarioId);

        String textoLimpo = texto.trim();
        String normalizado = CacheService.normalizar(textoLimpo);
        Ticket ticket = new Ticket(usuario, textoLimpo, normalizado);

        // 2) Classificacao por REGRAS (Incremento 3): roda sempre, e local e nao custa nada.
        String categoriaRegras = regras.categoria(textoLimpo);
        Prioridade prioridade = regras.prioridade(textoLimpo);

        // 3) CACHE (Incremento 5): consultar ANTES de chamar a IA - e isso que evita a chamada.
        long inicioBusca = System.currentTimeMillis();
        Optional<Ticket> emCache = cache.buscar(normalizado);
        long tempoDaBusca = System.currentTimeMillis() - inicioBusca;

        if (emCache.isPresent()) {
            Ticket original = emCache.get();
            ticket.registrarResposta(original.getResposta(), Origem.CACHE,
                    original.getProvedor(), original.getModelo(),
                    0, 0,                                        // cache nao gasta token
                    tempoDaBusca);                               // ~5 ms contra 1-3 s da IA
            // reaproveita tambem a categoria que a IA ja tinha dado para esta mesma pergunta
            ticket.classificar(categoriaRegras, original.getCategoriaIa(), prioridade);
            return TicketResponse.de(tickets.save(ticket));
        }

        // 4) IA (Incremento 1) com o contexto das ultimas conversas do cliente.
        IaProvider ia = fabrica.obter(props.provedor());
        String categoriaIa = null;
        try {
            RespostaIA resposta = ia.conversar(montarHistorico(usuarioId, textoLimpo), SYSTEM_PROMPT);

            ticket.registrarResposta(resposta.texto(), Origem.IA, resposta.provedor(), resposta.modelo(),
                    resposta.tokensEntrada(), resposta.tokensSaida(), resposta.tempoMs());

            categoriaIa = classificarComIa(ia, textoLimpo);      // Incremento 3

        } catch (IaException e) {
            // 5) FALLBACK (Incremento 5): a IA caiu, o sistema NAO cai.
            //    O ticket fica registrado com origem FALLBACK - isso e auditoria e vira metrica.
            log.error("IA indisponivel (HTTP {}): {}", e.getStatusHttp(), e.getMessage());
            ticket.registrarResposta(RESPOSTA_FALLBACK, Origem.FALLBACK, ia.nome(), null, 0, 0, 0);
        }

        ticket.classificar(categoriaRegras, categoriaIa, prioridade);

        // 6) Persistencia (Incremento 2)
        return TicketResponse.de(tickets.save(ticket));
    }

    /**
     * INCREMENTO 3 - a mesma tarefa do ClassificadorRegras, agora pela IA generativa.
     * Prompt curto de proposito: menos tokens, resposta previsivel.
     * Se a IA falhar aqui, o ticket NAO pode quebrar - fica sem categoria da IA.
     */
    private String classificarComIa(IaProvider ia, String texto) {
        String prompt = """
                Classifique a mensagem do cliente em UMA destas categorias:
                orcamento, prazo, garantia, suporte, outros.
                Responda SOMENTE com a palavra da categoria, em minusculas, sem acento,
                sem pontuacao e sem nenhuma outra palavra.
                MENSAGEM: \"\"\"%s\"\"\"
                """.formatted(texto);
        try {
            String bruto = ia.perguntar(prompt).texto();
            String palavra = ClassificadorRegras.normalizar(bruto);
            if (palavra.contains(" ")) {                       // o modelo escreveu demais: fica a 1a palavra
                palavra = palavra.substring(0, palavra.indexOf(' '));
            }
            // "a IA sugere, o codigo garante": so aceitamos uma das categorias validas
            return ClassificadorRegras.CATEGORIAS.contains(palavra) ? palavra : "outros";
        } catch (IaException e) {
            log.warn("Nao foi possivel classificar pela IA (HTTP {}): {}", e.getStatusHttp(), e.getMessage());
            return null;
        }
    }

    /**
     * Contexto da conversa: as ultimas interacoes do cliente, da mais antiga para a
     * mais recente, e por ultimo a pergunta atual.
     *
     * Dois cuidados: o limite vem de ia.historico.tamanho (historico longo = tokens
     * de entrada = custo) e a PRIMEIRA mensagem tem de ser do usuario (exigencia do Claude).
     */
    private List<MensagemChat> montarHistorico(Long usuarioId, String perguntaAtual) {
        List<Ticket> anteriores = new ArrayList<>(tickets.findTop6ByUsuarioIdOrderByDataHoraDesc(usuarioId));
        Collections.reverse(anteriores);                       // vieram do mais novo para o mais velho

        int maximoDeTickets = Math.max(0, props.historico().tamanho() / 2);  // cada ticket = 2 mensagens
        if (anteriores.size() > maximoDeTickets) {
            anteriores = anteriores.subList(anteriores.size() - maximoDeTickets, anteriores.size());
        }

        List<MensagemChat> historico = new ArrayList<>();
        for (Ticket anterior : anteriores) {
            if (anterior.getResposta() == null || anterior.getResposta().isBlank()) {
                continue;                                      // sem resposta nao vira contexto
            }
            historico.add(MensagemChat.usuario(anterior.getTexto()));
            historico.add(MensagemChat.assistente(anterior.getResposta()));
        }
        historico.add(MensagemChat.usuario(perguntaAtual));
        return historico;
    }

    // =====================================================================
    // INCREMENTO 2 - historico e consulta por id
    // =====================================================================

    /** Tickets do usuario, do mais recente para o mais antigo. Usuario sem tickets = lista vazia. */
    public List<TicketResponse> historico(Long usuarioId) {
        usuarios.buscarEntidade(usuarioId);                    // 404 se o usuario nao existir
        return tickets.findByUsuarioIdOrderByDataHoraDesc(usuarioId).stream()
                .map(TicketResponse::de)
                .toList();
    }

    /** Um ticket pelo id; id inexistente -> 404 (ProblemDetail, nunca stack trace). */
    public TicketResponse buscar(Long id) {
        Ticket ticket = tickets.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Ticket " + id + " nao encontrado"));
        return TicketResponse.de(ticket);
    }

    // =====================================================================
    // INCREMENTO 4 - foto do problema (visao computacional, Aula 06)
    // =====================================================================
    @Transactional
    public TicketResponse anexarFoto(Long ticketId, byte[] imagem, String mimeType) {

        Ticket ticket = tickets.findById(ticketId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Ticket " + ticketId + " nao encontrado"));

        // Validar ANTES de gastar token: arquivo vazio, tamanho e tipo.
        if (imagem == null || imagem.length == 0) {
            throw new IaException(400, "Nenhuma imagem foi enviada. Use o campo 'imagem' do formulario.");
        }
        if (imagem.length > TAMANHO_MAXIMO_BYTES) {
            throw new IaException(413, "Imagem muito grande (" + (imagem.length / (1024 * 1024))
                    + " MB). O limite e 8 MB.");
        }
        String tipo = mimeType == null ? "" : mimeType.toLowerCase().trim();
        if (!TIPOS_ACEITOS.contains(tipo)) {
            throw new IaException(415, "Tipo de arquivo nao suportado: '" + mimeType
                    + "'. Envie JPEG, PNG ou WEBP.");
        }

        // A imagem viaja em base64 dentro do JSON - quem monta isso e o provider.
        try {
            String descricao = fabrica.obter(props.provedor())
                    .perguntarComImagem(imagem, tipo, PROMPT_FOTO);
            ticket.setDescricaoAnexo(descricao == null || descricao.isBlank()
                    ? AVISO_ANEXO_INDISPONIVEL : descricao.trim());
        } catch (IaException e) {
            // Mesma regra do texto: a IA pode falhar, o endpoint nao pode quebrar.
            log.error("Falha ao analisar a imagem (HTTP {}): {}", e.getStatusHttp(), e.getMessage());
            ticket.setDescricaoAnexo(AVISO_ANEXO_INDISPONIVEL);
        }

        return TicketResponse.de(tickets.save(ticket));
    }

    // =====================================================================
    // INCREMENTO 5 - metricas
    // =====================================================================
    public MetricasResponse metricas() {
        long total = tickets.count();
        long porIa = tickets.countByOrigem(Origem.IA);
        long porCache = tickets.countByOrigem(Origem.CACHE);
        long fallbacks = tickets.countByOrigem(Origem.FALLBACK);

        long classificadosPorAmbos = tickets.classificadosPorAmbos();
        long concordancias = tickets.concordanciasRegrasIa();

        return new MetricasResponse(
                total,
                porIa,
                porCache,
                fallbacks,
                percentual(porCache, total),
                arredondar(tickets.tempoMedioPorOrigem(Origem.IA)),
                arredondar(tickets.tempoMedioPorOrigem(Origem.CACHE)),
                tickets.totalTokens(),
                tickets.countByPrioridade(Prioridade.ALTA),
                tickets.countByPrioridade(Prioridade.MEDIA),
                tickets.countByPrioridade(Prioridade.BAIXA),
                percentual(concordancias, classificadosPorAmbos));
    }

    /** Percentual com 1 casa decimal; divisao por zero devolve 0 (e nao NaN). */
    private static double percentual(long parte, long total) {
        return total == 0 ? 0.0 : arredondar(parte * 100.0 / total);
    }

    private static double arredondar(double valor) {
        return Math.round(valor * 10.0) / 10.0;
    }
}
