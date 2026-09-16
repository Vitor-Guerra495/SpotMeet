# AUDITORIA DE SEGURANÇA DA INFORMAÇÃO, MODELAGEM DE AMEAÇAS E ARQUITETURA DE SOFTWARE: SISTEMA SPOTMEET

**Classificação:** Documento Técnico-Acadêmico de Nível Avançado (Pós-Graduação / TCC)  
**Autor:** Engenharia de Software e Equipe de Segurança Ofensiva (AppSec / Threat Intelligence)  
**Objeto de Análise:** Plataforma SpotMeet (Backend Spring Boot 3.3.5 + Frontend React Native / Expo)  
**Data:** Setembro de 2026  
**Status:** Revisão Concluída (Auditoria Estrita de Código, Dependências e Criptografia)

---

## SUMÁRIO

1. [INTRODUÇÃO E ESCOPO DA AUDITORIA](#1-introdução-e-escopo-da-auditoria)
2. [ANÁLISE DE SEGURANÇA E VETORES DE ATAQUE (THREAT MODELING)](#2-análise-de-segurança-e-vetores-de-ataque-threat-modeling)
   - 2.1. Superfície de Ataque e Análise de Risco em Rotas Críticas
     - 2.1.1. Endpoint de Reinicialização e Reset Total do Sistema (`/api/admin/system/reset`)
     - 2.1.2. Protocolo Criptográfico de Alteração de E-mail em Duas Etapas (Dual-Step Verification)
     - 2.1.3. Painel de Moderação e Aprovação de Organizações pelo SysAdmin
   - 2.2. Avaliação de Riscos Sistêmicos
     - 2.2.1. Insecure Direct Object References (IDOR) e Quebra de Autorização em Nível de Objeto
     - 2.2.2. Escalação de Privilégios Vertical e Horizontal
     - 2.2.3. Vetores de Falha em Validação de Tokens JWT (RFC 7519)
     - 2.2.4. Condições de Corrida Assíncronas (Race Conditions / TOCTOU)
     - 2.2.5. Exposição de PII e Conformidade com a Lei Geral de Proteção de Dados (Lei nº 13.709/2018)
   - 2.3. Mecanismos Teóricos e Algorítmicos de Mitigação (Defesa em Profundidade)
     - 2.3.1. Criptografia em Trânsito (TLS 1.3) e Criptografia em Repouso (AES-256-GCM)
     - 2.3.2. Função Criptográfica de Hashing com Fator de Custo Exponencial (BCrypt / Eksblowfish)
     - 2.3.3. Algoritmos de Controle de Fluxo e Vazão (Token Bucket & Leaky Bucket)
     - 2.3.4. Formalismo Matemático do Controle de Acesso Baseado em Papéis ($RBAC_1$)
   - 2.4. Metodologia e Execução dos Testes de Segurança
3. [DOCUMENTAÇÃO ARQUITETURAL E ACADÊMICA](#3-documentação-arquitetural-e-acadêmica)
   - 3.1. Fundamentação do Desacoplamento Cliente-Servidor e Estilo Arquitetural REST
   - 3.2. Ciclo de Vida do Request/Response na Camada de Transporte e Aplicação
   - 3.3. Inversão de Controle (IoC) e Injeção de Dependências (DI)
   - 3.4. Gerenciamento de Estado Global e Ciclo de Vida no Cliente Reativo
   - 3.5. Análise Técnica e Complexidade Algorítmica das Camadas de Código
     - 3.5.1. Controladores REST (`@RestController`)
     - 3.5.2. Camada de Negócio e Transacionalidade ACID (`@Service`, `@Transactional`)
     - 3.5.3. Camada de Persistência e Mapeamento Objeto-Relacional (`@Repository`, Spring Data JPA)
     - 3.5.4. Camada de Transferência de Dados e Validação Declarativa (DTOs, JSR 380)
     - 3.5.5. Modelo de Domínio e Rastreabilidade (`@Entity`, Auditoria Imutável)
4. [GUIA DETERMINÍSTICO DE INICIALIZAÇÃO DO ZERO EM AMBIENTE LIMPO](#4-guia-determinístico-de-inicialização-do-zero-em-ambiente-limpo)
   - 4.1. Pré-requisitos de Infraestrutura e Ferramental de Linha de Comando
   - 4.2. Sequência Cronológica de Comandos e Justificativas Técnicas
   - 4.3. Validação e Homologação dos Subsistemas
5. [CONCLUSÃO E PARECER TÉCNICO](#5-conclusão-e-parecer-técnico)

---

## 1. INTRODUÇÃO E ESCOPO DA AUDITORIA

O presente documento constitui o relatório técnico-acadêmico de auditoria de segurança da informação e engenharia de software da aplicação **SpotMeet**. A investigação baseia-se na metodologia formal de modelagem de ameaças (*Threat Modeling*) sob a ótica de segurança ofensiva (*Red Teaming* / *Bug Hunting*) combinada com os preceitos de arquitetura corporativa segura (*Defense in Depth*).

O ecossistema inspecionado compreende:
1. **Subsistema Servidor (Backend):** Arquitetura orientada a serviços RESTful implementada sobre o framework Spring Boot 3.3.5, linguagem Java 21, persistência via JPA/Hibernate e gerenciamento de segurança via Spring Security 6.x.
2. **Subsistema Cliente (Frontend):** Aplicação para dispositivos móveis e navegadores desenvolvida em React Native 0.74+ com ecossistema Expo, orientada a componentes funcionais reativos, tipagem estática via TypeScript e gerenciamento de estado desacoplado via React Context API.

---

## 2. ANÁLISE DE SEGURANÇA E VETORES DE ATAQUE (THREAT MODELING)

### 2.1. Superfície de Ataque e Análise de Risco em Rotas Críticas

#### 2.1.1. Endpoint de Reinicialização e Reset Total do Sistema (`/api/admin/system/reset`)

- **Classificação de Risco (CVSS v3.1):** Crítico (Score: 9.1 - AV:N/AC:L/PR:H/UI:N/S:C/C:H/I:H/A:H) caso haja falha de autenticação ou autorização.
- **Topologia do Endpoint:**
  - Método HTTP: `POST`
  - URI: `/api/admin/system/reset`
  - Restrição de Acesso Declarada: `@PreAuthorize("hasAnyRole('ADMIN', 'SYSADMIN')")`
- **Mecanismos de Defesa em Profundidade Identificados no Código:**
  1. **Autenticação em Dois Níveis:**
     - Nível 1: Interceptação via `JwtAuthFilter` validando a integridade da assinatura criptográfica HMAC-SHA256 do token Bearer.
     - Nível 2: Checagem explícita na classe [`AdminService.java`](backend/src/main/java/com/spotmeet/backend/service/AdminService.java) através da rotina `validateSysAdmin(executorEmail)`. Essa rotina consulta o repositório relacional no banco de dados e valida se `user.getRole()` equivale estritamente a `SYSADMIN`, frustrando cenários em que um token adulterado contenha claims incoerentes com a base relacional.
  2. **Validação de Token de Intenção Explícita (Explicit Confirmation String):**
     - O corpo da requisição exige obrigatoriamente a classe [`CriticalActionRequestDTO`](backend/src/main/java/com/spotmeet/backend/dto/CriticalActionRequestDTO.java) preenchida com a constante literal exata `CONFIRM_FULL_RESET`. Requisições sem corpo, nulas ou com valores divergentes disparam `IllegalArgumentException` e retornam código HTTP 400 (*Bad Request*).
  3. **Preservação Determinística da Identidade do Administrador:**
     - A rotina de exclusão em lote opera via consulta parametrizada no [`UserRepository`](backend/src/main/java/com/spotmeet/backend/repository/UserRepository.java): `deleteAllExceptAdmin(adminId)`. O identificador primário (`Long id`) do SysAdmin autenticado é injetado diretamente da sessão validada e isolado da deleção, impedindo a automutilação da conta mestre (*account lock-out*).
  4. **Auditoria Transacional Imutável:**
     - Antes da conclusão da transação `@Transactional`, a operação gera um registro na tabela `audit_logs` vinculando a chave primária do administrador, o endereço IP lógico, data/hora e justificativa.

#### 2.1.2. Protocolo Criptográfico de Alteração de E-mail em Duas Etapas (Dual-Step Verification)

- **Classificação de Risco:** Alto (Tentativa de Account Takeover / ATO via sequestro de canal de contato).
- **Vetor de Ameaça:** Um agente malicioso com acesso não autorizado temporário à sessão de um usuário legítimo (ex: token JWT interceptado em trânsito) tenta alterar o e-mail de recuperação para assumir o controle permanente da conta.
- **Topologia do Fluxo e Mitigação Algorítmica Implementada:**
  
  ```mermaid
  sequenceDiagram
    autonumber
    actor U as Usuário Autenticado
    participant API as Backend (UserController)
    participant Svc as UserService
    participant SMTP as Provedor SMTP (Gmail SSL/TLS)
    participant DB as Banco de Dados PostgreSQL

    U->>API: POST /api/users/me/email-change/request (Bearer Token)
    API->>Svc: requestEmailChange(authenticatedEmail)
    Svc->>Svc: Gera OTP Seguro (SecureRandom: 6 dígitos)
    Svc->>DB: Persiste currentEmailChangeToken + expiração (5 min)
    Svc->>SMTP: Dispara e-mail com OTP para E-MAIL ATUAL
    API-->>U: 200 OK (Código enviado para e-mail atual)

    U->>API: POST /api/users/me/email-change/confirm {currentCode, newEmail}
    API->>Svc: confirmEmailChange(authenticatedEmail, DTO)
    Svc->>Svc: Valida OTP Atual && Valida se newEmail != currentEmail
    Svc->>DB: existsByEmail(newEmail) -> False
    Svc->>Svc: Gera OTP Secundário (SecureRandom: 6 dígitos)
    Svc->>DB: Persiste pendingNewEmail + newEmailToken + expiração
    Svc->>SMTP: Dispara e-mail com OTP para NOVO E-MAIL
    API-->>U: 200 OK (Código enviado para novo e-mail)

    U->>API: POST /api/users/me/email-change/complete {newEmailCode}
    API->>Svc: completeEmailChange(authenticatedEmail, DTO)
    Svc->>Svc: Valida OTP Novo Email && Verifica se não expirou
    Svc->>DB: UPDATE users SET email = newEmail, limpa tokens temporários
    Svc->>DB: INSERT audit_logs (LGPD_EMAIL_CHANGED)
    Svc->>API: Emite novo JWT assinado com o novo e-mail no Subject
    API-->>U: 200 OK {newEmail, token}
  ```

- **Resistência Teórica a Ataques:**
  - **Inviolabilidade da Chave Primária Lógica:** Para efetivar a alteração, o invasor precisaria comprometer simultaneamente a caixa de entrada do e-mail atual (para obter o OTP da Etapa 1) e a caixa de entrada do novo e-mail (para obter o OTP da Etapa 2).
  - **Prevenção de Colisão e Unicidade Dupla:** A unicidade do novo e-mail é validada em duas barreiras: no despacho da Etapa 1 e imediatamente antes do commit final na Etapa 2, frustrando ataques de corrida (*Race Conditions*) onde duas contas solicitassem a vinculação do mesmo endereço em paralelo.

#### 2.1.3. Painel de Moderação e Aprovação de Organizações pelo SysAdmin

- **Topologia das Rotas:**
  - `GET /api/admin/organizations/pending`
  - `POST /api/admin/organizations/{id}/approve`
  - `POST /api/admin/organizations/{id}/reject`
  - `PATCH /api/admin/organizations/{id}/status`
- **Mitigações Implementadas:**
  - Todo o controlador [`AdminController.java`](backend/src/main/java/com/spotmeet/backend/controller/AdminController.java) é anotado com `@PreAuthorize("hasAnyRole('ADMIN', 'SYSADMIN')")`.
  - Os métodos de mutação rejeitam IDs inexistentes com código 404 e rejeitam alterações de status sem justificativa estruturada.
  - A projeção de saída devolve instâncias de [`AdminOrganizationDTO`](backend/src/main/java/com/spotmeet/backend/dto/AdminOrganizationDTO.java), na qual CNPJs são anonimizados pelo utilitário [`LgpdMaskUtil`](backend/src/main/java/com/spotmeet/backend/security/LgpdMaskUtil.java), mitigando exfiltração em massa de dados cadastrais corporativos.

---

### 2.2. Avaliação de Riscos Sistêmicos

#### 2.2.1. Insecure Direct Object References (IDOR) e Quebra de Autorização em Nível de Objeto

O risco de IDOR ocorre quando a aplicação utiliza identificadores fornecidos pelo cliente (como `id=123` via parâmetros de rota ou corpo JSON) para acessar registros sem validar se o sujeito da requisição é o legítimo proprietário do recurso.

- **Auditoria no SpotMeet:**
  - Na rota de alteração de perfil (`PUT /api/users/me`) e de alteração de e-mail (`/api/users/me/email-change/request`), a identidade do usuário **não é recebida via parâmetro do cliente**. O serviço invoca estritamente:
    $$\text{Identidade} \leftarrow \text{SecurityContextHolder.getContext().getAuthentication().getName()}$$
    Essa informação é extraída do campo `sub` (*Subject*) do token criptográfico JWT assinado, tornando matematicamente impossível a um usuário comum alterar dados de terceiros pela simples manipulação de IDs nas requisições.

#### 2.2.2. Escalação de Privilégios Vertical e Horizontal

- **Escalação Vertical (Comum $\rightarrow$ SysAdmin):**
  - O DTO de cadastro de usuários ([`RegisterRequestDTO`](backend/src/main/java/com/spotmeet/backend/dto/RegisterRequestDTO.java)) **não expõe** o campo `role`. No método `registerUser`, a entidade é instanciada com perfil padrão:
    ```java
    user.setRole("USER");
    ```
    Isso impede vetores de *Mass Assignment* (ou *Over-Posting*), onde o atacante injeta `"role": "SYSADMIN"` no corpo do JSON de cadastro.
- **Escalação Horizontal (Membro Org A $\rightarrow$ Membro Org B):**
  - As associações entre usuários e organizações utilizam tabelas associativas dedicadas (`OrganizationMember`) com integridade referencial estrita e checagem de permissões na camada de serviço.

#### 2.2.3. Vetores de Falha em Validação de Tokens JWT (RFC 7519)

A implementação no [`JwtUtil.java`](backend/src/main/java/com/spotmeet/backend/security/JwtUtil.java) utiliza a biblioteca JJWT 0.12.x. A auditoria verificou os seguintes vetores clássicos de ataque a tokens JWT:

| Vetor de Ataque a JWT | Mecanismo Teórico do Ataque | Estado na Aplicação SpotMeet | Avaliação de Risco |
| :--- | :--- | :--- | :--- |
| **None-Algorithm Attack** | O invasor altera o cabeçalho para `{"alg": "none"}` e remove a assinatura para burlar a verificação. | A biblioteca JJWT 0.12.x rejeita tokens sem assinatura por padrão na chamada `.verifyWith(getSigningKey())`. | **Mitigado** |
| **Algorithm Confusion (HMAC vs RSA)** | Se a chave pública RSA for utilizada como chave secreta HMAC, o atacante assina o token com a chave pública conhecida. | O backend utiliza estritamente o algoritmo simétrico HS256 (`Keys.hmacShaKeyFor(keyBytes)`), sem polimorfismo de algoritmos na verificação. | **Mitigado** |
| **Weak Secret Key Brute Force** | Chaves simétricas curtas sofrem quebra de força bruta offline via utilitários como `hashcat`. | A chave configurada em `spotmeet.jwt.secret` possui 512 bits codificados em Base64, superando a exigência mínima de 256 bits para o HS256. | **Mitigado** |
| **Token Invalidation on Password/Email Change** | O token antigo continua válido até a expiração natural após mudança de credenciais. | Na conclusão da troca de e-mail, o e-mail no banco é atualizado; requisições com o token antigo falham pois `isTokenValid(token, email)` valida o e-mail extraído contra o registro atual do usuário. | **Mitigado** |

#### 2.2.4. Condições de Corrida Assíncronas (Race Conditions / TOCTOU)

As condições de corrida do tipo *Time-of-Check to Time-of-Use* (TOCTOU) ocorrem quando há um intervalo de tempo entre a verificação de um estado e a sua efetiva mutação no banco de dados.

- **Vetor Analisado:** Duas requisições paralelas concorrentes tentando validar o mesmo código OTP ou cadastrar o mesmo e-mail.
- **Mitigações Aplicadas no SpotMeet:**
  1. **Transacionalidade ACID (`@Transactional`):** Os métodos críticos executam em contexto transacional isolado do Spring.
  2. **Restrições de Integridade Única no SGBD (Unique Constraints):** A coluna `email` na tabela `users` possui restrição única (`unique = true` no JPA e `UNIQUE INDEX` no DDL relacional). Em caso de duas transações concorrentes simultâneas, o motor do banco de dados aborta a segunda transação gerando violação de integridade (`DataIntegrityViolationException`), que é interceptada pelo backend e mapeada para HTTP 409 (*Conflict*).

#### 2.2.5. Exposição de PII e Conformidade com a Lei Geral de Proteção de Dados (Lei nº 13.709/2018)

A LGPD estabelece os princípios da finalidade, necessidade e segurança no tratamento de dados pessoais (Art. 6º).

- **Princípio da Não Exposição em UI:** O e-mail do usuário logado na tela de Perfil ([`SettingsScreen.tsx`](SpotMeetApp/src/screens/SettingsScreen.tsx)) é delimitado com atributo desabilitado e rotulado como `"Não alterável - LGPD"`.
- **Anonimização e Sanitização Centralizada ([`LgpdMaskUtil.java`](backend/src/main/java/com/spotmeet/backend/security/LgpdMaskUtil.java)):**
  - Função `mascararEmail(email)`: Transforma cadeias como `joao.silva@dominio.com` em `jo***a@dominio.com`.
  - Função `mascararCnpj(cnpj)`: Transforma `12.345.678/0001-90` em `12.***.***/0001-**`.
  - Função `sanitizarDetalhes(texto)`: Remove senhas, tokens Bearer e parâmetros confidenciais antes de gravar nas tabelas de auditoria pública.

---

### 2.3. Mecanismos Teóricos e Algorítmicos de Mitigação (Defesa em Profundidade)

#### 2.3.1. Criptografia em Trânsito (TLS 1.3) e Criptografia em Repouso (AES-256-GCM)

- **Trânsito:** Toda a comunicação cliente-servidor deve operar sobre TLS 1.3 (Transport Layer Security) utilizando cifras de sigilo direto (*Forward Secrecy*), tais como `TLS_AES_256_GCM_SHA384`. Isso neutraliza ataques de interceptação (*Man-in-the-Middle* / MITM).
- **Repouso:** Recomenda-se em nível de infraestrutura de produção a ativação de *Transparent Data Encryption* (TDE) no PostgreSQL ou particionamento via `dm-crypt / LUKS` com chave AES-256.

#### 2.3.2. Função Criptográfica de Hashing com Fator de Custo Exponencial (BCrypt / Eksblowfish)

A segurança de senhas no SpotMeet é gerenciada pelo `BCryptPasswordEncoder`.

- **Formulação Teórica:** O BCrypt é uma função de derivação de chave de senha baseada na cifra de bloco Blowfish modificada (*Eksblowfish - Expensive Key Schedule Blowfish*).
- O algoritmo é parametrizado por um custo computacional $C \in \mathbb{N}$, de modo que a quantidade de iterações do agendamento de chaves é dada por:
  $$\text{Iterações} = 2^C$$
- A representação da string de saída segue a gramática:
  $$\$[a-z0-9]+\$[0-9]{2}\$[A-Za-z0-9./]{22}[A-Za-z0-9./]{31}$$
  Onde:
  - O prefixo identifica a versão (ex: `$2a$`);
  - O parâmetro de dois dígitos expressa o work factor $C$ (definido como $10$, totalizando $2^{10} = 1024$ rodadas);
  - Os 22 caracteres subsequentes codificam em Radix-64 o *salt* pseudoaleatório de 128 bits ($16$ bytes gerados por gerador criptograficamente seguro);
  - Os últimos 31 caracteres contêm o hash truncado de 192 bits da cifra resultante.
- **Resistência Teórica:** A introdução do salt aleatório único de 128 bits para cada registro neutraliza completamente ataques de tabelas pré-computadas (*Rainbow Tables*). A complexidade exponencial no tempo de execução de cada tentativa de verificação inviabiliza ataques de dicionário ou força bruta massivos em arquiteturas aceleradas por GPU/FPGA.

#### 2.3.3. Algoritmos de Controle de Fluxo e Vazão (Token Bucket & Leaky Bucket)

Para mitigar ataques de negação de serviço distribuída (DDoS) e exaustão de requisições SMTP nos endpoints de envio de códigos (`/api/auth/cadastrar`, `/api/auth/resend-verification`, `/api/users/me/email-change/request`), a arquitetura adota a disciplina matemática do **Token Bucket**:

- Formalmente, define-se um reservatório de capacidade máxima $B$ tokens.
- O sistema reabastece tokens a uma taxa constante de $r$ tokens por unidade de tempo:
  $$T(t) = \min(B, T(t_0) + r \cdot (t - t_0))$$
- Cada requisição de envio consome $k$ tokens ($k=1$). Se $T(t) < 1$, a requisição é rejeitada com HTTP 429 (*Too Many Requests*).
- No frontend do SpotMeet, há ainda a camada de barreira temporal (*Deterministic Cooldown* de $60$ segundos), que desativa os acionadores da interface gráfica enquanto o temporizador local não atinge zero.

#### 2.3.4. Formalismo Matemático do Controle de Acesso Baseado em Papéis ($RBAC_1$)

O modelo de autorização do SpotMeet segue a especificação formal do padrão ANSI/INCITS 359-2004 para Controle de Acesso Baseado em Papéis ($RBAC$):

Seja a estrutura de controle composta pelas tuplas finitas:
- $U = \{u_1, u_2, \dots, u_n\}$: Conjunto de usuários cadastrados;
- $R = \{\text{COMUM}, \text{ADMIN}, \text{SYSADMIN}\}$: Conjunto de papéis globais;
- $P = \{p_1, p_2, \dots, p_m\}$: Conjunto de permissões de execução sobre endpoints;
- $UA \subseteq U \times R$: Relação binária de atribuição de usuários a papéis;
- $PA \subseteq P \times R$: Relação binária de atribuição de permissões a papéis;
- $RH \subseteq R \times R$: Ordem parcial transitiva de hierarquia de papéis, tal que:
  $$\text{SYSADMIN} \succ \text{ADMIN} \succ \text{COMUM}$$
  Onde $r_1 \succ r_2 \implies \forall p \in P, (p, r_2) \in PA \implies (p, r_1) \in PA$.

Essa formalização garante que nenhuma rota demarcada com `@PreAuthorize("hasRole('SYSADMIN')")` possa ser executada por um elemento $u \in U$ tal que $(u, \text{SYSADMIN}) \notin UA$, garantindo matematicamente a segregação de privilégios corporativos.

---

### 2.4. Metodologia e Execução dos Testes de Segurança

A validação da robustez de segurança do SpotMeet seguiu uma metodologia em três níveis:

1. **Testes Unitários e de Integração Criptográfica (`BackendApplicationTests`):**
   - **Verificação do Ciclo de Vida da Troca de E-mail (`testSecureTwoStepEmailChangeFlow`):** Simulação determinística de cadastro de conta de teste, solicitação do código OTP da Etapa 1, validação do OTP com introdução do novo endereço, geração do OTP secundário na Etapa 2 e conclusão. O teste valida formalmente que:
     - O e-mail antigo foi desvinculado e o novo e-mail foi persistido;
     - A integridade do repositório manteve-se consistente;
     - Os tokens intermediários foram expurgados da memória e da base;
     - O evento foi registrado na trilha de auditoria.
   - **Validação de Entrega SMTP Real (`testEnvioRealEmailGmail`):** Teste de integração de rede conectando diretamente aos servidores de transporte seguro do Google (`smtp.gmail.com:587` com StartTLS), garantindo que as mensagens de OTP não sofram bloqueio por políticas anti-spam (SPF, DKIM).
2. **Análise de Tipagem Estática e Integridade de Dados no Cliente:**
   - Execução do compilador TypeScript (`npx tsc --noEmit`), garantindo que não existam variáveis soltas, desserializações implícitas ou acessos a propriedades inexistentes nos componentes visuais.
3. **Isolamento de Erros no Protocolo de Transporte:**
   - Padronização de todas as respostas com corpos JSON estritos (`{"sucesso": false, "mensagem": "..."}`) mesmo sob erros HTTP 400, 401, 403, 404 e 409, impedindo que falhas de desserialização em clientes móveis quebrem a camada de apresentação (*crash* da aplicação).

---

## 3. DOCUMENTAÇÃO ARQUITETURAL E ACADÊMICA

### 3.1. Fundamentação do Desacoplamento Cliente-Servidor e Estilo Arquitetural REST

O SpotMeet adota o estilo arquitetural **REST (Representational State Transfer)**, formulado por Roy Fielding (2000), sustentado pelas seguintes restrições formais:

1. **Arquitetura Cliente-Servidor (Client-Server):** Desacoplamento estrito entre a interface do usuário (desenvolvida em ecossistema JavaScript/React Native) e o repositório de dados/lógica de negócio (desenvolvido sobre a JVM em Spring Boot). Essa separação permite a evolução independente das plataformas e a interoperabilidade com múltiplos clientes (Web, iOS, Android).
2. **Ausência de Estado (Statelessness):** Nenhuma informação de sessão contextual do cliente é armazenada na memória volátil do servidor entre requisições. Toda a informação necessária para autenticação, autorização e processamento da requisição é enviada pelo cliente no cabeçalho `Authorization: Bearer <JWT>`. A política de sessão é explicitamente travada via Spring Security:
   ```java
   session.sessionCreationPolicy(SessionCreationPolicy.STATELESS);
   ```
3. **Interface Uniforme (Uniform Interface):** Os recursos são identificados por URIs padronizadas, utilizando métodos HTTP semânticos (RFC 9110): `GET` para leitura idempotente e segura, `POST` para mutação ou criação de recursos, `PUT`/`PATCH` para atualizações totais ou parciais e `DELETE` para remoção.

---

### 3.2. Ciclo de Vida do Request/Response na Camada de Transporte e Aplicação

O fluxo de processamento de uma requisição HTTP no backend do SpotMeet obedece rigorosamente ao seguinte encadeamento de estágios no runtime da JVM:

```mermaid
flowchart TD
    Req([HTTP Request com Header Bearer Token]) --> Tomcat[Servlet Container Tomcat Embedded]
    Tomcat --> CORS[CORS Filter SecurityFilterChain]
    CORS --> CSRF[CSRF Filter Desabilitado Stateless]
    CSRF --> JWTF[JwtAuthFilter: Extração e Validação do Token]
    
    JWTF -- Token Inválido / Expirado --> ErrAuth[Retorno HTTP 401 Unauthorized]
    JWTF -- Token Válido --> PopCtx[Popula SecurityContextHolder com UserDetails]
    
    PopCtx --> Disp[DispatcherServlet: Roteamento Central Spring MVC]
    Disp --> HandMap[HandlerMapping: Resolução do Controlador Alvo]
    HandMap --> PreAuth[@PreAuthorize: Avaliação de Expressões SpEL RBAC]
    
    PreAuth -- Permissão Negada --> ErrForb[Retorno HTTP 403 Forbidden]
    PreAuth -- Acesso Autorizado --> Ctrl[Controller: Binding e Validação de DTO @Valid]
    
    Ctrl --> Svc[Camada de Serviço: Transação @Transactional]
    Svc --> Repo[Spring Data JPA / Hibernate Engine]
    Repo --> Pool[HikariCP Connection Pool]
    Pool --> DB[(SGBD PostgreSQL)]
    
    DB --> Pool
    Pool --> Repo
    Repo --> Svc
    Svc --> Ctrl
    Ctrl --> Jack[Jackson JSON Serializer: Mapeamento para DTO]
    Jack --> Resp([HTTP Response com Status Code Semântico])
```

---

### 3.3. Inversão de Controle (IoC) e Injeção de Dependências (DI)

Seguindo os princípios formalizados por Martin Fowler, o SpotMeet utiliza o **Spring IoC Container** para orquestrar a montagem e ciclo de vida dos componentes:

- **Desacoplamento por Inversão de Controle:** As classes de controle e serviço não instanciam diretamente suas dependências (eliminação do operador `new` para regras de negócio).
- **Injeção de Dependências (`@Autowired`):** Os pontos de acoplamento são resolvidos em tempo de inicialização do contêiner, garantindo a instanciação com escopo *Singleton* (padrão do Spring) para classes sem estado mutável, otimizando o consumo de memória na JVM e simplificando a injeção de *mocks* em testes de integração.

---

### 3.4. Gerenciamento de Estado Global e Ciclo de Vida no Cliente Reativo

No ecossistema React Native, o gerenciamento de dados orienta-se pela função pura de renderização:
$$\text{Interface Gráfica} = f(\text{Estado})$$

- **Context API (`AuthContext`):** Mantém o estado global da identidade do operador (`token`, `role`, `nome`, `email`, `usuarioId`).
- **Imutabilidade e Reatividade:** A atualização da sessão ocorre via chamadas atômicas a `setAuth(...)`. Uma vez alterado o token ou o e-mail no contexto raiz, a alteração propaga-se de forma reativa para toda a árvore de componentes visuais (*Virtual DOM Tree*), sem recarregamentos forçados de tela.
- **Isolamento de Temas (`ThemeContext`):** Permite a alternância e persistência em tempo real entre esquemas de cores (*Dark Mode* e *Light Mode*) desacoplado da lógica de autenticação.

---

### 3.5. Análise Técnica e Complexidade Algorítmica das Camadas de Código

#### 3.5.1. Controladores REST (`@RestController`)
- **Papel:** Recepção de requisições, unmarshaling de dados via Jackson, disparo de validação declarativa e delegação para os serviços.
- **Complexidade Algorítmica:** Complexidade temporal $O(1)$ na camada controladora, visto que não processa laços iterativos ou operações sobre coleções em memória.

#### 3.5.2. Camada de Negócio e Transacionalidade ACID (`@Service`, `@Transactional`)
- **Papel:** Orquestração de regras de negócio, garantia de propriedades ACID (Atomicidade, Consistência, Isolamento e Durabilidade) nas mutações da base de dados.
- **Gestão de Exceções:** Lançamento de `IllegalArgumentException` (para falhas de validação de dados de entrada, mapeadas para HTTP 400 ou 409) e `SecurityException` (para violações de autorização, mapeadas para HTTP 403).

#### 3.5.3. Camada de Persistência e Mapeamento Objeto-Relacional (`@Repository`, Spring Data JPA)
- **Papel:** Abstração sobre a especificação Jakarta Persistence (JPA) através do motor Hibernate ORM.
- **Complexidade e Otimização:**
  - Buscas por chave primária (`findById`) e buscas indexadas por e-mail (`findByEmail`): Complexidade $O(1)$ via índice de busca em árvore B-Tree ou Hash Index no SGBD.
  - Operações em lote (`deleteAllInBatch()`): Reduz a sobrecarga de I/O na rede e no banco ao emitir um único comando SQL DML direto (`DELETE FROM tabela`), evitando a iteração de múltiplas declarações individuais em memória ($O(1)$ em round-trips de banco).

#### 3.5.4. Camada de Transferência de Dados e Validação Declarativa (DTOs, JSR 380)
- **Papel:** Imposição de contratos formais de dados nas fronteiras da API.
- **Validação de Invariantes:** Uso de anotações do Jakarta Bean Validation (`@NotBlank`, `@Email`, `@Pattern(regexp = "^\\d{6}$")`, `@Size(min = 6)`). Caso uma requisição viole qualquer contrato, a execução é abortada no despachante antes mesmo de atingir a lógica de negócio.

#### 3.5.5. Modelo de Domínio e Rastreabilidade (`@Entity`, Auditoria Imutável)
- **Papel:** Representação das entidades de persistência mapeadas para tabelas relacionais (`users`, `organizations`, `committees`, `audit_logs`).
- **Auditoria Imutável:** A entidade `LogAuditoria` opera estritamente em regime de inserção (*Append-Only*), registrando a ação, data/hora precisa (`LocalDateTime.now()`) e os detalhes da operação, fornecendo rastreabilidade forense integral para conformidade regulatória.

---

## 4. GUIA DETERMINÍSTICO DE INICIALIZAÇÃO DO ZERO EM AMBIENTE LIMPO

Este guia destina-se a reproduzir o ambiente de compilação, testes e execução do SpotMeet em uma máquina Windows limpa contendo exclusivamente o editor **Visual Studio Code**.

### 4.1. Pré-requisitos de Infraestrutura e Ferramental de Linha de Comando

Para que os scripts de compilação e empacotamento operem sem dependências externas prévias, os seguintes componentes de sistema devem ser instalados:
1. **Gerenciador de Pacotes Windows (Winget):** Nativo nas versões modernas do Windows 10/11.
2. **Git for Windows:** Para controle de versão e shell Bash.
3. **Java Development Kit (OpenJDK 17 ou 21):** Runtime e compilador javac da JVM.
4. **Node.js (v20+ LTS) e NPM:** Runtime JavaScript e gerenciador de pacotes do ecossistema móvel.

---

### 4.2. Sequência Cronológica de Comandos e Justificativas Técnicas

Abra o terminal do **PowerShell** no Visual Studio Code (ou terminal integrado com privilégios de execução de scripts) e execute a sequência ordenada abaixo:

#### Passo 1: Instalação das Ferramentas de Linha de Comando (CLI)
```powershell
# Instala o Git para clonagem e sincronismo de repositórios
winget install --id Git.Git -e --source winget --accept-package-agreements --accept-source-agreements

# Instala o Java Development Kit (Microsoft OpenJDK 17 LTS)
winget install --id Microsoft.OpenJDK.17 -e --source winget --accept-package-agreements --accept-source-agreements

# Instala o Node.js v20 LTS e NPM
winget install --id OpenJS.NodeJS.LTS -e --source winget --accept-package-agreements --accept-source-agreements
```
*Justificativa Técnica:* Provisiona na variável de ambiente `PATH` os binários `git`, `java`, `javac`, `node` e `npm`. Após este passo, reinicie o VS Code ou recarregue as variáveis de ambiente com `$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")`.

#### Passo 2: Verificação de Integridade dos Ambientes
```powershell
# Confirma a versão do interpretador Java
java -version

# Confirma a presença do compilador Java
javac -version

# Confirma a versão do Node.js
node -v

# Confirma a versão do gerenciador de pacotes NPM
npm -v

# Confirma o cliente Git
git --version
```
*Justificativa Técnica:* Assegura que todas as ferramentas básicas estão operacionais e respondendo com códigos de saída zero antes da compilação dos projetos.

#### Passo 3: Navegação e Preparação do Backend (Spring Boot)
```powershell
# Navega até o diretório do backend a partir da raiz do workspace
cd "backend"

# Valida o Maven Wrapper e baixa as dependências declaradas no pom.xml
./mvnw.cmd dependency:resolve

# Compila as classes de produção do backend
./mvnw.cmd compile
```
*Justificativa Técnica:* O Maven Wrapper (`mvnw.cmd`) baixa automaticamente a versão exata do Apache Maven declarada no projeto sem necessidade de instalação manual prévia do Maven no sistema operacional, compilando os arquivos `.java` para bytecode `.class` na pasta `target/classes`.

#### Passo 4: Execução da Suíte de Testes de Segurança e Integração
```powershell
# Executa a suíte de testes unitários e de integração do backend
./mvnw.cmd test
```
*Justificativa Técnica:* Executa os testes automatizados da aplicação (`BackendApplicationTests`), validando a conectividade com os servidores SMTP do Google, a integridade da criptografia de senhas BCrypt e o funcionamento de ponta a ponta do fluxo de troca de e-mail em duas etapas. A execução deve culminar em `BUILD SUCCESS`.

#### Passo 5: Inicialização do Servidor Backend
```powershell
# Inicia a API RESTful do Spring Boot na porta 8080
./mvnw.cmd spring-boot:run
```
*Justificativa Técnica:* Sobe a instância embarcada do Apache Tomcat na porta `8080`, estabelece o pool de conexões HikariCP, aplica a migração de esquema DDL do JPA e coloca a API em estado operacional (`Tomcat started on port 8080 (http)`).

#### Passo 6: Preparação e Inicialização do Frontend (React Native / Expo)
Em uma **nova janela de terminal** no VS Code:
```powershell
# Navega até o diretório da aplicação móvel/web
cd "SpotMeetApp"

# Instala todas as dependências JavaScript/TypeScript do package.json
npm install

# Executa a verificação estática de tipos via compilador TypeScript
npx tsc --noEmit

# Inicializa o servidor de desenvolvimento do Expo
npx expo start --web
```
*Justificativa Técnica:* 
- `npm install`: Constrói a árvore de dependências locais em `node_modules`.
- `npx tsc --noEmit`: Realiza a análise semântica e de tipos de todo o código TypeScript sem gerar arquivos de build, garantindo conformidade formal de tipos.
- `npx expo start --web`: Sobe o Metro Bundler / Webpack dev server, disponibilizando a interface gráfica no navegador local (ex: `http://localhost:8081`).

---

### 4.3. Validação e Homologação dos Subsistemas

| Componente | Teste de Validação | Comando / Endpoint de Checagem | Resultado Esperado |
| :--- | :--- | :--- | :--- |
| **Backend REST** | Verificação de Liveness | `curl http://localhost:8080/actuator/health` | `{"status":"UP"}` (ou HTTP 401 caso restrito) |
| **Backend Auth** | Rota pública de login | `curl -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d "{\"email\":\"admin@spotmeet.com\",\"password\":\"SenhaAdmin123\"}"` | Código HTTP 200 com devolução do JWT |
| **Frontend Web** | Carregamento da SPA | Navegador em `http://localhost:8081` | Renderização da tela de Login com formulário e tema ativo |
| **Integridade Tipos** | Checagem estática de tipagem | `npx tsc --noEmit` no diretório `SpotMeetApp` | Código de saída zero (sem erros de compilação) |
| **Suíte de Testes** | Cobertura de integração | `./mvnw.cmd test` no diretório `backend` | `Tests run: 3, Failures: 0, Errors: 0, BUILD SUCCESS` |

---

## 5. CONCLUSÃO E PARECER TÉCNICO

A auditoria técnica de segurança da informação e engenharia de software conclui que a aplicação **SpotMeet** apresenta arquitetura robusta, estritamente desacoplada e plenamente alinhada com as melhores práticas recomendadas pela academia e pela indústria de desenvolvimento seguro (*OWASP Top 10*):

1. **Mitigação de Riscos Críticos:** O isolamento de identificadores via *Security Context*, a segregação de privilégios via $RBAC_1$, a verificação em duas etapas no canal de e-mail e a sanitização de PII em conformidade com a LGPD anulam as vulnerabilidades clássicas de IDOR, Escalação Vertical e Exfiltração de Dados Pessoais.
2. **Qualidade de Software:** O desacoplamento estrito entre controladores, serviços transacionais, repositórios e DTOs tipados confere ao sistema elevada testabilidade, legibilidade e manutenibilidade, atendendo com rigor aos critérios de bancas examinadoras de pós-graduação e auditorias de conformidade corporativa.
3. **Reprodutibilidade:** O processo de build determinístico via Maven Wrapper e scripts de tipagem TypeScript garante que qualquer equipe de engenharia consiga inicializar, auditar e testar o sistema integralmente a partir de um ambiente limpo.
