# Bloco 3 - Padrões e Qualidade

## 1. Objetivo

Este documento define os padrões de nomenclatura, programação, documentação, versionamento e qualidade que deverão ser seguidos durante o desenvolvimento do SpotMeet.

O objetivo é manter consistência entre os integrantes da equipe, facilitar a manutenção do código e estabelecer uma estratégia comum para execução e documentação dos testes durante os incrementos do projeto.

Os exemplos de testes apresentados neste documento são ilustrativos. Os casos de teste definitivos deverão ser criados ou atualizados conforme os requisitos funcionais e não funcionais forem definidos em cada incremento.

## 2. Idioma e Convenções Gerais

Para manter consistência entre código, documentação e interface, serão adotadas as seguintes regras:

| Área | Idioma |
|---|---|
| Código-fonte | Inglês |
| Nomes de classes, funções e variáveis | Inglês |
| Banco de dados | Inglês |
| Endpoints da API | Inglês |
| Documentação do projeto | Português |
| Interface apresentada ao usuário | Português |
| Branches e mensagens de commit | Português |

Não deverão ser misturados idiomas em um mesmo identificador.

Exemplo inadequado:

```text
MeetingService
criarMeeting()
usuarioAtual
```

Exemplo adequado:

```text
MeetingService
createMeeting()
currentUser
```

## 3. Padrões de Nomenclatura

### 3.1. Aplicativo Mobile - React Native e TypeScript

#### Componentes e Telas

Componentes React Native, telas, interfaces e tipos deverão utilizar `PascalCase`.

Exemplos:

```text
MeetingCard.tsx
MeetingDetailsScreen.tsx
CreateMeetingScreen.tsx
ParticipantList.tsx
```

#### Variáveis e Funções

Variáveis, funções e métodos deverão utilizar `camelCase`.

Exemplos:

```text
currentUser
selectedMeeting
meetingDate

createMeeting()
loadMeetings()
cancelMeeting()
```

#### Constantes

Constantes globais deverão utilizar `UPPER_SNAKE_CASE`.

Exemplos:

```text
MAX_PARTICIPANTS
DEFAULT_PAGE_SIZE
TOKEN_EXPIRATION_TIME
```

#### Hooks

Hooks personalizados deverão utilizar `camelCase` e iniciar com `use`.

Exemplos:

```text
useMeetings()
useAuthentication()
useTheme()
```

### 3.2. Backend - Java

#### Classes

Classes deverão utilizar `PascalCase`.

Exemplos:

```text
MeetingController
MeetingService
MeetingRepository
Participant
```

#### Métodos e Variáveis

Métodos e variáveis deverão utilizar `camelCase`.

Exemplos:

```text
createMeeting()
findMeetingById()
cancelMeeting()

currentUser
meetingId
participantList
```

#### Constantes

Constantes deverão utilizar `UPPER_SNAKE_CASE`.

Exemplos:

```text
MAX_PARTICIPANTS
DEFAULT_PAGE_SIZE
```

#### Pacotes

Pacotes deverão possuir nomes em letras minúsculas.

Exemplos:

```text
controller
service
repository
model
dto
security
config
exception
```

### 3.3. Banco de Dados - PostgreSQL

Tabelas e colunas deverão utilizar `snake_case`.

Exemplos de tabelas:

```text
users
meetings
participants
meeting_agendas
meeting_minutes
```

Exemplos de colunas:

```text
created_at
updated_at
organizer_id
meeting_id
user_id
```

### 3.4. API REST

Endpoints deverão utilizar nomes em inglês, minúsculos e no plural.

Exemplos:

```text
GET /meetings
GET /meetings/{id}
POST /meetings
PUT /meetings/{id}
DELETE /meetings/{id}
```

## 4. Organização do Código

### 4.1. Aplicativo Mobile

Estrutura inicial proposta:

```text
mobile/
`-- src/
    |-- components/
    |-- screens/
    |-- navigation/
    |-- services/
    |-- hooks/
    |-- types/
    |-- utils/
    `-- constants/
```

Responsabilidades:

- `components`: componentes reutilizáveis da interface;
- `screens`: telas completas do aplicativo;
- `navigation`: configuração da navegação;
- `services`: comunicação com a API e serviços externos permitidos;
- `hooks`: hooks reutilizáveis;
- `types`: tipos e interfaces TypeScript;
- `utils`: funções utilitárias;
- `constants`: constantes utilizadas na aplicação.

### 4.2. Backend

Estrutura inicial proposta:

```text
backend/
`-- src/
    `-- main/
        `-- java/
            `-- .../
                |-- controller/
                |-- service/
                |-- repository/
                |-- model/
                |-- dto/
                |-- security/
                |-- config/
                `-- exception/
```

Responsabilidades principais:

- `controller`: recebe e responde requisições HTTP;
- `service`: concentra regras de negócio;
- `repository`: realiza acesso e persistência de dados;
- `model`: representa entidades e modelos do domínio;
- `dto`: define dados aceitos e retornados pela API;
- `security`: autenticação e autorização;
- `config`: configurações da aplicação;
- `exception`: tratamento padronizado de erros.

Regras obrigatórias:

- controllers não deverão concentrar regras de negócio;
- regras de negócio deverão permanecer na camada de serviço;
- acesso ao banco de dados deverá ocorrer pela camada de persistência;
- o aplicativo mobile não deverá decidir autorização de acesso.

## 5. Práticas de Programação

A equipe deverá seguir as seguintes práticas:

- utilizar nomes claros e descritivos;
- manter funções, métodos e componentes com responsabilidade definida;
- evitar duplicação desnecessária de código;
- evitar métodos e componentes excessivamente grandes;
- remover código morto ou comentado que não seja necessário;
- evitar valores mágicos quando uma constante for mais adequada;
- validar os dados recebidos pelo sistema;
- tratar erros de forma padronizada;
- separar interface, regra de negócio e persistência;
- manter código e documentação coerentes;
- revisar alterações antes de incorporá-las à branch principal;
- não inserir emojis, desenhos ou elementos decorativos em comentários e docstrings.

## 6. Padrões de Segurança

Como o SpotMeet manipulará dados de usuários e informações relacionadas a reuniões, as seguintes práticas deverão ser consideradas durante o desenvolvimento e os testes:

### 6.1. Senhas

- senhas nunca poderão ser armazenadas em texto puro;
- deverá ser utilizada função de hash apropriada para armazenamento de senhas;
- senhas não deverão aparecer em logs, mensagens de erro ou evidências de teste.

### 6.2. Autenticação e Tokens

- recursos privados deverão exigir autenticação válida;
- tokens de acesso e refresh tokens, caso adotados, não deverão ser registrados em logs;
- secrets e credenciais não poderão ser versionados no Git;
- o armazenamento de tokens no aplicativo deverá utilizar mecanismo apropriado de armazenamento seguro da plataforma.

### 6.3. Autorização por Operação e por Objeto

Toda autorização deverá ser validada no backend.

O sistema deverá impedir cenários de BOLA e IDOR, nos quais um usuário autenticado tente acessar, alterar ou excluir um recurso pertencente a outro usuário apenas modificando um identificador.

Exemplo:

```text
Usuário A possui acesso à reunião 15.

Usuário B tenta:
GET /meetings/15

Resultado esperado:
Acesso negado quando o usuário B não possuir autorização para a reunião.
```

### 6.4. Validação de Entrada e Mass Assignment

- os dados recebidos pela API deverão ser validados;
- DTOs deverão limitar explicitamente os campos aceitos nas operações;
- campos sensíveis não deverão ser atualizáveis apenas por serem enviados pelo cliente;
- a aplicação deverá impedir Mass Assignment.

### 6.5. SQL Injection

O acesso ao banco de dados deverá utilizar consultas parametrizadas ou mecanismos equivalentes disponibilizados pela camada de persistência.

Não deverão ser construídas consultas SQL concatenando diretamente entradas do usuário.

### 6.6. XSS

Caso pautas, atas ou outros campos aceitem conteúdo textual exibido posteriormente na interface, os dados deverão ser tratados de forma que conteúdo fornecido pelo usuário não seja executado como código.

### 6.7. CORS e CSRF

- CORS deverá ser configurado de forma restritiva para os ambientes previstos;
- caso a estratégia de autenticação utilize cookies ou outro mecanismo sujeito a CSRF, deverá ser aplicada proteção adequada;
- a necessidade de CSRF deverá ser reavaliada conforme a estratégia de autenticação adotada.

### 6.8. Logs

Logs não deverão conter:

- senhas;
- tokens de acesso;
- refresh tokens;
- secrets;
- chaves;
- payloads sensíveis;
- informações pessoais além do estritamente necessário para diagnóstico.

## 7. Padrões de Documentação

A documentação técnica principal será mantida em arquivos Markdown no repositório GitHub.

Estrutura inicial:

```text
docs/
|-- 01-analise-mercado-rnf.md
|-- 02-arquitetura-tecnologias.md
|-- 03-padroes-qualidade.md
`-- 04-design.md
```

Regras:

- documentos deverão possuir títulos e seções claras;
- alterações relevantes no comportamento do sistema deverão atualizar a documentação correspondente;
- requisitos deverão possuir identificadores únicos quando forem formalizados;
- casos de teste deverão possuir identificadores únicos;
- diagramas deverão possuir título e descrição;
- comentários e docstrings deverão ser objetivos e sem elementos decorativos;
- a documentação deverá acompanhar a evolução dos incrementos.

## 8. Git e Versionamento

A branch principal do projeto será:

```text
main
```

O desenvolvimento deverá ocorrer em branches específicas.

### 8.1. Padrão de Branches

Serão utilizados nomes em português.

```text
funcionalidade/criar-reuniao
funcionalidade/tela-login

correcao/validar-acesso-reuniao
correcao/corrigir-calendario

documentacao/atualizar-arquitetura
documentacao/revisar-rnf

teste/autorizacao-reuniao
teste/api-reunioes
```

Não deverão ser utilizados espaços, acentos ou caracteres especiais nos nomes das branches.

### 8.2. Padrão de Commits

As mensagens de commit serão escritas em português, com descrição objetiva da alteração.

Exemplos:

```text
Adicionar criação de reunião
Corrigir validação de acesso à reunião
Atualizar documentação de arquitetura
Adicionar testes de autorização
Refatorar serviço de reuniões
```

Boas práticas:

- um commit deverá representar uma alteração coerente;
- mensagens como `teste`, `alteração`, `mudanças` ou `final` deverão ser evitadas;
- arquivos com secrets, credenciais ou dados sensíveis não deverão ser commitados;
- antes de integrar alterações à `main`, o código deverá compilar e os testes aplicáveis deverão ser executados.

## 9. Estratégia de Testes

A estratégia de qualidade utilizará diferentes níveis e tipos de teste conforme os requisitos implementados em cada incremento.

### 9.1. Testes Unitários

Validarão unidades isoladas de código, como regras de negócio, validações e funções utilitárias.

Exemplos ilustrativos:

- validação de data e horário;
- regras de criação de reunião;
- cancelamento de reunião;
- validação de participantes.

### 9.2. Testes de Integração

Validarão a interação entre diferentes camadas do sistema.

Exemplo de fluxo:

```text
Controller
    v
Service
    v
Repository
    v
PostgreSQL
```

### 9.3. Testes de API

Validarão endpoints, dados de entrada, respostas e controle de acesso.

Exemplos ilustrativos:

```text
POST /meetings
GET /meetings
GET /meetings/{id}
PUT /meetings/{id}
DELETE /meetings/{id}
```

Deverão ser considerados cenários como:

- sucesso;
- entrada inválida;
- usuário não autenticado;
- usuário sem autorização;
- recurso inexistente;
- conflito de dados, quando aplicável.

### 9.4. Testes Funcionais

Validarão o comportamento do sistema do ponto de vista do usuário.

Exemplos ilustrativos:

- autenticar usuário;
- criar reunião;
- consultar reuniões;
- editar reunião;
- cancelar reunião;
- consultar agenda;
- criar pauta;
- registrar ata.

Os testes funcionais definitivos deverão acompanhar os requisitos definidos pelo professor em cada incremento.

### 9.5. Testes de Segurança

Deverão verificar, conforme aplicável:

- autenticação;
- autorização;
- BOLA;
- IDOR;
- Mass Assignment;
- SQL Injection;
- XSS;
- validação de entrada;
- proteção de dados sensíveis;
- exposição indevida em logs.

### 9.6. Testes de Usabilidade

Deverão avaliar se os fluxos principais são claros e simples para o usuário.

Exemplos:

- localizar uma reunião;
- iniciar a criação de uma reunião ou pauta;
- consultar o calendário;
- identificar o status de uma reunião;
- navegar entre as principais áreas do aplicativo.

### 9.7. Testes de Compatibilidade

Deverão considerar:

- Android;
- iOS;
- diferentes tamanhos de tela;
- modo claro;
- modo escuro;
- comportamento responsivo dos componentes.

As versões mínimas efetivamente suportadas deverão acompanhar a definição final da stack e os requisitos do projeto.

## 10. Identificação dos Casos de Teste

Os casos de teste deverão possuir identificadores únicos de acordo com a categoria.

| Categoria | Padrão |
|---|---|
| Unitário | `CT-UNIT-001` |
| Integração | `CT-INT-001` |
| API | `CT-API-001` |
| Funcional | `CT-FUNC-001` |
| Segurança | `CT-SEC-001` |
| Usabilidade | `CT-UI-001` |
| Compatibilidade | `CT-COMP-001` |

A numeração deverá ser sequencial dentro de cada categoria.

## 11. Modelo de Caso de Teste

Cada caso de teste deverá conter, no mínimo:

```text
ID:
CT-FUNC-001

Título:
Criar reunião com dados válidos

Tipo:
Funcional

Requisito relacionado:
RF-XXX / RNF-XXX

Pré-condições:
Usuário autenticado.

Dados de teste:
Informar os dados necessários para execução.

Passos:
1. ...
2. ...
3. ...

Resultado esperado:
Descrever o comportamento esperado.

Resultado obtido:
Preenchido durante a execução.

Status:
PENDENTE / APROVADO / REPROVADO / BLOQUEADO

Evidências:
Print, log ou outro registro quando necessário.

Responsável:
Nome do integrante.

Data:
Data da execução.
```

## 12. Registro dos Resultados

Os resultados deverão ser documentados durante cada incremento.

Estrutura proposta:

```text
docs/
`-- testing/
    |-- templates/
    |   `-- modelo-caso-teste.md
    `-- resultados/
        |-- incremento-1/
        |-- incremento-2/
        `-- incremento-3/
```

Os resultados poderão ser armazenados individualmente por caso de teste ou consolidados em um relatório do incremento, desde que mantenham rastreabilidade entre requisito, teste e resultado.

Evidências não deverão conter senhas, tokens, secrets ou informações pessoais desnecessárias.

## 13. Status dos Testes

Serão utilizados os seguintes status:

### PENDENTE

O teste ainda não foi executado.

### APROVADO

O resultado obtido corresponde ao resultado esperado.

### REPROVADO

O resultado obtido diverge do resultado esperado.

### BLOQUEADO

O teste não pôde ser executado devido a uma dependência, indisponibilidade do ambiente ou defeito anterior que impede sua execução.

## 14. Critérios Mínimos antes de Integrar Alterações

Antes de uma alteração ser incorporada à branch `main`, deverá ser verificado, quando aplicável:

- código compilando sem erros;
- testes relacionados executados;
- ausência de secrets ou credenciais nos arquivos alterados;
- autorização validada no backend para recursos protegidos;
- documentação atualizada quando houver alteração relevante;
- ausência de código morto ou artefatos temporários;
- revisão das alterações realizadas.

## 15. Evolução da Estratégia de Qualidade

Este documento define a estratégia inicial de qualidade do SpotMeet.

Como os requisitos do projeto serão definidos progressivamente ao longo dos incrementos, os casos de teste, critérios de aceite e matrizes de rastreabilidade deverão ser atualizados conforme novos requisitos forem apresentados.

A estrutura de nomenclatura, registro de evidências, segurança e documentação deverá permanecer consistente durante todo o projeto.
