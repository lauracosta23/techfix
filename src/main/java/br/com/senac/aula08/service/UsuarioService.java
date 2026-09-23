package br.com.senac.aula08.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.senac.aula08.controller.RecursoNaoEncontradoException;
import br.com.senac.aula08.dto.Dtos.UsuarioRequest;
import br.com.senac.aula08.dto.Dtos.UsuarioResponse;
import br.com.senac.aula08.model.Usuario;
import br.com.senac.aula08.repository.UsuarioRepository;

@Service
public class UsuarioService {

    private final UsuarioRepository repo;

    public UsuarioService(UsuarioRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public UsuarioResponse cadastrar(UsuarioRequest req) {
        repo.findByEmailIgnoreCase(req.email()).ifPresent(u -> {
            throw new ConflitoException("Ja existe usuario com o e-mail " + req.email());
        });
        return UsuarioResponse.de(repo.save(new Usuario(req.nome().trim(), req.email().trim().toLowerCase())));
    }

    public List<UsuarioResponse> listar() {
        return repo.findAll().stream().map(UsuarioResponse::de).toList();
    }

    public Usuario buscarEntidade(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuario " + id + " nao encontrado"));
    }

    public static class ConflitoException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public ConflitoException(String m) { super(m); }
    }
}
