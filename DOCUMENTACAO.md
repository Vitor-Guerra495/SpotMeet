# SpotMeet — Documentação Completa do Sistema

> Documento de referência do projeto SpotMeet para a disciplina **Projeto e Desenvolvimento de Sistemas II**.
> Cobre o que o sistema é, como está construído, o que cada parte faz, o que já foi entregue, como executar e o que ainda falta.
> Última atualização: 16/09/2026, verificada por script contra o código (rotas, enums, colunas, telas, ações de auditoria). Branch `refatoracao/codigo-em-ingles` já **mesclada na `main`** via PR #1 (commit `075b9b2`) neste repositório de testes. O **repositório oficial**, avaliado pelo professor, é outro: `github.com/Vitor-Guerra495/SpotMeet` (ver seção 14.5).

---

## Sumário

1. [Visão geral](#1-visão-geral)
2. [Contexto acadêmico e entrega](#2-contexto-acadêmico-e-entrega)
3. [Arquitetura e tecnologias](#3-arquitetura-e-tecnologias)
4. [Estrutura do repositório](#4-estrutura-do-repositório)
5. [Perfis, papéis e permissões](#5-perfis-papéis-e-permissões)
6. [Funcionalidades por requisito](#6-funcionalidades-por-requisito)
7. [Modelo de dados e dicionário](#7-modelo-de-dados-e-dicionário)
8. [API REST completa](#8-api-rest-completa)
9. [Segurança](#9-segurança)
10. [Aplicativo mobile](#10-aplicativo-mobile)
11. [Ambiente de desenvolvimento e execução](#11-ambiente-de-desenvolvimento-e-execução)
12. [Testes](#12-testes)
13. [Histórico do que foi feito](#13-histórico-do-que-foi-feito)
14. [O que falta](#14-o-que-falta)
15. [Problemas conhecidos e decisões tomadas](#15-problemas-conhecidos-e-decisões-tomadas)
16. [Glossário português → inglês](#16-glossário-português--inglês)
17. [Plano de publicação no repositório oficial](#17-plano-de-publicação-no-repositório-oficial)

---

## 1. Visão geral

### 1.1 O que é o SpotMeet

O SpotMeet é um aplicativo mobile (Android e iOS) com backend próprio para **organizar equipes em organizações e comissões**. A proposta de longo prazo é apoiar o ciclo completo de reuniões (agenda, pautas, atas). O **Incremento 1**, que é o escopo atual do código, entrega a base de tudo isso: contas de usuário, organizações com aprovação administrativa, comissões dentro das organizações, configurações de conta e um painel de administração do sistema.

### 1.2 Escopo do Incremento 1 (o que existe no código)

| Módulo | Resumo |
|---|---|
| Cadastro de usuário | Conta com verificação por e-mail, solicitação de acesso a organização por chave, gestão de membros e papéis. |
| Cadastro de organização | Usuário cria, SysAdmin aprova, rejeita ou bloqueia; dono gerencia e exclui. |
| Cadastro de comissão | Admins da organização criam comissões, convidam membros, aprovam solicitações e removem integrantes. |
| Configurações de usuário | Dados pessoais, status de disponibilidade, tema, troca de senha, recuperação de conta e troca de e-mail em duas etapas. |
| Informações do sistema | SysAdmin criado automaticamente, status dos subsistemas, relatórios com auditoria, reinício e reset total. |

### 1.3 Visão futura (fora do escopo atual)

Reuniões, agenda, calendário, pautas e atas aparecem na documentação da etapa anterior (`Doc/`, no repositório oficial) como visão do produto. **Nada disso está implementado** e não faz parte do Incremento 1.

---

## 2. Contexto acadêmico e entrega

| Item | Valor |
|---|---|
| Disciplina | Projeto e Desenvolvimento de Sistemas II |
| Entrega | Incremento 1 — **18/09/2026 às 18:00**, via GitHub, com apresentação no mesmo dia |
| Avaliação | Código e documentação coerentes entre si; app executado em **um Android e um iOS reais**; domínio do conteúdo por cada membro |
| Equipe | 5 membros; **cada membro responde por um requisito** e deve ter commits nele |

### 2.1 Divisão por requisito

| Requisito | Responsável | Telas | Backend |
|---|---|---|---|
| RF1 Cadastro de usuário e gestão de membros | João | AuthScreen, OrganizationScreen (membros e solicitações) | AuthController, MembershipController, UserService, MembershipService |
| RF2 Cadastro de organização | Lavinia | OrganizationScreen, AdminOrganizationsScreen | OrganizationController, AdminController (aprovação), OrganizationService |
| RF3 Cadastro de comissão | Enrico | OrganizationScreen (aba comissões, convites, solicitações) | CommitteeController, CommitteeService |
| RF4 Configurações de usuário | Vitor | SettingsScreen, AuthScreen (recuperação) | UserController, AuthController, UserService, EmailService |
| RF5 Informações do sistema | Guilherme | AdminMonitoringReportsScreen, AdminControlsScreen | AdminController, AdminService, BackendApplication (bootstrap) |

### 2.2 Feedback do professor na etapa anterior (AP, 21/08)

Pontos a corrigir na documentação antiga, ainda pendentes (ver seção 14):

- poucos aplicativos concorrentes avaliados e usuários não avaliados de verdade;
- faltaram as versões das tecnologias;
- Expo não estava previsto;
- o grupo não soube dizer qual tecnologia Java cria a API (resposta: **Spring Web / Spring MVC**, com `@RestController`, Jackson para JSON e Tomcat embutido);
- faltou exemplo com tema claro;
- padrões de programação genéricos demais;
- projeto de testes sem ferramenta e sem método;
- poucos RNFs e alguns não verificáveis.

### 2.3 Casos de uso exigidos pelo slide oficial (slides 5 a 9)

Fonte: slide do Incremento 1 entregue pelo professor. **Esta é a lista normativa** — os diagramas de caso de uso da seção 14.1 devem cobrir exatamente estes itens. A última coluna é o resultado da auditoria feita no código em 13/09/2026.

| Requisito | Caso de uso exigido pelo slide | Situação no código |
|---|---|---|
| Cadastro de organização (Lavinia) | Usuário realiza o cadastro de uma nova organização | Implementado (`POST /api/organizations`) |
| | Administrador do sistema autoriza/revoga o cadastro | Implementado (approve, reject e `PATCH /status` → `BLOCKED`) |
| | Administrador realiza gerenciamento de organizações | Implementado — lista, aprova, rejeita, bloqueia, **edita** (`PUT /api/organizations/{id}`) e exclui |
| Cadastro de comissão (Enrico) | Administrador da organização cadastra nova comissão | Implementado (`POST /api/organizations/{id}/committees`) |
| | Administrador da organização gerencia comissões | Implementado — lista, **edita** (`PUT /api/committees/{id}`) e exclui |
| | Administrador da organização gerencia equipe da comissão | Implementado (convite, resposta, solicitação, aprovação, remoção) |
| Cadastro de usuário (João) | Usuário se cadastra na plataforma | Implementado (com verificação por e-mail) |
| | Solicitação de acesso a organização com aprovação/rejeição | Implementado (`/api/access-requests`) |
| | Administrador da organização gerencia usuários da organização | Implementado (promover, rebaixar, remover) |
| Configurações de usuário (Vitor) | Troca de senha de usuário | Implementado (`PUT /api/users/me/password`) |
| | Recuperação de conta | Implementado (token por e-mail) |
| | Alteração de dados pessoais | Implementado (`PUT /api/users/me`) |
| | Tema preferido do aplicativo | Implementado (`preferredTheme`, persistido) |
| Informações do sistema (Guilherme) | Status do sistema e dos subsistemas | Implementado — **Banco**, **Rede** e **Recursos** |
| | Relatórios de usuários/organizações/comissões | Implementado (com e-mail mascarado por LGPD) |
| | Reinicialização de subsistemas | **Frágil** — `POST /api/admin/system/restart` é global e executa apenas `System.gc()`; não reinicia subsistema individual |
| | Setup inicial e inicialização do sistema | Implementado (SysAdmin criado no bootstrap) |
| | Reset do sistema | Implementado (`CONFIRM_FULL_RESET`, preserva o SysAdmin) |

### 2.4 Calendário das etapas seguintes (para o planejamento do final)

| Etapa | Entrega | Peso |
|---|---|---|
| Incremento 1 (Di1) | 18/09/2026 às 18:00, com apresentação | 1,5 de 10 (15%) |
| Incremento 2 (Di2) | início 25/09, entrega 09/10/2026 | — |
| Incremento 3 (Di3) | início 16/10, entrega 06/11/2026 | — |
| Avaliação final (AF) | início 13/11, entrega e apresentação 27/11/2026 | — |

Forma de entrega em todas as etapas: **GitHub ou GitLab, código e documentação juntos**.

---

## 3. Arquitetura e tecnologias

### 3.1 Arquitetura cliente-servidor

```text
┌──────────────────────────────┐        HTTP / REST / JSON        ┌──────────────────────────────┐
│  App mobile                  │ ───────────────────────────────▶ │  Backend                     │
│  React Native + Expo + TS    │ ◀─────────────────────────────── │  Java 21 + Spring Boot 3.3.5 │
│  (Android, iOS)              │     JWT no header Authorization  │  Spring Web / Security / JPA │
└──────────────────────────────┘                                  └──────────────┬───────────────┘
                                                                                 │ JDBC / Hibernate
                                                                                 ▼
                                                                  ┌──────────────────────────────┐
                                                                  │  PostgreSQL 18               │
                                                                  └──────────────────────────────┘
                                                                                 │ SMTP (Gmail)
                                                                                 ▼
                                                                  E-mails de verificação e recuperação
```

- O app só cuida de interface e estado de sessão. Toda regra de negócio, validação e autorização fica no backend.
- O backend é **stateless**: cada requisição carrega o JWT; não há sessão em servidor.
- O banco é criado e atualizado pelo Hibernate (`ddl-auto=update`), sem scripts de migração manuais.

### 3.2 Tecnologias e versões

| Camada | Tecnologia | Versão | Uso |
|---|---|---|---|
| Backend | Java | 21 | Linguagem |
| Backend | Spring Boot | 3.3.5 | Base do projeto |
| Backend | Spring Web (MVC) | via Boot | Cria a API REST (`@RestController`) |
| Backend | Spring Data JPA + Hibernate | via Boot | Persistência |
| Backend | Spring Security | 6.x via Boot | Autenticação/autorização, BCrypt |
| Backend | JJWT | 0.12.6 | Tokens JWT (HS256) |
| Backend | Spring Boot Actuator | via Boot | `/actuator/health`, `/actuator/info` |
| Backend | Spring Mail | via Boot | SMTP Gmail |
| Backend | Bean Validation (JSR 380) | via Boot | Validação declarativa dos DTOs |
| Backend | Maven Wrapper | 3.9.16 | Build (`./mvnw`) |
| Banco | PostgreSQL | 18 (Docker `postgres:18-alpine` ou instalação nativa) | Dados |
| App | Expo SDK | 57 | Toolchain, Expo Go, prebuild |
| App | React Native | 0.86.2 | UI nativa |
| App | React | 19 | Componentes |
| App | TypeScript | 6 | Tipagem |
| App | React Navigation | 7 | Stack + bottom tabs |
| App | expo-constants | ~57.0.18 | Descoberta do host do Expo |
| App | expo-network | ~57.0.2 | IP do aparelho para varredura da rede |
| App | @react-native-async-storage/async-storage | 2.2.0 | Persistir host do servidor |
| App | react-native-safe-area-context | ~5.7.0 | Área segura: cabeçalhos e barras de abas fora da barra de status e da barra de gestos |
| App | expo-build-properties | ~57.0.17 | Permitir HTTP em build release Android |
| Infra | Docker Compose | — | Postgres local opcional |

---

## 4. Estrutura do repositório

```text
Projeto-SpotMeet/
├── backend/                         # API Java / Spring Boot
│   ├── .env                         # segredos locais (NÃO versionado)
│   ├── .env.example                 # modelo das variáveis
│   ├── pom.xml
│   └── src/main/java/com/spotmeet/backend/
│       ├── BackendApplication.java  # main + bootstrap do SysAdmin
│       ├── config/SecurityConfig.java
│       ├── controller/              # Auth, User, Organization, Membership, Committee, Admin
│       ├── service/                 # User, Organization, Membership, Committee, Admin, Email
│       ├── repository/              # Spring Data JPA (7 repositórios)
│       ├── model/                   # 7 entidades JPA
│       ├── dto/                     # 25 DTOs de entrada/saída
│       ├── security/                # JwtUtil, JwtAuthFilter, UserDetailsServiceImpl, LgpdMaskUtil
│       └── exception/GlobalExceptionHandler.java
├── SpotMeetApp/                     # App React Native / Expo
│   ├── App.tsx                      # navegação, carregamento do host da API
│   ├── app.json                     # config Expo (plugins, bundle ids, ícone adaptativo)
│   ├── assets/                      # ícones do app e splash
│   ├── eas.json                     # perfis de build EAS
│   ├── .env.example
│   ├── android/                     # projeto nativo gerado por prebuild (NÃO versionado)
│   ├── ios/                         # projeto nativo gerado por prebuild (NÃO versionado)
│   └── src/
│       ├── config/api.ts            # resolução da URL da API
│       ├── config/discovery.ts      # varredura da rede local
│       ├── context/AuthContext.tsx  # sessão (token, papel, usuário)
│       ├── context/ThemeContext.tsx # tema claro/escuro
│       ├── hooks/useLiveSync.ts     # atualização em tempo real
│       ├── utils/                   # alerts, toast, validators, syncBus
│       ├── components/auth/         # AuthLayout, DevOtpBanner, ServerSettings
│       ├── components/ui/           # ToastHost (notificações do app)
│       └── screens/                 # Auth, Organization, Settings, admin/*
├── imagens/
├── documentacao_auditoria_seguranca_academica.md
├── guia_inicializacao_ambiente.pdf
├── docker-compose.yml               # PostgreSQL 18
├── .gitignore
└── DOCUMENTACAO.md                  # este arquivo
```

---

## 5. Perfis, papéis e permissões

### 5.1 Papéis globais (campo `users.role`)

| Papel | Quem | O que pode |
|---|---|---|
| `USER` | Qualquer conta cadastrada | Usar o app, criar organizações, participar de organizações e comissões |
| `ADMIN` | Reservado | Mesmas rotas administrativas que SysAdmin (previsto para expansão) |
| `SYSADMIN` | Conta única criada no startup | Aprovar/rejeitar/bloquear organizações, ver status e relatórios, reiniciar e resetar o sistema, acessar Actuator |

### 5.2 Papéis dentro de uma organização (`organization_members.role`)

| Papel | Como obtém | Poderes |
|---|---|---|
| `ORG_OWNER` | Criador da organização | Tudo: aprova acessos, promove/rebaixa, revoga, cria/exclui comissões, exclui a organização |
| `ORG_VICE_OWNER` | Promovido pelo dono | Gerencia membros e comissões |
| `ORG_SUBOWNER` | Promovido por dono/vice | Gerencia membros e comissões |
| `MEMBER` | Acesso aprovado | Vê a organização, solicita entrada em comissões |

### 5.3 Papéis dentro de uma comissão (`committee_members.role`)

| Papel | Descrição |
|---|---|
| `COMMITTEE_ADMIN` | Líder da comissão |
| `MEMBER` | Integrante |

### 5.4 Status usados no sistema

| Contexto | Valores |
|---|---|
| Organização (`organizations.status`) | `PENDING`, `APPROVED`, `REJECTED`, `BLOCKED` |
| Solicitação de acesso | `PENDING`, `APPROVED`, `REJECTED` |
| Vínculo com comissão | `PENDING_USER_ACCEPTANCE` (convite), `PENDING_LEADER_APPROVAL` (solicitação), `ACTIVE`, `DECLINED` |
| Disponibilidade do usuário | `PRESENT`, `ONLINE`, `BUSY`, `AWAY` |
| Tema preferido | `LIGHT`, `DARK`, `SYSTEM` |

---

## 6. Funcionalidades por requisito

### RF1 — Cadastro de usuário e gestão de membros

**Cadastro e verificação**

1. Usuário informa nome, e-mail e senha na tela de cadastro.
2. Backend valida (senha com minúscula, maiúscula e dígito, mínimo 6 caracteres), grava a senha com BCrypt e gera um código OTP de 6 dígitos com validade.
3. Um e-mail com o código é enviado pelo Gmail. Se o envio falhar, a resposta traz `devCode` para não travar o desenvolvimento.
4. Usuário digita o código na tela de verificação. Conta passa a `emailVerified = true`. Há reenvio de código.
5. Login só é permitido com e-mail verificado; caso contrário o backend responde `EMAIL_NOT_VERIFIED` e o app leva à tela de verificação.

**Solicitação de acesso a organização**

1. Usuário digita a chave de acesso da organização (formato `#Nome`, gerada pelo dono).
2. Cria-se uma `AccessRequest` com status `PENDING`. Não é possível duplicar solicitação nem solicitar em organização não aprovada.
3. Dono, vice ou subdono aprovam ou rejeitam. Aprovar cria o vínculo `OrganizationMember` com papel `MEMBER`.

**Gestão de membros**

- Listar membros e solicitações pendentes.
- Promover (`MEMBER → ORG_SUBOWNER → ORG_VICE_OWNER`) e rebaixar.
- Revogar acesso, respeitando a hierarquia: subdono remove só membros; vice remove membros e subdonos; dono (e SysAdmin) remove qualquer um, exceto a si mesmo.
- Toda ação gera registro em `audit_logs`.

### RF2 — Cadastro de organização

1. Usuário cria organização informando nome e chave de acesso (`^#[A-Za-z0-9_\-]{2,50}$`); CNPJ é opcional.
2. Criador vira `ORG_OWNER` automaticamente. Organização nasce com `status = PENDING` e `approved = false`.
3. SysAdmin vê a fila de pendentes, aprova ou rejeita. Também pode mudar o status depois (`APPROVED`, `REJECTED`, `BLOCKED`).
4. Somente organizações aprovadas aparecem para os membros e aceitam solicitações de acesso.
5. O dono ou vice pode **editar** nome, chave e CNPJ em `PUT /api/organizations/{id}`. `approved`, `status` e `owner` nunca vêm do corpo.
6. O dono pode excluir a organização (cascata em membros, solicitações e comissões).
6. O app mostra, em tempo real, quando a organização foi aprovada (polling de 4 s).

### RF3 — Cadastro de comissão

1. Dono, vice ou subdono criam comissões dentro de uma organização aprovada (nome `^[A-Za-z0-9_-]{2,50}$`, descrição opcional).
2. **Convite**: um admin convida um membro da organização; o vínculo nasce `PENDING_USER_ACCEPTANCE`. O convidado aceita ou recusa na sua lista de convites.
3. **Solicitação**: um membro pede para entrar; o vínculo nasce `PENDING_LEADER_APPROVAL`. Um admin aprova ou rejeita.
4. Líderes **editam** nome e descrição em `PUT /api/committees/{id}`; o nome segue único dentro da organização e a comissão nunca muda de organização.
5. Admins removem integrantes e excluem comissões.
6. Consulta de quais comissões um membro participa.

### RF4 — Configurações de usuário

| Função | Como funciona |
|---|---|
| Dados pessoais | Nome, telefone, bio e status de disponibilidade em `PUT /api/users/me` |
| Tema | `LIGHT` ou `DARK`, aplicado no app na hora e persistido no backend |
| Troca de senha (logado) | `PUT /api/users/me/password` com senha atual e nova; recusa senha atual errada e nova igual à atual |
| Recuperação de conta | Fluxo de **3 telas**: (1) e-mail → `forgot-password` gera o OTP; (2) código → `verify-recovery-code` confere sem consumir, com "Alterar e-mail" e reenvio travado por 60 s; (3) nova senha + confirmação → `reset-password` valida de novo e grava. Todos os códigos valem **5 minutos** |
| Troca de e-mail em duas etapas | (1) pede troca → código vai para o e-mail **atual**; (2) confirma o código → informa novo e-mail → código vai para o **novo** e-mail; (3) confirma o segundo código → e-mail trocado, novo JWT emitido |
| Sair | Limpa a sessão em memória |

### RF5 — Informações do sistema (SysAdmin)

| Função | Como funciona |
|---|---|
| Setup inicial | No startup, `ApplicationRunner` cria `sysadmin@spotmeet.com` / `Admin@123` se não existir |
| Status | `GET /api/admin/system/status`: estado geral, versão, uptime e três subsistemas — **Banco** (conexão do pool), **Rede** (endereço, host e porta reais do servidor) e **Recursos** (memória e CPUs da JVM) |
| Relatórios | `GET /api/admin/reports`: totais de usuários, verificados, por papel, organizações, comissões, logs e as atividades recentes com e-mail mascarado |
| Reiniciar | `POST /api/admin/system/restart`: registra auditoria e roda GC. **Não reinicia subsistema individual** — ver a ressalva na seção 2.3 |
| Reset total | `POST /api/admin/system/reset` com `confirmation = CONFIRM_FULL_RESET`: apaga todas as organizações, comissões, vínculos e usuários comuns; preserva o SysAdmin |
| Aprovação de organizações | Listar todas ou só pendentes; aprovar, rejeitar, alterar status |

---

## 7. Modelo de dados e dicionário

Sete entidades JPA. Nomes de tabela e coluna em inglês, gerados pelo Hibernate.

### 7.1 `users` — User

| Coluna | Tipo | Regras |
|---|---|---|
| id | bigint PK | auto |
| name | varchar | obrigatório |
| email | varchar | único, obrigatório |
| phone | varchar | opcional |
| password | varchar | hash BCrypt, nunca exposto |
| role | varchar | `USER`, `ADMIN`, `SYSADMIN`; padrão `USER` |
| email_verified | boolean | padrão `false` |
| availability_status | varchar (enum) | padrão `PRESENT` |
| preferred_theme | varchar (enum) | padrão `DARK` |
| bio | varchar | opcional |
| verification_token / verification_token_expires_at | varchar / timestamp | OTP de verificação de conta |
| recovery_token / recovery_token_expires_at | varchar / timestamp | OTP de recuperação de senha |
| current_email_change_token / current_email_change_token_expires_at | varchar / timestamp | etapa 1 da troca de e-mail |
| pending_new_email | varchar | e-mail aguardando confirmação |
| new_email_token / new_email_token_expires_at | varchar / timestamp | etapa 2 da troca de e-mail |

### 7.2 `organizations` — Organization

| Coluna | Tipo | Regras |
|---|---|---|
| id | bigint PK | auto |
| name | varchar | obrigatório |
| access_key | varchar | única, formato `#...` |
| owner_id | FK users | criador |
| cnpj | varchar | opcional |
| approved | boolean | padrão `false` |
| status | varchar | `PENDING` (padrão), `APPROVED`, `REJECTED`, `BLOCKED` |
| created_at | timestamp | |

### 7.3 `organization_members` — OrganizationMember

| Coluna | Tipo | Regras |
|---|---|---|
| id | bigint PK | |
| user_id | FK users | |
| organization_id | FK organizations | |
| role | varchar (enum) | `ORG_OWNER`, `ORG_VICE_OWNER`, `ORG_SUBOWNER`, `MEMBER` |

### 7.4 `access_requests` — AccessRequest

| Coluna | Tipo | Regras |
|---|---|---|
| id | bigint PK | |
| user_id | FK users | solicitante |
| organization_id | FK organizations | |
| status | varchar (enum) | `PENDING`, `APPROVED`, `REJECTED` |
| requested_at | timestamp | |

### 7.5 `committees` — Committee

| Coluna | Tipo | Regras |
|---|---|---|
| id | bigint PK | |
| name | varchar | obrigatório |
| description | varchar | opcional |
| organization_id | FK organizations | |
| created_at | timestamp | |

### 7.6 `committee_members` — CommitteeMember

| Coluna | Tipo | Regras |
|---|---|---|
| id | bigint PK | |
| user_id | FK users | |
| committee_id | FK committees | |
| status | varchar (enum) | `PENDING_USER_ACCEPTANCE`, `PENDING_LEADER_APPROVAL`, `ACTIVE`, `DECLINED` |
| role | varchar (enum) | `COMMITTEE_ADMIN`, `MEMBER` |
| invited_by_id | FK users | quem convidou (nulo em solicitação) |
| requested_at / responded_at | timestamp | |

### 7.7 `audit_logs` — AuditLog (append-only)

| Coluna | Tipo | Regras |
|---|---|---|
| id | bigint PK | |
| user_id | FK users | executor |
| action | varchar | código da ação (lista em 9.5) |
| details | varchar | texto livre, sem dados sensíveis |
| created_at | timestamp | |

### 7.8 Relacionamentos

```text
User 1 ──── N Organization (owner)
User N ──── N Organization  via organization_members (com papel)
User N ──── N Organization  via access_requests (com status)
Organization 1 ──── N Committee
User N ──── N Committee     via committee_members (com status, papel, convidado_por)
User 1 ──── N AuditLog
```

---

## 8. API REST completa

Base: `http://<host>:8080/api`. Rotas autenticadas exigem `Authorization: Bearer <jwt>`.

### 8.1 Autenticação — `/api/auth` (públicas)

| Método | Rota | Corpo | Resposta |
|---|---|---|---|
| POST | `/register` | `{name, email, password}` | `{success, message, devCode?, delivered, channel, destination}` |
| POST | `/verify-email` | `{email, code}` | `{success, message}` |
| POST | `/resend-verification` | `{email}` | `{success, message, devCode?}` |
| POST | `/login` | `{email, password}` | `{token, role, name, email, userId, emailVerified}` ou `400 {error: EMAIL_NOT_VERIFIED}` |
| POST | `/forgot-password` | `{email}` | `{success, message, devCode?}` |
| POST | `/verify-recovery-code` | `{email, token}` | `{success, message}`. Valida **sem consumir** o código (etapa 2 do fluxo de 3 telas) |
| POST | `/reset-password` | `{email, token, newPassword}` | `{success, message}` |

### 8.2 Usuário — `/api/users`

| Método | Rota | Descrição |
|---|---|---|
| GET | `/me` | Perfil (id, name, email, bio, availabilityStatus, preferredTheme, role) |
| PUT | `/me` | Atualiza name, phone, bio, availabilityStatus, preferredTheme |
| PUT | `/me/password` | `{currentPassword, newPassword}` |
| POST | `/me/email-change/request` | Etapa 1: envia código ao e-mail atual |
| POST | `/me/email-change/confirm` | `{code, newEmail}`: valida etapa 1, envia código ao novo e-mail |
| POST | `/me/email-change/complete` | `{code}`: conclui; devolve novo token |

### 8.3 Organizações — `/api/organizations`

| Método | Rota | Quem | Descrição |
|---|---|---|---|
| POST | `/` | usuário | Cria (`{name, accessKey, cnpj?}`) |
| GET | `/mine` | usuário | Organizações em que participa, com papel e status |
| PUT | `/{id}` | dono/vice | Edita (`{name, accessKey, cnpj?}`). `approved`, `status` e `owner` nunca vêm do corpo; `cnpj` ausente preserva o valor gravado |
| DELETE | `/{id}` | dono | Exclui |
| GET | `/{id}/access-requests` | admins da org | Solicitações pendentes |
| GET | `/{id}/members` | membros | Lista de membros |
| DELETE | `/{id}/members/{memberId}` | admins da org | Revoga acesso |
| POST | `/{id}/members/{userId}/promote` | dono/vice | Promove |
| POST | `/{id}/members/{userId}/demote` | dono/vice | Rebaixa |
| POST | `/{id}/committees` | admins da org | Cria comissão |
| GET | `/{id}/committees` | membros | Lista comissões |
| GET | `/{id}/members/{userId}/committees` | membros | Comissões de um membro |
| GET | `/{id}/committees/join-requests` | admins da org | Solicitações pendentes de todas as comissões |

### 8.4 Solicitações de acesso — `/api/access-requests`

| Método | Rota | Descrição |
|---|---|---|
| POST | `/` | `{accessKey}`: solicita entrada |
| POST | `/{id}/approve` | Aprova (admins da org) |
| POST | `/{id}/reject` | Rejeita (admins da org) |

### 8.5 Comissões — `/api/committees`

| Método | Rota | Descrição |
|---|---|---|
| PUT | `/{id}` | Edita nome e descrição (`{name, description?}`), só líderes da organização |
| DELETE | `/{id}` | Exclui comissão |
| POST | `/{id}/members/{userId}` | Convida membro |
| DELETE | `/{id}/members/{userId}` | Remove integrante |
| GET | `/invitations` | Convites pendentes do usuário logado |
| POST | `/{id}/invitations/respond?accept=true|false` | Aceita/recusa convite |
| POST | `/{id}/join-requests` | Solicita entrada |
| GET | `/{id}/join-requests` | Solicitações pendentes da comissão |
| POST | `/join-requests/{membershipId}/respond?approve=true|false` | Aprova/rejeita solicitação |

### 8.6 Administração — `/api/admin` (ADMIN ou SYSADMIN)

| Método | Rota | Descrição |
|---|---|---|
| GET | `/organizations` | Todas as organizações |
| GET | `/organizations/pending` | Pendentes |
| POST | `/organizations/{id}/approve` | Aprova |
| POST | `/organizations/{id}/reject` | Rejeita |
| PATCH | `/organizations/{id}/status` | `{approved, justification?}` |
| GET | `/system/status` | Status dos subsistemas (banco, rede, recursos) |
| GET | `/reports` | Relatórios e auditoria recente |
| POST | `/system/restart` | Reinício lógico |
| POST | `/system/reset` | `{confirmation: "CONFIRM_FULL_RESET", reason?}` |

### 8.7 Actuator

| Rota | Acesso | Uso |
|---|---|---|
| `GET /actuator/health` | público | Saúde |
| `GET /actuator/info` | público | Devolve `{"app":{"name":"SpotMeet"}}`; usado pelo app para achar o backend na rede |
| demais | SYSADMIN | métricas, env, loggers |

### 8.8 Padrão de erros

`GlobalExceptionHandler` converte erros de validação dos DTOs em `400` e violações de regra de negócio (`IllegalArgumentException`, `IllegalStateException`) em `409`, sempre com `{success:false, message, status}`. Alguns controllers tratam a exceção localmente e respondem `400` (ex.: troca de senha). Acesso indevido responde `403`; token ausente ou inválido, `401`.

---

## 9. Segurança

### 9.1 Autenticação

- **JWT HS256** (JJWT 0.12.6), chave de 256 bits em `JWT_SECRET`, expiração configurável (`JWT_EXPIRATION_MS`, padrão 24 h).
- `JwtAuthFilter` lê o header, valida e carrega o usuário; sem sessão em servidor.
- Token guardado **apenas em memória** no app (AuthContext). Fechar o app encerra a sessão.

### 9.2 Senhas

- BCrypt com fator de custo padrão do Spring Security. Nenhuma senha em texto claro.
- Regra única em cadastro, reset e troca: `^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{6,}$` (aceita caracteres especiais).

### 9.3 Autorização (RBAC + verificação por objeto)

- Rotas `/api/admin/**` exigem `ADMIN` ou `SYSADMIN`; `/actuator/**` exige `SYSADMIN`; `/api/auth/**`, `/actuator/health` e `/actuator/info` são públicas; o resto exige JWT.
- Dentro dos serviços, cada operação verifica se o usuário autenticado tem o papel necessário **naquela organização/comissão** (proteção contra IDOR/BOLA). Testado: membro comum recebe `403` ao tentar aprovar acesso, promover ou criar comissão.

### 9.4 Validação de entrada

- DTOs com Bean Validation (`@NotBlank`, `@Email`, `@Pattern`, `@Size`).
- Chaves de organização e nomes de comissão com regex estrita.
- Nenhuma entidade JPA é recebida diretamente no controller (evita mass assignment).

### 9.5 Auditoria e LGPD

- **Validade dos códigos de uso único:** 5 minutos para todos (verificação de cadastro, recuperação de senha e as duas etapas da troca de e-mail). Valor único na constante `UserService.CODE_EXPIRATION_MINUTES`, refletido nos textos dos e-mails.
- Toda ação relevante grava em `audit_logs` (append-only). Ações: `USER_REGISTERED`, `ACCOUNT_VERIFIED`, `VERIFICATION_RESENT`, `PASSWORD_RECOVERY_REQUESTED`, `PASSWORD_RESET`, `PASSWORD_CHANGED`, `PROFILE_UPDATED`, `LGPD_EMAIL_CHANGE_REQUESTED`, `LGPD_EMAIL_CHANGE_STEP1_CONFIRMED`, `LGPD_EMAIL_CHANGED`, `ORGANIZATION_CREATED`, `ORGANIZATION_UPDATED`, `ORGANIZATION_DELETED`, `ACCESS_REQUESTED`, `ACCESS_APPROVED`, `ACCESS_REJECTED`, `ACCESS_REVOKED`, `ROLE_PROMOTED_SUBOWNER`, `ROLE_PROMOTED_VICE_OWNER`, `ROLE_DEMOTED_SUBOWNER`, `ROLE_DEMOTED_MEMBER`, `COMMITTEE_CREATED`, `COMMITTEE_UPDATED`, `COMMITTEE_DELETED`, `COMMITTEE_INVITATION_SENT`, `COMMITTEE_INVITATION_ACCEPTED`, `COMMITTEE_INVITATION_DECLINED`, `COMMITTEE_JOIN_REQUESTED`, `COMMITTEE_JOIN_APPROVED`, `COMMITTEE_JOIN_REJECTED`, `COMMITTEE_MEMBER_REMOVED`, `ADMIN_ORGANIZATIONS_LISTED`, `ADMIN_PENDING_ORGANIZATIONS_LISTED`, `ADMIN_ORGANIZATION_APPROVED`, `ADMIN_ORGANIZATION_REJECTED`, `ADMIN_ORGANIZATION_STATUS_CHANGED`, `ADMIN_REPORTS_VIEWED`, `ADMIN_SYSTEM_RESTART`, `ADMIN_SYSTEM_FULL_RESET`.
- `LgpdMaskUtil` mascara e-mails nos relatórios (`jo***@gmail.com`).
- Troca de e-mail exige prova de posse dos dois endereços (dois OTPs).

### 9.6 Segredos e configuração

- `application.properties` não contém nenhum segredo; tudo vem de `backend/.env` via `spring.config.import`.
- `.env` está no `.gitignore`; `.env.example` documenta as chaves.
- **Atenção**: o histórico do Git anterior a este trabalho continha senha do banco, chave JWT e senha do Gmail. Recomenda-se trocar a senha de app do Gmail e a chave JWT antes de tornar o repositório público.

### 9.7 Ações críticas

- Reset total exige o código `CONFIRM_FULL_RESET` no corpo e papel SysAdmin.
- Reinício exige confirmação no app (`RESTART_SYSTEM`) e papel SysAdmin.

---

## 10. Aplicativo mobile

### 10.1 Navegação

```text
Stack (headerShown: false)
├── Auth (modos: login | register | verify | forgot | reset)   ← AuthScreen
└── Main
    ├── UserTabs   : Organization | Settings
    └── AdminTabs  : AdminApprovals | AdminOrganizations | AdminReports | AdminControls
```

A escolha entre `UserTabs` e `AdminTabs` é feita pelo papel retornado no login.

### 10.2 Telas

| Tela | Arquivo | Conteúdo |
|---|---|---|
| Autenticação | `screens/AuthScreen.tsx` | Login, cadastro, verificação OTP, esqueci a senha, redefinir senha, rodapé "Servidor" |
| Organização | `screens/OrganizationScreen.tsx` | Criar organização, solicitar acesso por chave, lista de organizações, membros, solicitações, promover/rebaixar/revogar, comissões, convites, solicitações de comissão, modal de comissões do integrante |
| Ajustes | `screens/SettingsScreen.tsx` | Foto/avatar, nome, telefone, bio, status de disponibilidade, tema, alterar senha, troca de e-mail em duas etapas (modal), sair |
| Aprovações / Organizações (admin) | `screens/admin/AdminOrganizationsScreen.tsx` | Fila de pendentes e lista completa com aprovar, rejeitar, alterar status |
| Monitoramento & Relatórios | `screens/admin/AdminMonitoringReportsScreen.tsx` | Status dos subsistemas, memória, contadores, atividades recentes |
| Controles do Sistema | `screens/admin/AdminControlsScreen.tsx` | Reiniciar (modal com código) e reset total (modal com código) |


**Fluxo de recuperação de conta (3 telas, revisado em 13/09/2026):**

1. **E-mail** — apenas o campo de e-mail; envia o código.
2. **Código de Verificação** — apenas o OTP, com "Alterar e-mail" (volta à etapa 1) e "Reenviar e-mail" bloqueado por 60 segundos. O código é conferido em `POST /api/auth/verify-recovery-code` antes de liberar a etapa 3.
3. **Nova Senha** — nova senha e confirmação, **cada campo com o seu próprio botão de exibir/ocultar**.

**Ajustes da conta:** o card "Segurança" (troca de senha, três olhinhos independentes) fica logo abaixo dos dados pessoais. O campo de e-mail é rotulado apenas "E-mail".

**Área segura (safe area):** `SafeAreaProvider` na raiz; cabeçalhos usam `insets.top` e as barras de abas somam `insets.bottom`. Sem isso, no edge-to-edge do Android o cabeçalho ficava atrás da barra de status e a barra de gestos cobria os rótulos das abas (reproduzido em Galaxy S24 Ultra e Motorola).

**Painel do SysAdmin:** o status mostra três subsistemas — **Banco**, **Rede** e **Recursos**.

**Ícone do app (corrigido em 14/09/2026):** o Android usa **ícone adaptativo** — o launcher amplia a camada da frente em 50% e a recorta com a máscara da fabricante (círculo no Pixel, *squircle* na Samsung). Só o centro, 66 de 108 unidades (~61% do canvas), é garantido visível.

- `assets/android-icon-foreground.png` — **apenas a arte branca**, sobre fundo transparente, centralizada e dimensionada para caber na zona segura.
- `app.json` → `android.adaptiveIcon.backgroundColor` = `#901ECE` (o roxo da marca), que é o que aparece atrás da arte.
- `assets/icon.png` — ícone cheio, quadrado, usado pelo iOS e pelas demais plataformas, que não aplicam esse recorte.

Problema que isso resolveu: o `android-icon-foreground.png` era uma **cópia byte a byte** do `icon.png`, ou seja, o ícone quadrado inteiro servindo de camada frontal. A arte ocupava de 270 a 880 na horizontal e de 52 a 854 na vertical, contra uma zona segura de 199 a 824 — estourando 147px no topo, 56px à direita e 30px embaixo. No aparelho, a câmera e o balão apareciam cortados em cima e o "S" cortado embaixo. Sites de pré-visualização de ícone não reproduzem o recorte, por isso o problema só apareceu no celular.

**Notificações (revisado em 14/09/2026):** mensagens informativas não usam mais a caixa de diálogo do sistema. Aparecem como notificação na parte de baixo da tela, na identidade do app (cor de destaque por tipo, ícone e tema claro/escuro), e somem sozinhas em 3,5 s — com botão de fechar.

- `components/ui/ToastHost.tsx` fica montado na raiz, acima de todas as telas.
- `utils/toast.ts` guarda o handler em módulo, então `showAlert()` continua sendo uma função comum: as **67 chamadas existentes** passaram a virar notificação sem alterar nenhuma tela (o comentário no código-fonte diz "140+", o que é impreciso — contagem real via grep em 15/09/2026).
- O tipo (sucesso, erro, atenção, informação) é deduzido do título já usado nas chamadas.
- **Confirmações continuam em diálogo modal** (`confirmAction` e os `Alert.alert` de exclusão, promoção e logout): uma notificação não tem botões e a escolha do usuário não pode sumir sozinha.
- Se o host ainda não estiver montado, `showAlert` cai no diálogo nativo — nenhuma mensagem se perde.
### 10.3 Estado e contextos

- **AuthContext**: `{token, role, name, email, userId, emailVerified}`, `signIn`, `signOut`, `updateSession`.
- **ThemeContext**: `theme` (`LIGHT` | `DARK`), paleta `colors`, `setTheme(theme, persistToBackend)`. Carrega a preferência do backend após login.
- **Sincronização em tempo real** (`hooks/useLiveSync.ts` + `utils/syncBus.ts`): polling silencioso a cada 4 s enquanto a tela está em foco, mais eventos locais (`ORGANIZATIONS_MUTATED`, `MEMBERS_MUTATED`, `REQUESTS_MUTATED`, `COMMITTEES_MUTATED`, `ADMIN_ORGS_MUTATED`, `MONITORING_REFRESH`, `PROFILE_MUTATED`, `VISIBILITY_RESTORED`) para atualizar na hora após uma ação.

### 10.4 Como o app encontra o backend (`config/api.ts` e `config/discovery.ts`)

Ordem de resolução do host, porta padrão 8080:

1. Host digitado pelo usuário em "Servidor" na tela de login (salvo no aparelho).
2. `EXPO_PUBLIC_API_HOST` do `.env` (fixado no build).
3. Host que serviu o bundle JS (Expo Go / Metro): **funciona sozinho em qualquer rede** quando se usa Expo Go.
4. **Varredura da rede local** (só em APK/IPA instalados): o app lê o próprio IP, testa os 254 endereços da sub-rede na porta 8080 em lotes de 64 com timeout de 1,2 s e aceita o primeiro que responder `{"app":{"name":"SpotMeet"}}` em `/actuator/info`. O IP encontrado fica em cache. Medido na rede de desenvolvimento: menos de 4 s.
5. Padrões: `10.0.2.2` no emulador Android, `localhost` nos demais.

### 10.5 Validações no app (`utils/validators.ts`)

E-mail, regra de senha idêntica à do backend com feedback por regra, código OTP de 6 dígitos.

### 10.6 Textos e idioma

Código, arquivos, variáveis, rotas e chaves JSON em **inglês**. Interface, mensagens de erro, e-mails, commits e branches em **português**.

---

## 11. Ambiente de desenvolvimento e execução

### 11.1 Pré-requisitos

| Ferramenta | Versão |
|---|---|
| JDK | 21 |
| Node.js | 20+ (usado 24) |
| PostgreSQL | 18 (Docker ou nativo) |
| Docker Desktop | opcional, para o `docker-compose.yml` |
| Expo Go | no celular (Android/iOS) |
| Android Studio | opcional, para gerar APK |

### 11.2 Variáveis de ambiente do backend (`backend/.env`)

```properties
SERVER_PORT=8080
DB_URL=jdbc:postgresql://localhost:5432/spotmeet_db
DB_USERNAME=postgres
DB_PASSWORD=<senha>
JWT_SECRET=<base64 de 32+ bytes: openssl rand -base64 48>
JWT_EXPIRATION_MS=86400000
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=<conta gmail>
MAIL_PASSWORD=<senha de app do gmail>
```

Copie `backend/.env.example` para `backend/.env` e preencha. O arquivo real nunca deve ser commitado.

### 11.3 Subir o banco

Docker (recomendado no Linux/WSL):

```bash
docker compose up -d        # cria o container spotmeet-postgres com o banco spotmeet_db
```

Nativo no Windows: instalar PostgreSQL 18, criar o banco `spotmeet_db` e ajustar `DB_PASSWORD`.

### 11.4 Subir o backend

```bash
cd backend
./mvnw spring-boot:run      # Windows: mvnw.cmd spring-boot:run
```

Na primeira execução o Hibernate cria as 7 tabelas e o `ApplicationRunner` cria o SysAdmin:

```text
E-mail: sysadmin@spotmeet.com
Senha:  Admin@123
```

Verificação rápida: `curl http://localhost:8080/actuator/info` deve devolver o nome SpotMeet.

### 11.5 Subir o app com Expo Go (recomendado para a apresentação)

```bash
cd SpotMeetApp
npm install
npx expo start
```

Escaneie o QR code com o Expo Go (Android) ou a câmera (iOS). Celular e computador precisam estar **na mesma rede Wi-Fi** ou no hotspot do celular. O app descobre o backend sozinho a partir do servidor do Expo.

### 11.6 Gerar APK instalável (Android)

```bash
cd SpotMeetApp
npx expo prebuild --platform android --clean   # gera a pasta android/
```

Depois, abrir `SpotMeetApp/android` no Android Studio e gerar o APK (Build → Build APK), ou `cd android && ./gradlew assembleRelease`. O APK acha o backend pela varredura da rede; se não achar, use o rodapé "Servidor" na tela de login.

Em builds iOS instalados (requer Mac/EAS) o comportamento é o mesmo; no iPhone sem Mac usa-se o Expo Go.

### 11.7 Rede: WSL2 no Windows

Para o celular alcançar o backend rodando no WSL2:

1. `C:\Users\<usuário>\.wslconfig` com `networkingMode=mirrored` na seção `[wsl2]`, seguido de `wsl --shutdown`.
2. Regras de firewall de entrada TCP para 8080 e 8081 (`New-NetFirewallRule`).
3. Liberar a entrada no firewall do Hyper-V para o WSL (necessário quando a rede está como "Pública"):

```powershell
Set-NetFirewallHyperVVMSetting -Name '{40E0AC32-46A5-438A-A0B2-2B479E8F2E90}' -DefaultInboundAction Allow
```

4. Fechar programas que ocupem a porta 8080 (ex.: NVIDIA Broadcast).

Plano B em redes de faculdade com isolamento entre clientes: hotspot do celular com o notebook conectado nele.

### 11.8 Ler códigos OTP sem abrir o e-mail

Os códigos ficam no banco enquanto válidos, o que ajuda em demonstrações:

```sql
SELECT email, verification_token, recovery_token, current_email_change_token, new_email_token FROM users;
```

---

## 12. Testes

### 12.1 Roteiro ponta a ponta já executado (via API, contra Postgres 18 real)

Todos os passos abaixo passaram em 13/09/2026 após a renomeação para inglês:

1. Cadastro com envio de e-mail real, verificação, login, bloqueio de login sem verificação.
2. Perfil: leitura, atualização de dados, tema persistido.
3. Troca de senha logado: senha atual errada recusada, nova igual recusada, sucesso e login com a nova.
4. Recuperação de senha por token.
5. Troca de e-mail em duas etapas com os dois códigos.
6. Organização: criação, pendente invisível, aprovação pelo SysAdmin, listagem `/mine`, exclusão.
7. Solicitação de acesso por chave, aprovação, promoção a subdono e vice, rebaixamento, revogação.
8. Comissões: criação, convite, aceite, solicitação, aprovação, remoção, exclusão.
9. IDOR: membro comum recebe 403 ao aprovar acesso, promover ou criar comissão em organização alheia.
10. Admin: status, relatórios com e-mail mascarado, reinício, reset total preservando o SysAdmin.

### 12.1.1 Edição de organização e de comissão (13/09/2026)

Executado contra o Postgres 18 real, numa segunda instância do backend na porta 8099 para não derrubar a instância de desenvolvimento. Dados de teste removidos do banco ao final.

| # | Caso | Resultado |
|---|---|---|
| 1 | `PUT /api/organizations/{id}` renomeando nome e chave | 200, dados atualizados |
| 2 | CNPJ ausente no corpo | preservado no banco (não foi apagado) |
| 3 | Corpo com `approved:true`, `status:APPROVED`, `ownerId:999` | ignorados; continuou `PENDING`, `approved=false`, dono inalterado |
| 4 | Chave já usada por outra organização | 409 |
| 5 | Chave fora do padrão (sem `#`) | 400 |
| 6 | Sem token | 403 |
| 7 | `PUT /api/committees/{id}` renomeando nome e descrição | 200 |
| 8 | Renomear a comissão para o próprio nome | 200 (não colide consigo mesma) |
| 9 | Nome já usado por outra comissão da mesma organização | 409 |
| 10 | Nome com espaço | 400 |
| 11 | Membro comum editando organização | 403 |
| 12 | Membro comum editando comissão | 403, nada alterado no banco |
| 13 | Auditoria | `ORGANIZATION_UPDATED` e `COMMITTEE_UPDATED` gravados com valor antigo → novo |

### 12.1.2 Correções de interface e fluxo de recuperação (13/09/2026)

Backend testado na instância 8099 contra o Postgres real; app verificado com `tsc --noEmit`.

| # | Caso | Resultado |
|---|---|---|
| 1 | `GET /api/admin/system/status` | devolve `database`, `network` e `memory`; `securityCryptography` e `auditLgpd` removidos |
| 2 | Subsistema de rede | reporta endereço, host e porta reais do servidor |
| 3 | `POST /api/auth/verify-recovery-code` com código correto | 200 |
| 4 | Código incorreto | 400 |
| 5 | Validar duas vezes o mesmo código | 200 nas duas — a verificação **não consome** o código |
| 6 | Código expirado | 400 |
| 7 | Código vazio | 400 (Bean Validation) |
| 8 | `POST /api/auth/reset-password` após a refatoração | 200, senha alterada |
| 9 | Login com a nova senha | 200 |
| 10 | Reusar o código após o reset | 400 — o reset continua consumindo o código |
| 11 | Auditoria | `PASSWORD_RESET` gravado |

Observação de ambiente: o container do Postgres roda em UTC e a JVM em -03. Como os prazos de token são gravados e comparados pela própria aplicação (`LocalDateTime`), isso não afeta o sistema — mas testes que alteram `recovery_token_expires_at` por SQL precisam usar a hora local, não `NOW()`.

Observação: id inexistente devolve **409**, não 404, porque o `GlobalExceptionHandler` mapeia `IllegalArgumentException` para 409 em todo o projeto. As rotas DELETE já existentes se comportam igual — é um padrão do projeto, não uma regressão destas rotas.

### 12.2 Testes em aparelhos reais

| Aparelho | Meio | Resultado |
|---|---|---|
| iPhone 13 | Expo Go | Funcionando após ajuste de rede do WSL; **re-teste pendente** com as correções de 13–14/09 |
| Android (Galaxy S24 Ultra) | APK instalado | 13/09: cabeçalho atrás da barra de status e barra de gestos sobre as abas — **corrigido** com safe area; **re-teste pendente** com APK novo |
| Android (Motorola) | APK instalado | 13/09: mesmos problemas de área segura e título espremido — **corrigido**; **re-teste pendente** |

### 12.3 Testes automatizados

- Backend: apenas `BackendApplicationTests` (contexto sobe). Depende de banco e SMTP configurados.
- App: sem testes automatizados.
- Verificações estáticas usadas: `./mvnw compile` e `npx tsc --noEmit`.

### 12.4 Ferramentas previstas (para a documentação de testes)

JUnit 5 + Spring Boot Test + MockMvc no backend; Jest + React Native Testing Library no app; roteiro manual para funcional, usabilidade e compatibilidade.

---

## 13. Histórico do que foi feito

### 13.1 Antes desta fase (código legado em português)

Backend e app com os cinco módulos implementados, exceto troca de senha logado. Java 17, Spring Boot 3.1.5, credenciais dentro de `application.properties`, URL da API fixa em localhost/10.0.2.2, sem `.gitignore` na raiz.

### 13.2 Commits desta fase (branch `refatoracao/codigo-em-ingles`)

| Commit | Conteúdo |
|---|---|
| `587e3c1` | Renomeação completa do backend e do app para inglês (141 arquivos): pacotes, classes, campos, tabelas, colunas, enums, rotas, chaves JSON e ações de auditoria. Java 21 e Spring Boot 3.3.5. `.env` + `.env.example` no backend. `.gitignore` na raiz. `docker-compose.yml` com Postgres 18. Descoberta do host do backend a partir do servidor do Expo. Remoção do script SQL manual e das rotas antigas. |
| `7585549` | Troca de senha para usuário autenticado: `PUT /api/users/me/password`, card "Alterar Senha" em Ajustes, auditoria `PASSWORD_CHANGED`, regex de senha unificada aceitando caracteres especiais. |
| `30507e1` | APK: host do servidor configurável na tela de login e salvo no aparelho; plugin `expo-build-properties` liberando HTTP em release; config de build nativo (`package`, `bundleIdentifier`, `eas.json`). |
| `b8184a4` | Descoberta automática do backend na rede local (varredura da sub-rede via `expo-network`), identificação em `/actuator/info`, cache do IP, botão "Procurar na rede". |
| `232ec44` | Teclado cobrindo campos no Android com edge-to-edge: `KeyboardAvoidingView` com `padding` em todas as telas e modais com entrada de texto. |

### 13.2.1 Commits de 13–14/09/2026

Confirmado em 15/09/2026 que **tudo isto está commitado** (a versão anterior deste documento dizia "ainda não commitado" — estava desatualizada):

| Commit | Área | Mudança |
|---|---|---|
| `bc71781` | Casos de uso do slide | `PUT /api/organizations/{id}` e `PUT /api/committees/{id}` — edição de organização e de comissão, com telas no app |
| `4950011` | Recuperação de conta | Fluxo dividido em 3 telas; rota nova `POST /api/auth/verify-recovery-code`; validade unificada em **5 minutos** (`UserService.CODE_EXPIRATION_MINUTES`) |
| `6e7fe41` | Status do sistema | Subsistema **Rede** criado; **Criptografia** e **Auditoria LGPD** removidos do painel; SysAdmin passa a se chamar "Administrador do Sistema" |
| `d9f1ab3` | Ajustes / recuperação | Reenvio com trava de 60 s, olhinho por campo, selo LGPD removido, "E-mail Atual" → "E-mail", card **Segurança** movido para cima |
| `74a1325` | Layout / notificações | `SafeAreaProvider` e insets reais nas 5 telas e nas 2 barras de abas (corrige Galaxy S24 Ultra e Motorola); `ToastHost` substitui as caixas de diálogo informativas, confirmações seguem modais |
| `04c289f` | Ícone | Camada frontal do ícone adaptativo Android refeita dentro da zona segura; `adaptiveIcon.backgroundColor` corrigido de `#1E1E2E` para `#901ECE` |
| `8f83f2d` | Documentação | Doc de auditoria alinhado ao código; correções de rotas erradas nesta documentação (ver abaixo) |

Erros de documentação corrigidos em 14/09, que já existiam antes desta fase: a rota de relatórios era `/api/admin/reports` e não `/api/admin/system/reports`; a alteração de status é `PATCH` e não `PUT`; e o corpo das ações críticas usa o campo `confirmation`, não `confirmationCode`.

### 13.3 Infraestrutura de rede resolvida

WSL2 em modo espelhado, regras de firewall, liberação do firewall do Hyper-V, conflito de porta com NVIDIA Broadcast identificado.

---

## 14. O que falta

### 14.1 Obrigatório para a entrega de 18/09 (slide 12 do Incremento 1)

Nenhum destes itens existe ainda no repositório:

| # | Entregável | Fonte para produzir | Status |
|---|---|---|---|
| 1 | Diagrama de casos de uso de cada um dos 5 requisitos | Seção 6 deste documento | **Pronto** — gerado em 16/09/2026 em PlantUML, renderizado sem erro. Falta só publicar no repositório oficial (seção 17) |
| 2 | Descrição de cada caso de uso **como requisito funcional**, usando *apenas* o formulário padrão | Seções 2.3 e 6 + modelo do professor | Pendente e **bloqueado**. O slide 12 exige "utilizar apenas o formulário padrão", mas o modelo não está no slide nem no `Doc/` do repositório oficial. Precisa ser pedido ao professor |
| 3 | Diagrama de classes com dicionário de dados | Seção 7 + `model/` | **Pronto** — gerado em 16/09/2026. Falta só publicar no repositório oficial (seção 17) |
| 4 | Diagrama relacional com dicionário de dados | Seção 7 (tabelas reais) | **Pronto** — gerado em 16/09/2026. Falta só publicar no repositório oficial (seção 17) |
| 5 | Descrição dos requisitos não funcionais | Só existem 5 RNFs em `Doc/01-analise-mercado-rnf.md` (repositório oficial); RNF01, RNF02 e RNF04 medem lista de reuniões, pautas e atas, que **não existem no Incremento 1** — logo não são verificáveis contra o código entregue | Pendente. Maior risco de incoerência com o slide 10 |
| 6 | Diagrama de sequência de cada requisito | Seção 6 e 8 | **Pronto** — um diagrama por RF, cobrindo o fluxo mais representativo de cada um (ver ressalva de escopo no `README.md` dos diagramas). Falta só publicar no repositório oficial (seção 17) |

### 14.2 Correções do feedback da etapa AP em `Doc/` (repositório oficial)

- Adicionar concorrentes e pesquisa real com usuários (01).
- Versões das tecnologias (tabela da seção 3.2) e seção sobre Expo (02).
- Explicar que Spring Web cria a API (02).
- Capturas do app real em tema claro e escuro (04).
- Trocar exemplos genéricos por trechos reais de controller, service, DTO e componente (03).
- Definir ferramentas e método de teste (03; ver 12.4).
- Atualizar o documento 03: hoje o código **já** está em inglês e em Java 21, como o documento exige.
- Deixar claro que reuniões, agenda, pautas e atas são visão futura.

### 14.3 Documentos que citam nomes antigos — **concluído em 13/09/2026**

`documentacao_auditoria_seguranca_academica.md` foi alinhado ao código atual:

- versões: `Spring Boot 3.x` → `Spring Boot 3.3.5`, `Java 17+` → `Java 21`;
- rotas: `/api/admin/sistema/reset` → `/api/admin/system/reset`, `/api/usuario/*-troca-email` → `/api/users/me/email-change/{request,confirm,complete}`, `/api/usuarios/me` → `/api/users/me`, `/api/admin/organizacoes/{pendentes,aprovar,rejeitar}` → `/api/admin/organizations/{pending,approve,reject}`, `/api/auth/reenviar-verificacao` → `/api/auth/resend-verification`;
- classes: `AcaoCriticaRequestDTO` → `CriticalActionRequestDTO`, `AdminOrganizacaoDTO` → `AdminOrganizationDTO`, `CadastroRequestDTO` → `RegisterRequestDTO`, `UsuarioRepository/Controller/Service` → `UserRepository/Controller/Service`, `UsuarioOrganizacao` → `OrganizationMember`, `Ajustes.tsx` → `SettingsScreen.tsx`;
- métodos e constantes: `validarSysAdmin` → `validateSysAdmin`, `cadastrarUsuario` → `registerUser`, `isTokenValido` → `isTokenValid`, `CONFIRMAR_RESET_TOTAL` → `CONFIRM_FULL_RESET`, `tipoUsuario` → `role`, ação de auditoria `LGPD_EMAIL_ALTERADO_SUCESSO` → `LGPD_EMAIL_CHANGED`;
- tabelas: `usuarios`/`organizacoes`/`comissoes`/`log_auditoria` → `users`/`organizations`/`committees`/`audit_logs`;
- diagrama de sequência da troca de e-mail reescrito com os nomes reais de método e coluna;
- os 9 links de arquivo apontavam para `a:/IA N MEXER/PROJETO TIAGO/` (caminho local de outra máquina) e estavam **quebrados**; agora são caminhos relativos ao repositório e todos resolvem.

### 14.4 Apresentação

- Documento individual por membro explicando o seu requisito, o diagrama de sequência e onde está no código.
- Cada membro com commits no requisito pelo qual responde.
- Ensaiar a resposta "qual tecnologia Java cria a API".

### 14.5 Repositório

Situação verificada em 15/09/2026 (branch local sincronizada com o GitHub, `git fetch` conferido):

| Item | Situação real |
|---|---|
| `Doc/` + `README.md` da etapa AP | Vivem **só no repositório oficial** (`github.com/Vitor-Guerra495/SpotMeet`), sem mexer neles por aqui. Removidos do controle de versão deste repositório de testes em 16/09/2026 (estavam duplicados em `Docs/`, idênticos ao original — serviam só para eu conferir contra o código) |
| Branch no GitHub | **Mesclada.** `refatoracao/codigo-em-ingles` foi enviada e integrada à `main` via **Pull Request #1** (commit de merge `075b9b2`), por outro membro da equipe. A `main` local estava desatualizada e foi sincronizada nesta verificação |
| Commits de 13–14/09 | **Commitados** — ver a lista de commits em 13.2.1 |
| Merge na `main` | **Concluído** |
| APK avulso | `SpotMeet.apk` (76 MB, gerado em 14/09 às 16:15) está solto na raiz do repositório, **sem controle de versão** e **sem entrada no `.gitignore`** — risco de ser commitado sem querer. Adicionada regra `*.apk` ao `.gitignore` nesta verificação |
| Segredos no histórico | A senha de app do Gmail e a chave JWT estão nos commits `a584ab0` e `9c24e62`, presentes na `main`. O `application.properties` atual está limpo (só variáveis de ambiente), mas o histórico não. Decisão da equipe em 13/09: não trocar agora, por ser ambiente de teste local da faculdade |

> Este repositório é o ambiente de trabalho da equipe, usado para validar antes de publicar a entrega oficial. Por isso esta documentação precisa refletir exatamente o que existe e o que falta. A entrega oficial de 18/09 deve confirmar que este mesmo estado da `main` é o que vai para o GitHub definitivo.

### 14.6 Opcionais / melhorias

- Testes automatizados no backend (MockMvc) e no app (Jest).
- Tornar o teste de contexto do backend independente de Gmail.
- Persistir a sessão no aparelho com armazenamento seguro (hoje o token fica só em memória).
- Papel `ADMIN` global não tem fluxo de criação (só `SYSADMIN` existe na prática).
- Reinicialização **por subsistema** em `POST /api/admin/system/restart` (hoje é global e só roda GC — ver 2.3).
- Novo APK com as correções de 13–14/09 e re-teste no Galaxy S24 Ultra, no Motorola e no iPhone 13.

---

## 15. Problemas conhecidos e decisões tomadas

| Tema | Decisão / situação |
|---|---|
| Idioma do código | Equipe decidiu renomear tudo para inglês **antes** da entrega para bater com a documentação. Feito e testado. |
| Java | Migrado de 17 para 21 (documentação já pedia 21). Spring Boot 3.1.5 → 3.3.5. |
| Segredos | Movidos para `.env`. `application.properties` limpo. |
| Postgres | Docker no ambiente Linux/WSL; instalação nativa no Windows dos colegas. Ambos na versão 18 e compatíveis. |
| Porta 8080 | Mantida. NVIDIA Broadcast ocupa a porta ao iniciar com o Windows e precisa ser fechado. |
| IP do backend | Resolvido em três camadas: Expo Go descobre sozinho; APK varre a rede; campo manual como reserva. |
| Rede da faculdade | Se houver isolamento entre clientes, nada conecta. Usar hotspot do celular. |
| E-mails | Enviados de verdade pelo Gmail (`spotmeeteste@gmail.com`). Sem rede, o backend devolve `devCode` na resposta. |
| Teclado no Android | Edge-to-edge do Expo 57 ignora `adjustResize`; corrigido com `KeyboardAvoidingView` em modo `padding`. |
| Docs | Não alteradas nesta fase por decisão do usuário; serão atualizadas depois. |
| Merge | Nada vai para `main` sem revisão do responsável pelo repositório. |

---

## 16. Glossário português → inglês

Usado na renomeação. Ajuda a ler a documentação antiga contra o código atual.

| Português (antigo) | Inglês (atual) |
|---|---|
| Usuario | User (`users`) |
| Organizacao | Organization (`organizations`), `chave_acesso` → `access_key` |
| Comissao | Committee (`committees`) |
| SolicitacaoAcesso | AccessRequest (`access_requests`) |
| UsuarioOrganizacao | OrganizationMember (`organization_members`) |
| UsuarioComissao | CommitteeMember (`committee_members`) |
| LogAuditoria | AuditLog (`audit_logs`) |
| Papel DONO / VICE_DONO / SUBDONO / MEMBRO | ORG_OWNER / ORG_VICE_OWNER / ORG_SUBOWNER / MEMBER |
| Status PENDENTE / APROVADA / REJEITADA / BLOQUEADA | PENDING / APPROVED / REJECTED / BLOCKED |
| Tema CLARO / ESCURO / SISTEMA | LIGHT / DARK / SYSTEM |
| Disponibilidade PRESENTE / ONLINE / OCUPADO / AUSENTE | PRESENT / ONLINE / BUSY / AWAY |
| AcaoCriticaRequestDTO | CriticalActionRequestDTO |
| `/api/admin/sistema/reset` | `/api/admin/system/reset` |
| `/api/usuarios/me` | `/api/users/me` |
| `/api/organizacoes` | `/api/organizations` |
| `/api/comissoes` | `/api/committees` |
| `codigoConfirmacao` | `confirmation` |
| `sucesso` / `mensagem` | `success` / `message` |
| Tela Configuracoes | SettingsScreen |
| Tela Organizacao | OrganizationScreen |
| Telas Admin* | admin/AdminControlsScreen, AdminMonitoringReportsScreen, AdminOrganizationsScreen |

---

## 17. Plano de publicação no repositório oficial

Definido em 16/09/2026. Cobre como o que está pronto neste repositório de testes chega ao repositório que o professor avalia, e como cada membro consegue ter commits reais no próprio requisito — incluindo no código, não só nos diagramas.

### 17.1 Os dois repositórios

| | Repositório de testes | Repositório oficial |
|---|---|---|
| URL | `github.com/Joao-pedro002/Projeto-SpotMeet` | `github.com/Vitor-Guerra495/SpotMeet` |
| Papel | Onde a equipe desenvolve e valida antes de publicar | O que o professor acessa e avalia |
| Estado em 16/09/2026 | Código completo do Incremento 1, `main` atualizada | Só a documentação da etapa AP (`README.md` + `Doc/`, 7 commits); **nenhum código ainda** |
| Quem pode commitar/dar push nele | Qualquer um da equipe, à vontade | **Só manualmente**, por cada pessoa na própria máquina. Eu (assistente) nunca dou `commit` nem `push` aqui — só leitura (`clone`, `fetch`, `ls-remote`) |

`Doc/` e `README.md` do repositório oficial **não devem ser mexidos** por este trabalho — a cópia idêntica que existia em `Docs/` neste repositório de testes já foi removida do controle de versão e foi para o `.gitignore` (ver seção 14.5).

### 17.2 Passo 1 — Importação inicial (uma vez, manual)

Como o conteúdo não conflita (a doc da AP é idêntica nos dois lados), a importação é só adicionar o código por cima, sem reescrever o histórico:

```bash
# 1. Clonar o oficial numa pasta separada
git clone https://github.com/Vitor-Guerra495/SpotMeet.git spotmeet-oficial
cd spotmeet-oficial

# 2. Copiar o código e a documentação novos, sem mexer em Doc/ e README.md que já estão lá
rsync -av --exclude=.git --exclude=.env --exclude='.env.*' --exclude='!.env.example' \
  --exclude=node_modules --exclude=target --exclude=android --exclude=ios \
  --exclude=.expo --exclude='*.apk' --exclude='*.apk:Zone.Identifier' --exclude=Doc --exclude=README.md \
  /home/funky/Projeto-SpotMeet/ ./

# 3. Conferir que nenhum segredo veio junto (tem que devolver vazio, ou só *.example)
find . -iname ".env*" -not -name "*.example"

# 4. Commit e push, manuais
git add -A
git commit -m "Importar código do Incremento 1 (infraestrutura compartilhada)"
git push origin main
```

### 17.3 Por que o código não dá para ser dividido de forma perfeitamente limpa

O backend Java compila como um módulo só: não existe "mandar só o meu pedaço" de um jeito que o projeto continue funcionando até a próxima pessoa completar o resto. Além disso, vários arquivos atendem mais de um requisito ao mesmo tempo:

- `AuthController.java` e `UserService.java` — cadastro/login (RF1) e recuperação de senha (RF4) no mesmo arquivo.
- `AuthScreen.tsx` — telas de login/cadastro (RF1) e de recuperação (RF4) juntas.
- `OrganizationScreen.tsx` — organização (RF2), comissões (RF3) e lista de membros (RF1) na mesma tela.
- `AdminController.java` e `AdminService.java` — aprovação de organização (RF2) e status/relatórios do sistema (RF5) juntos.

Por isso o plano é em duas camadas: uma base única (passo 17.2, o sistema inteiro, é a única forma de ele compilar) e, por cima dela, **um commit real de cada membro** nos arquivos do próprio requisito — não reenviar o código pronto como se tivesse acabado de escrevê-lo, e sim revisar, ajustar ou melhorar de verdade a própria parte. Combinado com o diagrama (seção 17.5) e a documentação individual (seção 14.4) de cada um, isso dá a cada pessoa **três commits reais** no seu requisito, e ajuda a preparar a defesa oral do slide 10.

### 17.4 Mapa de arquivos por responsável (verificado endpoint a endpoint em 16/09/2026)

Conferido método a método no código: só duas duplas de arquivo atendem dois requisitos ao mesmo tempo (`AuthController`+`UserService`, entre RF1 e RF4; `AdminController`+`AdminService`, entre RF2 e RF5). Os outros quatro controllers são de um requisito só, sem ambiguidade.

| Responsável | Requisito | Backend, exclusivo do requisito | App, exclusivo do requisito |
|---|---|---|---|
| Vitor | Infra + o par compartilhado RF1/RF4 | `config/`, `security/`, `exception/`, `model/User`, `model/AuditLog`, `repository/UserRepository`, `repository/AuditLogRepository`, `EmailService`, `BackendApplication`, `AuthController` + `UserService` (inteiros, por serem a base de login/cadastro E de senha/recuperação) | `App.tsx`, `config/`, `context/`, `utils/`, `components/`, `hooks/` |
| Lavinia | RF2 | `OrganizationController`, `OrganizationService`, `model/Organization`, `repository/OrganizationRepository`, DTOs de organização | `AdminOrganizationsScreen.tsx`, dono principal de `OrganizationScreen.tsx` |
| Enrico | RF3 | `CommitteeController`, `CommitteeService`, `model/Committee`, `model/CommitteeMember`, repositórios e DTOs de comissão | touque próprio na aba de comissões dentro de `OrganizationScreen.tsx` |
| João | RF1 | `MembershipController`, `MembershipService`, `model/AccessRequest`, `model/OrganizationMember`, repositórios e DTOs de acesso/membro | `AuthScreen.tsx` (dono principal, inclui a parte de recuperação que é do RF4); touque próprio na aba de membros dentro de `OrganizationScreen.tsx` |
| Vitor | RF4 | `UserController`, DTOs de senha/e-mail/perfil; touque próprio na parte de `changePassword`/`forgotPassword`/`resetPassword` dentro do `UserService`/`AuthController` já commitados na base | `SettingsScreen.tsx`; touque próprio na tela de recuperação dentro de `AuthScreen.tsx` |
| Guilherme | RF5 | `AdminController` + `AdminService` (inteiros — leem contagens de todas as outras tabelas, só fazem sentido depois que todo mundo já commitou), `SystemStatusDTO`, `AdminReportDTO`, `CriticalActionRequestDTO` | `AdminMonitoringReportsScreen.tsx`, `AdminControlsScreen.tsx` |

Depois do commit do Guilherme, a Lavinia faz um touque próprio na parte de aprovação de organização dentro do `AdminController`/`AdminService` (endpoints `approve`, `reject`, `status`), que é conteúdo do RF2 mas mora no arquivo do RF5.

### 17.4.1 Ordem exata dos 6 commits (dependência de compilação)

Confirmado nos campos das entidades: `Organization` depende de `User`; `AccessRequest`/`OrganizationMember` dependem de `Organization`; `Committee` depende de `Organization`; `CommitteeMember` depende de `Committee`; `AdminService.getReports()` lê os repositórios de todas as tabelas. Isso obriga uma ordem — e por coincidência **é exatamente a ordem dos slides 5 a 9** do Incremento 1:

| # | Quem | Requisito | Por que essa posição |
|---|---|---|---|
| 1 | Vitor | Infra + par RF1/RF4 | Nada compila sem isso; nenhum outro passo depende de mais nada além do passo 1 |
| 2 | Lavinia | RF2 | `Organization` precisa existir antes de RF1 e RF3 |
| 3 | Enrico | RF3 | `Committee` depende só de `Organization` (passo 2), não de RF1 |
| 4 | João | RF1 | `AccessRequest`/`OrganizationMember` dependem só de `Organization` (passo 2), não de RF3 |
| 5 | Vitor | RF4 | `UserController` depende só de `User` (passo 1) |
| 6 | Guilherme | RF5 | `AdminService` lê contagens de organizações, comissões e usuários — só faz sentido com tudo já commitado |

Os passos 3 e 4 não dependem um do outro, só do passo 2 — a ordem entre eles é só para seguir a numeração dos slides.

**Aviso**: entre o passo 1 e o passo 6, o projeto **não compila/empacota como app completo** (o `App.tsx` da base importa telas que só chegam nos passos 2, 4, 5 e 6). É esperado em um histórico de commits sequencial — só o estado final, depois do passo 6, precisa rodar. Ninguém deve tentar rodar `./mvnw spring-boot:run` ou `npx expo start` no meio da sequência.

### 17.5 Passo 2 — Fluxo de commit de cada membro

Os 12 diagramas (5 casos de uso, 5 sequências, classes, relacional) e o dicionário de dados já estão **gerados e renderizados sem erro**, em 16/09/2026, dentro de `Docs/Doc/Diagramas/` deste repositório de testes (fora do controle de versão, junto com o resto de `Docs/` — ver `README.md` daquela pasta). Cada membro copia a própria pasta para dentro do clone do repositório oficial, na estrutura abaixo, e faz o commit.

Estrutura de pastas dentro de `Doc/`, seguindo a convenção que o repositório oficial já usa:

```text
Doc/
├── Diagramas/
│   ├── RF1-cadastro-usuario/        (João)
│   ├── RF2-cadastro-organizacao/    (Lavinia)
│   ├── RF3-cadastro-comissao/       (Enrico)
│   ├── RF4-configuracoes-usuario/   (Vitor)
│   ├── RF5-informacoes-sistema/     (Guilherme)
│   └── modelo-dados/                (classes + relacional, sem dono por RF — padrão: Vitor)
```

Cada membro roda isto, na própria máquina, com a própria conta:

```bash
git clone https://github.com/Vitor-Guerra495/SpotMeet.git
cd SpotMeet
git config user.name "Nome Completo"
git config user.email "email-da-conta-github"
git checkout -b diagramas/rf1-joao
# ...edita os arquivos do próprio requisito (tabela 17.4) e adiciona os diagramas em Doc/Diagramas/RF1-cadastro-usuario/...
git add -A
git commit -m "Adicionar diagrama de caso de uso e de sequência do RF1"
git push origin diagramas/rf1-joao
# abre Pull Request no GitHub pedindo revisão do Vitor
```

Branches sugeridas: `diagramas/rf1-joao`, `diagramas/rf2-lavinia`, `diagramas/rf3-enrico`, `diagramas/rf4-vitor`, `diagramas/rf5-guilherme`, `diagramas/modelo-dados-vitor`. O Vitor revisa e mescla cada Pull Request pelo próprio GitHub — nenhum `push` do assistente envolvido em nenhum momento.
