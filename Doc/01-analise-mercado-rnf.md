# Bloco 1 - Análise de Mercado e Requisitos Não Funcionais

## 1. Pesquisa de Mercado (Visão de Negócio)

O SpotMeet foi concebido como um sistema para gestão de organizações, comissões e reuniões corporativas e acadêmicas, focado na preparação da reunião (convocação dos integrantes e definição da pauta), no acompanhamento durante a reunião (registro de entrada e saída dos participantes e votações) e no fechamento (lista de presença e ata aprovada pelos presentes). Cada usuário também consulta o próprio relatório de participação, e o manual de uso fica dentro do aplicativo.

Esta pesquisa analisa oito aplicativos usados para comunicação, agendamento e organização de reuniões, comparando suas funcionalidades, preços, avaliações nas lojas oficiais e limitações em relação ao escopo do projeto. Os preços, as notas e as faixas de downloads foram consultados em **05/11/2026** nas páginas oficiais de cada fornecedor e nas lojas brasileiras da Google Play Store e da Apple App Store.

### 1.1. Aplicativos Analisados

1. **Microsoft Teams** (Microsoft Corporation): plataforma corporativa de chat, videoconferência, armazenamento e integração ao Microsoft 365.
2. **Google Meet** (Google LLC): ferramenta de chamadas de vídeo integrada ao Google Workspace e ao Google Agenda.
3. **Zoom Workplace** (Zoom Communications): plataforma de videoconferência e colaboração.
4. **Fellow** (Fellow Insights Inc.): assistente de reuniões com pautas, anotações e resumos gerados por inteligência artificial e acompanhamento de tarefas.
5. **Notion** (Notion Labs Inc.): espaço de trabalho em blocos usado como repositório de documentos, wikis e atas de reunião.
6. **Calendly** (Calendly LLC): serviço de agendamento de horários integrado a calendários pessoais e corporativos.
7. **Trello** (Atlassian): ferramenta visual de quadros Kanban usada para organizar tópicos de pauta e tarefas.
8. **Asana** (Asana Inc.): plataforma de gestão de projetos e tarefas usada para estruturar pautas e distribuir pendências depois da reunião.

### 1.2. Matriz de Comparação Competitiva

A tabela a seguir compara as oito ferramentas nas dimensões ligadas à gestão de reuniões. Os preços são os do plano pago de entrada, por usuário e por mês, exibidos na página oficial em 05/11/2026 (com pagamento anual, quando a página informa as duas formas).

| Aplicativo | Plataformas | Plano gratuito e plano pago de entrada | Agendamento | Convite com aceite e recusa | Controle de presença | Gestão de pauta | Registro de ata |
|---|---|---|---|---|---|---|---|
| **Microsoft Teams** | Android, iOS, Windows, macOS, Web | Versão gratuita para uso pessoal; Teams Essentials por R$ 22,90 ([preços](https://www.microsoft.com/pt-br/microsoft-teams/compare-microsoft-teams-business-options)) | Sim, integrado ao calendário do Outlook | Sim (aceitar, provisório ou recusar, com resposta) | Relatório de presença para o organizador nas contas corporativas e educacionais | Sim, por anotações compartilhadas da reunião | Transcrição e resumo restritos a licenças pagas adicionais (Teams Premium ou Copilot) |
| **Google Meet** | Android, iOS, Web | Uso gratuito com conta Google; Google Workspace a partir de R$ 20 (plano Base) ([preços](https://workspace.google.com/intl/pt-BR/pricing.html)) | Sim, pelo Google Agenda | Sim (sim, não ou talvez, pelo Google Agenda) | Controle de presença só a partir do plano Plus (R$ 128,40) | Anotações vinculadas ao Google Docs | Transcrições e gravações nos planos pagos |
| **Zoom Workplace** | Android, iOS, Windows, macOS, Web | Básico gratuito com reuniões de até 40 minutos e 100 participantes; Pro por US$ 14,16 ([preços](https://zoom.us/pt/pricing)) | Sim, pelo aplicativo ou integrado a calendários | Sim, quando vinculado ao Google Agenda ou ao Outlook | Relatórios de participantes nas contas pagas | Anotações do Zoom antes e durante a reunião | Resumos automáticos por IA, limitados no plano gratuito |
| **Fellow** | Android, iOS, Web | Gratuito para até 5 usuários, com 5 anotações por IA por usuário; Team por US$ 7 ([preços](https://fellow.ai/pricing)) | Integrado ao Google Agenda e ao Outlook, sem calendário próprio | Depende do calendário externo integrado | Não possui registro de entrada e saída nem lista de presença | Sim, pautas de reunião | Sim, anotações, resumos e itens de ação gerados por IA |
| **Notion** | Android, iOS, Windows, macOS, Web | Free para uso pessoal; Plus por US$ 10 ([preços](https://www.notion.com/pt/pricing)) | Pelo Notion Calendar, aplicativo separado | Não nativo | Não possui; exige marcação manual em tabelas | Sim, editor de blocos com modelos de pauta | Sim, muito usado para redigir e arquivar atas |
| **Calendly** | Android, iOS, Web, extensões | Free com um tipo de evento; Standard por US$ 10 ([preços](https://calendly.com/pricing)) | Sim, é o foco da plataforma, por links de disponibilidade | Confirmação no agendamento e cancelamento ou reagendamento por link | Não possui | Não possui | Não possui |
| **Trello** | Android, iOS, Web, Windows, macOS | Gratuito para até 10 colaboradores por espaço de trabalho; Standard por US$ 5 ([preços](https://trello.com/pt-BR/pricing)) | Parcial, por datas nos cartões | Não possui | Não possui | Parcial, tópicos em cartões e listas | Parcial, em comentários e descrições dos cartões |
| **Asana** | Android, iOS, Web | Personal gratuito para uma ou duas pessoas; Starter a partir de R$ 40,99 ([preços](https://asana.com/pt/pricing)) | Parcial, por datas de tarefas e integração com calendários | Não possui; apenas atribui tarefas | Não possui | Parcial, projetos com tópicos em tarefas | Parcial, anotações e responsáveis por tarefa |

### 1.3. Avaliação nas Lojas de Aplicativos

Notas e quantidades de avaliações exibidas nas lojas brasileiras em **05/11/2026**. A Google Play Store informa também a faixa de downloads; a App Store não divulga esse dado.

| Aplicativo | Google Play Store | Apple App Store |
|---|---|---|
| Microsoft Teams | Nota 4,8 com 8,85 mi avaliações; 500 mi+ downloads ([página](https://play.google.com/store/apps/details?id=com.microsoft.teams)) | Nota 4,8 com 437 mil avaliações ([página](https://apps.apple.com/br/app/microsoft-teams/id1113153706)) |
| Google Meet | Nota 4,3 com 12 mi avaliações; 10 bi+ downloads ([página](https://play.google.com/store/apps/details?id=com.google.android.apps.tachyon)) | Nota 4,7 com 20 mil avaliações ([página](https://apps.apple.com/br/app/google-meet/id1096918571)) |
| Zoom Workplace | Nota 4,4 com 4,6 mi avaliações; 1 bi+ downloads ([página](https://play.google.com/store/apps/details?id=us.zoom.videomeetings)) | Nota 4,8 com 304 mil avaliações ([página](https://apps.apple.com/br/app/zoom-workplace/id546505307)) |
| Fellow | Sem nota exibida no Brasil; 10 mil+ downloads ([página](https://play.google.com/store/apps/details?id=co.fellow.app)) | Sem avaliações no Brasil ([página](https://apps.apple.com/br/app/fellow-ai-meeting-assistant/id1477264742)) |
| Notion | Nota 4,8 com 405 mil avaliações; 10 mi+ downloads ([página](https://play.google.com/store/apps/details?id=notion.id)) | Nota 4,9 com 44 mil avaliações ([página](https://apps.apple.com/br/app/notion-notas-tarefas-e-ia/id1232780281)) |
| Calendly | Nota 4,9 com 19,5 mil avaliações; 1 mi+ downloads ([página](https://play.google.com/store/apps/details?id=com.calendly.app)) | Nota 4,9 com 2,2 mil avaliações ([página](https://apps.apple.com/br/app/calendly-meeting-scheduling/id1451094657)) |
| Trello | Nota 4,5 com 123 mil avaliações; 10 mi+ downloads ([página](https://play.google.com/store/apps/details?id=com.trello)) | Nota 4,6 com 4,5 mil avaliações ([página](https://apps.apple.com/br/app/trello-rastreador-de-tarefas/id461504587)) |
| Asana | Nota 4,5 com 45,6 mil avaliações; 5 mi+ downloads ([página](https://play.google.com/store/apps/details?id=com.asana.app)) | Nota 4,8 com 2 mil avaliações ([página](https://apps.apple.com/br/app/asana-gest%C3%A3o-do-trabalho/id489969512)) |

As notas altas mostram que as ferramentas de comunicação (Teams, Meet e Zoom) são as mais usadas e bem avaliadas. O Fellow, que é o concorrente mais próximo no foco em reuniões, tem baixa presença no Brasil: não há nota exibida em nenhuma das lojas brasileiras e o aplicativo está na faixa de 10 mil downloads.

### 1.4. Análise de Forças e Fraquezas dos Concorrentes

| Concorrente | Principais Forças | Principais Fraquezas para o escopo do SpotMeet |
|---|---|---|
| **Microsoft Teams** | Ecossistema completo de comunicação e arquivos; integração com o Outlook | Relatório de presença só registra quem entrou na chamada de vídeo e depende de conta corporativa ou educacional |
| **Google Meet** | Acesso rápido por link; integração com o Google Agenda | Controle de presença só a partir do plano Plus; não organiza comissões nem gera lista de presença no aplicativo |
| **Zoom Workplace** | Estabilidade de vídeo e suporte a muitos participantes | Limite de 40 minutos no plano gratuito; foco na chamada de vídeo |
| **Fellow** | Pautas e anotações com IA e acompanhamento de tarefas | Cobrança em dólar; sem registro de entrada e saída nem lista de presença; pouca presença no Brasil |
| **Notion** | Editor versátil para modelos de pauta e atas | Não convoca integrantes, não recebe aceite de convite e não controla presença |
| **Calendly** | Agendamento rápido por link com conferência de disponibilidade | Atende só a etapa de agendamento; não cobre pauta, presença nem ata |
| **Trello** | Organização visual simples em quadros e cartões | Não tem recursos de reunião (convocação, resposta a convite ou lista de presença) |
| **Asana** | Controle de tarefas, responsáveis e prazos depois da reunião | Não gerencia o ciclo da reunião (convites, entrada e saída e lista de presença) |

### 1.5. Onde o SpotMeet se Encaixa (Posicionamento no Projeto)

A comparação mostra que o controle completo de uma reunião de comissão costuma ficar dividido entre vários aplicativos: uma agenda ou link para marcar o horário, um aplicativo de mensagens para avisar o grupo e uma planilha ou documento para anotar quem compareceu. Nas ferramentas que geram relatório de presença (Teams, Meet e Zoom), o recurso depende de planos pagos ou corporativos e registra apenas a conexão na chamada de vídeo, sem atender reuniões presenciais ou híbridas.

Para definir os campos e as regras de reunião do Incremento 2 (Seção 11.1 do `Doc/05-documento-requisitos.md`), a equipe aplicou em **20/10/2026** um questionário de seis perguntas respondido por **quatro integrantes da própria equipe** (João, Lavínia, Enrico e Guilherme). É uma amostra interna, usada para orientar o desenvolvimento, e não uma pesquisa de mercado externa. Nesse levantamento:

- Na pergunta sobre informações indispensáveis para aceitar um convite, João citou *prioridade*, Lavínia citou *título e data*, Enrico citou *pauta e prioridade* e Guilherme citou *título e data*; somadas às respostas da primeira pergunta (título, datas de início e término, pauta, local e link), essas informações definiram os campos da reunião (`RF25`).
- Os quatro integrantes responderam que o presidente deve escolher quem convidar na comissão (`RF25` e `RF26`).
- Três integrantes pediram a opção de informar o motivo ou um comentário ao recusar um convite (`RF30`, com comentário opcional de até 200 caracteres).
- Sobre o registro de presença, dois pediram marcação automática, um pediu marcação pelo presidente e um pediu as duas formas (`RF31` a `RF33`).
- Sobre a lista de presença, três pediram nome, horários de entrada e saída e resposta ao convite, e um pediu apenas nome e horários (`RF34` e `RF35`).

As regras do Incremento 3 (ata, votação, relatório individual e manual de uso) foram decididas pela equipe em 06 e 07/11/2026 e estão na Seção 11.2 do `Doc/05-documento-requisitos.md`.

Com base nessa análise, o **SpotMeet** atende ao fluxo de organizações e comissões com as seguintes características implementadas:

1. **Estrutura de Organizações e Comissões:** organiza os usuários em organizações (com dono, vice-líder, subdono e membros) e em comissões conduzidas por um presidente designado (`COMMITTEE_ADMIN`).
2. **Agendamento e Convocação pelo Presidente:** o presidente da comissão agenda, edita e cancela reuniões com título, início, término (ou dia inteiro), repetição, pauta com anexo, local, link e prioridade (*Baixa*, *Média* ou *Alta*), escolhendo os integrantes ativos que serão convidados.
3. **Resposta ao Convite com Justificativa Opcional:** cada convidado pode aceitar ou recusar o convite enquanto a reunião não termina, com um comentário opcional de até 200 caracteres.
4. **Registro de Entrada e Saída pelo Integrante e pelo Presidente:** durante a reunião, cada convidado registra a própria entrada e saída pelo aplicativo, e o presidente registra ou corrige a presença de um convidado ou de todos de uma vez, sempre com o horário do servidor.
5. **Lista de Presença no Aplicativo:** o presidente gera a lista de presença, que pode ser consultada pelos integrantes da comissão e pelos líderes da organização e apresenta o nome, a resposta ao convite, os horários de entrada e saída, o tempo total em minutos e a situação de cada participante (*Presente*, *Presente, saída não registrada* ou *Ausente*).
6. **Ata da Reunião Aprovada pelos Presentes:** depois da reunião, o presidente escreve a ata (assuntos tratados, decisões tomadas e observações) e a envia para aprovação; quem registrou entrada aprova ou reprova com motivo, e a ata vale pela maioria de quem respondeu (`RF37` a `RF42`).
7. **Votação Durante a Reunião:** com a reunião em andamento, o presidente abre uma votação e quem registrou entrada vota uma única vez em *Sim*, *Não* ou *Abstenção*; o resultado nominal aparece quando o presidente encerra a votação ou quando a reunião termina (`RF43` a `RF46`).
8. **Relatório Individual de Participação:** cada usuário consulta as reuniões em que registrou entrada, com o total de reuniões, de horas e de comissões, filtrando por período e por comissão (`RF47`).
9. **Manual de Uso no Aplicativo:** a Ajuda, em Ajustes, traz o passo a passo de cada função, separado por perfil e com busca (`RF48`).
10. **Custo Zero:** construído com ferramentas gratuitas e de código aberto (Java 21, Spring Boot 3.3.5, PostgreSQL 18, React Native 0.88.1 e Expo SDK 58), sem cobrança de licença para registrar presença, atas e votações.

### 1.6. Fontes Consultadas (05/11/2026)

- Microsoft Teams: [preços](https://www.microsoft.com/pt-br/microsoft-teams/compare-microsoft-teams-business-options), [Google Play](https://play.google.com/store/apps/details?id=com.microsoft.teams) e [App Store](https://apps.apple.com/br/app/microsoft-teams/id1113153706).
- Google Meet: [preços do Google Workspace](https://workspace.google.com/intl/pt-BR/pricing.html), [Google Play](https://play.google.com/store/apps/details?id=com.google.android.apps.tachyon) e [App Store](https://apps.apple.com/br/app/google-meet/id1096918571).
- Zoom Workplace: [preços](https://zoom.us/pt/pricing), [Google Play](https://play.google.com/store/apps/details?id=us.zoom.videomeetings) e [App Store](https://apps.apple.com/br/app/zoom-workplace/id546505307).
- Fellow: [preços](https://fellow.ai/pricing), [Google Play](https://play.google.com/store/apps/details?id=co.fellow.app) e [App Store](https://apps.apple.com/br/app/fellow-ai-meeting-assistant/id1477264742).
- Notion: [preços](https://www.notion.com/pt/pricing), [Google Play](https://play.google.com/store/apps/details?id=notion.id) e [App Store](https://apps.apple.com/br/app/notion-notas-tarefas-e-ia/id1232780281).
- Calendly: [preços](https://calendly.com/pricing), [Google Play](https://play.google.com/store/apps/details?id=com.calendly.app) e [App Store](https://apps.apple.com/br/app/calendly-meeting-scheduling/id1451094657).
- Trello: [preços](https://trello.com/pt-BR/pricing), [Google Play](https://play.google.com/store/apps/details?id=com.trello) e [App Store](https://apps.apple.com/br/app/trello-rastreador-de-tarefas/id461504587).
- Asana: [preços](https://asana.com/pt/pricing), [Google Play](https://play.google.com/store/apps/details?id=com.asana.app) e [App Store](https://apps.apple.com/br/app/asana-gest%C3%A3o-do-trabalho/id489969512).

---

## 2. Requisitos Não Funcionais (RNF)

Os requisitos não funcionais abaixo seguem a mesma numeração e os mesmos assuntos das Seções 3 e 5 do Documento de Requisitos (`Doc/05-documento-requisitos.md`, de `RNF01` a `RNF14`). Para cada um são apresentadas a descrição do que existe no sistema, a métrica e a forma de conferir.

### RNF01 – Plataformas
- **Descrição:** o aplicativo é desenvolvido com React Native 0.88.1 e Expo SDK 58 (pacote `expo` 58.0.21), que exigem no mínimo **Android 7.0 (API 24)** e **iOS 16.4**. No Android, pode ser executado pelo Expo Go ou pelo APK gerado pelo EAS Build; no iOS, pelo Expo Go. O login com Google funciona no APK Android; o Face ID exige build própria de iOS. A interface oferece tema claro e tema escuro (`ThemeContext.tsx`), com a cor principal `#5C6BC0`, fundo claro `#F4F6F9` e escuro `#121212` e cartão claro `#FFFFFF` e escuro `#1E1E1E`.
- **Métrica:** abertura sem falhas em aparelhos a partir do Android 7.0 e do iOS 16.4, com as cores dos dois temas aplicadas.
- **Forma de Conferir:** executar o aplicativo em um Android (7.0 ou superior) e em um iPhone (iOS 16.4 ou superior) reais, fazer o login, navegar pelas abas "Reuniões", "Organização" e "Ajustes" e alternar entre os temas claro e escuro, conferindo as cores definidas em `ThemeContext.tsx`.

### RNF02 – Segurança dos dados sensíveis
- **Descrição:** as senhas são armazenadas apenas como hash BCrypt com fator de custo 12 (Spring Security 6), na coluna `users.password`. Os códigos de verificação de 6 dígitos (confirmação de cadastro, recuperação de conta e troca de e-mail) também são guardados como hash BCrypt, valem por 5 minutos e só podem ser usados uma vez. Nenhuma rota da API devolve esses hashes nem o código em texto.
- **Métrica:** 100% dos valores de `users.password` iniciando com o prefixo `$2a$12$`; nenhum campo de senha ou de código nas respostas JSON da API.
- **Forma de Conferir:** executar `SELECT password FROM users;` no PostgreSQL e confirmar que todos os valores começam com `$2a$12$`; chamar `GET /api/users/me` e `POST /api/auth/login` e confirmar que o JSON não contém os campos `password`, `verificationToken`, `recoveryToken`, `currentEmailChangeToken` e `newEmailToken`.

### RNF03 – Senha forte
- **Descrição:** a senha deve ter no mínimo 6 caracteres, com pelo menos uma letra minúscula, uma letra maiúscula e um dígito (expressão regular `^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{6,}$`). A regra é validada no aplicativo (`SpotMeetApp/src/utils/validators.ts`) e no servidor (`ValidationPatterns.java` e Bean Validation nos DTOs de cadastro, redefinição e troca de senha).
- **Métrica:** 100% de rejeição, no formulário e com HTTP 400 na API, das senhas que não atendem ao tamanho mínimo e à presença de letra maiúscula, minúscula e número.
- **Forma de Conferir:** tentar cadastrar a senha `abc123` (sem letra maiúscula) no aplicativo e confirmar que a regra não atendida é exibida sem envio da requisição; enviar a mesma senha diretamente para `POST /api/auth/register` e confirmar a resposta HTTP 400.

### RNF04 – Acesso autenticado
- **Descrição:** a autenticação usa token JWT assinado com HMAC (biblioteca JJWT 0.12.6), enviado no cabeçalho `Authorization: Bearer <token>`. São públicas as rotas `/api/auth/**`, `GET /api/app/android-release`, a página `/abrir/reuniao/{id}` (que só encaminha ao aplicativo, sem mostrar dados), `/actuator/health` e `/actuator/info`. Qualquer outra rota sem token válido responde HTTP 401; as rotas `/api/admin/**` respondem HTTP 403 para quem não tem perfil `ADMIN` ou `SYSADMIN`, e as rotas de organizações, comissões e reuniões respondem HTTP 403 quando o usuário não tem o vínculo ou o cargo exigido no recurso.
- **Métrica:** 100% de respostas HTTP 401 para chamadas sem token ou com token inválido em rotas privadas; 100% de respostas HTTP 403 para acessos sem o perfil ou o vínculo exigido.
- **Forma de Conferir:** chamar `GET /api/users/me`, `GET /api/meetings/mine` e `GET /api/admin/reports` sem o cabeçalho `Authorization` e com um token inválido e confirmar a resposta HTTP 401 (verificação executada em 20/10/2026); chamar `POST /api/meetings/{id}/cancel` com o token de um usuário que não é presidente da comissão e confirmar a resposta HTTP 403.

### RNF05 – Validade das sessões
- **Descrição:** o token JWT vale 24 horas (`JWT_EXPIRATION_MS=86400000` em `application.properties`). A coluna `users.tokens_valid_after` guarda o instante a partir do qual os tokens da conta são aceitos: ao trocar ou redefinir a senha, ao confirmar pelo Google uma conta ainda pendente e ao resetar o sistema, os tokens emitidos antes desse instante passam a ser recusados com HTTP 401. Com o aplicativo em primeiro plano, a validade da sessão é consultada a cada 15 segundos.
- **Métrica:** expiração do token em 24 horas e encerramento da sessão nos outros aparelhos em até 15 segundos depois da troca ou redefinição de senha.
- **Forma de Conferir:** entrar com a mesma conta em dois aparelhos, alterar a senha em um deles e cronometrar no outro a volta automática à tela de login, que deve ocorrer em até 15 segundos.

### RNF06 – Proteção contra tentativas repetidas
- **Descrição:** cada finalidade de verificação (login, senha atual, código de confirmação de cadastro, código de recuperação e as duas etapas da troca de e-mail) tem limite de 10 falhas em 15 minutos para a mesma identidade (`AuthenticationAttempts`, constantes `MAX_FAILURES = 10` e `WINDOW_MILLIS = 15 * 60 * 1000L`). A 10ª falha já bloqueia novas tentativas por 15 minutos e responde HTTP 429 (*Too Many Requests*), com o cabeçalho `Retry-After` e o campo `retryAfterSeconds`.
- **Métrica:** HTTP 429 na 10ª falha consecutiva dentro de 15 minutos e em toda tentativa durante o bloqueio, com tempo de espera de até 900 segundos.
- **Forma de Conferir:** enviar 10 códigos de confirmação incorretos seguidos para `POST /api/auth/verify-email` (ou 10 senhas incorretas para `POST /api/auth/login`) com o mesmo e-mail e confirmar que a 10ª resposta é HTTP 429; enviar em seguida o código ou a senha corretos e confirmar que a resposta continua HTTP 429 com o cabeçalho `Retry-After`.

### RNF07 – Tempo de resposta
- **Descrição:** em rede local, as consultas devem responder em até 1 segundo e o login (`POST /api/auth/login`) em até 2 segundos, por causa do cálculo do hash BCrypt de custo 12. Em rede móvel (4G ou 5G), a lista de reuniões deve carregar no aplicativo em até 5 segundos.
  - *Medições de 20/10/2026 (servidor e banco no mesmo computador, 5 chamadas por rota):* `POST /api/auth/login` entre 0,27 s e 0,29 s (0,74 s na primeira chamada, de aquecimento); `GET /api/users/me` entre 0,013 s e 0,021 s (0,099 s na primeira); `GET /api/meetings/mine` entre 0,015 s e 0,026 s (0,046 s na primeira).
- **Métrica:** tempo de resposta em rede local de até 1,0 s para consultas e até 2,0 s para o login; lista de reuniões em rede móvel em até 5,0 s.
- **Forma de Conferir:**
  1. *Na API, em rede local:* executar `curl -o /dev/null -s -w "%{time_total}\n"` em 5 chamadas seguidas para `POST /api/auth/login`, `GET /api/users/me` e `GET /api/meetings/mine` e conferir que os tempos ficam abaixo de 2,0 s (login) e 1,0 s (consultas).
  2. *No aplicativo, em rede móvel:* cronometrar 10 aberturas seguidas da aba "Reuniões" e conferir que a lista carrega em até 5 segundos.

### RNF08 – Integridade em acessos simultâneos
- **Descrição:** as escritas que podem ser disputadas são serializadas no PostgreSQL: a criação e a edição de organizações e comissões bloqueiam a tabela de organizações durante a transação (`LOCK TABLE organizations`, isolamento `READ_COMMITTED`); a designação do presidente bloqueia a linha da organização (`findByIdForUpdate`); senha, códigos e foto bloqueiam a linha do usuário (`findByEmailForUpdate`); a resposta ao convite, o registro de entrada e saída e a correção de presença bloqueiam a linha do participante (`findForUpdate`). As restrições únicas `uq_committee_member`, `uq_committee_org_name` e `uq_meeting_participant` impedem registros duplicados.
- **Métrica:** nenhum registro duplicado de participante ou de integrante de comissão e um único presidente ativo por comissão depois de requisições simultâneas.
- **Forma de Conferir:** disparar ao mesmo tempo duas respostas ao mesmo convite (`POST /api/meetings/{id}/respond`), duas entradas do mesmo participante (`POST /api/meetings/{id}/participants/{userId}/entry`) e duas designações de presidente na mesma comissão e conferir no banco que fica uma única linha por participante em `meeting_participants` e um único `COMMITTEE_ADMIN` na comissão.

### RNF09 – Usabilidade
- **Descrição:** textos e avisos em português; validação local de preenchimento, formato e tamanho antes do envio; confirmação nas ações destrutivas ou coletivas. Rascunhos de ata aceitam texto em qualquer um dos três campos, mas não aceitam todos vazios ou somente espaços. Os campos longos mostram o contador de caracteres. O botão voltar fecha a página, folha ou seleção de período mais interna antes de sair da aba. Avisos temporários deixam os toques passar e somem em 3,5 segundos.
- **Métrica:** 100% das regras locais de preenchimento obrigatório e formato conferidas antes do envio; confirmação em 100% das ações destrutivas ou coletivas; um contador por campo longo; voltar um nível por vez.
- **Forma de Conferir:** enviar formulários com preenchimento obrigatório ausente ou formato inválido e verificar que a operação não foi enviada; testar rascunho de ata vazio e parcial; cancelar ações na confirmação; digitar até o limite dos campos e conferir o contador; testar o botão voltar nas páginas internas e na escolha do período do relatório; tocar nos controles atrás de um aviso temporário. As regras detalhadas estão no RNF09 da seção 5 do Doc/05.

### RNF10 – Horário das reuniões
- **Descrição:** datas e horários são enviados e gravados como data e hora local sem fuso (por exemplo, `2026-10-23T14:00:00`) e exibidos sem conversão de fuso. Servidor e usuários devem operar na mesma região de fuso horário.
- **Métrica:** nenhuma diferença de horas ou minutos entre o horário enviado, o gravado e o exibido em aparelhos diferentes.
- **Forma de Conferir:** agendar uma reunião para as 14:00 e comparar a requisição, o registro no banco e a exibição em dois aparelhos configurados para a região do servidor. As regras de início futuro, dia inteiro e repetição pertencem aos RF25 e RF26. Para testar a regra funcional de início passado em reunião comum, a API responde HTTP 409; reuniões de dia inteiro, isoladas ou em série, terminam às 23:59:59.

### RNF11 – Preservação dos dados
- **Descrição:** o esquema do banco é atualizado pelo Hibernate com `spring.jpa.hibernate.ddl-auto=update`, que cria tabelas e colunas novas entre os incrementos sem apagar as existentes. A conta inicial do administrador do sistema só é criada na inicialização quando ainda não existe.
- **Métrica:** 100% dos registros preservados depois de reiniciar ou atualizar o servidor.
- **Forma de Conferir:** cadastrar usuários, organizações, comissões e reuniões, parar e iniciar o servidor novamente e consultar os dados pelo aplicativo e no PostgreSQL, confirmando que todos continuam lá.

### RNF12 – Execução do servidor
- **Descrição:** o servidor é um arquivo JAR executável (Spring Boot 3.3.5 com Tomcat embutido na porta 8080, configurável por `SERVER_PORT`) que exige Java 21 e um PostgreSQL 18 acessível, em Windows ou Linux. O banco é criado na primeira execução, se ainda não existir, e a conexão e as chaves ficam em variáveis de ambiente ou no arquivo `backend/.env`, que não é versionado.
- **Métrica:** compilação do JAR com código de saída `0` e resposta HTTP 200 com `"status":"UP"` na rota de saúde do servidor.
- **Forma de Conferir:** compilar com `./mvnw package`, executar o JAR em um computador com Java 21 e PostgreSQL 18 e consultar `GET /actuator/health`, confirmando o retorno `{"status":"UP"}`.

### RNF13 – Custo zero
- **Descrição:** as tecnologias usadas são gratuitas, de código aberto e não exigem assinatura paga: Java 21 (OpenJDK, licença GPLv2 com Classpath Exception), Spring Boot 3.3.5 (Apache 2.0), PostgreSQL 18 (PostgreSQL License), React Native 0.88.1 (MIT), Expo SDK 58 (MIT), JJWT 0.12.6 (Apache 2.0) e react-hook-form 7.90.0 (MIT). O envio de e-mails usa uma conta gratuita do Gmail, e o login com Google usa uma conta Google comum.
- **Métrica:** R$ 0,00 de custo com licenças ou mensalidades para compilar, executar e usar o sistema.
- **Forma de Conferir:** inspecionar `backend/pom.xml` e `SpotMeetApp/package.json` e conferir as licenças nas páginas oficiais de cada dependência listada.


### RNF14 – Plataforma do aplicativo atualizada
- **Descrição:** o aplicativo utiliza Expo SDK 58, com `expo` 58.0.21, React Native 0.88.1, React 19.3.0 e TypeScript 6.0.3, conforme os manifestos do projeto.
- **Métrica:** nenhuma dependência incompatível com a versão do SDK e nenhum erro de tipos.
- **Forma de Conferir:** executar `npm ls expo react-native react`, `npx expo config --type public`, `npx expo-doctor` e `npx tsc --noEmit`; comparar os resultados com a seção 2.1 e com o RNF14 do Doc/05. Os mínimos de Android e iOS permanecem os definidos no RNF01.
