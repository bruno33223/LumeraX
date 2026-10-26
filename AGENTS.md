# AGENTS.md — Diretrizes de Engenharia e Arquitetura do LumeraX

Bem-vindo ao repositório do **LumeraX**. Este documento estabelece a arquitetura, convenções técnicas, padrões de código e diretrizes de desenvolvimento para engenheiros e subagentes autônomos que operam na base de código.

---

## 1. Visão Geral da Plataforma e Stack Tecnológica

O LumeraX é uma aplicação nativa para **Android TV**, projetada para streaming de alta fidelidade e navegação otimizada por controle remoto (D-Pad).

| Componente | Versão / Especificação | Observações |
|---|---|---|
| **Plataforma** | Android TV (Leanback / TV-first) | Suporte rigoroso a navegação 10-foot UI e D-Pad |
| **SDKs** | Min SDK: `26` (Android 8.0 Oreo)<br>Target SDK: `34`<br>Compile SDK: `36` | Alinhamento com as diretrizes do Google Play e Android TV |
| **Linguagem & Toolchain** | **Kotlin**: `2.0.21`<br>**AGP**: `8.7.3`<br>**Gradle**: `8.9` (JDK 17) | Compose Compiler integrado via plugin Kotlin 2.0 |
| **UI Framework** | **Jetpack Compose for TV (Material 3)**<br>- `androidx.tv:tv-material:1.0.1`<br>- `androidx.tv:tv-foundation:1.0.0-alpha12`<br>- Compose BOM: `2025.02.00` | Foco em fluidez 60fps/120fps sem jank e foco visual consistente |
| **Injeção de Dependências** | **Hilt (Dagger)** `2.51.1` | Anotações `@HiltAndroidApp`, `@AndroidEntryPoint`, `@HiltViewModel` |
| **Persistência Local (DB)** | **Room Database** `2.6.1` (KAPT/KSP) | Migrações seguras, queries assíncronas com Kotlin Coroutines & Flow |
| **Networking & HTTP** | **Retrofit**: `2.9.0`<br>**OkHttp**: `4.12.0` | Sockets de alta performance, timeouts adaptados para streaming e conexão de addons |
| **Playback & Codecs** | **AndroidX Media3 ExoPlayer** `1.4.1`<br>Decodificadores customizados no `:playbackcore` | Fallback de software via ffmpeg, AV1, IAMF, MPEG-H e renderizador ASS |
| **Torrent Engine** | **TorrServer Embutido** (`libtorrserver.so`) | Instância local headless controlada via IPC/HTTP (`127.0.0.1:8090`) |

---

## 2. Estrutura de Módulos

O projeto adota uma arquitetura modularizada para isolar responsabilidades e desacoplar dependências nativas:

```
lumerax/
├── app/                  # Módulo principal da aplicação Android TV
│   ├── src/main/java/    # Código-fonte da aplicação (UI, Domain, Data, DI)
│   ├── src/main/jniLibs/ # Binários nativos do TorrServer (arm64-v8a, armeabi-v7a)
│   └── build.gradle.kts  # Configurações de build, dependências de Compose e módulos
├── playbackcore/         # Biblioteca core de reprodução e codecs Media3
│   ├── libs/             # AARs customizados pré-compilados do ExoPlayer e decodificadores
│   │   ├── lib-exoplayer-release.aar
│   │   ├── lib-decoder-av1-release.aar
│   │   ├── lib-decoder-ffmpeg-release.aar
│   │   ├── lib-decoder-iamf-release.aar
│   │   └── lib-decoder-mpegh-release.aar
│   └── build.gradle.kts  # Media3 modules (media3-ui, media3-session, media3-datasource, etc.)
└── ../assrender/assrender # Módulo auxiliar C++/JNI para legendas avançadas (ASS/SSA)
    └── (Incluído via settings.gradle.kts como :assrender)
```

- **`:app`**: Responsável pelo ciclo de vida, navegação Compose TV, injeção Hilt, repositórios de dados (Addons Stremio, Metadados TMDB/Cinemeta, Trakt, Perfis, Configurações) e controle do ciclo do reprodutor.
- **`:playbackcore`**: Fornece suporte de mídia avançado empacotando os binários nativos compilados com suporte a codecs estendidos (AV1, FFmpeg audio/video, IAMF áudio imersivo, MPEG-H).
- **`:assrender`**: Módulo dedicado à renderização nativa de legendas ASS/SSA com formatação complexa, estilos dinâmicos e overlays sobre o canvas de vídeo.

---

## 3. Arquitetura da Aplicação (App Layering)

A base de código segue princípios de **Clean Architecture** com o padrão **MVI/MVVM**:

1. **UI Layer (`com.lumera.app.ui`)**:
   - Telas e componentes baseados em `@Composable` com foco estrito em TV (`androidx.tv.material3.*`).
   - Gerenciamento de foco explícito (`FocusRequester`, `Modifier.focusRestorer()`, `onFocusChanged`).
   - `ViewModel` injetado via `@HiltViewModel` expondo `StateFlow` imutável para a UI.
2. **Domain Layer (`com.lumera.app.domain`)**:
   - Casos de uso (`UseCase`), detecção de hardware (`DeviceCapabilities`), lógica de resolução de streams e políticas de reprodução.
3. **Data Layer (`com.lumera.app.data`)**:
   - **Local**: Room Database (`LumeraDatabase`), DAOs para perfis, histórico de reprodução, favoritos e cache de catálogos.
   - **Remote**: Serviços Retrofit para consumo de manifests Stremio, streams de addons e metadados.
   - **Torrent**: `TorrServerEngine` gerenciando o ciclo de vida do processo nativo e `TorrServerApi` para comunicação HTTP com o daemon local.

---

## 4. Diretrizes Críticas de Playback Engine

O pipeline de reprodução multimídia é o coração do LumeraX e opera em hardware heterogêneo de TVs e TV Boxes (desde chipsets modestos Amlogic até processadores avançados Nvidia Tegra e MediaTek). As seguintes diretrizes devem ser estritamente cumpridas:

### 4.1. Gerenciamento Estrito de SurfaceView
- Em Android TV, prefira sempre `SurfaceView` a `TextureView` para evitar o overhead de composição gráfica do WindowManager e suportar reprodução segura de DRM / 4K HDR.
- Não recrie a `SurfaceView` durante recomposições do Jetpack Compose. Isole a visualização do vídeo em nós `AndroidView` estáveis (`factory` e `update` bem delimitados).
- Garanta que a superfície de vídeo seja liberada e desconectada do `ExoPlayer` (`player.clearVideoSurface()`) quando a tela for descartada ou pausada, prevenindo travamento do codec de hardware.

### 4.2. Fallback de Decodificadores MediaCodec (Hardware vs Software)
- Configure os renderizadores de áudio e vídeo com fallback habilitado: `setEnableDecoderFallback(true)`.
- Respeite o modo de decodificação configurado pelo perfil (`decoderPriority`):
  - `0`: Apenas decodificadores de hardware do sistema (`EXTENSION_RENDERER_MODE_OFF`).
  - `1`: Preferência por hardware com fallback para extensões (`EXTENSION_RENDERER_MODE_ON`).
  - `2`: Preferência por decodificadores das extensões de software (`EXTENSION_RENDERER_MODE_PREFER`), utilizando as bibliotecas nativas (`lib-decoder-ffmpeg`, `lib-decoder-av1`) quando o hardware apresentar incompatibilidade de perfil ou artefatos.
- Tratamento explícito de `MediaCodec.CodecException`: detectar erros de hardware (`ERROR_INSUFFICIENT_RESOURCE` ou falha de inicialização) e aplicar degradação suave ou reinicialização controlada.

### 4.3. Tunelamento de Áudio (Audio/Video Tunneling)
- Em dispositivos compatíveis, o tunelamento de A/V (`tunnelingEnabled = true`) sincroniza vídeo e áudio via hardware DSP e reduz latência em fluxos 4K HDR/Dolby Vision.
- Como certas TVs possuem implementações proprietárias defeituosas de tunneling que causam tela preta ou desync de áudio, mantenha o tunneling como opção configurável no perfil do usuário e trate exceções de track selection com fallback automático para modo não tunelado.

### 4.4. Ciclo de Vida do Compose e Descarte de Listeners
- Todo observer, listener (`Player.Listener`, `AssHandler.Listener`) ou callback acoplado ao `ExoPlayer` deve ser registrado e liberado estritamente dentro de blocos `DisposableEffect`.
- No bloco `onDispose`:
  1. Remova todos os listeners registrados.
  2. Pause ou pare a reprodução (`player.pause()` / `player.stop()`).
  3. Desvincule o player da `PlayerView` / `SubtitleOverlayView`.
  4. Garanta a liberação da instância do player quando a sessão terminar (`player.release()`).
- O sinalizador `keepScreenOn` da Activity/View deve ser gerenciado reativamente baseado no estado real de playback (`isPlaying || isBuffering`), sendo resetado imediatamente no descarte da tela.

---

## 5. Torrent Engine Embutido (TorrServer)

- O arquivo nativo `libtorrserver.so` é carregado dinamicamente a partir dos diretórios de JNI (`jniLibs/arm64-v8a`, `jniLibs/armeabi-v7a`).
- **Ciclo de Inicialização**: O `TorrServerEngine` gerencia a extração, permissões de execução (`chmod 700`) e execução do processo daemon em background.
- **Detecção de Estado**: A saúde do serviço deve ser verificada via requisições de ping/echo na porta `8090` com timeouts curtos e tentativas exponenciais controladas.
- **Gerenciamento de Recursos**: No encerramento da reprodução de torrent ou saída do app, os recursos de cache e conexões P2P devem ser limpos via `TorrServerApi` para evitar esgotamento de memória RAM da TV.

---

## 6. Convenções de UI e Compose for TV

1. **Navegação D-Pad**: Cada componente interativo deve ter um estado de foco evidente (borda destacada, escala, elevação ou cor de acento). Nunca oculte o indicador de foco.
2. **Recomposição Otimizada**:
   - Marque modelos de dados com `@Immutable` ou `@Stable` para evitar recomposições desnecessárias de listas longas (linhas horizontais de filmes/séries).
   - Use `remember` e chaves derivadas (`derivedStateOf`) para cálculos frequentes no loop de renderização.
3. **Evitar Alocações em Loops Críticos**: Não instancie formatadores de data, objetos `Modifier` excessivos ou listas temporárias dentro de composables chamados a cada frame (como timecode e seekbar do player).
4. **Strings e Localização**: Todos os textos de interface devem residir em `res/values/strings.xml`, com suporte para português brasileiro (`res/values-pt-rBR`) e espanhol (`res/values-es`).

---

## 7. Práticas de Código para Engenheiros e Agentes Autônomos

- **Fidelidade ao Plano**: Siga a arquitetura definida e preserve os contratos estabelecidos entre módulos (`:app`, `:playbackcore`, `:assrender`).
- **Edição Cirúrgica**: Modifique apenas os trechos necessários em arquivos existentes. Preserve convenções de identação, importações e comentários existentes.
- **Tratamento Defensivo de Erros**: Valide streams nulos, conexões caídas em streaming P2P/HTTP, ausência de codecs suportados e desvios de formato com mensagens de erro claras e fallbacks seguros.
- **Logs Condicionais**: Utilize `if (BuildConfig.DEBUG) Log.d(TAG, ...)` para evitar sobrecarga de I/O em builds de release em dispositivos com hardware de entrada.
- **Tipos Estritos**: Não utilize `Any` desnecessariamente; empregue sealed classes / sealed interfaces para representar estados de UI e fluxos de reprodução.
