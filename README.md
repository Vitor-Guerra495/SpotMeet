# SpotMeet

![Logo do SpotMeet](Doc/imagens/design/ícones.png)

O **SpotMeet** é um aplicativo mobile voltado ao gerenciamento e à organização de reuniões. A proposta é centralizar informações importantes do fluxo de uma reunião, facilitando o acompanhamento de agenda, pautas, participantes, registros e atas em uma interface simples e organizada.

O projeto é desenvolvido para **Android e iOS** e utiliza uma arquitetura cliente-servidor, com separação entre o aplicativo mobile, o backend e a persistência dos dados.

## Objetivo

O SpotMeet busca oferecer uma solução centralizada para apoiar a organização das reuniões, permitindo que usuários consultem compromissos, organizem pautas e mantenham registros relacionados às reuniões de forma estruturada.

A interface foi planejada com foco em:

- facilidade de uso;
- organização das informações;
- navegação simples;
- responsividade;
- compatibilidade com Android e iOS;
- identidade visual consistente;
- segurança no armazenamento e no acesso aos dados.

## Tecnologias

### Aplicativo Mobile

- **React Native**
- **TypeScript**

O React Native permite o desenvolvimento para Android e iOS utilizando uma única base de código. O TypeScript adiciona tipagem estática e auxilia na legibilidade, manutenção e identificação de erros durante o desenvolvimento.

### Backend

- **Java 21**
- **Spring Boot**
- **Spring Web**
- **Spring Data JPA**
- **Spring Security**

O backend concentra as regras de negócio, autenticação, autorização, validação dos dados e comunicação com a camada de persistência.

### Banco de Dados

- **PostgreSQL**

O PostgreSQL será utilizado para persistência dos dados do sistema e executado localmente durante o desenvolvimento.

### Comunicação

A comunicação entre o aplicativo mobile e o backend será realizada por meio de:

- HTTP;
- API REST;
- JSON.

## Arquitetura

A arquitetura geral do SpotMeet segue o modelo cliente-servidor:

```text
Aplicativo Mobile
React Native + TypeScript
        |
        | HTTP / REST / JSON
        v
Backend
Java 21 + Spring Boot
        |
        | Persistência
        v
PostgreSQL
```

O aplicativo mobile é responsável pela interface e interação com o usuário. O backend concentra as regras de negócio, validações, autenticação, autorização e acesso aos dados.

## Segurança e Qualidade

O projeto estabelece práticas de segurança e qualidade desde a documentação inicial, incluindo:

- armazenamento seguro de senhas por meio de função de hash apropriada;
- autenticação controlada pelo backend;
- autorização por operação e por recurso;
- proteção contra BOLA e IDOR;
- validação de dados recebidos pela API;
- prevenção de Mass Assignment;
- consultas parametrizadas;
- tratamento adequado de conteúdo textual;
- controle de informações sensíveis em logs;
- proibição de credenciais e secrets no repositório;
- estratégia de testes unitários, de integração, API, funcionais, segurança, usabilidade e compatibilidade.

## Design e UI/UX

A identidade visual do SpotMeet utiliza tons de violeta e roxo, com foco em uma interface moderna, organizada e consistente.

O aplicativo prevê:

- modo claro;
- modo escuro;
- acompanhamento do tema definido pelo sistema;
- cards de reunião;
- badges de status;
- criação rápida por Bottom Sheet;
- telas adaptáveis a diferentes tamanhos de dispositivos.

A frase de apoio utilizada na tela de login é:

> **Organize suas reuniões em um só lugar.**

## Documentação

A documentação inicial do projeto está dividida nos seguintes arquivos:

| Documento | Conteúdo |
|---|---|
| [01 - Análise de Mercado e RNF](Doc/01-analise-mercado-rnf.md) | Avaliação de concorrentes e requisitos não funcionais |
| [02 - Arquitetura e Tecnologias](Doc/02-arquitetura-tecnologias.md) | Arquitetura cliente-servidor, tecnologias e segurança |
| [03 - Padrões e Qualidade](Doc/03-padroes-qualidade.md) | Nomenclatura, práticas de programação, Git e estratégia de testes |
| [04 - Design e UI/UX](Doc/04-design.md) | Identidade visual, cores, tipografia, componentes e telas |
| [05 - Documento de Requisitos do Incremento 1](Doc/05-documento-requisitos.md) | Requisitos funcionais e não funcionais, diagramas de casos de uso, classes, relacional e sequência, dicionário de dados e testes |

## Estrutura da Documentação

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
    `-- imagens/
        |-- design/
        |   |-- ícones.png
        |   |-- Tela_Login.jpeg
        |   |-- Tela_Agenda.jpg
        |   |-- Tela_Calendario.jpg
        |   |-- Tela_Atas.jpg
        |   `-- Tela_Ajuste.jpg
        `-- diagramas/    # diagramas do Incremento 1
```

## Como Executar

### Pré-requisitos

| Ferramenta | Versão |
|---|---|
| JDK | 21 |
| PostgreSQL | 18 |
| Node.js | 20 ou superior |
| Expo Go | Instalado no celular (Android ou iOS) |

O computador e o celular precisam estar na mesma rede Wi-Fi.

### 1. Banco de dados

Criar o banco `spotmeet_db` no PostgreSQL:

```bash
psql -U postgres -c "CREATE DATABASE spotmeet_db;"
```

As tabelas são criadas automaticamente na primeira execução do servidor.

### 2. Configuração do servidor

Copiar `backend/.env.example` para `backend/.env` e preencher:

- `DB_PASSWORD`: senha do usuário `postgres`;
- `JWT_SECRET`: chave em Base64 com pelo menos 32 bytes. No Linux ou macOS: `openssl rand -base64 48`. No PowerShell:

```powershell
$b = New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)
```

- `MAIL_USERNAME` e `MAIL_PASSWORD`: conta do Gmail e senha de app, usadas para enviar os códigos de verificação. Se ficarem vazios, o código é exibido na própria tela do aplicativo.

### 3. Servidor

```bash
cd backend
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
```

O servidor inicia na porta 8080. Na primeira execução é criada a conta do administrador do sistema:

| E-mail | Senha |
|---|---|
| `sysadmin@spotmeet.com` | `Admin@123` |

### 4. Aplicativo

Em outro terminal:

```bash
cd SpotMeetApp
npm install
npx expo start
```

Ler o QR code exibido com o Expo Go (Android) ou com a câmera (iOS). O aplicativo localiza o servidor automaticamente pelo endereço do computador. Se não localizar, informar o IP do computador na opção "Servidor" da tela de login.

No Windows, liberar no firewall as portas 8080 (servidor) e 8081 (Expo) para que o celular consiga acessar o computador.

## Padrões de Desenvolvimento

O código-fonte utilizará identificadores em inglês, enquanto a documentação, a interface do usuário, os nomes de branches e as mensagens de commit serão mantidos em português.

As alterações deverão ser desenvolvidas em branches específicas e revisadas antes da integração à branch `main`.

## Projeto Acadêmico

O SpotMeet está sendo desenvolvido como projeto acadêmico de software, com documentação e implementação organizadas de forma incremental ao longo do desenvolvimento.
