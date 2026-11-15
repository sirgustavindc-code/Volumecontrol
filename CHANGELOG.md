# Mudanças

## 1.0.2

### Novidades

- **Painel flutuante** por cima de jogos e apps (janela oficial `TYPE_APPLICATION_OVERLAY`, permissão
  `SYSTEM_ALERT_WINDOW`), aberto pelo botão **Abrir painel** da notificação do serviço.
- Painel: fundo escuro semitransparente, cantos arredondados, ícone e nome reais de cada app, controle
  deslizante colorido, porcentagem, indicador animado nos apps que estão tocando.
- Arrastar com posição salva, **minimizar** (botão redondo), **expandir** (só apps em uso / todos os apps),
  **fechar** e **cadeado** para fixar a posição.
- Cartão "Painel flutuante" na tela principal, com o fluxo das permissões e avisos claros (sobreposição e
  notificações bloqueadas) e o botão "Mostrar o painel agora".
- Notificação única com ações "Abrir painel" e "Parar painel".

### Integração com o projeto existente (nada removido)

- O painel vive no `VolumeService` existente (uma notificação, um serviço). O motor de áudio, o Shizuku, as
  teclas, o painel em folha e o botão dos Ajustes rápidos continuam como na 1.0.1.
- `ServicePolicy`: o serviço também fica ligado enquanto o painel está ativo, mesmo sem Shizuku pronto ou com o
  controle individual desligado (a notificação precisa existir). Comportamento anterior preservado quando o
  painel está desligado.
- O serviço só chama o motor para restaurar volumes se houver algo a restaurar (evita chamadas inúteis).
- A notificação do serviço usa `FOREGROUND_SERVICE_IMMEDIATE` (o Android 12+ pode atrasar até 10 s sem isso) e é
  reexibida quando o app é aberto (o Android 14+ permite ao usuário dispensá-la).

### Testes

- 53 testes automáticos (eram 39): regras do painel e política do serviço.
- Corrigido durante a escrita um teste meu com expectativa errada (altura máxima da lista: 530, não 540) e uma
  asserção sem sentido, removida antes de rodar.

### Ainda não verificado em aparelho

Janela flutuante, notificação, arrastar, minimizar/expandir, comportamento sobre jogos e vídeos, reinício do
celular e encerramento do serviço. Ver "Limitações conhecidas" no README.

## 1.0.1

### Problemas encontrados na 1.0.0 e correções

| Problema | Correção |
|---|---|
| A lista de apps alterados ficava só na memória: se o serviço morresse, o app continuava baixo e "Restaurar" não desfazia | Lista gravada em disco (`touched`); "Restaurar", "Desativar" e reinício devolvem a 100% |
| Liberação de APIs ocultas para o processo inteiro (`"L"`) | Restrita a `android.media.*` e `ServiceManager` |
| Shizuku: sem tratamento de "permissão negada para sempre" e versão antiga; pedido de permissão falhava em silêncio | Novos estados `DENIED` e `OUTDATED`, mensagens e botões específicos; tudo dentro de try/catch |
| Falhas do motor de áudio eram invisíveis | Aviso na tela: "Função não disponível neste Android" ou "O sistema de áudio não respondeu" |
| Teclas: "pressionar" e "soltar" decididos separadamente | O "soltar" só é consumido se o "pressionar" foi |
| Teclas atuavam com tela bloqueada e durante ligações | Passam direto ao Android com tela bloqueada/apagada e com áudio em modo de chamada |
| Teclas desligadas na tela, mas o serviço de acessibilidade continuava recebendo teclas | Desligar a chave encerra o serviço (`disableSelf`) |
| "Volume +" com o app em 100% não fazia nada | Toque novo passa ao Android (volume geral); segurar a tecla não vaza |
| Reaplicação a cada 2 s mesmo sem Shizuku | Ocioso sem Shizuku |
| Serviço não era religado para restaurar volumes com o controle desligado | `ServicePolicy` decide quando rodar |
| Sem testes; CI só compilava | 39 testes; CI roda testes e gera o APK |
| `minSdk 30` sem verificação das APIs internas | `minSdk 34` (só Android 14 e 15 considerados) |

### Novidades

- Painel rápido (atalho do ícone e botão dos Ajustes rápidos) com os apps que estão tocando.
- Filtro "Configurados" e contador de apps configurados.
- Atalho para a tela de bateria (a One UI pode fechar o serviço).
- Configurações migradas automaticamente do formato da 1.0.0.

### Removido

- Suporte a Android 11 a 13 (não verificado).
- Nenhuma função removida além disso.
