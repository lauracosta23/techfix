package br.com.senac.aula08.ia;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/**
 * Escolhe o provedor pelo nome ("claude", "openai", "gemini").
 *
 * O Spring injeta automaticamente TODOS os beans que implementam IaProvider
 * na lista do construtor - e assim que se adiciona um 4o provedor sem mexer
 * em nenhuma outra classe (principio Open/Closed).
 */
@Component
public class IaProviderFactory {

    private final Map<String, IaProvider> porNome;

    public IaProviderFactory(List<IaProvider> provedores) {
        this.porNome = provedores.stream()
                .collect(Collectors.toMap(IaProvider::nome, Function.identity()));
    }

    public IaProvider obter(String nome) {
        IaProvider p = porNome.get(nome == null ? "" : nome.toLowerCase().trim());
        if (p == null) {
            throw new IaException(400, "Provedor desconhecido: '" + nome
                    + "'. Use um destes: " + porNome.keySet());
        }
        return p;
    }

    public List<IaProvider> todos() {
        return List.copyOf(porNome.values());
    }
}
