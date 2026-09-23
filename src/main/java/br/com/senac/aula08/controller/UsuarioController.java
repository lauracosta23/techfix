package br.com.senac.aula08.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.senac.aula08.dto.Dtos.TicketResponse;
import br.com.senac.aula08.dto.Dtos.UsuarioRequest;
import br.com.senac.aula08.dto.Dtos.UsuarioResponse;
import br.com.senac.aula08.service.TicketService;
import br.com.senac.aula08.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** PRONTO no kit: cadastro e listagem de usuarios (+ historico de tickets, Incremento 2). */
@RestController
@RequestMapping("/api/usuarios")
@Tag(name = "1. Usuarios")
public class UsuarioController {

    private final UsuarioService usuarios;
    private final TicketService tickets;

    public UsuarioController(UsuarioService usuarios, TicketService tickets) {
        this.usuarios = usuarios;
        this.tickets = tickets;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastra um usuario")
    public UsuarioResponse cadastrar(@Valid @RequestBody UsuarioRequest req) {
        return usuarios.cadastrar(req);
    }

    @GetMapping
    @Operation(summary = "Lista os usuarios")
    public List<UsuarioResponse> listar() {
        return usuarios.listar();
    }

    @GetMapping("/{id}/tickets")
    @Operation(summary = "Incremento 2 - tickets do usuario, mais recente primeiro")
    public List<TicketResponse> tickets(@PathVariable Long id) {
        return tickets.historico(id);
    }
}
