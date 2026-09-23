package br.com.senac.aula08.service;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import br.com.senac.aula08.model.Ticket.Prioridade;

/**
 * INCREMENTO 3 (Aula 02 - Machine Learning): classificador "classico" por regras.
 *
 * E o baseline que sera comparado com a classificacao da IA generativa.
 * Aqui TODAS as regras sao escritas a mao pelo desenvolvedor - exatamente a
 * "programacao tradicional" da Aula 02 (SE o texto contem X ENTAO categoria Y).
 * A IA generativa faz a mesma tarefa sem regra nenhuma; o endpoint de metricas
 * mede em quantos % dos tickets os dois concordam.
 */
@Component
public class ClassificadorRegras {

    public static final List<String> CATEGORIAS = List.of("orcamento", "prazo", "garantia", "suporte", "outros");

    /**
     * Mapa categoria -> palavras-chave. LinkedHashMap porque a ORDEM importa:
     * em caso de empate na contagem, vence a primeira categoria declarada.
     * As palavras ja estao normalizadas (minusculas, sem acento) porque a
     * comparacao e feita sempre contra o texto normalizado.
     */
    private static final Map<String, List<String>> PALAVRAS_POR_CATEGORIA = new LinkedHashMap<>();
    static {
        PALAVRAS_POR_CATEGORIA.put("orcamento", List.of(
                "orcamento", "orcar", "quanto custa", "quanto fica", "quanto sai", "preco", "precos",
                "valor", "valores", "custa", "custo", "pagamento", "pagar", "parcelar", "parcelamento",
                "desconto", "cobra", "cobram", "cartao", "pix", "barato", "caro"));

        PALAVRAS_POR_CATEGORIA.put("prazo", List.of(
                "prazo", "demora", "demoram", "quanto tempo", "quando fica", "quando estara",
                "fica pronto", "ficar pronto", "entrega", "entregar", "retirar", "dias uteis",
                "horario", "abre", "fecha", "funcionamento"));

        PALAVRAS_POR_CATEGORIA.put("garantia", List.of(
                "garantia", "garantido", "nota fiscal", "cupom fiscal", "devolucao", "devolver",
                "troca do aparelho", "reembolso", "consertaram", "voltou a dar problema",
                "de novo o mesmo", "mesmo defeito", "assistencia anterior", "reparo coberto"));

        PALAVRAS_POR_CATEGORIA.put("suporte", List.of(
                "nao liga", "nao ligou", "nao carrega", "nao funciona", "parou de funcionar",
                "tela", "trincou", "quebrou", "quebrada", "rachada", "bateria", "descarrega",
                "formatar", "formatacao", "lento", "travando", "trava", "reiniciando", "desliga sozinho",
                "virus", "molhou", "caiu na agua", "agua", "teclado", "wifi", "sem som", "esquentando",
                "superaquecendo", "barulho", "tela azul", "nao instala", "sem imagem"));
    }

    /** Termos que indicam urgencia (prioridade ALTA). */
    private static final List<String> TERMOS_ALTA = List.of(
            "urgente", "urgencia", "hoje", "agora", "imediato", "imediatamente", "emergencia",
            "nao liga", "nao funciona", "parou de funcionar", "morreu", "nao carrega",
            "trabalho", "trabalhar", "prova", "faculdade", "para ontem", "socorro");

    /** Termos de incomodo, mas sem parar o uso (prioridade MEDIA). */
    private static final List<String> TERMOS_MEDIA = List.of(
            "amanha", "essa semana", "esta semana", "semana que vem", "lento", "lentidao",
            "travando", "trava", "esquentando", "esquenta", "as vezes", "de vez em quando",
            "falhando", "intermitente", "ruim", "piorando");

    /**
     * Devolve a categoria com MAIS palavras-chave encontradas no texto.
     * Nenhuma palavra encontrada -> "outros".
     */
    public String categoria(String texto) {
        String normalizado = normalizar(texto);
        String melhor = "outros";
        int maiorContagem = 0;

        for (Map.Entry<String, List<String>> entrada : PALAVRAS_POR_CATEGORIA.entrySet()) {
            int contagem = contarOcorrencias(normalizado, entrada.getValue());
            if (contagem > maiorContagem) {   // ">" e nao ">=": empate mantem a 1a categoria (ordem do mapa)
                maiorContagem = contagem;
                melhor = entrada.getKey();
            }
        }
        return melhor;
    }

    /** ALTA se houver termo de urgencia; senao MEDIA se houver termo de incomodo; senao BAIXA. */
    public Prioridade prioridade(String texto) {
        String normalizado = normalizar(texto);
        if (contarOcorrencias(normalizado, TERMOS_ALTA) > 0) {
            return Prioridade.ALTA;
        }
        if (contarOcorrencias(normalizado, TERMOS_MEDIA) > 0) {
            return Prioridade.MEDIA;
        }
        return Prioridade.BAIXA;
    }

    /** Conta quantas palavras-chave da lista aparecem no texto ja normalizado. */
    private int contarOcorrencias(String textoNormalizado, List<String> palavras) {
        int total = 0;
        for (String palavra : palavras) {
            if (textoNormalizado.contains(palavra)) {
                total++;
            }
        }
        return total;
    }

    /** Pronto: "  Vocês TROCAM tela?? " -> "voces trocam tela" */
    static String normalizar(String texto) {
        String semAcento = Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.toLowerCase().replaceAll("[^\\p{L}\\p{N}\\s]", " ").replaceAll("\\s+", " ").trim();
    }
}
