package br.com.senac.aula08.controller;

public class RecursoNaoEncontradoException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
