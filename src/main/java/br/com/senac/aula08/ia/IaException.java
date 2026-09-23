package br.com.senac.aula08.ia;

/**
 * Erro "amigavel" vindo de um provedor de IA. O status HTTP original e
 * guardado para o GlobalExceptionHandler devolver algo util ao cliente
 * (401 chave invalida, 429 limite, 503 provedor fora, etc.) - nunca um
 * stack trace cru.
 */
public class IaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int statusHttp;

    public IaException(int statusHttp, String mensagem) {
        super(mensagem);
        this.statusHttp = statusHttp;
    }

    public int getStatusHttp() {
        return statusHttp;
    }
}
