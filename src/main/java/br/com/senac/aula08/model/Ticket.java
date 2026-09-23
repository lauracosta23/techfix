package br.com.senac.aula08.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Ticket de atendimento da TechFix: a pergunta do cliente, a resposta da IA,
 * a classificacao (categoria + prioridade) e, opcionalmente, a descricao da
 * foto do problema anexada.
 */
@Entity
@Table(name = "ticket", indexes = {
        @Index(name = "idx_ticket_usuario", columnList = "usuario_id"),
        @Index(name = "idx_ticket_normalizado", columnList = "texto_normalizado")
})
public class Ticket {

    public enum Origem { IA, CACHE, FALLBACK }

    public enum Prioridade { BAIXA, MEDIA, ALTA }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(nullable = false, length = 4000)
    private String texto;

    @Column(name = "texto_normalizado", nullable = false, length = 4000)
    private String textoNormalizado;

    @Column(length = 8000)
    private String resposta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Origem origem = Origem.IA;

    @Column(length = 30)
    private String provedor;

    @Column(length = 60)
    private String modelo;

    private int tokensEntrada;
    private int tokensSaida;
    private long tempoMs;

    // ---- Incremento 3: classificacao
    @Column(length = 30)
    private String categoriaRegras;

    @Column(length = 30)
    private String categoriaIa;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Prioridade prioridade;

    // ---- Incremento 4: visao computacional
    @Column(name = "descricao_anexo", length = 2000)
    private String descricaoAnexo;

    @Column(name = "data_hora", nullable = false)
    private LocalDateTime dataHora = LocalDateTime.now();

    protected Ticket() {}

    public Ticket(Usuario usuario, String texto, String textoNormalizado) {
        this.usuario = usuario;
        this.texto = texto;
        this.textoNormalizado = textoNormalizado;
    }

    public void registrarResposta(String resposta, Origem origem, String provedor, String modelo,
                                  int tokensEntrada, int tokensSaida, long tempoMs) {
        this.resposta = resposta;
        this.origem = origem;
        this.provedor = provedor;
        this.modelo = modelo;
        this.tokensEntrada = tokensEntrada;
        this.tokensSaida = tokensSaida;
        this.tempoMs = tempoMs;
    }

    public void classificar(String categoriaRegras, String categoriaIa, Prioridade prioridade) {
        this.categoriaRegras = categoriaRegras;
        this.categoriaIa = categoriaIa;
        this.prioridade = prioridade;
    }

    public Long getId() { return id; }
    public Usuario getUsuario() { return usuario; }
    public String getTexto() { return texto; }
    public String getTextoNormalizado() { return textoNormalizado; }
    public String getResposta() { return resposta; }
    public Origem getOrigem() { return origem; }
    public String getProvedor() { return provedor; }
    public String getModelo() { return modelo; }
    public int getTokensEntrada() { return tokensEntrada; }
    public int getTokensSaida() { return tokensSaida; }
    public long getTempoMs() { return tempoMs; }
    public String getCategoriaRegras() { return categoriaRegras; }
    public String getCategoriaIa() { return categoriaIa; }
    public Prioridade getPrioridade() { return prioridade; }
    public String getDescricaoAnexo() { return descricaoAnexo; }
    public void setDescricaoAnexo(String descricaoAnexo) { this.descricaoAnexo = descricaoAnexo; }
    public LocalDateTime getDataHora() { return dataHora; }
}
