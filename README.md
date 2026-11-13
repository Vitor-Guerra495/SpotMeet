# SpotMeet

![Logo do SpotMeet](Doc/imagens/design/ícones.png)

O **SpotMeet** é um aplicativo mobile para organizar as reuniões de organizações e comissões. Ele reúne num só lugar o agendamento, os convites, a pauta, a presença dos participantes e os avisos por e-mail.

O projeto é desenvolvido para **Android e iOS** e usa uma arquitetura cliente-servidor, com o aplicativo mobile, o servidor (backend) e o banco de dados separados.

## Funcionalidades

- Cadastro com confirmação do e-mail por código, login com e-mail e senha e login com Google (APK Android).
- Organizações e comissões, com pedidos de acesso pela chave ou pela lista de organizações, convites e aprovação pelo administrador do sistema.
- Agendamento de reuniões com data e hora pelo seletor do celular, dia inteiro, vários dias, repetição (diária, semanal, mensal, anual, dias úteis ou personalizada), modalidade presencial, online ou híbrida e anexo da pauta.
- Convites com aceite ou recusa, aviso dos convites sem resposta na aba Reuniões, recusas avisadas ao presidente no aplicativo e por e-mail e e-mails de convite, alteração e cancelamento.
- Registro de entrada e saída e lista de presença.
- Ata da reunião escrita pelo presidente, com aprovação ou reprovação pelos presentes e e-mails do resultado.
- Votação durante a reunião (Sim, Não ou Abstenção), com contagem ao vivo para o presidente e resultado nominal.
- Relatório individual com as reuniões em que o usuário registrou entrada, por período e comissão.
- Manual de utilização dentro do aplicativo (Ajustes, Ajuda), com busca por tópico.
- Configurações do usuário em menu: perfil com foto, nome, e-mail e tema claro ou escuro, troca de senha, bloqueio biométrico e status de presença.
- Informações do sistema para o administrador: aprovação de organizações, monitoramento, relatórios e controles.

## Tecnologias

### Aplicativo Mobile

- **React Native** 0.88.1
- **Expo SDK 58**
- **TypeScript** 6.0
- **React Navigation** 7

O React Native permite desenvolver para Android e iOS com uma única base de código. O Expo permite testar no celular pelo Expo Go e gerar o APK do Android pelo EAS Build. O TypeScript adiciona tipagem estática ao código.

### Backend

- **Java 21**
- **Spring Boot** 3.3.5 (Spring Web, Spring Data JPA, Spring Security, Validation, Mail e Actuator)
- **JJWT** 0.12.6

O servidor concentra as regras de negócio, a autenticação, a autorização, a validação dos dados, o envio dos e-mails e o acesso ao banco.

### Banco de Dados

- **PostgreSQL** 18, executado localmente.

### Comunicação

O aplicativo e o servidor se comunicam por HTTP, numa API REST com JSON.

A descrição da arquitetura e das bibliotecas está no [Doc/02](Doc/02-arquitetura-tecnologias.md).

## Arquitetura

```text
Aplicativo Mobile
React Native + Expo + TypeScript
        |
        | HTTP / REST / JSON
        v
Backend                        ---> Gmail (SMTP): e-mails
Java 21 + Spring Boot          ---> Google: chaves públicas do login
        |
        | Spring Data JPA
        v
PostgreSQL
```

## Segurança e Qualidade

- senhas e códigos de verificação guardados com BCrypt (custo 12);
- autenticação por token JWT, controlada pelo servidor;
- autorização por operação e por recurso, conferida no servidor, sem confiar nos identificadores enviados pelo aplicativo;
- validação dos dados recebidos e uso de DTOs para limitar os campos aceitos;
- consultas parametrizadas;
- bloqueio temporário depois de 10 tentativas erradas de login ou de código;
- rotas do Actuator restritas ao administrador do sistema, exceto `/actuator/health` e `/actuator/info`;
- senha do banco, senha do e-mail e chave JWT configuradas no arquivo `backend/.env`, que não é versionado;
- testes automatizados do servidor (JUnit e Spring Boot Test) e do aplicativo (`node:test`).

## Design e UI/UX

O aplicativo usa o anil `#5C6BC0` como cor principal; a logo mantém o roxo `#901ECE`. A interface tem:

- tema claro e tema escuro, escolhidos em Ajustes e salvos no perfil;
- abas inferiores Reuniões, Organização e Ajustes para o usuário;
- abas Aprovações, Organizações, Monitoramento e Controles para o administrador do sistema;
- cartões de reunião com selos de situação e prioridade;
- formulários e detalhes de reuniões que abrem como folhas a partir da parte de baixo da tela;
- seletores de data e hora do próprio celular.

A frase de apoio da tela de login é **Conecte-se com sua equipe em qualquer lugar**, e a do cadastro, **Organize suas reuniões em um só lugar**.

## Documentação

| Documento | Conteúdo |
|---|---|
| [01 - Análise de Mercado e RNF](Doc/01-analise-mercado-rnf.md) | Avaliação de concorrentes e requisitos não funcionais |
| [02 - Arquitetura e Tecnologias](Doc/02-arquitetura-tecnologias.md) | Arquitetura cliente-servidor, tecnologias, bibliotecas e segurança |
| [03 - Padrões e Qualidade](Doc/03-padroes-qualidade.md) | Nomenclatura, práticas de programação, Git e estratégia de testes |
| [04 - Design e UI/UX](Doc/04-design.md) | Identidade visual, cores, tipografia, componentes e telas |
| [05 - Documento de Requisitos dos Incrementos 1 a 3](Doc/05-documento-requisitos.md) | Requisitos funcionais e não funcionais, diagramas de casos de uso, classes, relacional e sequência, dicionários e testes |
| [06 - Manual de Uso](Doc/06-manual-de-uso.md) | Como usar o aplicativo, por perfil: o mesmo texto da Ajuda do aplicativo |

## Estrutura do Repositório

```text
SpotMeet/
|-- README.md
|-- backend/              # servidor (Java 21 + Spring Boot)
|-- SpotMeetApp/          # aplicativo (React Native + Expo)
`-- Doc/
    |-- 01-analise-mercado-rnf.md
    |-- 02-arquitetura-tecnologias.md
    |-- 03-padroes-qualidade.md
    |-- 04-design.md
    |-- 05-documento-requisitos.md
    |-- 06-manual-de-uso.md
    `-- imagens/
        |-- design/       # imagens do Doc/04
        `-- diagramas/    # diagramas do Doc/05 (PNG e fonte PlantUML)
```

## Como Executar

### Pré-requisitos

| Ferramenta | Versão |
|---|---|
| JDK | 21 |
| PostgreSQL | 18 |
| Node.js | 22.13 ou superior |
| Expo Go | Versão para o Expo SDK 58, instalada no celular (Android 7.0 ou superior, iOS 16.4 ou superior) |

O computador e o celular precisam estar na mesma rede Wi-Fi. O login com Google e o envio de e-mails exigem internet no computador e no celular.

### 1. Banco de dados

Basta o PostgreSQL estar em execução. Na primeira execução, o servidor cria o banco `spotmeet_db`, se ele ainda não existir (o usuário configurado precisa ter permissão para criar bancos), e todas as tabelas. Nas versões seguintes, as tabelas novas são acrescentadas sem apagar os dados.

### 2. Configuração do servidor

Copiar `backend/.env.example` para `backend/.env` e preencher:

- `DB_PASSWORD`: senha do usuário `postgres` (endereço e usuário podem ser alterados em `DB_URL` e `DB_USERNAME`);
- `JWT_SECRET`: chave em Base64 com pelo menos 32 bytes. No Linux ou macOS: `openssl rand -base64 48`. No PowerShell:

```powershell
$b = New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)
```

- `MAIL_USERNAME` e `MAIL_PASSWORD`: conta do Gmail e senha de app, usadas para enviar os códigos de verificação e os avisos. Se ficarem vazios, nenhum e-mail é enviado e o código de verificação aparece no console do servidor, na linha `[SpotMeet-Email] Código local de QA`.

O login com Google já vem configurado com o ID de cliente do projeto da equipe no Google Cloud; não é preciso preencher `GOOGLE_CLIENT_ID`.

### 3. Servidor

```bash
cd backend
./mvnw spring-boot:run        # Windows: .\mvnw.cmd spring-boot:run
```

O servidor inicia na porta 8080. Na primeira execução é criada a conta do administrador do sistema, cuja senha pode ser trocada depois na aba Controles:

| E-mail | Senha |
|---|---|
| `sysadmin@spotmeet.com` | `Admin@123` |

### 4. Aplicativo pelo Expo Go

Em outro terminal:

```bash
cd SpotMeetApp
npm install
npx expo start
```

Ler o QR code exibido com o Expo Go (Android) ou com a câmera (iOS). O aplicativo usa o endereço do computador que está rodando o Expo. Se não encontrar o servidor, informar o IP do computador na opção "Servidor" da tela de login.

No Windows, liberar no firewall as portas 8080 (servidor) e 8081 (Expo) para que o celular consiga acessar o computador.

### 5. Aplicativo pelo APK (Android)

O APK é gerado pelo EAS Build, serviço de compilação do Expo, com o comando abaixo (exige uma conta Expo com acesso ao projeto):

```bash
cd SpotMeetApp
npx eas-cli build --platform android --profile preview
```

Depois de instalar o APK, só é preciso o servidor em execução (passos 1 a 3); o Expo não precisa estar aberto.

Algumas funções dependem de código nativo e só funcionam no APK:

- **Login com Google:** o Google aceita só o aplicativo assinado com a chave registrada no projeto da equipe. No Expo Go e no iPhone, o botão avisa que o recurso não está disponível.
- **Botão "Abrir no SpotMeet" dos e-mails:** abre o aplicativo na reunião. No Expo Go, a reunião pode ser aberta pela aba Reuniões.
- **Face ID no iPhone:** o Expo Go não tem acesso ao Face ID; o bloqueio biométrico funciona no Android e em builds próprias de iOS.

## Padrões de Desenvolvimento

O código-fonte (classes, funções, variáveis, rotas e banco de dados) e os comentários do código são escritos em inglês. A documentação, a interface do usuário, os e-mails, os nomes de branches e as mensagens de commit são escritos em português.

As alterações são desenvolvidas em branches específicas e revisadas antes da integração à branch `main`.

## Projeto Acadêmico

O SpotMeet é desenvolvido como projeto acadêmico da disciplina Projeto e Desenvolvimento de Sistemas II do IFSP, com documentação e implementação organizadas de forma incremental.
