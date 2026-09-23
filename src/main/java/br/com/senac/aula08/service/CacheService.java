package br.com.senac.aula08.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import br.com.senac.aula08.config.IaProperties;
import br.com.senac.aula08.model.Ticket;
import br.com.senac.aula08.repository.TicketRepository;

/**
 * INCREMENTO 5 (Aula 07): cache de respostas.
 *
 * Por que existe: chamar a IA de novo para a MESMA pergunta custa dinheiro
 * (tokens) e tempo (1-3 s contra ~5 ms do banco). Em atendimento as perguntas
 * se repetem muito ("qual o horario?", "voces trocam tela?").
 *
 * O cache mora no proprio banco - sobrevive a restart e ja fica auditavel.
 * Redis ficaria para um passo seguinte.
 */
@Service
public class CacheService {

    private static final Logger log = LoggerFactory.getLogger(CacheService.class);

    private final TicketRepository repo;
    private final IaProperties props;

    public CacheService(TicketRepository repo, IaProperties props) {
        this.repo = repo;
        this.props = props;
    }

    public boolean ativo() {
        return props.cache().ativo();
    }

    /**
     * Procura a ULTIMA resposta real (origem IA) para o mesmo texto normalizado,
     * dentro da validade configurada em ia.cache.validade-minutos.
     *
     * Detalhe importante: so vale como cache um ticket de origem IA. Assim nao
     * se encadeia cache em cima de cache, nem se reaproveita uma resposta de
     * FALLBACK (que nao e resposta de verdade).
     */
    public Optional<Ticket> buscar(String textoNormalizado) {
        if (!ativo()) {
            log.info("CACHE DESLIGADO (ia.cache.ativo=false)");
            return Optional.empty();
        }

        LocalDateTime desde = LocalDateTime.now().minusMinutes(props.cache().validadeMinutos());

        Optional<Ticket> hit = repo.findFirstByTextoNormalizadoAndOrigemAndDataHoraAfterOrderByDataHoraDesc(
                textoNormalizado, Ticket.Origem.IA, desde);

        log.info(hit.isPresent() ? "CACHE HIT  -> '{}'" : "CACHE MISS -> '{}'", textoNormalizado);
        return hit;
    }

    /** Criterio de "mesma pergunta": minusculas, sem acento, sem pontuacao, espacos unicos. */
    public static String normalizar(String texto) {
        return ClassificadorRegras.normalizar(texto);
    }
}
