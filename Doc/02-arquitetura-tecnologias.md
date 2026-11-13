# Bloco 2 - Arquitetura e Tecnologias

## 1. Arquitetura do Sistema

O SpotMeet usa uma arquitetura cliente-servidor, separando o aplicativo mobile, o servidor (backend) e o banco de dados. O servidor concentra as regras de negócio, a autorização e a gravação dos dados. O aplicativo exibe as informações, envia as ações do usuário e também valida os campos dos formulários para orientar o usuário; essa validação é repetida no servidor.

### 1.1. Aplicativo Mobile

O aplicativo é desenvolvido para **Android e iOS** com uma única base de código.

Responsabilidades:

- exibir as telas e os componentes da interface no tema claro ou escuro, com a preferência salva no perfil;
- cadastro, login com e-mail e senha, login com Google (APK Android) e bloqueio biométrico opcional;
- consultar e gerenciar organizações e comissões;
- agendar, editar, repetir e cancelar reuniões, anexar a pauta e responder aos convites;
- registrar entrada e saída nas reuniões e consultar a lista de presença;
- escrever e enviar a ata da reunião, e aprovar ou reprovar a ata das reuniões em que registrou entrada;
- abrir votações durante a reunião, votar e consultar o resultado;
- consultar o relatório individual de participação nas reuniões;
- consultar o manual de uso (Ajuda) dentro do aplicativo;
- abrir a reunião a partir do botão "Abrir no SpotMeet" dos e-mails;
- comunicar-se com o servidor por meio da API REST.

A sessão fica em memória durante o uso. Quando o usuário habilita o bloqueio biométrico, a sessão também é guardada no armazenamento seguro do aparelho, para permitir a reentrada pela biometria.

### 1.2. Backend

O servidor centraliza as regras de negócio e controla o acesso aos dados. O código é dividido em camadas:

| Pacote | Responsabilidade |
|---|---|
| `controller` | Recebe as requisições HTTP e devolve as respostas em JSON, os arquivos anexados e a página de abertura da reunião |
| `service` | Regras de negócio, envio de e-mails e tarefas agendadas |
| `repository` | Acesso ao banco de dados pelo Spring Data JPA |
| `model` | Entidades mapeadas para as tabelas do banco |
| `dto` | Objetos que definem os campos aceitos e devolvidos pela API |
| `security` | Autenticação por token JWT e controle das tentativas de login e de código |
| `config` | Configuração do Spring Security, do envio assíncrono de e-mails, do agendamento e da criação do banco |
| `exception` | Tratamento padronizado dos erros da API |

Responsabilidades:

- autenticação dos usuários e autorização por operação e por recurso;
- gerenciamento de usuários, organizações, comissões, reuniões, atas e votações;
- cálculo do relatório individual a partir dos registros de entrada e saída;
- encerramento da votação no horário de término da reunião, quando o presidente não a encerra antes;
- validação dos dados recebidos;
- armazenamento dos anexos das reuniões e das fotos de perfil no próprio banco de dados (até 10 MB por anexo);
- envio de e-mails em HTML, com versão em texto simples, feito em segundo plano depois que a operação é gravada;
- tarefa agendada, a cada hora, que cria as próximas datas das reuniões que se repetem (até três meses à frente);
- remoção, a cada novo envio de anexo, dos anexos com mais de um dia que não foram ligados a nenhuma reunião;
- registro das ações importantes em log de auditoria.

A autorização é feita no servidor, sem confiar nos identificadores enviados pelo aplicativo.

### 1.3. Banco de Dados

O banco de dados é o **PostgreSQL**, executado localmente. Na primeira execução, o servidor cria o banco `spotmeet_db`, se ele ainda não existir, e as tabelas. As alterações de estrutura entre os incrementos são aplicadas pelo Hibernate (`ddl-auto=update`), sem apagar os dados existentes.

### 1.4. Serviços Externos

| Serviço | Uso | Obrigatório |
|---|---|---|
| Gmail (SMTP) | Envio dos códigos de verificação e dos avisos, com uma conta Gmail e senha de app | Não. Sem a conta configurada, o código de verificação aparece no console do servidor |
| Google (login) | Confirma a identidade de quem entra com a conta Google. O servidor baixa as chaves públicas do Google para conferir o token recebido do aplicativo | Não. Usado só pelo botão "Continuar com Google" |

Nenhum dos dois exige assinatura paga.

## 2. Fluxo Geral da Arquitetura

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
Local
```

No login com Google, o aplicativo obtém o token de identidade diretamente do Google e o envia ao servidor, que confere o token e só então emite a sessão JWT do SpotMeet.

## 3. Escolha de Tecnologias

### 3.1. Aplicativo Mobile

- **React Native** 0.88.1, com React 19.3.0
- **Expo SDK 58** (pacote `expo` ~58.0.21)
- **TypeScript** 6.0
- **React Navigation** 7

O React Native permite manter uma única base de código para Android e iOS, sem projetos separados para cada sistema operacional.

O Expo reúne as bibliotecas nativas usadas pelo aplicativo e permite testar no celular pelo Expo Go, sem compilar o aplicativo a cada mudança. O APK de Android é gerado a partir do mesmo código pelo EAS Build, serviço de compilação do Expo. O login com Google, a abertura do aplicativo pelo link dos e-mails e o Face ID dependem de módulos nativos que o Expo Go não inclui, por isso funcionam no APK ou em builds próprias.

O TypeScript adiciona tipagem estática ao código, o que ajuda a encontrar erros durante o desenvolvimento e facilita a manutenção.

Principais bibliotecas usadas pelo aplicativo:

| Biblioteca | Uso |
|---|---|
| `@react-navigation/native`, `bottom-tabs` e `native-stack` | Abas inferiores e navegação entre telas |
| `react-hook-form` | Formulários e validação dos campos |
| `@react-native-community/datetimepicker` | Seletores de data e de hora do próprio celular |
| `expo-document-picker` | Escolha do arquivo anexado à pauta |
| `expo-file-system` e `expo-sharing` | Download do anexo e abertura com outro aplicativo do celular |
| `expo-image-picker` e `expo-image-manipulator` | Foto de perfil pela câmera ou galeria, recortada e reduzida antes do envio |
| `expo-local-authentication` | Bloqueio biométrico (digital ou rosto, com o PIN do aparelho como alternativa no desbloqueio) |
| `expo-secure-store` | Guarda a sessão de forma protegida quando o bloqueio biométrico está habilitado |
| `@react-native-async-storage/async-storage` | Endereço do servidor e preferências do bloqueio biométrico |
| `expo-network` | Endereço do aparelho na rede, usado para localizar o servidor |
| `@react-native-google-signin/google-signin` | Login com Google no Android |
| `@react-native-vector-icons/ionicons` | Ícones |
| `expo-application` e `expo-constants` | Versão instalada do aplicativo e configuração do ambiente |
| `expo-splash-screen` e `expo-status-bar` | Tela de abertura e barra de status |
| `react-native-safe-area-context` e `react-native-screens` | Áreas seguras da tela e telas nativas da navegação |
| `expo-build-properties` | Configuração da compilação nativa |

### 3.2. Backend

- **Java 21**
- **Spring Boot** 3.3.5
- **Spring Web** (Spring MVC)
- **Spring Data JPA** (Hibernate)
- **Spring Security**
- **Spring Validation**
- **Spring Mail**
- **Spring Actuator**
- **JJWT** 0.12.6

O Java foi escolhido por ser uma linguagem consolidada, fortemente tipada e adequada à organização em classes e camadas. O Spring Boot simplifica a configuração e a estrutura do servidor.

O **Spring Web (Spring MVC)** é a tecnologia Java que cria a API REST usada pelo aplicativo: os controllers recebem as requisições HTTP e devolvem JSON.

O Spring Data JPA faz o mapeamento entre as classes Java e as tabelas do banco, e as consultas são parametrizadas.

O Spring Security implementa a autenticação e a autorização, centralizando o controle de acesso às rotas protegidas.

O Spring Validation confere os dados recebidos nos DTOs antes de chegarem às regras de negócio, que fazem as demais verificações. O Spring Mail envia os e-mails pelo SMTP do Gmail. O Spring Actuator publica as rotas `/actuator/health` e `/actuator/info`; a segunda é usada pelo aplicativo para encontrar o servidor na rede local. As demais rotas do Actuator são restritas ao perfil SYSADMIN, enquanto as rotas `/api/admin/**` aceitam ADMIN ou SYSADMIN.

### 3.3. Autenticação e Segurança

- senhas e códigos de verificação guardados com BCrypt (custo 12);
- autenticação por token JWT, gerado e conferido pela biblioteca JJWT em conjunto com o Spring Security;
- e-mail confirmado por código de 6 dígitos, válido por 5 minutos;
- bloqueio temporário de 15 minutos na 10ª tentativa errada de login ou de código;
- no login com Google, o servidor confere a assinatura do token com as chaves públicas do Google, o emissor, o ID do cliente, a validade e se o e-mail foi verificado;
- autorização por operação e por recurso, com proteção contra acesso a recursos de outros usuários;
- validação dos dados recebidos e uso de DTOs para controlar os campos aceitos;
- anexos conferidos pelo conteúdo do arquivo, não só pela extensão;
- senha do banco, senha do e-mail e chave JWT configuradas no arquivo `backend/.env`, que não é versionado. O ID de cliente do Google é público e já vem configurado no servidor.

### 3.4. Banco de Dados

- **PostgreSQL** 18

O PostgreSQL é um sistema gerenciador de banco de dados relacional gratuito e de código aberto, adequado ao armazenamento estruturado das informações do SpotMeet e compatível com o Spring Data JPA. As fotos de perfil e os anexos das reuniões também ficam no banco, sem serviço externo de arquivos.

### 3.5. Comunicação entre Aplicativo e Backend

A comunicação entre o aplicativo e o servidor ocorre por:

- HTTP;
- API REST;
- JSON (e `multipart/form-data` no envio do anexo e da foto de perfil).

O aplicativo localiza o servidor na rede Wi-Fi automaticamente; o endereço também pode ser informado na opção "Servidor" da tela de login.

O botão "Abrir no SpotMeet" dos e-mails aponta para uma página do servidor (`/abrir/reuniao/{id}`), que abre o aplicativo na reunião pelo endereço `spotmeet://reunioes/{id}`. A página não mostra dados da reunião; o aplicativo continua exigindo login e permissão para exibi-la.

## 4. Versões

| Parte | Tecnologia | Versão |
|---|---|---|
| Aplicativo | Expo SDK | 58 |
| Aplicativo | React Native | 0.88.1 |
| Aplicativo | React | 19.3.0 |
| Aplicativo | TypeScript | 6.0 |
| Aplicativo | React Navigation | 7 |
| Aplicativo | Versão do SpotMeet | 1.2.0 |
| Ambiente | Node.js | 22.13 ou superior |
| Ambiente | Android | 7.0 (API 24) ou superior |
| Ambiente | iOS | 16.4 ou superior |
| Servidor | Java | 21 |
| Servidor | Spring Boot | 3.3.5 |
| Servidor | JJWT | 0.12.6 |
| Banco de dados | PostgreSQL | 18 |

As versões de todas as dependências estão em `SpotMeetApp/package.json` e `backend/pom.xml`.
