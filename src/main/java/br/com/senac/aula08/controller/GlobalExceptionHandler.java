package br.com.senac.aula08.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import br.com.senac.aula08.ia.IaException;
import br.com.senac.aula08.service.UsuarioService.ConflitoException;

/** Erros viram ProblemDetail (RFC 7807). Nunca stack trace para o cliente. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ProblemDetail naoEncontrado(RecursoNaoEncontradoException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ConflitoException.class)
    public ProblemDetail conflito(ConflitoException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validacao(MethodArgumentNotValidException e) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setTitle("Dados de entrada invalidos");
        pd.setDetail(e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b).orElse("erro de validacao"));
        return pd;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail jsonInvalido(HttpMessageNotReadableException e) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setTitle("JSON invalido");
        pd.setDetail("Nao foi possivel ler o corpo da requisicao. Confira o JSON e a codificacao (UTF-8).");
        return pd;
    }

    /**
     * Incremento 4: o Spring corta o upload em spring.servlet.multipart.max-file-size
     * (8 MB) ANTES de o arquivo chegar ao nosso codigo. Sem este handler a resposta
     * seria um 500 com stack trace; com ele, o cliente recebe 413 com a explicacao.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail arquivoGrandeDemais(MaxUploadSizeExceededException e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE,
                "Imagem muito grande. O limite e 8 MB.");
        pd.setTitle("Arquivo acima do limite");
        return pd;
    }

    /** Incremento 4: chamou o endpoint da foto sem mandar o campo "imagem". */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ProblemDetail campoAusente(MissingServletRequestPartException e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Envie o campo '" + e.getRequestPartName() + "' como arquivo (multipart/form-data).");
        pd.setTitle("Campo obrigatorio ausente");
        return pd;
    }

    /**
     * Normalmente a IaException e tratada no Service (fallback). Se escapar
     * (ex.: provedor mal configurado), ainda assim vira uma resposta clara.
     */
    @ExceptionHandler(IaException.class)
    public ProblemDetail ia(IaException e) {
        HttpStatus status = switch (e.getStatusHttp()) {
            case 400 -> HttpStatus.BAD_REQUEST;
            case 413 -> HttpStatus.PAYLOAD_TOO_LARGE;
            case 415 -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            default -> HttpStatus.BAD_GATEWAY;
        };
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        pd.setTitle("Falha ao consultar o provedor de IA");
        pd.setProperty("statusDoProvedor", e.getStatusHttp());
        return pd;
    }
}
