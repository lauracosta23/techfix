package br.com.senac.aula08.dto;

import java.time.LocalDateTime;

import br.com.senac.aula08.model.Ticket;
import br.com.senac.aula08.model.Usuario;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class Dtos {

    private Dtos() {}

    // ------------------------------------------------------------ Usuario (pronto)
    public record UsuarioRequest(
            @Schema(example = "Maria Silva") @NotBlank @Size(max = 120) String nome,
            @Schema(example = "maria@email.com") @NotBlank @Email @Size(max = 160) String email) {}

    public record UsuarioResponse(Long id, String nome, String email, LocalDateTime criadoEm) {
        public static UsuarioResponse de(Usuario u) {
            return new UsuarioResponse(u.getId(), u.getNome(), u.getEmail(), u.getCriadoEm());
        }
    }

    // ------------------------------------------------------------ Ticket
    public record TicketRequest(
            @Schema(example = "1") @NotNull Long usuarioId,
            @Schema(example = "Meu notebook nao liga mais desde ontem, e urgente, preciso dele para trabalhar!")
            @NotBlank @Size(max = 4000) String texto) {}

    public record TicketResponse(
            Long id,
            Long usuarioId,
            String texto,
            String resposta,
            Ticket.Origem origem,
            String provedor,
            String modelo,
            int tokensEntrada,
            int tokensSaida,
            long tempoMs,
            String categoriaRegras,
            String categoriaIa,
            Ticket.Prioridade prioridade,
            String descricaoAnexo,
            LocalDateTime dataHora) {

        public static TicketResponse de(Ticket t) {
            return new TicketResponse(t.getId(), t.getUsuario().getId(), t.getTexto(), t.getResposta(),
                    t.getOrigem(), t.getProvedor(), t.getModelo(), t.getTokensEntrada(), t.getTokensSaida(),
                    t.getTempoMs(), t.getCategoriaRegras(), t.getCategoriaIa(), t.getPrioridade(),
                    t.getDescricaoAnexo(), t.getDataHora());
        }
    }

    // ------------------------------------------------------------ Incremento 5
    public record MetricasResponse(
            long totalTickets,
            long respondidosPelaIa,
            long respondidosPeloCache,
            long fallbacks,
            double taxaCacheHitPercentual,
            double tempoMedioIaMs,
            double tempoMedioCacheMs,
            long totalTokens,
            long prioridadeAlta,
            long prioridadeMedia,
            long prioridadeBaixa,
            double concordanciaRegrasIaPercentual) {}
}
