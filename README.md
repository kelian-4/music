# music

Fork de [BitChord](https://github.com/kushagrasinghx/BitChord) (Android) et de
[BitChord pour Apple](https://github.com/bagumamartin/BitChord) (iOS / macOS),
adapté à un public où la data est chère et le réseau instable.

Les deux codebases ont des historiques séparés et des racines qui se
chevauchent (`build.gradle.kts`, `shared/`, `gradlew`) : elles vivent donc sur
des branches distinctes.

| Branche   | Contenu                                                       |
|-----------|---------------------------------------------------------------|
| `main`    | cette page, rien d'autre                                      |
| `android` | fork Android stable (upstream `kushagrasinghx/BitChord`)      |
| `ios`     | fork iOS / macOS stable (upstream `bagumamartin/BitChord`)    |
| `feat/*`  | une feature = une branche = une PR vers `android` ou `ios`    |

Licence GPLv3, crédits et mention des projets d'origine conservés dans chaque
branche.
