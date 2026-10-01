# LOS Gear Plus — ambiente de desenvolvimento

Addon para o **los_gear**, que por sua vez é addon do **Danny's AoT (DAOT)**.
Minecraft 1.21.1 · Fabric · Java 21 · mapeamentos Mojang (o mesmo do DAOT).
Toolchain: **Loom 1.17.21 + Gradle 9.7.1 + JDK 21** (a que o GeckoLib 4.9.3 usou; roda no JDK 21).

> Este projeto foi montado sem poder rodar o Gradle (o ambiente onde foi gerado não
> alcança o Maven do Fabric). A sintaxe do Java e dos JSONs foi conferida e as
> referências ao DAOT/los_gear foram checadas contra os jars, mas **o primeiro
> `./gradlew build` é o teste de verdade**. Se falhar, mande o log.

## Começando

1. Instale o **JDK 21** e use-o também como *Gradle JVM* no IntelliJ (Settings → Build Tools → Gradle).
2. Copie exatamente 4 jars para `libs/` (DAOT, los_gear, GeckoLib, Player Animation Library; nomes em `libs/README.txt`).
   **Não** coloque o `fabric-api` (vem do Maven) nem duas cópias do DAOT.
3. Extraia o projeto em uma **pasta nova** (sem reaproveitar `.gradle/` ou `.idea/` de tentativas anteriores) e abra no **IntelliJ IDEA** (*Open* → selecione a pasta → importar como Gradle). Ou use o terminal:

```
./gradlew build        # compila e gera build/libs/los-gear-plus-0.1.0.jar
./gradlew runClient    # abre o Minecraft de desenvolvimento com DAOT + los_gear + seu mod
./gradlew genSources   # decompila o Minecraft para você navegar/depurar
```
(No Windows use `gradlew.bat`.)

Se `runClient` abrir, você deve ver no log `LOS Gear Plus carregado (DAOT presente: true)`,
a aba criativa **LOS Gear Plus** com o item de exemplo, e nenhum erro de mixin.

## Problemas comuns

| Mensagem | Causa | Solução |
|---|---|---|
| `Dependency requires at least JVM runtime version 25` | Loom 1.18+ | Use `loom_version=1.17.21` (já configurado) |
| `No matching variant of net.fabricmc:fabric-loom:X ... api-version '9.x'` | Gradle velho demais para esse Loom | Ajuste `gradle-wrapper.properties` (Loom 1.17.21 → Gradle 9.7.1) e recarregue |
| `Mod was built with a newer version of Loom (X)` | Algum jar em `libs/` foi compilado com Loom > `loom_version` | Suba `loom_version` para X e o Gradle conforme necessário |
| `Há mais de um jar de ... em libs/` | Jar duplicado | Deixe um só |
| Mod duplicado ao abrir o jogo | Dois jars do mesmo mod em `libs/` | Idem |
| Gradle ainda mostra a versão antiga | IntelliJ usando outro Gradle/JDK | *Settings → Build Tools → Gradle*: *Use Gradle from* `'gradle-wrapper.properties' file`, *Gradle JVM* = JDK 21 |

## Estrutura

| Caminho | Para quê |
|---|---|
| `src/main/java/.../LosGearPlus.java` | Entrada principal (servidor + cliente) |
| `src/main/java/.../ModItems.java` | Registro de itens e aba criativa |
| `src/main/java/.../ModTags.java` | Tags (`los_gear_plus:odm_gear`) |
| `src/main/java/.../compat/LosGearCompat.java` | **Único** lugar que importa classes do los_gear |
| `src/main/java/.../mixin/DannysAotOdmMixin.java` | Mixin de exemplo dentro do DAOT |
| `src/client/java/.../client/` | Código só de cliente (renderers, HUD, teclas) |
| `src/main/resources/` | `fabric.mod.json`, mixins, assets, data |
| `libs/` | Jars dos mods de que você depende (não versionados) |

Regra prática: precisa de `Minecraft` cliente (render, tela, tecla)? → `src/client`.
Resto → `src/main`.

## Como o DAOT e o los_gear se conectam (resumo da análise dos jars)

- O DAOT expõe todos os itens como campos estáticos de `daot.DannysAot`
  (`ODM_GEAR`, `BLADE`, `THUNDER_SPEAR`, `GAS_CANISTER`, ...) e as regras de jogo como `RULE_*`.
- O **los_gear não herda** itens do DAOT. Cria armaduras próprias e faz o DAOT as tratar como
  ODM via mixins em `daot.DannysAot`. Ganchos usados por ele (e disponíveis para você):

| Método em `daot.DannysAot` | Função |
|---|---|
| `isODMGear(Item)` | reconhece a peça como ODM *(já implementado no exemplo, via tag)* |
| `isAPG(Item)` | reconhece a peça como APG |
| `isValidGripForLeggings(ItemStack, Item)` | libera lâminas/empunhaduras com a sua peça |
| `getMaxGasForGear(ItemStack)` | gás máximo |
| `getGasFromGear(ItemStack)` | gás atual |
| `setGasOnGear(ItemStack, int)` | grava o gás |
| `consumeGasFromGear(ItemStack, int, Player)` | gasta gás |
| `gearHasGas(ItemStack, Player)` | tem gás? |

  **Reconhecer não basta:** para um ODM novo funcionar você precisa dos ganchos de gás também.
- O DAOT procura o ODM no **slot de pernas**. O los_gear suporta uniformes ODM no **peito**
  redirecionando a leitura de slot em `ODMTickHandler`, `GasCanisterItem`,
  `network.ModNetworking`, `TitanDashTracker` e `CombatModeState`. *(Inferido do bytecode dele.)*
- O reconhecimento do los_gear é **por identidade**: `RoyalAttire.isOurOdmGear` só aceita
  `NEW_ODM_GEAR` e `NEW_ODM_UNIFORM`. Item seu precisa dos seus próprios mixins.
- APIs úteis do los_gear (todas `public static`): `RoyalAttire.isOurOdmGear`, `queryOdmGear`,
  `isPistol`, `getPistolAmmo`, `getStoredSpears`, `OdmGasLink.gas/maxGas/setGas/consumeGas`
  e os itens `RoyalAttire.MAUSER_RIFLE`, `MAUSER_CARTRIDGE`, `QUAD_THUNDER_SPEAR`, ...

### Navegando o código deles
Os jars não trazem código-fonte, mas as classes têm nomes legíveis. No IntelliJ, abra
`libs/*.jar` em *External Libraries* e ele decompila na hora.

## Personalizando (primeiros passos)

- **Renomear o mod:** troque `los_gear_plus` em `settings.gradle`, `build.gradle` (bloco `loom.mods`),
  `fabric.mod.json`, `los_gear_plus.mixins.json`, `LosGearPlus.MOD_ID`, e as pastas
  `assets/los_gear_plus` e `data/los_gear_plus`. Troque também o pacote `com.example.losgearplus`
  (no IntelliJ: clique direito no pacote → *Refactor → Rename*) e lembre de ajustar os nomes de classe
  em `fabric.mod.json` e `los_gear_plus.mixins.json` (`package`), que o IntelliJ não atualiza.
- **Tornar o los_gear obrigatório:** em `fabric.mod.json`, mova `"los_gear"` de `recommends` para `depends`.
- **Novo item:** uma linha em `ModItems`, um modelo em `assets/.../models/item/`, uma textura
  em `assets/.../textures/item/` e um nome nos arquivos de `lang/`.
- **Mixin em classe do DAOT/los_gear:** copie `DannysAotOdmMixin` (`targets = "classe.completa"`,
  `remap = false`) e registre o nome em `los_gear_plus.mixins.json`.

## Antes de publicar
- No `los_gear_plus.mixins.json`, `defaultRequire: 1` faz o jogo **falhar alto** se um alvo do DAOT
  mudar (ótimo em desenvolvimento). O los_gear usa `0` em release para não quebrar com updates do DAOT.
  Escolha conscientemente.
- O DAOT declara licença *All Rights Reserved*: não copie código ou assets dele e não inclua o jar.
  O los_gear é MIT. (Não sou advogado; confira os termos do autor.)
- O jar do DAOT se chama `2_4_3`, mas o `fabric.mod.json` interno diz `2.4.1`; por isso o `depends`
  usa `>=2.4.1`.
