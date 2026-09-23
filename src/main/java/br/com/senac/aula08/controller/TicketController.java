package br.com.senac.aula08.controller;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import br.com.senac.aula08.dto.Dtos.MetricasResponse;
import br.com.senac.aula08.dto.Dtos.TicketRequest;
import br.com.senac.aula08.dto.Dtos.TicketResponse;
import br.com.senac.aula08.ia.IaException;
import br.com.senac.aula08.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Controller FINO: recebe, valida (@Valid), chama o Service e devolve o DTO.
 * Nenhuma regra de negocio, nenhuma chamada de IA e nenhum acesso ao banco aqui.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "2. Tickets")
public class TicketController {

    private final TicketService service;

    public TicketController(TicketService service) {
        this.service = service;
    }

    // ---------------------------------------------------- Incrementos 1, 2, 3 e 5
    @PostMapping("/tickets")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Abre um ticket e responde com a IA",
               description = "Consulta o cache antes de chamar a IA, classifica por regras e pela IA "
                           + "e grava o ticket. Se a IA falhar, devolve a resposta de fallback (ainda 201).")
    public TicketResponse abrir(@Valid @RequestBody TicketRequest req) {
        return service.abrirTicket(req.usuarioId(), req.texto());
    }

    // ---------------------------------------------------- Incremento 2
    @GetMapping("/tickets/{id}")
    @Operation(summary = "Consulta um ticket pelo id (404 se nao existir)")
    public TicketResponse buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    // ---------------------------------------------------- Incremento 4
    @PostMapping(value = "/tickets/{id}/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Anexa a foto do problema e grava a descricao gerada pela IA",
               description = "Aceita JPEG, PNG ou WEBP de ate 8 MB. "
                           + "Vazio = 400, grande demais = 413, tipo errado = 415.")
    public TicketResponse anexarFoto(@PathVariable Long id,
                                     @RequestPart("imagem") MultipartFile imagem) {
        return service.anexarFoto(id, lerBytes(imagem), imagem.getContentType());
    }

    // ---------------------------------------------------- Incremento 5
    @GetMapping("/metricas")
    @Operation(summary = "Metricas de uso: cache, fallback, tempos, tokens, prioridades e concordancia")
    public MetricasResponse metricas() {
        return service.metricas();
    }

    /** Unico "encanamento" do controller: transformar o arquivo enviado em bytes. */
    private byte[] lerBytes(MultipartFile arquivo) {
        try {
            return arquivo.getBytes();
        } catch (IOException e) {
            throw new IaException(400, "Nao foi possivel ler o arquivo enviado: " + e.getMessage());
        }
    }
}
