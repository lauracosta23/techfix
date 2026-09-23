package br.com.senac.aula08.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.senac.aula08.model.Usuario;

/** Spring Data JPA gera o SQL a partir do nome do metodo. */
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmailIgnoreCase(String email);
}
