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

## Estrutura da Documentação

```text
SpotMeet/
|-- README.md
`-- Doc/
    |-- 01-analise-mercado-rnf.md
    |-- 02-arquitetura-tecnologias.md
    |-- 03-padroes-qualidade.md
    |-- 04-design.md
    `-- imagens/
        `-- design/
            |-- ícones(1).png
            |-- Tela_Login.jpeg
            |-- Tela_Agenda.jpg
            |-- Tela_Calendario.jpg
            |-- Tela_Atas.jpg
            `-- Tela_Ajuste.jpg
```

## Padrões de Desenvolvimento

O código-fonte utilizará identificadores em inglês, enquanto a documentação, a interface do usuário, os nomes de branches e as mensagens de commit serão mantidos em português.

As alterações deverão ser desenvolvidas em branches específicas e revisadas antes da integração à branch `main`.

## Projeto Acadêmico

O SpotMeet está sendo desenvolvido como projeto acadêmico de software, com documentação e implementação organizadas de forma incremental ao longo do desenvolvimento.
