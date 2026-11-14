# Volume Control

App Android (Kotlin + Jetpack Compose) que define um volume separado para cada aplicativo.
Feito para o Samsung Galaxy A15 5G (Android 14+, One UI 6+), mas segue a mesma API em qualquer Android 11+.

> **Status honesto:** o código foi escrito sem Android SDK disponível, então **não foi compilado nem
> testado em aparelho**. Pode haver um erro de compilação pequeno na primeira vez; veja "Se der erro".

## Como o controle individual funciona de verdade

O Android **não tem API pública** para um app comum mudar o volume de outro app.
O que existe é um mecanismo interno, o mesmo usado pelo "volume por app" da Xiaomi/MIUI:

1. `AudioManager.getActivePlaybackConfigurations()` lista os players de áudio ativos. Só quem tem a
   permissão de sistema `MODIFY_AUDIO_ROUTING` recebe a lista completa (com o `IPlayer` de cada player).
2. Cada item informa o `uid` do app dono (`getClientUid`).
3. `PlayerProxy.setVolume(float)` -> `IPlayer.setVolume()` muda um multiplicador de volume **só daquele
   player**, sem tocar no volume do sistema nem em outros apps.

Um app instalado normalmente **não** recebe `MODIFY_AUDIO_ROUTING`. O **Shizuku** (app gratuito) resolve:
ele roda com a identidade `shell` (que tem essa permissão) e deixa o Volume Control fazer as chamadas
ao serviço de áudio por meio dele. Por isso o Shizuku é obrigatório.

APIs que **não** resolvem o problema (e por isso não foram usadas como solução):
- `AudioManager.setStreamVolume`: mexe no volume do *tipo* de áudio (mídia, toque...), afeta todos os apps.
- `AudioFocus`: só pede a outros apps que pausem ou abaixem, quem decide é o outro app.
- Serviço de acessibilidade: não tem acesso ao áudio.
- `DynamicsProcessing`/equalizador por sessão: exige o ID de sessão de áudio de outro app,
  que o Android só entrega se o próprio app tocador o divulgar. Funciona só com alguns apps.

## Mudar só o volume do app que você está usando

Ligue "Teclas de volume no app em uso" na tela inicial e ative o serviço de acessibilidade
(Acessibilidade > Apps instalados > Volume Control). Depois, com um app **tocando áudio na tela**,
as teclas de volume mudam só o volume dele (5% por toque) e aparece um aviso no topo com a porcentagem.
Se o app da tela não estiver tocando nada, as teclas funcionam normalmente (volume geral do sistema).

O serviço de acessibilidade só descobre qual app está na tela e captura as teclas de volume;
não lê o conteúdo da tela. Em apps instalados fora da loja, o Android pode pedir
"Permitir configurações restritas" (Informações do app > menu ⋮).

## O Shizuku precisa estar ativo toda vez?

Não precisa abrir o Shizuku a cada uso. Ele só precisa estar **iniciado**, e isso se faz uma vez
por reinicialização do celular. Depois de reiniciar: inicie o Shizuku (Depuração sem fio) e abra o
Volume Control uma vez para religar o serviço. Se o Shizuku parar, os volumes deixam de ser aplicados
e o app mostra o aviso.

## Permissões

| Permissão | Para quê |
|---|---|
| Permissão do Shizuku (pedida na tela) | Chamar o serviço de áudio com privilégio |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` | Manter o serviço que reaplica os volumes |
| `POST_NOTIFICATIONS` | Mostrar a notificação fixa do serviço |
| Serviço de acessibilidade (ligado por você) | Saber o app em uso e capturar as teclas de volume |
| `<queries>` (launcher) | Listar os apps instalados sem a permissão ampla `QUERY_ALL_PACKAGES` |

O app **não tem permissão de Internet**. Nada sai do celular.

## Preparar o celular (Shizuku)

1. Instale o Shizuku: https://shizuku.rikka.app/download/
2. Configurações > Sobre o telefone > Informações do software > toque 7x em "Número da versão".
3. Configurações > Opções do desenvolvedor > ative "Depuração sem fio" (precisa de Wi-Fi).
4. Abra o Shizuku > "Iniciar via Depuração sem fio" e siga o pareamento.
5. Abra o Volume Control > "Conceder permissão" > aceite.

O Shizuku precisa ser iniciado de novo a cada reinicialização do celular.

## Compilar o APK

### Opção A: Android Studio
1. Instale o Android Studio (versão recente).
2. File > Open > escolha a pasta `VolumeControl`. Aguarde o "Gradle sync".
   (Aceite instalar o Android SDK Platform 35 se pedir.)
3. Menu Build > Build Bundle(s) / APK(s) > Build APK(s).
4. O arquivo fica em `app/build/outputs/apk/debug/app-debug.apk`.
5. Copie para o celular e instale (permita "instalar apps desconhecidos" para o gerenciador de arquivos).

### Opção B: linha de comando
```
./gradlew assembleDebug        # Linux/macOS
gradlew.bat assembleDebug      # Windows
```
Precisa de JDK 17 e do Android SDK (variável `ANDROID_HOME`).

### Opção C: sem instalar nada (GitHub Actions)
Suba esta pasta para um repositório no GitHub. O arquivo `.github/workflows/build-apk.yml`
compila sozinho; baixe o APK em Actions > execução > Artifacts > `VolumeControl-debug-apk`.

## Estrutura

```
app/src/main/java/com/volumecontrol/app/
  VolumeApp.kt            libera APIs ocultas, inicia configurações e monitor do Shizuku
  MainActivity.kt
  data/Settings.kt        volumes e opções salvos (SharedPreferences)
  data/AppRepository.kt   lista de apps com ícone e nome
  engine/AudioEngine.kt   NÚCLEO: lê players ativos e aplica o volume
  engine/ShizukuMonitor.kt estado do Shizuku (instalado / ativo / autorizado)
  engine/PlaybackState.kt quem está tocando agora
  service/VolumeService.kt reaplica volumes a cada evento de áudio
  ui/                     tela, tema e ViewModel
```

## Limitações conhecidas

- Exige o Shizuku ativo (veja acima).
- Ao iniciar uma música/vídeo, pode haver uma fração de segundo no volume original antes do ajuste.
- Alguns players nativos (certos jogos que usam áudio de baixa latência) não expõem controle ao
  sistema; o app marca "Este app não aceita controle externo".
- Se um app redefinir o próprio volume, o serviço reaplica o seu em até 2 s.
- A curva é linear: 50% = metade da amplitude. Para mudar, edite `toGain()` em `AudioEngine.kt`.
- Se o app for encerrado pelo sistema, abra-o de novo para religar o serviço.

## Alternativa nativa da Samsung

O app **Sound Assistant** (Galaxy Store, módulo Good Lock) tem "Volumes individuais de apps"
sem precisar de Shizuku. Vale testar como comparação.

## Se der erro na compilação

Copie a mensagem de erro do painel "Build" do Android Studio e envie; costuma ser um ajuste de
uma linha (import, versão de biblioteca).
