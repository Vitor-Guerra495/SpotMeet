# Bloco 2 - Arquitetura e Tecnologias

## 1. Arquitetura do Sistema

O SpotMeet adotará uma arquitetura cliente-servidor modular, separando a aplicação mobile, o backend e a persistência dos dados.

A arquitetura será composta por três responsabilidades principais:

### 1.1. Aplicativo Mobile

O aplicativo será responsável pela interface com o usuário e pelo consumo dos serviços disponibilizados pelo backend.

A aplicação será desenvolvida para **Android e iOS** utilizando uma única base de código.

Principais responsabilidades:

- exibição das telas e componentes da interface;
- autenticação do usuário;
- consulta e gerenciamento das reuniões;
- criação e edição de pautas;
- consulta e registro de atas;
- gerenciamento das informações relacionadas às reuniões;
- comunicação com o backend por meio de API REST.

### 1.2. Backend

O backend será responsável por centralizar as regras de negócio e controlar o acesso aos dados do sistema.

Principais responsabilidades:

- autenticação dos usuários;
- autorização por operação e por recurso;
- gerenciamento de usuários;
- gerenciamento de reuniões;
- gerenciamento de pautas e atas;
- validação dos dados recebidos;
- acesso ao banco de dados;
- tratamento padronizado de erros;
- disponibilização de uma API REST para o aplicativo mobile.

A autorização deverá ser executada no backend, sem confiar exclusivamente em identificadores enviados pelo aplicativo cliente.

### 1.3. Banco de Dados

O banco de dados será responsável pela persistência das informações do SpotMeet.

Durante o desenvolvimento, será utilizado **PostgreSQL executado localmente**, evitando dependência de serviços externos que exijam assinatura.

## 2. Fluxo Geral da Arquitetura

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
Local
```

## 3. Escolha de Tecnologias

### 3.1. Aplicativo Mobile

- **React Native**
- **TypeScript**

O React Native será utilizado para o desenvolvimento da aplicação mobile multiplataforma, permitindo manter uma única base de código para Android e iOS. A escolha reduz a necessidade de manter projetos separados para cada sistema operacional e facilita a padronização da interface e das funcionalidades.

O TypeScript será utilizado por adicionar tipagem estática ao código, auxiliando na identificação de erros durante o desenvolvimento e melhorando a legibilidade e a manutenção da aplicação.

### 3.2. Backend

- **Java 21**
- **Spring Boot**
- **Spring Web**
- **Spring Data JPA**
- **Spring Security**

O backend será desenvolvido em Java 21 com Spring Boot. O Java foi escolhido por ser uma linguagem consolidada, fortemente tipada e adequada à organização de aplicações orientadas a objetos. O Spring Boot será utilizado para simplificar a configuração e a estruturação do backend, facilitando a criação dos serviços necessários ao SpotMeet.

O Spring Web será utilizado para disponibilizar a API REST responsável pela comunicação com o aplicativo mobile.

O Spring Data JPA será utilizado para simplificar a camada de persistência e o acesso aos dados por meio do mapeamento entre objetos Java e estruturas do banco de dados.

O Spring Security será utilizado para implementar os mecanismos de autenticação e autorização, permitindo centralizar o controle de acesso aos recursos protegidos do sistema.

### 3.3. Autenticação e Segurança

A arquitetura deverá prever:

- armazenamento de senhas utilizando função de hash apropriada;
- autenticação controlada pelo backend;
- autorização por operação e por objeto;
- proteção contra acesso indevido a recursos de outros usuários;
- validação dos dados recebidos pela API;
- consultas parametrizadas por meio da camada de persistência;
- uso de DTOs para controlar os campos aceitos nas operações;
- proibição de credenciais, tokens e secrets no repositório;
- logs sem senhas, tokens ou outros dados sensíveis.

A utilização de tokens JWT poderá ser adotada como estratégia de autenticação durante a implementação, em conjunto com o Spring Security.

### 3.4. Banco de Dados

- **PostgreSQL**

O PostgreSQL será executado localmente durante o desenvolvimento e será responsável pelo armazenamento dos dados necessários ao funcionamento do sistema. A escolha foi feita por ser um sistema gerenciador de banco de dados relacional gratuito e open-source, adequado ao armazenamento estruturado das informações do SpotMeet e compatível com a arquitetura definida para o backend.

### 3.5. Comunicação entre Aplicativo e Backend

A comunicação entre o aplicativo mobile e o backend ocorrerá por meio de:

- HTTP;
- API REST;
- JSON.
