# Bloco 4 - Design e UI/UX do SpotMeet

## 1. Escopo e fontes

Este documento descreve a identidade da marca e a interface implementada nos Incrementos 1, 2 e 3. A descrição foi conferida no código e nas telas do aplicativo ligado a um servidor de teste.

A identidade e os ativos de design publicados nos incrementos anteriores foram mantidos. As cores atuais vêm do [ThemeContext](<../SpotMeetApp/src/context/ThemeContext.tsx>); a navegação, do [App](<../SpotMeetApp/App.tsx>); os componentes de reuniões usam os [estilos compartilhados](<../SpotMeetApp/src/components/meetings/meetingStyles.tsx>).

## 2. Nome, ícone e logo

![Logo do SpotMeet, identidade da marca](<imagens/design/ícones.png>)

O nome **SpotMeet** representa um ponto de encontro: **Spot**, local ou ponto, e **Meet**, encontrar-se ou reunir-se. A proposta é centralizar a organização das informações de reuniões.

A logo mantém o roxo **#901ECE**, escolhido para transmitir modernidade, sofisticação e inovação. Sua letra **S**, de SpotMeet, usa **Bree Serif** na composição gráfica. Os elementos adjacentes preservam os significados definidos para a marca:

- prancheta com checklist: organização de pautas, documentação e acompanhamento;
- balão de conversa: comunicação e colaboração;
- câmera: associação visual com reuniões digitais, não uma função de videoconferência própria do aplicativo.

O roxo da logo não foi substituído pela cor dos botões. O [app.json](<../SpotMeetApp/app.json>) também mantém `#901ECE` como fundo do ícone adaptativo Android. As imagens existentes da marca são usadas no ícone, na abertura e na autenticação.

## 3. Cores e temas implementados

A interface usa o **anil #5C6BC0** como cor principal. A paleta violeta `#7C3AED`, `#5B21B6`, `#A78BFA` e `#C084FC` da proposta anterior foi substituída pelo anil nas telas; o roxo continua na logo.

| Uso no ThemeContext | Claro | Escuro |
|---|---|---|
| Fundo (`background`) | `#F4F6F9` | `#121212` |
| Cartões (`card`) | `#FFFFFF` | `#1E1E1E` |
| Texto (`text`) | `#1A1A1A` | `#FFFFFF` |
| Texto secundário (`subtext`) | `#666666` | `#A0A0A0` |
| Campos (`inputBg`) | `#ECEFF1` | `#2A2A2A` |
| Bordas (`border`) | `#CFD8DC` | `#333333` |
| Itens (`itemBg`) | `#F0F2F5` | `#2A2A2A` |
| Ação principal e aba ativa | `#5C6BC0` | `#5C6BC0` |
| Fundo da barra de abas | `#FFFFFF` | `#1E1E1E` |
| Borda da barra de abas | `#E0E0E0` | `#333333` |
| Aba inativa | `#78909C` | `#A0A0A0` |
| Sucesso | `#388E3C` | `#4CAF50` |
| Atenção | `#F57C00` | `#FFB300` |
| Erro e ação destrutiva | `#D32F2F` | `#EF5350` |

Alguns componentes têm complementos locais: reuniões usam `#ADB5EF` para acentos no escuro e superfícies `#EBEDF7` no claro e `#2A2D45` no escuro. O [AuthLayout](<../SpotMeetApp/src/components/auth/AuthLayout.tsx>) usa fundo `#5C6BC0` no claro e `#4C51C6` no escuro. A abertura nativa tem fundo `#1E1E2E`, separado do fundo das telas.

Na tela **Perfil** de Ajustes, existem **Claro** e **Escuro**. A escolha tem prévia e só é persistida ao tocar em **Salvar Alterações**; sair do Perfil sem salvar pergunta se a pessoa quer salvar ou descartar, e descartar desfaz a prévia. Sem sessão, o ThemeContext inicia no escuro. Embora o tipo de preferência aceito pela API inclua `SYSTEM`, o aplicativo atual não oferece a opção Sistema nem acompanha automaticamente a aparência do aparelho.

## 4. Tipografia e ícones

Bree Serif pertence à arte da logo, não à fonte dos formulários. Os componentes inspecionados usam a fonte padrão nativa do React Native, sem definir uma família Roboto personalizada. A escala Roboto/Sans Serif da proposta anterior é referência histórica, não uma escala única aplicada a todas as telas.

Exemplos de `fontSize` e peso definidos no código atual, sujeitos à escala de texto do aparelho:

| Elemento | Tamanho | Peso |
|---|---:|---:|
| Título Reuniões | 30 | 700 |
| Título Ajustes | 26 | 700 |
| Título de folha de reunião | 22 | 700 |
| Título de cartão de reunião | 15 | 700 |
| Texto de campo | 16 | padrão nativo |
| Botão de formulário | 16 | 600 |
| Rótulo de campo | 11 | 700 |
| Texto de selo de reunião | 12 | 600 |

Os ícones são da família **Ionicons**, pelo pacote `@react-native-vector-icons/ionicons`, acompanhados de textos ou rótulos de acessibilidade nos componentes correspondentes.

## 5. Navegação atual

Antes do login, os fluxos são entrada, cadastro, confirmação do e-mail e recuperação de senha em etapas. O login mostra **Conecte-se com sua equipe em qualquer lugar**; **Organize suas reuniões em um só lugar** aparece no cadastro. Os dois textos estão no [AuthScreen](<../SpotMeetApp/src/screens/AuthScreen.tsx>).

Depois do login, há duas navegações diferentes:

| Perfil | Abas inferiores, na ordem |
|---|---|
| Usuário comum, inclusive dirigentes de organizações e presidentes de comissões | Reuniões, Organização, Ajustes |
| Papel global ADMIN ou SYSADMIN | Aprovações, Organizações, Monitoramento, Controles |

O selo da aba Reuniões conta só convites de reuniões sem resposta. As atas para aprovar aparecem no aviso do topo da lista, junto com os convites ("Você tem 1 convite e 1 ata para responder."), que abre a página Pendências. Para quem preside, o mesmo aviso mostra as recusas dos convites ("1 convidado recusou seu convite."), listadas em Pendências com o motivo. Não há abas independentes de Agenda, Calendário ou Atas: a ata e as votações ficam no detalhe de cada reunião, e Meu Relatório e Ajuda ficam em Ajustes. A organização e a comissão são gerenciadas dentro de Organização, conforme as permissões, sem se confundirem com o painel global do sistema.

O botão voltar do Android fecha primeiro o que está mais por dentro (folha, janela ou página interna, como Membros, Comissões ou Pendências); em Organização, as abas internas Entrar, Criar e Gerenciar voltam para Minhas; em Minhas ou na raiz de Ajustes, volta para Reuniões; na raiz de Reuniões, o aplicativo vai para o segundo plano. Na tela "SpotMeet bloqueado", ele fecha o aplicativo.

## 6. Telas e componentes implementados

| Área | Conteúdo atual |
|---|---|
| Autenticação | Logo, cartão de formulário, campos com erros, senha com controle de visibilidade, regras de senha, código de seis dígitos e opção Servidor |
| Reuniões | Filtros Todas, Próximas e Passadas; Acontecendo agora, Próximas e Anteriores; bloco de data nos cartões; atalho Meu Relatório no topo; aviso de convites, atas e recusas e página Pendências; selos "Ata para aprovar", "1 recusou" (para quem preside) e "Só visualização" (reuniões que o líder da organização acompanha sem participar); botão de agendamento para quem preside comissão |
| Formulário de reunião | Comissão, título, datas, horas, dia inteiro, frequência, modalidade, local/link, pauta, anexo, prioridade e seleção de convidados com busca |
| Detalhe da reunião | Situação, prioridade, data e hora, recusas com o motivo no topo (para quem preside) ou o quadro "Só visualização", pauta/anexo, resposta ao convite com o motivo da recusa na largura do cartão, presença antes dos convidados, fotos ou iniciais, cartões Ata e Votações e ações do presidente |
| Ata | Cartão com a situação (Rascunho, Em aprovação, Aprovada, Reprovada) e a ação de cada perfil; página de escrita com Assuntos tratados, Decisões tomadas e Observações; página de leitura com presentes e ausentes; Aprovar com confirmação e Reprovar com motivo; respostas com nome e motivo para o presidente |
| Votações | Lista com selo Aberta ou Encerrada; página Abrir votação com pergunta e descrição; opções Sim, Não e Abstenção (com justificativa opcional) e confirmação do voto; contagem ao vivo para o presidente e resultado nominal com barras e porcentagens depois de encerrada |
| Organização | Minhas, Entrar (chave ou lista de organizações com busca) e Criar (com "Mostrar na lista de organizações"); membros (com o status de presença de cada um), solicitações, comissões com "Convidar integrantes", convites e ações de gestão conforme o papel e o estado da organização |
| Ajustes | Menu com o cartão de foto, nome e e-mail (abre o Perfil), "Reuniões e ajuda" (Meu Relatório de Reuniões, Ajuda), "Conta" (Segurança), "Status de Presença" (Presente, Ausente e Ocupado, salvos no toque, sem página própria) e Sair da conta |
| Perfil | Foto, nome, biografia, troca de e-mail e tema, com Salvar Alterações |
| Segurança | Troca de senha e bloqueio biométrico opcional |
| Meu Relatório | Totais de reuniões, horas e comissões; filtros de período (Este mês, Últimos 3 meses, Este ano, Todo o período, Escolher as datas) e de comissão em botões na própria tela; reuniões agrupadas por mês, com "Saída não registrada" quando falta a saída |
| Ajuda | Busca por palavra, tópicos agrupados em cinco perfis e página do tópico com passo a passo, destaque e "Veja também", sem imagens |
| Administração | Aprovação e gestão das organizações, monitoramento/relatórios do sistema e controles com confirmação de ações críticas |

A disponibilidade **Presente, Ausente ou Ocupado**, escolhida no grupo Status de Presença do menu de Ajustes, é uma preferência do usuário, vista pelos demais integrantes na página Membros da organização. Não é o registro de entrada ou saída em uma reunião. Nos Controles, as ações de subsistema oferecidas são Banco de dados e Recursos; o estado da Rede é consultado no Monitoramento.

### 6.1. Formulários, cartões e folhas

- [FormField](<../SpotMeetApp/src/components/forms/FormField.tsx>): rótulo em maiúsculas, indicação de obrigatório, borda de foco ou erro, mensagem junto ao campo, contador "usados/limite" nos textos longos e cantos de 14 unidades.
- [FormButton](<../SpotMeetApp/src/components/forms/FormButton.tsx>): ação primária preenchida ou secundária, indicador de carregamento e bloqueio durante a operação; altura mínima de 52, ou 48 no modo compacto de autenticação.
- Reuniões: cartões com raio 16, borda e espaçamento; folhas inferiores com cantos superiores de raio 24; botão flutuante de agendamento de 60 por 60 e raio 20. Não se aplica uma elevação fixa de 2 a todos os cartões.
- [DateTimeField](<../SpotMeetApp/src/components/meetings/DateTimeField.tsx>): no Android abre o diálogo nativo; no iOS usa seletor de calendário ou hora dentro de uma folha, com Concluir e Cancelar.
- [UserAvatar](<../SpotMeetApp/src/components/profile/UserAvatar.tsx>): foto autorizada ou iniciais quando não há imagem disponível.
- [ToastHost](<../SpotMeetApp/src/components/ui/ToastHost.tsx>) e [ConfirmationHost](<../SpotMeetApp/src/components/ui/ConfirmationHost.tsx>): avisos locais no topo, que somem sozinhos em 3,5 segundos e deixam o toque passar para o que está atrás, e diálogos de confirmação. Esses avisos não são notificações push nem uma central de notificações.

### 6.2. Situação e presença das reuniões

Os selos combinam texto, ícone e cor, com fundo translúcido da cor correspondente. Não usam a tabela fixa de amarelo/azul/verde da proposta antiga:

| Selo | Cor atual |
|---|---|
| Agendada | Acento do componente (anil no claro, `#ADB5EF` no escuro) |
| Em andamento | Sucesso do tema |
| Encerrada | Texto secundário do tema |
| Cancelada | Erro do tema |
| Prioridade Alta | Atenção do tema |
| Prioridade Baixa ou Média | Texto secundário do tema |

Em Acontecendo agora, as ações de presença ficam no cartão. No detalhe, o cartão Presença permite entrada, saída e acesso à lista conforme a situação e o papel. O presidente pode registrar para convidados e gerar a lista; os demais a consultam depois de gerada. Cada ocorrência de uma série tem convite e presença próprios. Editar ou cancelar afeta só a data escolhida, não a série inteira. Depois que a ata é enviada, o detalhe esconde "Limpar registros" e avisa que a presença não pode mais ser corrigida.

### 6.3. Cores da ata e das votações

As duas usam as cores do tema, com o mesmo selo de fundo translúcido:

| Elemento | Cor |
|---|---|
| Ata Rascunho | Texto secundário |
| Ata Em aprovação | Atenção |
| Ata Aprovada; resposta Aprovou | Sucesso |
| Ata Reprovada; resposta Reprovou | Erro |
| Votação Aberta | Sucesso |
| Votação Encerrada | Texto secundário |
| Barra Sim / Não / Abstenção | Sucesso / Erro / Texto secundário |
| Resultado Aprovada / Rejeitada / Empate ou Sem votos | Sucesso / Erro / Atenção |

As porcentagens do resultado são arredondadas pelo maior resto, para que somem 100% (5, 2 e 1 votos dão 63%, 25% e 12%).

## 7. Adaptação e limites de execução

Há tratamento de área segura, rolagem, teclado, campos com erro e estados de carregamento. A orientação configurada é retrato.

O login com Google está implementado apenas no Android com módulo nativo e pacote/assinatura registrados no Google Cloud; não funciona no Expo Go nem no iOS. O link `spotmeet://reunioes/{id}` exige uma build própria com o esquema registrado. Pelo Expo Go, o caminho de consulta é abrir a aba Reuniões manualmente. Face ID também exige build própria de iOS, não é validado pelo Expo Go. Instalação e execução estão detalhadas no [README](<../README.md>).

## 8. Incremento 3 em relação ao design

As telas de ata, votação, Meu Relatório, Ajuda e o menu de Ajustes seguem o design feito pela equipe para o Incremento 3, com estas diferenças:

- a justificativa da abstenção aparece logo abaixo das opções, e não numa tela à parte;
- os filtros do relatório são botões na própria tela, e não uma folha que abre por cima;
- Aprovar a ata pede confirmação, porque a resposta não pode ser trocada;
- quem não é presidente também pode ler a ata reprovada;
- o cartão do nome em Ajustes não mostra um papel, porque o usuário não tem papel geral;
- a Ajuda não tem imagens das telas.

A visão de calendário separado continua fora do aplicativo.

## 9. Mockups da proposta inicial

Os mockups abaixo são da proposta inicial do design. A tabela indica o que mudou no aplicativo implementado.

| Mockup | Diferença para o aplicativo atual |
|---|---|
| [Mockup de Login](<imagens/design/Tela_Login.jpeg>) | A tela de login atual está descrita na seção 5 |
| [Mockup de Agenda](<imagens/design/Tela_Agenda.jpg>) | A lista atual se chama Reuniões |
| [Mockup de Calendário](<imagens/design/Tela_Calendario.jpg>) | A visão de calendário separada não foi implementada |
| [Mockup de Atas](<imagens/design/Tela_Atas.jpg>) | Não há uma tela só de atas; a ata fica no detalhe de cada reunião |
| [Mockup de Ajustes](<imagens/design/Tela_Ajuste.jpg>) | Ajustes virou um menu, descrito na seção 6 |
