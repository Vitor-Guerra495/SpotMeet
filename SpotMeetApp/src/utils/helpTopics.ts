/**
 * Topics of the in-app user manual ("Ajuda"), grouped by profile. Button names are the ones shown by the app.
 * The same text is in Doc/06-manual-de-uso.md.
 */
export type HelpProfile = 'all' | 'member' | 'president' | 'owner' | 'admin';

export interface HelpTopic {
  id: string;
  profile: HelpProfile;
  title: string;
  summary: string;
  steps: string[];
  note?: { title: string; text: string };
  related: string[];
}

export const PROFILE_LABELS: Record<HelpProfile, string> = {
  all: 'Primeiros Passos', member: 'Membro', president: 'Presidente da comissão', owner: 'Dono e vice-líder da organização', admin: 'Administrador do sistema',
};

export const PROFILE_HINTS: Record<HelpProfile, string> = {
  all: 'Todos', member: 'Seu perfil', president: 'Quem preside uma comissão', owner: 'Quem lidera uma organização', admin: 'Equipe do SpotMeet',
};

export const HELP_TOPICS: HelpTopic[] = [
  {
    id: 'criar-conta', profile: 'all', title: 'Criar conta e confirmar o e-mail',
    summary: 'Sem confirmar o e-mail, a conta não é ativada.',
    steps: [
      'Na tela de entrada, toque em Não tem uma conta? Cadastre-se.',
      'Preencha Nome completo, E-mail e Senha e toque em Criar Conta.',
      'Você recebe um código de 6 dígitos por e-mail. Digite o código na tela Confirme seu e-mail.',
      'Toque em Confirmar e Ativar Conta.',
      'Pronto: o SpotMeet entra na sua conta.',
    ],
    note: { title: 'O código não chegou?', text: 'Toque em Reenviar código por E-mail e confira também a caixa de spam.' },
    related: ['recuperar-senha', 'entrar'],
  },
  {
    id: 'entrar', profile: 'all', title: 'Entrar com e-mail e senha, Google ou biometria',
    summary: 'A primeira entrada é com e-mail e senha ou com a conta Google. A biometria protege as próximas aberturas do app.',
    steps: [
      'Na tela de entrada, informe o e-mail e a senha e toque em Entrar no SpotMeet.',
      'No Android, você também pode tocar em Continuar com Google e escolher a sua conta.',
      'Para usar a biometria, abra Ajustes, toque em Segurança e ligue o Bloqueio biométrico.',
      'Com a biometria ligada, o app pede a digital ou o rosto ao abrir. Se o pedido não aparecer, toque em Desbloquear.',
      'Para usar o PIN, o padrão ou a senha do celular, toque em Usar a senha do aparelho.',
      'Para trocar de conta, toque em Sair desta conta. A biometria deste aparelho é desligada.',
    ],
    note: { title: 'Esqueceu a senha?', text: 'Toque em Esqueci minha senha na tela de entrada.' },
    related: ['recuperar-senha', 'ajustes'],
  },
  {
    id: 'recuperar-senha', profile: 'all', title: 'Recuperar a senha',
    summary: 'São 3 etapas: e-mail, código e nova senha.',
    steps: [
      'Na tela de entrada, toque em Esqueci minha senha.',
      'Informe o e-mail cadastrado e toque em Enviar Código por E-mail.',
      'Digite o código recebido e toque em Validar Código.',
      'Defina e confirme a nova senha e toque em Atualizar Senha.',
      'O app volta para a tela de entrada. Entre com a nova senha.',
    ],
    note: { title: 'O código expira.', text: 'Se ele estiver inválido ou expirado, peça um novo código e tente de novo.' },
    related: ['criar-conta', 'entrar'],
  },
  {
    id: 'organizacao', profile: 'all', title: 'Criar uma organização e entrar em uma',
    summary: 'Uma organização reúne as comissões. Ela só funciona depois de aprovada pela administração do SpotMeet.',
    steps: [
      'Abra a aba Organização.',
      'Para criar, toque em Criar, informe o Nome da organização e a Chave de acesso e toque em Criar organização.',
      'Deixe marcado Mostrar na lista de organizações para que outras pessoas a encontrem. Desmarque se ela for privada.',
      'A organização fica em análise até a administração aprovar. Acompanhe a situação em Minhas.',
      'Para entrar em uma organização que já existe, toque em Entrar, escolha uma na lista ou digite a Chave da organização e toque em Solicitar acesso.',
      'O Dono ou o vice-líder da organização aprova o seu pedido.',
    ],
    note: { title: 'A chave', text: 'O # é colocado sozinho. Uma organização fora da lista só é encontrada pela chave: peça a quem lidera.' },
    related: ['convite-comissao', 'pedidos'],
  },
  {
    id: 'convite-comissao', profile: 'all', title: 'Responder a um convite de comissão',
    summary: 'Você só vê as reuniões de uma comissão depois de aceitar o convite.',
    steps: [
      'O convite aparece na aba Organização, com o nome da comissão. Você também recebe um e-mail.',
      'Toque em Aceitar ou em Recusar.',
      'Depois de aceitar, as reuniões da comissão aparecem na aba Reuniões.',
    ],
    related: ['organizacao', 'convite-reuniao'],
  },
  {
    id: 'ajustes', profile: 'all', title: 'Editar o perfil, a segurança e o status de presença',
    summary: 'Tudo fica em Ajustes. O perfil abre pelo cartão com o seu nome.',
    steps: [
      'Abra Ajustes e toque no cartão com o seu nome para abrir o Perfil.',
      'Troque a foto (Galeria ou Câmera), o nome, a biografia ou o tema e toque em Salvar Alterações.',
      'Para trocar o e-mail, toque em Alterar ao lado do e-mail e confirme os códigos enviados ao e-mail atual e ao novo.',
      'Em Segurança, troque a senha em Alterar Senha e ligue ou desligue o Bloqueio biométrico.',
      'Em Status de Presença, no próprio menu de Ajustes, toque em Presente, Ausente ou Ocupado. O status é salvo na hora e aparece para os outros integrantes, abaixo do seu e-mail, na página Membros da organização.',
    ],
    related: ['entrar', 'relatorio'],
  },
  {
    id: 'convite-reuniao', profile: 'member', title: 'Responder a um convite de reunião',
    summary: 'O presidente da comissão convida e espera a sua resposta.',
    steps: [
      'Os convites aparecem na aba Reuniões, no aviso de pendências, e chegam por e-mail.',
      'Toque na reunião para ver a data, o horário, o local e a pauta.',
      'Toque em Aceitar ou em Recusar. Ao recusar, você pode escrever o motivo para o presidente.',
      'Em uma reunião que se repete, cada data tem a sua resposta.',
    ],
    note: { title: 'Mudou de ideia?', text: 'Você pode mudar a resposta até o fim da reunião.' },
    related: ['presenca', 'convite-comissao'],
  },
  {
    id: 'presenca', profile: 'member', title: 'Registrar entrada e saída',
    summary: 'A presença só é registrada com a reunião em andamento.',
    steps: [
      'Quando a reunião começa, ela aparece em Acontecendo agora, na aba Reuniões.',
      'Toque em Registrar entrada ao chegar.',
      'Toque em Registrar saída ao sair.',
      'A lista de presença fica disponível depois que o presidente gerar.',
    ],
    note: { title: 'Por que registrar?', text: 'Só quem registrou entrada vota, responde à ata e tem a reunião no relatório.' },
    related: ['votar', 'relatorio'],
  },
  {
    id: 'aprovar-ata', profile: 'member', title: 'Aprovar ou reprovar uma ata',
    summary: 'Só responde quem registrou entrada na reunião. Quem faltou só lê a ata.',
    steps: [
      'Quando o presidente envia a ata, você recebe um e-mail e a reunião mostra Ata para aprovar.',
      'Abra a reunião e toque em Aprovar ou Reprovar, no cartão Ata.',
      'Leia a ata e toque em Aprovar, ou toque em Reprovar e escreva o motivo.',
      'Depois de enviada, a resposta não pode ser trocada.',
    ],
    note: { title: 'Como a ata é aprovada', text: 'Vale a maioria de quem respondeu. O presidente encerra a aprovação e você recebe o resultado por e-mail.' },
    related: ['presenca', 'escrever-ata'],
  },
  {
    id: 'votar', profile: 'member', title: 'Votar em uma votação',
    summary: 'Vota quem registrou entrada na reunião. O voto é nominal e não pode ser trocado.',
    steps: [
      'Quando o presidente abrir uma votação, ela aparece no cartão Votações da reunião.',
      'Toque em Votar Agora e leia a pergunta.',
      'Escolha Sim, Não ou Abstenção. Se escolher Abstenção, você pode escrever uma justificativa, mas não é obrigatório.',
      'Toque em Confirmar Voto. Depois de confirmado, ele não pode ser trocado.',
      'O resultado aparece para todos quando o presidente encerrar a votação ou quando a reunião terminar.',
    ],
    related: ['relatorio', 'presenca'],
  },
  {
    id: 'relatorio', profile: 'member', title: 'Ver meu relatório de reuniões',
    summary: 'Mostra as reuniões em que você registrou entrada, com o tempo em cada uma.',
    steps: [
      'Toque em Meu Relatório na aba Reuniões, ou abra Ajustes e toque em Meu Relatório de Reuniões.',
      'Escolha o período: Este mês, Últimos 3 meses, Este ano, Todo o período ou Escolher as datas.',
      'Escolha uma comissão ou deixe Todas as suas comissões.',
      'Cada reunião mostra a entrada, a saída e o tempo.',
    ],
    note: { title: 'Saída não registrada', text: 'Reuniões sem saída registrada aparecem com um traço e não entram em Horas no total.' },
    related: ['presenca', 'ajustes'],
  },
  {
    id: 'agendar', profile: 'president', title: 'Agendar, editar ou cancelar uma reunião',
    summary: 'Só o presidente da comissão agenda. Os convidados recebem um e-mail a cada mudança.',
    steps: [
      'Na aba Reuniões, toque no botão + (Agendar reunião).',
      'Escolha a comissão, escreva o título e defina o início e o término, ou marque o dia inteiro.',
      'Se quiser, defina a frequência, a modalidade, o local, o link, a pauta e um anexo, e escolha os convidados.',
      'Toque em Agendar reunião.',
      'Para mudar, abra a reunião e toque em Editar reunião ou em Cancelar reunião.',
      'Quando alguém recusa, você recebe um e-mail e vê quem recusou e o motivo no aviso de pendências e no topo da reunião.',
    ],
    note: { title: 'Reuniões que se repetem', text: 'Editar ou cancelar muda só a data escolhida. As outras continuam como estavam.' },
    related: ['lista-presenca', 'abrir-votacao'],
  },
  {
    id: 'lista-presenca', profile: 'president', title: 'Registrar a presença e gerar a lista',
    summary: 'O presidente registra a presença de qualquer convidado e gera a lista de presença.',
    steps: [
      'Com a reunião em andamento, abra a reunião.',
      'Use Entrada de todos que aceitaram e Saída de todos que estão dentro, ou registre a entrada e a saída de cada convidado.',
      'Toque em Gerar lista de presença.',
      'Para corrigir um registro, toque em Limpar registros no nome da pessoa.',
    ],
    note: { title: 'Até quando corrigir', text: 'A presença pode ser corrigida até a ata ser enviada para aprovação.' },
    related: ['escrever-ata', 'agendar'],
  },
  {
    id: 'escrever-ata', profile: 'president', title: 'Escrever a ata',
    summary: 'Só o presidente escreve a ata, e só depois que a reunião termina.',
    steps: [
      'Abra a reunião encerrada e toque em Escrever Ata, no cartão Ata.',
      'Preencha Assuntos tratados e Decisões tomadas. Observações é opcional.',
      'Os presentes e ausentes vêm da lista de presença e não podem ser editados.',
      'Toque em Salvar Rascunho para continuar depois, ou em Enviar para Aprovação.',
      'As pessoas presentes recebem um e-mail para aprovar ou reprovar.',
    ],
    note: { title: 'Ninguém além de você entrou?', text: 'A ata não pode ser enviada. Corrija a presença na reunião ou salve como rascunho.' },
    related: ['acompanhar-ata', 'lista-presenca'],
  },
  {
    id: 'acompanhar-ata', profile: 'president', title: 'Acompanhar a aprovação da ata',
    summary: 'A ata é aprovada se a maioria de quem respondeu aprovar. Você não responde à sua própria ata.',
    steps: [
      'Abra a reunião e toque em Acompanhar Aprovação, no cartão Ata.',
      'Veja quem aprovou, quem reprovou e o motivo, e quem ainda não respondeu.',
      'Toque em Encerrar Aprovação e confirme.',
      'Se a ata for reprovada, toque em Corrigir e Reenviar, ajuste o texto e toque em Reenviar para Aprovação.',
    ],
    note: { title: 'Empate ou nenhuma resposta', text: 'A ata não é aprovada. Ao reenviar, a contagem recomeça do zero. Uma ata aprovada não pode mais ser editada.' },
    related: ['escrever-ata', 'aprovar-ata'],
  },
  {
    id: 'abrir-votacao', profile: 'president', title: 'Abrir e encerrar uma votação',
    summary: 'A votação só é aberta com a reunião em andamento. Você vê a contagem ao vivo.',
    steps: [
      'Abra a reunião em andamento e toque em Abrir Votação, no cartão Votações.',
      'Escreva a pergunta e, se quiser, uma descrição, e toque em Abrir Votação.',
      'Acompanhe a contagem ao vivo e quem já votou.',
      'Toque em Encerrar Votação e confirme. Se você não encerrar, a votação fecha sozinha no fim da reunião.',
    ],
    note: { title: 'Resultado', text: 'Mais Sim do que Não aprova, mais Não do que Sim rejeita, e números iguais dão empate.' },
    related: ['votar', 'escrever-ata'],
  },
  {
    id: 'pedidos', profile: 'owner', title: 'Aprovar pedidos de acesso',
    summary: 'Quem pede para entrar, pela chave ou pela lista de organizações, espera a sua aprovação.',
    steps: [
      'Abra a aba Organização e toque em Gerenciar.',
      'Abra Solicitações.',
      'Toque em Aprovar ou em Rejeitar.',
    ],
    related: ['comissoes', 'integrantes'],
  },
  {
    id: 'comissoes', profile: 'owner', title: 'Criar comissões e convidar integrantes',
    summary: 'As reuniões acontecem dentro das comissões. Cada comissão tem um presidente.',
    steps: [
      'Em Gerenciar, abra as comissões e toque em Nova comissão.',
      'Para convidar, toque em Convidar integrantes no cartão da comissão e depois em Convidar, ao lado da pessoa.',
      'Para escolher o presidente, toque em Presidente, no cartão da comissão.',
      'Para trazer alguém de fora, toque em Convidar para organização.',
    ],
    note: { title: 'Reuniões das comissões', text: 'Na aba Reuniões você acompanha as reuniões de todas as comissões. Onde não é presidente nem convidado, aparece Só visualização.' },
    related: ['pedidos', 'integrantes'],
  },
  {
    id: 'integrantes', profile: 'owner', title: 'Gerenciar integrantes e papéis',
    summary: 'Os papéis da organização são Dono, Vice-líder, Subdono e Membro.',
    steps: [
      'Em Gerenciar, abra Membros e toque no integrante.',
      'Use Elevar papel ou Rebaixar papel para mudar o papel.',
      'Use Remover integrante para tirar a pessoa da organização.',
      'O Dono pode passar a organização para outra pessoa em Transferir liderança.',
    ],
    related: ['pedidos', 'comissoes'],
  },
  {
    id: 'admin-aprovacoes', profile: 'admin', title: 'Aprovar ou recusar um cadastro (aba Aprovações)',
    summary: 'Toda organização nova espera a análise da administração.',
    steps: [
      'Entre com a conta de administrador e abra a aba Aprovações.',
      'Toque na organização para ver os dados.',
      'Toque em Aprovar, ou em Recusar e escreva o motivo.',
      'O responsável pela organização recebe o resultado por e-mail.',
    ],
    related: ['admin-bloquear', 'admin-monitoramento'],
  },
  {
    id: 'admin-bloquear', profile: 'admin', title: 'Bloquear ou Aprovar uma Organização',
    summary: 'Uma organização bloqueada não cria comissões nem aprova pedidos de acesso.',
    steps: [
      'Abra a aba Organizações e toque na organização.',
      'Toque em Bloquear Organização e escreva o motivo.',
      'Confirme. O Dono da organização recebe um e-mail com o motivo.',
      'Para liberar de novo, abra a organização bloqueada e toque em Aprovar Organização.',
    ],
    note: { title: 'Os integrantes continuam como estavam.', text: 'Bloquear não remove ninguém. Ao aprovar de novo, tudo volta a funcionar.' },
    related: ['admin-aprovacoes', 'admin-controles'],
  },
  {
    id: 'admin-monitoramento', profile: 'admin', title: 'Acompanhar o Monitoramento',
    summary: 'Mostra se o servidor está funcionando e o que foi feito no sistema.',
    steps: [
      'Abra a aba Monitoramento.',
      'Confira a situação de Banco de dados, Rede, Recursos e API.',
      'Veja o total de Registros de auditoria e as Atividades recentes.',
    ],
    related: ['admin-controles', 'admin-aprovacoes'],
  },
  {
    id: 'admin-controles', profile: 'admin', title: 'Usar os Controles do sistema',
    summary: 'Ações que afetam o servidor inteiro. Todas pedem confirmação.',
    steps: [
      'Abra a aba Controles.',
      'Para reiniciar uma parte do servidor, escolha o subsistema e toque em Reiniciar subsistema.',
      'Para trocar a senha do administrador, toque em Alterar senha.',
      'O Executar Reset Total do Sistema apaga os dados. Ele só roda depois que você digita CONFIRM_FULL_RESET.',
    ],
    related: ['admin-monitoramento', 'admin-bloquear'],
  },
];

const fold = (text: string) => text.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();

/** Topics where every typed word starts a word of the title, summary, steps or highlight, ignoring accents and case ("ata" does not find "data"). */
export function searchTopics(query: string): HelpTopic[] {
  const words = fold(query).split(/\s+/).filter(Boolean);
  if (!words.length) return [];
  return HELP_TOPICS.filter(topic => {
    const parts = fold([topic.title, topic.summary, ...topic.steps, topic.note?.title ?? '', topic.note?.text ?? ''].join(' ')).split(/[^a-z0-9]+/);
    return words.every(word => parts.some(part => part.startsWith(word)));
  });
}
