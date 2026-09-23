package br.com.senac.aula08.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import br.com.senac.aula08.model.Ticket;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    List<Ticket> findByUsuarioIdOrderByDataHoraDesc(Long usuarioId);

    List<Ticket> findTop6ByUsuarioIdOrderByDataHoraDesc(Long usuarioId);

    Optional<Ticket> findFirstByTextoNormalizadoAndOrigemAndDataHoraAfterOrderByDataHoraDesc(
            String textoNormalizado, Ticket.Origem origem, LocalDateTime desde);

    long countByOrigem(Ticket.Origem origem);

    long countByPrioridade(Ticket.Prioridade prioridade);

    @Query("select coalesce(avg(t.tempoMs), 0) from Ticket t where t.origem = :origem")
    double tempoMedioPorOrigem(Ticket.Origem origem);

    @Query("select coalesce(sum(t.tokensEntrada + t.tokensSaida), 0) from Ticket t")
    long totalTokens();

    /** Quantos tickets tiveram a MESMA categoria pelas regras e pela IA (concordancia). */
    @Query("select count(t) from Ticket t where t.categoriaRegras is not null and t.categoriaRegras = t.categoriaIa")
    long concordanciasRegrasIa();

    @Query("select count(t) from Ticket t where t.categoriaRegras is not null and t.categoriaIa is not null")
    long classificadosPorAmbos();
}
