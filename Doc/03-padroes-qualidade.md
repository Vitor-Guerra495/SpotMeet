# Bloco 3 - Padrões e Qualidade

## 1. Objetivo

Este documento define os padrões de nomenclatura, programação, documentação, versionamento e qualidade seguidos durante o desenvolvimento e a manutenção do SpotMeet.

O objetivo é manter consistência entre os integrantes da equipe, facilitar a manutenção e a evolução do código-fonte e registrar a estratégia adotada para a implementação, execução e documentação dos testes ao longo dos incrementos do projeto.

Os exemplos apresentados neste documento foram extraídos das classes, telas e pacotes reais implementados no sistema (`SpotMeetApp/src/` e `backend/src/main/java/com/spotmeet/backend/`). Os casos de teste acompanham os requisitos funcionais (RF01 a RF48, dos Incrementos 1 a 3) e os requisitos não funcionais (RNF01 a RNF14).

## 2. Idioma e Convenções Gerais

Para manter consistência entre código-fonte, banco de dados, documentação e interface, são adotadas as seguintes regras de idioma:

| Área | Idioma |
|---|---|
| Código-fonte (backend Java e aplicativo React Native) | Inglês |
| Nomes de classes, interfaces, métodos, funções e variáveis | Inglês |
| Comentários de código | Inglês |
| Banco de dados (tabelas, colunas, chaves e restrições) | Inglês |
| Endpoints e rotas da API REST | Inglês |
| Documentação técnica do projeto (`Doc/`) | Português |
| Interface apresentada ao usuário (telas, botões, modais e mensagens) | Português |
| Branches e mensagens de commit do Git | Português |

Não devem ser misturados idiomas em um mesmo identificador.

Exemplo inadequado:

```text
MeetingService
criarMeeting()
usuarioAtual
reuniaoRepository
```

Exemplo adequado:

```text
MeetingService
createMeeting()
currentUser
meetingRepository
```

## 3. Padrões de Nomenclatura

### 3.1. Aplicativo Mobile - React Native e TypeScript

#### Componentes e Telas

Componentes React Native, telas, interfaces e tipos TypeScript utilizam `PascalCase`.

Exemplos extraídos do código real (`SpotMeetApp/src/`):

```text
MeetingsScreen.tsx
MeetingForm.tsx
MeetingAttendancePanel.tsx
AttendanceListView.tsx
MeetingInvitationActions.tsx
PresidentSheet.tsx
MinutesSection.tsx
MeetingPolls.tsx
AttendanceReport.tsx
HelpCenter.tsx
AuthScreen.tsx
OrganizationScreen.tsx
SettingsScreen.tsx
```

#### Variáveis e Funções

Variáveis, funções, métodos e propriedades de objetos utilizam `camelCase`.

Exemplos extraídos do código real (`SpotMeetApp/src/screens/MeetingsScreen.tsx` e `SpotMeetApp/src/utils/meetings.ts`):

```text
meetings
presided
detail
attendanceList
presidedIds

load()
loadDetail()
submitForm()
cancelMeeting()
readMeetings()
readMeeting()
readAttendanceList()
formatDateTime()
```

#### Constantes

Constantes, mapas imutáveis de rótulos e chaves de configuração no aplicativo utilizam `UPPER_SNAKE_CASE`.

Exemplos extraídos do código real (`SpotMeetApp/src/`):

```text
API_BASE_URL
PRIORITY_LABELS
STATUS_LABELS
RESPONSE_LABELS
GENERIC_ERROR
```

#### Hooks

Hooks personalizados do React utilizam `camelCase` e iniciam obrigatoriamente com o prefixo `use`.

Exemplos extraídos do código real (`SpotMeetApp/src/hooks/`, `SpotMeetApp/src/context/` e `SpotMeetApp/src/components/meetings/`):

```text
useLiveSync()
useRefreshRequest()
useMeetingStyles()
useAuth()
```

### 3.2. Backend - Java 21 / Spring Boot 3.3.5

#### Classes e Interfaces

Classes, interfaces, enums e records Java utilizam `PascalCase`.

Exemplos extraídos do código real (`backend/src/main/java/com/spotmeet/backend/`):

```text
MeetingController
MeetingInvitationController
MeetingAttendanceController
AttendanceListController
MeetingService
MeetingAccess
MeetingRepository
MeetingParticipantRepository
MeetingRequestDTO
MeetingResponseDTO
AttendanceListService
MeetingMinutesController
MeetingMinutesService
MeetingPollController
MeetingPollService
AttendanceReportService
```

#### Métodos e Variáveis

Métodos e variáveis locais ou de instância utilizam `camelCase`.

Exemplos extraídos do código real (`MeetingService.java` e `MeetingController.java`):

```text
createMeeting()
listCommitteeMeetings()
listMyMeetings()
getMeeting()
updateMeeting()
cancelMeeting()
resolveInvitees()
requirePresident()

meetingId
committeeId
invitees
president
startsAt
endsAt
```

#### Constantes e Variáveis de Ambiente

Constantes Java (`static final`) e valores de enumerações utilizam `UPPER_SNAKE_CASE`.

Exemplos extraídos do código real (`AuthenticationAttempts.java` e `ValidationPatterns.java`):

```text
MAX_FAILURES
WINDOW_MILLIS
CLEANUP_MILLIS
EMAIL
OTP
ORGANIZATION_ACCESS_KEY
COMMITTEE_NAME
```

Parâmetros de ambiente e propriedades externas lidos pelo Spring (`application.properties` e `backend/.env`) também seguem `UPPER_SNAKE_CASE`, como `JWT_SECRET`, `JWT_EXPIRATION_MS` e `SERVER_PORT`.

#### Pacotes

Pacotes Java possuem nomes inteiramente em letras minúsculas, organizados conforme a camada da aplicação.

Exemplos do código real:

```text
com.spotmeet.backend.controller
com.spotmeet.backend.service
com.spotmeet.backend.repository
com.spotmeet.backend.model
com.spotmeet.backend.dto
com.spotmeet.backend.security
com.spotmeet.backend.config
com.spotmeet.backend.exception
```

### 3.3. Banco de Dados - PostgreSQL 18

Nomes de tabelas e colunas utilizam `snake_case`, com nomes de tabelas no plural.

Exemplos de tabelas reais:

```text
users
organizations
organization_members
organization_invitations
committees
committee_members
meetings
meeting_participants
meeting_series
meeting_files
meeting_minutes
minutes_responses
meeting_polls
poll_votes
user_profile_photos
audit_logs
access_requests
```

Exemplos de colunas reais (`meetings`, `meeting_participants` e `users`):

```text
created_at
updated_at
starts_at
ends_at
created_by_id
committee_id
user_id
meeting_id
entered_at
entered_by_id
left_at
left_by_id
response
response_comment
responded_at
tokens_valid_after
```

Restrições de unicidade reais:

```text
uq_meeting_participant
uq_committee_member
uq_committee_org_name
uq_minutes_response
uq_poll_vote
```

### 3.4. API REST

Os endpoints da API utilizam termos em inglês, substantivos no plural para coleções, letras minúsculas nos caminhos e parâmetros de rota identificados entre chaves.

Exemplos das rotas reais de reuniões, convites, presença, lista de presença, ata, votação e relatório individual (`MeetingController`, `MeetingInvitationController`, `MeetingAttendanceController`, `AttendanceListController`, `MeetingFileController`, `MeetingMinutesController`, `MeetingPollController` e `AttendanceReportController`):

```text
POST   /api/committees/{committeeId}/meetings
GET    /api/committees/{committeeId}/meetings
GET    /api/meetings/mine
GET    /api/meetings/invitations/pending
GET    /api/meetings/{meetingId}
PUT    /api/meetings/{meetingId}
POST   /api/meetings/{meetingId}/cancel
POST   /api/meetings/{meetingId}/respond
POST   /api/meetings/{meetingId}/participants/{userId}/entry
POST   /api/meetings/{meetingId}/participants/{userId}/exit
POST   /api/meetings/{meetingId}/attendance/entries
POST   /api/meetings/{meetingId}/attendance/exits
DELETE /api/meetings/{meetingId}/participants/{userId}/attendance
POST   /api/meetings/{meetingId}/attendance-list
GET    /api/meetings/{meetingId}/attendance-list
POST   /api/committees/{committeeId}/meeting-files
GET    /api/meetings/{meetingId}/attachment
GET    /api/meetings/{meetingId}/minutes
PUT    /api/meetings/{meetingId}/minutes
POST   /api/meetings/{meetingId}/minutes/submit
POST   /api/meetings/{meetingId}/minutes/answer
POST   /api/meetings/{meetingId}/minutes/close
GET    /api/meetings/{meetingId}/polls
POST   /api/meetings/{meetingId}/polls
POST   /api/meetings/{meetingId}/polls/{pollId}/vote
POST   /api/meetings/{meetingId}/polls/{pollId}/close
GET    /api/users/me/report
```

## 4. Organização do Código

O sistema segue a arquitetura cliente-servidor, separando o aplicativo móvel (`SpotMeetApp/`) do servidor REST (`backend/`).

### 4.1. Aplicativo Mobile (`SpotMeetApp/src/`)

Estrutura de diretórios implementada:

```text
SpotMeetApp/
`-- src/
    |-- components/
    |   |-- admin/
    |   |-- auth/
    |   |-- forms/
    |   |-- meetings/
    |   |-- profile/
    |   |-- settings/
    |   `-- ui/
    |-- config/
    |-- context/
    |-- hooks/
    |-- screens/
    |   `-- admin/
    `-- utils/
```

Responsabilidades de cada diretório:

* `components/`: componentes reutilizáveis da interface, separados por área (`MeetingForm`, `MeetingAttendancePanel`, `AttendanceListView`, `MeetingInvitationActions`, `PresidentSheet`, `MinutesSection`, `MeetingPolls`, `ProfilePhotoEditor`, `ToastHost`), as páginas Meu Relatório e Ajuda de Ajustes (`settings/AttendanceReport` e `settings/HelpCenter`) e estilos compartilhados das reuniões (`useMeetingStyles`);
* `config/`: configurações do aplicativo e descoberta/definição do endereço do servidor (`API_BASE_URL`);
* `context/`: contextos globais do React (`AuthContext`, `ThemeContext`, `BiometricContext`, `ProfilePhotoContext`);
* `hooks/`: hooks personalizados (`useLiveSync`, `useRefreshRequest`, `usePendingInvitations`, `useAppUpdate`, `useAdminOperation`);
* `screens/`: telas principais da aplicação (`AuthScreen`, `OrganizationScreen`, `MeetingsScreen`, `SettingsScreen` e as telas administrativas em `screens/admin/`);
* `utils/`: funções auxiliares de leitura da API (`meetings.ts`, `minutes.ts`, `polls.ts` e `report.ts`), tópicos do manual de uso (`helpTopics.ts`), formatação de data e hora (`formatDateTime`) e validação de formulários (`validators.ts`).

### 4.2. Backend (`backend/src/main/java/`)

Estrutura de pacotes em Spring Boot 3.3.5 e Java 21:

```text
backend/
`-- src/
    |-- main/
    |   |-- java/
    |   |   `-- com/spotmeet/backend/
    |   |       |-- config/
    |   |       |-- controller/
    |   |       |-- dto/
    |   |       |-- exception/
    |   |       |-- model/
    |   |       |-- repository/
    |   |       |-- security/
    |   |       `-- service/
    |   `-- resources/
    `-- test/
        `-- java/
            `-- com/spotmeet/backend/
```

Responsabilidades de cada pacote:

* `controller`: recebe as requisições HTTP (`@RestController`), aciona a validação dos DTOs (`@Valid`), obtém o e-mail do usuário autenticado via `Authentication` e delega a execução para a camada `service`;
* `service`: concentra as regras de negócio, validações de domínio, controle de permissões (`MeetingAccess`) e transações (`@Transactional`);
* `repository`: interfaces Spring Data JPA para consultas ao PostgreSQL e bloqueios de linha (`findForUpdate`, `findByIdForUpdate`, `findByEmailForUpdate`);
* `model`: entidades JPA que mapeiam as tabelas do banco de dados (`User`, `Organization`, `Committee`, `Meeting`, `MeetingParticipant`, `AuditLog`, etc.);
* `dto`: objetos de entrada e saída das rotas HTTP, validados com Bean Validation, e as expressões regulares compartilhadas (`ValidationPatterns`);
* `security`: filtro de autenticação JWT (`JwtAuthFilter`), geração e leitura de tokens (`JwtUtil`), controle de tentativas (`AuthenticationAttempts`) e mascaramento de dados (`LgpdMaskUtil`);
* `config`: configurações de segurança (`SecurityConfig`), execução assíncrona de e-mails (`EmailAsyncConfig`), tarefas agendadas (`SchedulingConfig`) e criação do banco na inicialização (`DatabaseCreator`). A conta inicial do administrador é criada pela classe principal `BackendApplication`;
* `exception`: tratamento centralizado de exceções (`GlobalExceptionHandler`), convertendo erros em respostas JSON padronizadas.

#### Regras de Camadas:

1. Classes `controller` não contêm regras de negócio nem acessam interfaces `repository` diretamente;
2. Toda verificação de permissão é feita no servidor dentro da camada `service` (`MeetingAccess`, `MeetingService`, `MeetingInvitationService`, `MeetingAttendanceService`, `AttendanceListService`, `MeetingMinutesService`, `MeetingPollService`);
3. O acesso ao banco de dados ocorre somente por meio dos repositórios JPA;
4. A interface móvel apenas exibe ou oculta botões conforme o estado da sessão, cabendo sempre à API validar o token e o cargo do usuário em cada chamada.

## 5. Práticas de Programação

Durante o desenvolvimento e a manutenção do código, a equipe segue as seguintes práticas:

- Escrever identificadores de classes, métodos e variáveis em inglês e com nomes descritivos;
- Escrever comentários de código em inglês, apenas quando necessários para explicar regras não óbvias;
- Manter cada classe, componente e função com uma responsabilidade clara;
- Evitar duplicação de lógica, extraindo regras compartilhadas de reunião para classes auxiliares como `MeetingAccess`;
- Remover trechos de código comentados, imports sem uso e chamadas de depuração antes do commit;
- Declarar constantes em `UPPER_SNAKE_CASE` para limites, janelas de tempo e expressões regulares;
- Validar todas as entradas no aplicativo (`react-hook-form` e `validators.ts`) e novamente no servidor (Bean Validation e regras de serviço);
- Tratar erros de forma uniforme pelo `GlobalExceptionHandler`, devolvendo mensagens em português para o aplicativo.

## 6. Padrões de Segurança

### 6.1. Senhas e Códigos de Verificação

- As senhas são gravadas na coluna `users.password` apenas como hash **BCrypt com fator de custo 12** (`$2a$12$`);
- Os códigos temporários de 6 dígitos (confirmação de cadastro, recuperação de conta e troca de e-mail) têm validade de 5 minutos, uso único e também são gravados como hash BCrypt;
- Nenhuma rota da API retorna hashes de senha ou códigos de verificação.

### 6.2. Autenticação e Sessão

- As rotas privadas exigem token JWT válido no cabeçalho `Authorization: Bearer <token>`;
- O token JWT expira em 24 horas (`JWT_EXPIRATION_MS=86400000` por padrão);
- A coluna `users.tokens_valid_after` invalida imediatamente os tokens anteriores quando o usuário altera ou redefine a senha, quando uma conta pendente é confirmada pelo Google e quando o sistema é resetado;
- Chaves e senhas de infraestrutura (`JWT_SECRET`, banco e SMTP) ficam somente no arquivo local `backend/.env` ou em variáveis de ambiente, fora do controle de versão;
- Quando o bloqueio biométrico é ativado no aparelho, a sessão é guardada no armazenamento seguro (`expo-secure-store`).

### 6.3. Autorização por Objeto (Prevenção de BOLA / IDOR)

O servidor verifica o vínculo e o cargo do usuário autenticado em cada operação sobre organizações, comissões e reuniões:

```text
Exemplo de verificação de acesso à reunião (BOLA/IDOR):

Usuário A (integrante da Comissão 1) foi convocado para a reunião 15.

Usuário B (que não pertence à Comissão 1 nem foi convocado) chama:
GET /api/meetings/15

Resposta do servidor:
HTTP 403 Forbidden (o usuário B não é presidente da comissão, líder da organização nem convidado da reunião).
```

Somente o presidente ativo da comissão (`COMMITTEE_ADMIN`) pode:
- Agendar reunião (`POST /api/committees/{committeeId}/meetings`);
- Editar reunião (`PUT /api/meetings/{meetingId}`);
- Cancelar reunião (`POST /api/meetings/{meetingId}/cancel`, que retorna HTTP 200 com os dados da reunião cancelada);
- Registrar ou limpar a presença de outros convidados (`POST /api/meetings/{meetingId}/participants/{userId}/entry`, `/exit`, `/attendance/entries`, `/attendance/exits` e `DELETE /api/meetings/{meetingId}/participants/{userId}/attendance`);
- Gerar a lista de presença (`POST /api/meetings/{meetingId}/attendance-list`), que depois pode ser consultada pelos integrantes da comissão e pelos líderes da organização (`GET /api/meetings/{meetingId}/attendance-list`);
- Salvar o rascunho, enviar, reenviar e encerrar a aprovação da ata (`PUT /api/meetings/{meetingId}/minutes`, `POST /api/meetings/{meetingId}/minutes/submit` e `/minutes/close`);
- Abrir e encerrar votações (`POST /api/meetings/{meetingId}/polls` e `/polls/{pollId}/close`).

Só quem registrou entrada na reunião pode responder à ata, exceto quem a escreveu, e votar (`POST /api/meetings/{meetingId}/minutes/answer` e `/polls/{pollId}/vote`): uma resposta por rodada de aprovação da ata e um voto por votação. Depois do primeiro envio da ata, a presença não pode mais ser corrigida (HTTP 409). O relatório individual (`GET /api/users/me/report`) usa somente o usuário do token e não recebe o identificador de outra pessoa.

### 6.4. Validação de Entrada e Prevenção de Mass Assignment

- Os dados recebidos pela API são lidos exclusivamente por meio de DTOs de entrada (`MeetingRequestDTO`, etc.);
- Campos internos como autor da reunião (`created_by_id`), situação, carimbos de data/hora e quem registrou a presença (`entered_by_id`, `left_by_id`) são preenchidos pelo servidor a partir do usuário autenticado e do relógio do servidor.

### 6.5. Prevenção de SQL Injection

- Todas as consultas ao PostgreSQL utilizam métodos derivados ou queries parametrizadas (`@Query` com parâmetros nomeados `:param`) do Spring Data JPA / Hibernate, sem concatenação de strings SQL.

### 6.6. Sanitização de Texto

- Campos textuais das reuniões (título, pauta, local, link e comentário de recusa), da ata (assuntos, decisões, observações e motivo da reprovação) e da votação (pergunta, descrição e justificativa da abstenção) têm os espaços das pontas removidos, e o texto vazio é gravado como nulo (`MeetingAccess.clean()`); o tamanho é validado nos DTOs antes da gravação.

### 6.7. Proteção contra Tentativas Repetidas

- A classe `AuthenticationAttempts` limita falhas consecutivas de login, senha atual e códigos de verificação a **10 tentativas em 15 minutos** (`MAX_FAILURES = 10`, `WINDOW_MILLIS = 15 * 60 * 1000L`) por conta e finalidade. A 10ª falha já responde HTTP 429 com o cabeçalho `Retry-After`, e as tentativas seguintes continuam bloqueadas por 15 minutos.

### 6.8. Logs de Auditoria e Mascaramento (LGPD)

- Os eventos relevantes do sistema são registrados na tabela `audit_logs` sem armazenar senhas, tokens JWT ou códigos de verificação;
- Nas consultas de relatórios administrativos, e-mails e CNPJs passam pelo mascaramento da classe `LgpdMaskUtil`.

## 7. Padrões de Documentação

A documentação do projeto fica concentrada na pasta `Doc/` na raiz do repositório, organizada conforme a estrutura real abaixo:

```text
Doc/
|-- 01-analise-mercado-rnf.md
|-- 02-arquitetura-tecnologias.md
|-- 03-padroes-qualidade.md
|-- 04-design.md
|-- 05-documento-requisitos.md
|-- 06-manual-de-uso.md
`-- imagens/
    |-- design/
    `-- diagramas/
```

Diretrizes seguidas nos documentos:
- Os seis blocos documentais (`01` a `06`) são mantidos em Markdown na raiz de `Doc/`, as imagens do design ficam em `Doc/imagens/design/` e os diagramas, com as fontes PlantUML, em `Doc/imagens/diagramas/`;
- Mudanças em rotas, tabelas, telas ou regras de negócio devem ser atualizadas em conjunto nos documentos correspondentes;
- Requisitos Funcionais seguem a numeração `RFxx` e Requisitos Não Funcionais seguem `RNFxx` (mantendo a mesma numeração entre o `Doc/01-analise-mercado-rnf.md` e o `Doc/05-documento-requisitos.md`);
- Casos de teste seguem o padrão `CT-<TIPO>-xxx`.

## 8. Git e Versionamento

A branch principal do repositório é:

```text
main
```

### 8.1. Nomenclatura de Branches

As branches secundárias são nomeadas em português, com letras minúsculas, sem acentos e palavras separadas por hífen:

```text
funcionalidade/agendamento-reunioes
funcionalidade/lista-presenca
funcionalidade/convite-integrantes

correcao/validar-acesso-reuniao
correcao/ajuste-horario-presenca

documentacao/atualizar-analise-mercado
documentacao/revisar-padroes-qualidade

teste/autorizacao-reuniao
teste/desempenho-api-reunioes
```

### 8.2. Mensagens de Commit

As mensagens de commit são escritas em português, descrevendo de forma direta a alteração realizada:

```text
Adicionar agendamento de reuniões pelo presidente da comissão
Adicionar lista de presença e registro de entrada e saída
Adicionar bloqueio de reuniões para organizações não aprovadas
Corrigir validação de acesso à reunião no backend
Atualizar pesquisa de mercado e requisitos não funcionais
```

Regras de versionamento:
- Cada commit deve reunir alterações relacionadas a uma mesma tarefa;
- Evitar mensagens vagas como `ajustes`, `fix` ou `teste`;
- Nunca versionar o arquivo `backend/.env`, chaves privadas ou pastas de build (`target/`, `node_modules/`, `.expo/`);
- Garantir que o servidor compile e passe nos testes automatizados antes de integrar na `main`.

## 9. Estratégia de Testes

A verificação de qualidade do SpotMeet combina os testes automatizados do servidor e do aplicativo com testes de API via `curl` e testes funcionais manuais no aplicativo móvel.

### 9.1. Ferramentas de Teste Utilizadas no Projeto

| Camada | Ferramenta / Framework | Uso Real no Projeto |
|---|---|---|
| Backend | **JUnit 5 (Jupiter)** | Suíte automatizada do servidor em `backend/src/test/java/com/spotmeet/backend/`, com 28 classes de teste, executada com `./mvnw test`. |
| Backend | **Mockito** | Simulação dos repositórios e de outras dependências nos testes das regras de negócio, de segurança e de e-mail. |
| Backend | **MockMvc e Spring Boot Test** | Testes das rotas HTTP de autenticação, organizações, comissões, foto de perfil e monitoramento, com os códigos de retorno e o filtro JWT. Duas classes (`OrganizationModerationPostgresTests` e `ProfilePhotoPostgresTests`) usam um PostgreSQL local de teste. |
| Aplicativo | **Executor de testes do Node (`node:test`)** | 27 arquivos de teste em `SpotMeetApp/tests/`, com 826 testes, executados com `npm test` (o mesmo que `node --test "tests/*.test.mjs"`), para validação de formulários, sessão, biometria, foto de perfil, avisos, sincronização das telas e rascunho da ata. |
| API | **cURL** | Verificação direta de rotas HTTP, códigos de retorno (200, 201, 400, 401, 403, 429) e medição de tempo de resposta (`RNF07`). |
| Aplicativo | **Expo Go e APK Android (teste manual)** | Verificação manual dos fluxos de tela, da validação dos formulários, dos temas claro e escuro e das confirmações de ações em aparelhos reais. |

### 9.2. Níveis de Teste

#### 1. Testes de Regras de Negócio (JUnit 5 + Mockito)
Verificam as regras dos serviços e as validações do servidor com os repositórios simulados (por exemplo `MeetingRulesTests`, `MeetingRecurrenceTests`, `CommitteeRulesTests`, `OrganizationRulesTests`, `AuthenticationAttemptTests`, `OtpValidationTests`, `EmailValidationTests` e `NameValidationTests`):
- Agendamento só pelo presidente, início no futuro e reunião de dia inteiro que pode começar no dia atual;
- Convite dos integrantes ativos escolhidos, resposta até o fim da reunião e comentário mantido na recusa;
- Entrada e saída dentro do horário da reunião, registro por outra pessoa só pelo presidente e ações em massa só com a reunião em andamento;
- Lista de presença gerada pelo presidente e consultada por integrantes e líderes da organização;
- Regras de repetição semanal, mensal, anual e diária e conferência do tipo do anexo pelo conteúdo;
- Limite de 10 falhas em 15 minutos em `AuthenticationAttempts` e expressões regulares de senha, e-mail, nome e código de 6 dígitos;
- Ata escrita depois da reunião e aprovada pelos presentes, votação só para quem registrou entrada e relatório só com as reuniões em que o usuário entrou (`MeetingRulesTests`).

#### 2. Testes de Rotas HTTP e Segurança (JUnit 5 + MockMvc)
Verificam o mapeamento das rotas, o JSON das respostas, a autenticação JWT e os códigos HTTP (por exemplo `AccountSecurityTests`, `JwtSessionTests`, `SessionRevocationTests`, `SecurityReviewTests`, `GoogleSignInTests`, `OrganizationRulesTests` e `ProfilePhotoTests`):
- Cadastro, confirmação do código e login, com HTTP 401 para token ausente ou inválido;
- Encerramento das sessões antigas depois da troca ou redefinição de senha;
- HTTP 403 quando o usuário não tem o perfil ou o vínculo exigido na organização ou na comissão;
- HTTP 429 depois de tentativas repetidas e ausência de campos sensíveis nas respostas JSON.

#### 3. Testes Funcionais (No Aplicativo e Banco PostgreSQL)
Verificam os fluxos de ponta a ponta com o servidor conectado ao PostgreSQL 18:
- Cadastrar conta, confirmar código de 6 dígitos por e-mail (`POST /api/auth/verify-email`) e iniciar sessão;
- Agendar, editar e cancelar reunião como presidente da comissão;
- Aceitar ou recusar convite com justificativa, registrar entrada e saída durante a reunião e gerar a lista de presença;
- Escrever, enviar e aprovar a ata, abrir uma votação e votar, e consultar o relatório individual.

#### 4. Testes de Segurança
Verificam o cumprimento dos requisitos `RNF02` a `RNF06`:
- Tentativa de acesso a reunião de outra comissão por alteração de `{meetingId}` na URL, confirmando bloqueio com HTTP 403;
- Conferência dos hashes `$2a$12$` na tabela `users` e ausência de campos sensíveis nas respostas JSON;
- Envio de 10 tentativas incorretas seguidas em `/api/auth/verify-email` ou `/api/auth/login`, confirmando a resposta HTTP 429 já na 10ª falha e durante os 15 minutos seguintes.

#### 5. Testes de Usabilidade, Desempenho e Compatibilidade
Verificam os requisitos `RNF01`, `RNF07` e `RNF09`:
- Submissão de formulários vazios ou com formato inválido no aplicativo, confirmando exibição dos avisos em português junto aos campos sem envio de requisição à API;
- Aferição de tempo de resposta local com `curl` e medição com cronômetro em 10 testes de abertura da aba "Reuniões" em rede móvel;
- Execução do aplicativo em Android (7.0+) e iOS (16.4+) nos temas Claro e Escuro.

## 10. Identificação dos Casos de Teste

Os casos de teste utilizam identificadores sequenciais de acordo com a categoria:

| Categoria | Padrão de Identificador |
|---|---|
| Unitário | `CT-UNIT-xxx` |
| Integração | `CT-INT-xxx` |
| API | `CT-API-xxx` |
| Funcional | `CT-FUNC-xxx` |
| Segurança | `CT-SEC-xxx` |
| Usabilidade | `CT-UI-xxx` |
| Desempenho | `CT-PERF-xxx` |
| Compatibilidade | `CT-COMP-xxx` |

## 11. Modelo de Caso de Teste

Os casos de teste manuais e de aceitação seguem o modelo em branco abaixo, cujos campos de execução (`Resultado Obtido`, `Status`, `Evidências`, `Responsável` e `Data da Execução`) são preenchidos somente no momento em que a bateria de testes é executada:

```text
ID:
CT-FUNC-001

Título:
Agendar reunião com dados válidos pelo presidente da comissão

Tipo:
Funcional

Requisito Relacionado:
RF25 / RNF09

Pré-condições:
1. Usuário autenticado com cargo ativo de presidente da comissão (COMMITTEE_ADMIN).
2. Organização aprovada pelo administrador do sistema.
3. Existência de integrantes ativos vinculados à comissão.

Dados de Teste:
Título: "Reunião de Alinhamento"
Início: <data/hora futura no formato YYYY-MM-DDTHH:mm:ss>
Término: <data/hora posterior ao início>
Pauta: "Definição das tarefas da comissão"
Prioridade: HIGH
Convidados: Integrantes ativos selecionados

Passos de Execução:
1. Acessar a aba "Reuniões" no aplicativo SpotMeet.
2. Abrir o formulário de criação de reunião da comissão presidida.
3. Preencher os campos obrigatórios com os dados de teste.
4. Confirmar o salvamento da reunião.

Resultado Esperado:
A reunião é gravada no servidor (HTTP 201), os convites são criados para os integrantes selecionados com resposta inicial PENDING, o presidente fica com resposta ACCEPTED e a reunião passa a aparecer na listagem da comissão.

Resultado Obtido:
[A preencher na execução]

Status:
PENDENTE

Evidências:
[A preencher na execução]

Responsável:
[A preencher na execução]

Data da Execução:
[A preencher na execução]
```

## 12. Registro dos Resultados de Teste

Os resultados dos testes automatizados do servidor são obtidos diretamente pela execução do Maven (`./mvnw test`, que gera os relatórios do JUnit 5 em `backend/target/surefire-reports/`), enquanto as formas de conferir os requisitos não funcionais ficam nas Seções 3 e 5 do Documento de Requisitos (`Doc/05-documento-requisitos.md`) e os testes executados, com as verificações de API, na Seção 10.2.

Nenhuma evidência ou relatório de teste deve expor senhas em texto, tokens JWT válidos ou dados pessoais sem mascaramento.

## 13. Status dos Testes

* **PENDENTE:** Caso de teste especificado, aguardando execução;
* **APROVADO:** O resultado obtido na execução corresponde ao resultado esperado;
* **REPROVADO:** O comportamento observado divergiu do esperado, exigindo correção antes da entrega;
* **BLOQUEADO:** O teste não pôde ser executado por impedimento de ambiente ou dependência pendente.

## 14. Critérios de Aceite para Merge na Branch Main

Antes de integrar qualquer branch na `main`, devem ser conferidos os seguintes itens:

- [ ] Servidor compilando sem erros (`./mvnw clean compile`) e suíte JUnit 5 aprovada (`./mvnw test`);
- [ ] Aplicativo móvel sem erros de tipagem TypeScript (`npx tsc --noEmit`);
- [ ] Ausência de arquivos `.env`, senhas ou tokens nos arquivos versionados;
- [ ] Verificação de permissão implementada no servidor para todas as rotas novas ou alteradas;
- [ ] Documentação na pasta `Doc/` atualizada de acordo com o código;
- [ ] Ausência de código comentado ou imports não utilizados;
- [ ] Revisão das alterações por outro integrante da equipe.

## 15. Evolução Contínua da Qualidade

As convenções de código, nomenclatura, rotas REST, segurança e verificação descritas neste documento devem ser mantidas alinhadas ao código-fonte e ao Documento de Requisitos (`Doc/05-documento-requisitos.md`) em todos os incrementos do SpotMeet.
