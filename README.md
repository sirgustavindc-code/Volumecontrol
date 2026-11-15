# Volume Control 1.0.2

Define um volume separado para cada aplicativo (Android 14 e 15, feito para o Galaxy A15 5G) e agora tem
um **painel flutuante** que abre por cima de jogos e vídeos, pela barra de notificações.
(README = só o "leia-me" do projeto. Você não precisa entender nada daqui para usar o app.)

## Situação real desta versão

- **Não foi compilado nem testado em um celular.** O ambiente onde foi escrito não tem o Android SDK.
- **Foi testado (53 testes automáticos, rodados e aprovados no computador):** a lógica de volumes, restauração,
  gravação das configurações, regras das teclas e do Shizuku, e as regras do painel (posição dentro da tela,
  tamanho em retrato e paisagem, quais apps aparecem, quando o serviço deve ficar ligado).
- **Não foi testado:** a janela flutuante em si, a notificação com "Abrir painel", o arrastar com o dedo, o
  Shizuku real, a acessibilidade e o comportamento da One UI. Isso só se confirma no seu aparelho.
- A primeira compilação pode apontar um erro pequeno. Se acontecer, envie a mensagem para corrigir.

## Painel flutuante (novo na 1.0.2)

**Ligar:** no Volume Control, ligue "Painel flutuante". O Android abre a tela "Sobrepor a outros apps";
ative o Volume Control e volte. Aparece uma notificação com os botões **Abrir painel** e **Parar painel**.

**Usar:** abra o jogo, puxe a barra de notificações, toque em **Abrir painel** e feche a barra. O painel fica
por cima do jogo, e os toques fora dele continuam indo para o jogo. (Se não vir os botões, expanda a notificação
deslizando-a para baixo.) Também dá para abrir pelo botão "Mostrar o painel agora" dentro do app.

**O que tem no painel:**
- Uma linha por app, com o **ícone e o nome reais do app** instalado no seu celular (muda conforme o app),
  controle deslizante colorido, porcentagem e um indicador animado (barrinhas) nos apps que estão tocando.
- **Arrastar:** segure a barrinha roxa/título e mova. A última posição fica salva.
- **Cadeado (rodapé):** fixa a posição, para não arrastar sem querer durante uma partida.
- **Minimizar (−):** vira um botão redondo pequeno. Toque nele para voltar; também dá para arrastá-lo.
  Os volumes continuam funcionando.
- **Expandir (⛶):** alterna entre "só os apps tocando ou já configurados" e "todos os apps" (com rolagem).
- **Fechar (✕):** esconde o painel. A notificação continua para abrir de novo.
- **Engrenagem (rodapé):** abre o Volume Control.

Para desligar de vez: "Parar painel" na notificação, ou a chave "Painel flutuante" no app.

## Como o volume por app funciona (e o que é frágil)

O Android **não tem função pública** para um app mudar o volume de outro. O Volume Control usa o mesmo
mecanismo interno do "volume por app" da Xiaomi: lê os players de áudio ativos e ajusta um multiplicador
só do player de cada app. Isso exige uma permissão de sistema que apps comuns não têm; o **Shizuku**
(app gratuito, de terceiros) a empresta.

As classes usadas (`IAudioService`, `PlayerProxy`, `ServiceManager`) **não são documentadas** e podem mudar em
qualquer atualização da Samsung ou do Android. Não consegui confirmar que funcionam no Android 14/15 do A15.
Se não funcionarem, o app mostra "Função não disponível neste Android" e **não altera nada**: os controles
não fingem funcionar. O painel flutuante em si usa só recursos oficiais do Android; o que depende da função
interna é o ajuste do volume.

## Permissões, uma por uma

| Permissão | Para quê | Se negar |
|---|---|---|
| Autorização no Shizuku | Chamar o serviço de áudio com privilégio | Nada funciona |
| `SYSTEM_ALERT_WINDOW` ("Sobrepor a outros apps") | Desenhar o painel por cima de jogos e apps. Só é pedida quando você liga o painel | O painel não aparece; o resto do app funciona |
| `FOREGROUND_SERVICE` e `FOREGROUND_SERVICE_SPECIAL_USE` | Manter o serviço (volumes + notificação do painel). Android 14 exige declarar um tipo; nenhum dos tipos prontos descreve este caso | Os volumes não são aplicados |
| `POST_NOTIFICATIONS` | Mostrar a notificação com "Abrir painel" | Sem o botão na barra de notificações (use o botão dentro do app) |
| Acessibilidade (você liga) | **Opcional.** Saber qual app está na tela e capturar as teclas de volume | Só as teclas deixam de funcionar |
| Botão dos Ajustes rápidos | Atalho para uma folha de volume (você adiciona; o sistema o chama) | Use o atalho do ícone |

Não há permissão de Internet: **nada sai do aparelho**. Também não há `QUERY_ALL_PACKAGES`, leitura de
mensagens, senhas ou conteúdo de outros apps. `allowBackup` está desligado. O painel só lê o nome e o
ícone dos apps instalados e o volume que você definiu. Nada contorna as proteções do Android: a sobreposição
usa a janela oficial (`TYPE_APPLICATION_OVERLAY`) com a permissão concedida por você.

Sobre a acessibilidade (opcional): ela só recebe o **nome do app** quando a janela muda e as teclas físicas.
Por limitação do Android, ao pedir as teclas o serviço recebe todas as teclas físicas; o código descarta na hora
tudo que não seja volume + / volume -. Não lê o conteúdo da tela. A chave "Teclas de volume" dentro do app
**desliga o serviço de acessibilidade por completo**. O Android pode pedir "Permitir configurações restritas"
(Informações do app > menu ⋮) por o APK não vir da loja; isso vale também para "Sobrepor a outros apps".

## Instalar e preparar

1. Instale o **Shizuku** (loja do aparelho ou https://shizuku.rikka.app/download/).
2. Configurações > Sobre o telefone > Informações do software > toque 7x em "Número da versão".
3. Opções do desenvolvedor > ative "Depuração sem fio" (precisa de Wi-Fi).
4. Abra o Shizuku > "Iniciar via Depuração sem fio" e siga o pareamento.
5. Instale o APK do Volume Control, abra e toque em "Conceder permissão". Permita as notificações.
6. Ligue o "Painel flutuante" e conceda "Sobrepor a outros apps".
7. Na One UI: Configurações > Apps > Volume Control > Bateria > **Sem restrições**, para o sistema não fechar o serviço.

## Depois de reiniciar o celular

Nada inicia sozinho (o app não pede permissão para iniciar com o sistema). Inicie o **Shizuku** de novo
(isso já era necessário) e **abra o Volume Control uma vez**: o serviço e a notificação "Abrir painel" voltam.
Suas configurações, volumes e a posição do painel ficam salvos.

## Teclas de volume (opcional)

Com um app tocando na tela, volume +/- muda só o volume dele (5% por toque). Passam direto para o Android:
tela bloqueada/apagada, ligação, app em uso sem tocar, Shizuku não pronto, e um toque novo em volume + com o
app já em 100% (assim o volume geral segue acessível).

## Limitações conhecidas

- **Apps que bloqueiam sobreposições:** alguns apps e jogos (bancos, pagamentos, alguns jogos com proteção
  anti-sobreposição) pedem ao Android para esconder janelas flutuantes. Neles o painel pode não aparecer; isso
  não dá para contornar, e o app não tenta.
- O painel **não tem campo de texto** (não rouba o foco do jogo, então o teclado não funciona nele). Por isso não
  há busca no painel; a busca fica na tela principal.
- A barra de notificações **não fecha sozinha** garantidamente ao tocar em "Abrir painel"; feche-a manualmente.
- No Android 14+ o usuário pode **dispensar a notificação** do serviço. Ela volta ao abrir o Volume Control; até
  lá use o botão dentro do app ou o dos Ajustes rápidos.
- Se o sistema (ou a "limpeza" da One UI) encerrar o serviço, o painel e a notificação somem até você abrir o app.
- Com tela dividida ou janela flutuante, "app em uso" (teclas) é o último que mudou de janela.
- Alguns players nativos (jogos de baixa latência) não aceitam controle; o app avisa "sem controle".
- Ao começar um áudio pode haver uma fração de segundo no volume original.
- Curva linear: 50% = metade da amplitude.
- Versões anteriores ao Android 14 não são suportadas (minSdk 34).

## Compilar o APK

**Sem instalar nada (GitHub):** crie um repositório, envie o conteúdo da pasta e aguarde a aba *Actions*.
O fluxo roda os testes e gera o APK em *Artifacts > VolumeControl-1.0.2-debug-apk*.

**Android Studio:** File > Open > pasta `VolumeControl` > aguarde o Gradle sync > Build > Build APK(s).
O arquivo fica em `app/build/outputs/apk/debug/app-debug.apk`.

**Terminal:** `./gradlew testDebugUnitTest assembleDebug` (JDK 17 e Android SDK 35).

Copie o APK para o celular e instale (permita "instalar apps desconhecidos" para o app que o abrir).

## Testes

`./gradlew testDebugUnitTest` roda 53 testes em `app/src/test`: volumes independentes por app, um app não
afetar outro, restauração (inclusive após "reiniciar" o app), nova faixa do mesmo app, perda e volta do Shizuku,
decisão de cada estado do Shizuku, regras das teclas, leitura de configurações corrompidas, posição do painel
dentro da tela (inclusive após girar o aparelho), tamanho do painel e quais apps aparecem na lista.

## Estrutura

```
app/src/main/java/com/volumecontrol/app/
  VolumeApp.kt, MainActivity.kt
  data/        Settings (grava no aparelho), AppRepository (apps com ícone e nome)
  engine/      VolumePlanner, VolumeMath, VolumeCodec, ShizukuStatus, PanelRules   <- lógica pura, testada
               AudioEngine (chamadas ao Android), ShizukuMonitor, PlaybackState
  service/     VolumeService (motor + notificação), FloatingPanelController (janela do painel),
               OverlayState (permissões), VolumeKeysService (acessibilidade), QuickTileService, ServiceController
  ui/          MainScreen, FloatingPanelUi (painel), QuickPanelActivity, AppRow, tema
app/src/test/  testes
```
