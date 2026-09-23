# TechFix Inteligente — Avaliação Final do Módulo 10

Sistema de tickets de uma assistência técnica fictícia (TechFix, Blumenau/SC), em Spring Boot,
que usa IA generativa para responder o cliente, classificar o chamado e descrever a foto do
problema — com persistência em banco, cache, fallback e métricas.

---

## Como rodar

1. **Importar no Eclipse:** `File > Import > Maven > Existing Maven Projects` → esta pasta.
2. **Configurar a chave** (nunca no código): `Run > Run Configurations > Environment > New...`
   - `Name`: `GEMINI_API_KEY` · `Value`: a chave gerada em <https://aistudio.google.com/apikey>
   - Para trocar de provedor, mude `ia.provedor` no `application.properties` para `claude` ou
     `openai` e configure `ANTHROPIC_API_KEY` ou `OPENAI_API_KEY`.
3. **Rodar** `Aula08Application` e abrir:
   - Página web: <http://localhost:8080/>
   - Swagger UI: <http://localhost:8080/swagger-ui.html>
   - H2 console: <http://localhost:8080/h2-console> (JDBC URL `jdbc:h2:mem:techfix`, user `sa`, senha vazia)

Banco **H2 em memória** por padrão (não precisa instalar nada).
**MySQL:** `--spring.profiles.active=mysql` (ver `application-mysql.properties`; criar antes o banco
com `CREATE DATABASE techfix CHARACTER SET utf8mb4;`).

### Variáveis de ambiente

| Variável | Obrigatória | Para quê |
|---|---|---|
| `GEMINI_API_KEY` | sim (provedor padrão) | chamadas de texto e de imagem ao Gemini |
| `ANTHROPIC_API_KEY` | só se `ia.provedor=claude` | idem, no Claude |
| `OPENAI_API_KEY` | só se `ia.provedor=openai` | idem, no ChatGPT |
| `MYSQL_USER` / `MYSQL_PASSWORD` | só no profile `mysql` | acesso ao banco |

---

## Endpoints

| Método | Rota | O que faz | Incremento |
|---|---|---|---|
| `POST` | `/api/usuarios` | cadastra usuário (409 se o e-mail repetir) | kit |
| `GET` | `/api/usuarios` | lista usuários | kit |
| `GET` | `/api/usuarios/{id}/tickets` | histórico do usuário, mais recente primeiro | 2 |
| `POST` | `/api/tickets` | abre o ticket: cache → IA → fallback, classifica e grava (**201**) | 1, 2, 3, 5 |
| `GET` | `/api/tickets/{id}` | consulta um ticket (404 se não existir) | 2 |
| `POST` | `/api/tickets/{id}/foto` | envia a foto (multipart, campo `imagem`) e grava a descrição | 4 |
| `GET` | `/api/metricas` | cache, fallback, tempos, tokens, prioridades, concordância | 5 |

**Exemplo:**

```bash
curl -X POST http://localhost:8080/api/usuarios \
  -H "content-type: application/json" \
  -d '{"nome":"Maria Silva","email":"maria@email.com"}'

curl -X POST http://localhost:8080/api/tickets \
  -H "content-type: application/json" \
  -d '{"usuarioId":1,"texto":"Meu notebook nao liga, e urgente, preciso para trabalhar!"}'

curl -X POST http://localhost:8080/api/tickets/1/foto -F "imagem=@tela-trincada.png"

curl http://localhost:8080/api/metricas
```

---

## Arquitetura (onde cada coisa mora)

| Camada | Classe | Responsabilidade | O que **não** faz |
|---|---|---|---|
| Controller | `TicketController`, `UsuarioController` | receber JSON, `@Valid`, chamar o Service, devolver DTO | regra de negócio, IA, banco |
| Service | `TicketService`, `CacheService`, `ClassificadorRegras`, `UsuarioService` | cache → contexto → IA → fallback → salvar | conhecer HTTP (status, headers) |
| Repository | `TicketRepository`, `UsuarioRepository` | consultas (Spring Data gera o SQL) | regra de negócio |
| `ia/` | `IaProvider`, `ProviderBase`, `Claude/OpenAi/GeminiProvider` | falar HTTP com o provedor e traduzir erros | saber o que é um "ticket" |
| DTO × Entidade | `Dtos` × `Ticket`, `Usuario` | DTO é o contrato da API; entidade é a tabela | a entidade **nunca** sai no JSON |

**Fluxo de `abrirTicket()`:** valida o usuário (404) → normaliza o texto → classifica por regras →
consulta o cache → se MISS, monta o contexto das últimas conversas e chama a IA → classifica também
pela IA → em caso de `IaException`, grava `FALLBACK` → salva o ticket.

---

## Nota de ética / LGPD (Incremento 6)

**Por que este sistema não faz reconhecimento facial.** O rosto é dado biométrico e, pela LGPD
(art. 5º, II), dado pessoal **sensível**: exige base legal específica, finalidade declarada e,
na prática, consentimento explícito do titular. Para consertar um aparelho não existe necessidade
alguma de identificar o rosto de quem quer que seja — então a feature `FACE_DETECTION` não é usada
**de propósito**. O sistema também não pede documento com foto, CPF nem endereço: guarda apenas
nome e e-mail, que é o mínimo para o atendimento (princípio da necessidade, art. 6º, III).

**O que acontece com as fotos.** A foto do problema é validada (tipo e tamanho), convertida em
base64 e enviada ao provedor de IA apenas para gerar a descrição técnica. **A imagem não é gravada
no banco** — só o texto da descrição fica no campo `descricao_anexo` do ticket. Assim o sistema não
acumula um acervo de imagens de clientes, que seria um passivo em caso de vazamento.

**Provedor externo e transferência internacional.** As chamadas vão para Google (Gemini), Anthropic
(Claude) ou OpenAI, com servidores fora do Brasil. Isso é transferência internacional de dados
(art. 33) e **precisa ser informado ao cliente** antes do envio — na tela do site, no termo de
serviço e na ordem de serviço. Por isso o cliente deve ser orientado a fotografar **apenas o
aparelho**, nunca documentos, telas com dados pessoais ou terceiros.

**Retenção e logs.** Retenção mínima: tickets ficam pelo prazo do atendimento e da garantia
(90 dias sobre o serviço) e depois são descartados. O texto extraído/descrito **não é gravado em
log**, para não duplicar dado pessoal em arquivos sem controle de acesso. O cache tem validade
configurável (`ia.cache.validade-minutos`) justamente para não reaproveitar respostas velhas.

**Viés e supervisão humana.** Modelos erram mais com pessoas e contextos sub-representados no
treino, e alucinam com confiança. Por isso o system prompt proíbe inventar preços e prazos, a
categoria sugerida pela IA é validada no código contra uma lista fechada, e a resposta é sempre
um **apoio** ao técnico — a decisão sobre o conserto continua sendo humana.

**Segredos.** Nenhuma chave de API está no código ou em arquivo versionado: todas vêm de variável
de ambiente, e o `.gitignore` do projeto cobre os arquivos locais.

---

## Autoavaliação

1. **O que eu já consigo fazer sozinha depois deste módulo?** Montar uma API REST em Spring Boot
   organizada em camadas, integrar as APIs de Claude, ChatGPT e Gemini (texto e imagem) com
   `HttpClient` + Jackson, persistir com JPA e tratar falhas do provedor sem derrubar o sistema.
2. **Qual incremento foi o mais difícil e por quê?** O 5 — não pela quantidade de código, mas
   porque exige pensar em ordem: o cache só economiza se for consultado **antes** da IA, e o
   fallback só serve se o ticket for gravado com `origem = FALLBACK` para virar métrica depois.
3. **O que quero estudar a seguir?** Cache com Redis, testes automatizados da camada de IA
   (com provedor simulado) e autenticação/autorização da API.

---

## Publicação (deploy)

O projeto é um **monolito**: o site em `src/main/resources/static` é servido pelo
próprio Spring Boot, na mesma porta da API. Por isso **um único serviço publica
site, API e Swagger** — não existem dois deploys, e não há necessidade de CORS,
porque o navegador vê tudo na mesma origem.

### Plataforma

**Render**, via **Docker**. O Render não tem ambiente nativo de Java (os nativos são
Node, Python, Ruby, Go, Rust e Elixir), então aplicações Java são publicadas por
`Dockerfile` — que é o caminho oficial e documentado da plataforma.

### Arquivos de deploy

| Arquivo | Para que serve |
|---|---|
| `Dockerfile` | Build em dois estágios: Maven compila, JRE 17 executa |
| `.dockerignore` | Mantém `target/`, `.git/` e segredos fora da imagem |
| `render.yaml` | Blueprint: plano, região, health check e variáveis |
| `.env.example` | Nomes das variáveis necessárias, **sem valores** |

### Porta

`server.port=${PORT:8081}` — em produção a plataforma injeta `PORT`; na máquina
de desenvolvimento cai no padrão **8081**. Nada precisa ser alterado para rodar local.

### Variáveis de ambiente

| Variável | Obrigatória | Observação |
|---|---|---|
| `GEMINI_API_KEY` | sim | Sem ela, todo chamado responde em modo *fallback* |
| `PORT` | não | Injetada pela plataforma |
| `H2_CONSOLE` | não | `false` em produção — ver aviso abaixo |
| `SHOW_SQL` | não | `false` em produção, para não inundar o log |
| `ANTHROPIC_API_KEY` / `OPENAI_API_KEY` | não | Só se `ia.provedor` for alterado |
| `MYSQL_USER` / `MYSQL_PASSWORD` | não | Só no profile `mysql` |

**Nenhum valor secreto está neste repositório.** As chaves são informadas no painel
da plataforma e o `.gitignore` cobre `.env` e derivados.

### Por que o console do H2 fica desligado em produção

O console do H2 permite executar SQL e abrir conexões JDBC arbitrárias pelo navegador.
Em um servidor público, isso é uma porta aberta — e o processo carrega a chave da IA
em variável de ambiente. Por isso `H2_CONSOLE=false` no `render.yaml`. Localmente
continua ligado, sem nenhuma mudança de comportamento.

### Banco de dados em produção

H2 **em memória**, rodando no próprio servidor. O Hibernate cria as tabelas a partir
das entidades (`spring.jpa.hibernate.ddl-auto=update`); não há `schema.sql` nem
`data.sql`. Os dados são reiniciados quando o serviço reinicia — adequado para
demonstração, em que cada visitante cria os próprios registros.

Para persistência real, o projeto já traz o profile `mysql`
(`--spring.profiles.active=mysql`), com usuário e senha vindos de variáveis de ambiente.

### Endereços depois do deploy

| O quê | Caminho |
|---|---|
| Site | `https://SEU-SERVICO.onrender.com/` |
| Painel interno | `https://SEU-SERVICO.onrender.com/painel.html` |
| Swagger UI | `https://SEU-SERVICO.onrender.com/swagger-ui/index.html` |
| Especificação OpenAPI | `https://SEU-SERVICO.onrender.com/v3/api-docs` |

### Limitação do plano gratuito

O serviço hiberna após 15 minutos sem acesso e leva cerca de 1 minuto para voltar.
Abra o link alguns minutos antes de apresentar.
