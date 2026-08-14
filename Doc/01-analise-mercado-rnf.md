# Bloco 1 - Análise de Mercado e Requisitos Não Funcionais

## 1. Avaliação de Concorrentes (Visão de Negócio)

O SpotMeet foi idealizado para atuar como um Gestor Documental focado na potencialização da produtividade em reuniões. Esta análise de mercado direciona-se aos concorrentes diretos no segmento de organização corporativa, com ênfase na gestão de pautas, atas e agendamentos.

### 1.1. Matriz de Comparação Competitiva

A tabela a seguir consolida a avaliação dos principais sistemas de produtividade disponíveis no mercado:

| Aplicativo | Gestão de Calendário | Criação de Pauta (Pré-reunião) | Registro de Ata (Pós-reunião) | Modelo de Negócio (Acessibilidade) |
|---|---|---|---|---|
| Fellow.app | Sim: Integração automática com agendas. | Sim: É o foco principal do sistema, permitindo pautas colaborativas. | Sim: Registra decisões e gera planos de ação. | Não: Custo elevado; bloqueia integrações essenciais no plano gratuito. |
| Notion | Parcial: Exige construção manual de bases de dados ou templates. | Sim: Criação via blocos de texto dinâmicos. | Sim: Excelente para documentação de atas. | Parcial: Gratuito para uso pessoal, mas limita a colaboração em equipe, permissões de acesso e o histórico de blocos na versão livre. |
| Calendly / Doodle | Sim: Líderes na sincronização e agendamento. | Não: A jornada do usuário encerra no agendamento. | Não: Não possuem nenhum suporte ou editor de texto para registrar atas ou | Parcial: Recursos avançados exigem planos pagos. |

### 1.2. Mapeamento de Utilização Atual

Para fundamentar nossa proposta, analisamos o comportamento do consumidor em relação a essas ferramentas:

- **Fellow.app:** Alta adesão em startups e times ágeis para padronização de alinhamentos. Porém, a cobrança em dólar cria uma barreira de entrada para pequenas empresas e estudantes.
- **Notion:** Adotado globalmente como workspace e wiki corporativa. O principal gargalo é a necessidade de uma forte cultura organizacional; sem ela, a documentação de reuniões tende a ficar bagunçada.
- **Calendly / Doodle:** Domínio em vendas B2B e recrutamento (RH). O uso é estritamente transacional: o usuário interage por menos de dois minutos para agendar e abandona a plataforma durante a execução da reunião.
- **O Cenário de Fragmentação:** O mercado atual obriga o profissional a fragmentar seu fluxo de trabalho, utilizando o Calendly para agendar, o Notion para documentar e o Asana para delegar ações.

### 1.3. Análise de Forças e Fraquezas

- **Fellow.app:** Se destaca por ser nativo para pautas e integração automática. Falha em seu modelo comercial agressivo, limitando colaboração básica no modelo gratuito.
- **Notion:** Personalização e edição rica de documentos. Peca por exigir configuração manual (alta curva de aprendizado inicial), prejudicando usuários que buscam pautas "prontas para uso".
- **Calendly / Doodle:** Excelentes na eliminação de conflitos de agenda. Limitados por não oferecerem ecossistema de apoio durante e após a reunião.

## 2. Requisitos Não Funcionais (RNF)

As métricas a seguir foram estabelecidas para assegurar a viabilidade técnica por nossa equipe e o cumprimento de exigências arquiteturais do sistema:

- **RNF01 - Desempenho:** O sistema deve exibir a tela inicial (lista de reuniões) em até 5 segundos após autenticação, considerando o uso de rede móvel padrão.

- **RNF02 - Usabilidade:** A criação de uma nova reunião ou pauta de reunião deve ser iniciada com um limite máximo de 3 toques a partir da tela inicial (Home).

- **RNF03 - Segurança de Dados (Crítico):** É estritamente proibido o armazenamento de senhas em texto limpo ou de forma reversível; o banco de dados deve armazenar as senhas utilizando uma função de hash segura e apropriada para senhas.

- **RNF04 - Controle de Acesso (Crítico):** O sistema deve validar a sessão ativa. O backend deverá verificar a identidade autenticada do usuário e autorizar individualmente cada operação e recurso solicitado, bloqueando a visualização ou modificação de reuniões, pautas e atas para usuários que não sejam criadores ou participantes autorizados.

- **RNF05 - Compatibilidade e Portabilidade:** O aplicativo deve garantir renderização responsiva e funcionamento adequado nos sistemas operacionais Android (8.0+) e iOS (13.0+).
