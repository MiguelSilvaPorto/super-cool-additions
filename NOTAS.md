# Pendências — Super Cool Additions (sccadd)

Backlog do que ainda precisa ser feito ou investigado. Atualizar conforme os
testes avançam.

## 1. Análise de receitas do SCC — DIAGNÓSTICO (dados reais do ATM10)

**Caso:** ATM10, 492 mods, 278.889 receitas, 156.928 itens, 128.419 mappings.
Análise medida: **713 s (12 min)** numa sessão e **462 s (7,7 min)** na outra.
`analysis.dat` = **21,4 MB**, salvo em ~8-10 s em background.

**Causa raiz do "cache morre a cada fechamento":** o *hard fingerprint* do SCC
(`AnalysisManager.hardPrint()`) inclui:
- **versões de TODOS os mods** (`modId@version`) — qualquer atualização de pack invalida;
- **quase todos os campos do `SccConfig`** que não estão na lista `FINGERPRINT_IGNORED`
  — inclusive `blockedItems`, `ignoredComponents`, `terminalBar`, `terminalRecentRow`,
  `design`, `font`, `largeText`, etc. O `scc-client.json` do ATM10 foi editado às
  18:47 (o usuário estava mexendo justamente em `ignoredComponents`/`blockedItems`
  para domar variantes) → **cada ajuste descarta o cache** e refaz 8-12 min.

Quando o hard fingerprint muda, o SCC **nem tenta usar o cache**. Já quando só o
*soft* muda (ids de receita/goo), ele usa o cache velho e re-analisa em background
("cache matches roughly") — exatamente o comportamento desejado.

**Correção proposta (rápida, alto impacto):**
- [x] **IMPLEMENTADO** — mixin `AnalysisFingerprintMixin` + `FingerprintExtra`:
      hard print vira constante (formato segue protegido pelo cabeçalho `#SCC62`);
      soft print passa a incluir versões de mods + configs que afetam a análise.
      Config: `[analysis] resilientCache` (padrão true).
- [ ] **Testar no ATM10**: 1ª abertura faz a última análise completa; da 2ª em
      diante o log deve mostrar `analysis loaded from cache`. Depois, update de
      pack/config deve usar o cache na hora + refresh em background.

**Pedidos do usuário (features maiores):**
- [ ] **Análise incremental/priorizada por busca**: mostrar receitas já analisadas
      enquanto o resto roda em segundo plano; ao pesquisar "planks", priorizar tudo
      relacionado e exibir assim que ficar pronto. (Hoje: nada é utilizável antes
      de a análise terminar; `AnalysisManager` só vira READY no fim.)
- [ ] **Análise/cache no servidor**: um cliente analisa e envia o `analysis.dat`
      ao servidor; quem entra depois baixa e carrega na hora, sem depender do
      processamento do próprio PC.

## 2. Variantes na busca (pedido do usuário)

**Hoje:** buscar "espada de diamante" lista todas as variações (encantamentos,
atributos, nome, etc.). O usuário já tentou resolver via config
(`ignoredComponents` com damage/custom_name/lore/rarity... e `blockedItems` com
`justdirethings:*_sword~*`) — mas `stored_enchantments` continua como componente
de variante e a lista polui.

**Pedido:**
- [ ] Padrão: mostrar só o item **vanilla/limpo** (sem atributos, encantamentos, nome...).
- [ ] **Botão novo "filtro"** no terminal para escolher o que mostrar (tudo ↔ limpo
      ↔ configurável).

## 3. Fluxo de itens SCC ↔ AE2 — "Interface + Storage Bus" (pedido do usuário)

**Pedido:** melhorar o fluxo de itens para funcionar "quase igual ao Interface +
Storage Bus do AE2":
- AE2, **via Storage Bus encostado num bloco do SCC**, vê **todos** os itens da
  rede SCC — ida e volta (extrair e inserir).
- O inverso (SCC via Storage Connector em bloco AE2) já funciona bem — manter.

**Estado atual:** o SCC expõe a rede ao AE2 apenas pela **Antena de Rede** (mount
automático de `MEStorage` no grid — sem partição/filtro/prioridade, e exige a antena).
Não dá para plugar um Storage Bus num bloco SCC e ver a rede como inventário.

**Investigação já feita (AE2 19.2.18):**
- Existe `appeng.api.AECapabilities.ME_STORAGE` (`BlockCapability<MEStorage, Direction>`)
  — é o que o Storage Bus consulta no bloco alvo.
- Caminho: registrar `ME_STORAGE` (e/ou `Capabilities.ItemHandler.BLOCK`) nos blocos
  do SCC com um `MEStorage`/`IItemHandler` virtual em cima do `NetworkView`.
- Reaproveitar o padrão do próprio SCC (`Ae2MeStore`, `Ae2Bridge.Storage`) incluindo
  a guarda anti-recursão (`BridgeGuard`).
- Decidir onde expor: só na Antena (bridge dedicado) ou em **todo bloco de
  armazenamento** (mais próximo de "pluga o bus em qualquer lugar").
- Checar comportamento do Storage Bus com `IItemHandler` (slots virtuais, prioridade,
  filtro por partição) vs `MEStorage` direto.

**Prioridade combinada:** implementar depois do filtro de variantes.

## 4. Líquidos entre AE2 e SCC (pedido do usuário — novo)

**Problema:** o AE2 tem líquidos na rede (ex.: água, image do terminal), e o SCC,
mesmo tendo armazenamento e uso de fluidos, **não reconhece os líquidos do AE2**
(nem o contrário).

**Diagnóstico (confirmado no código):**
- SCC tem suporte completo a fluidos: `NetworkView.fluidContents()`, `drainFluid`,
  `insertFluid`, blocos de Fluid Storage no `SccRegistry`.
- Mas a ponte AE2 do SCC só trata **itens**: `Ae2MeStore` e `Ae2Bridge.Storage`
  checam apenas `AEItemKey` — **nenhum `AEFluidKey`** no pacote `compat/ae2`.
- O AE2 19.2.18 tem fluidos como cidadãos de primeira classe (`AEFluidKey`).

**Correção proposta (dois sentidos):**
- [ ] **AE2 → SCC**: o Conector de Armazenamento num bloco AE2 também deve expor os
      fluidos do ME para a rede SCC (hoje só itens).
- [ ] **SCC → AE2**: a Antena deve montar os fluidos do SCC no grid (hoje
      `Ae2Bridge.Storage.listStock` lista só itens).
- [ ] Verificar o mesmo para produtos químicos (Mekanism) se aplicável.

## 5. Bugs de UI (usuário relatou; detalhes pendentes)


- [ ] Jade: `Missing config translation: config.jade.plugin_scc.*` — as opções do
      SCC na tela de config do Jade aparecem como chaves cruas.
      (pré-existente do SCC; dá para mitigar com traduções do addon?)
- [ ] Aguardando a lista completa do usuário (prints ajudam).

## 5. Problemas com tarefas / autocrafting (usuário relatou; detalhes pendentes)

- [ ] Aguardando os casos específicos (jobs travados? itens somem? máquina não
      encontrada?).

## 6. Performance — acompanhamento

- [ ] Usuário medir `/scc profile` com a Calculadora de Quebra Singularidade e
      mandar as linhas `sccadd breaking work`, `sccadd loot roll`,
      `sccadd drop event`.
- [ ] Avaliar depois: `fits` + inserção dupla (simular + real) na saída;
      cache de plano/tag filters; fairness do orçamento global entre máquinas.

## 7. Integração AE2 — validação em jogo

- [ ] Padrões AE2 com ingredientes no calculador SCC (config `[ae2] patternsInScc`).
- [ ] Receitas SCC como padrões AE2 reais + push de ingredientes
      (config `[ae2] sccPatternsInAe2`).
- [ ] Conferir `logs/scc.log` para mensagens `sccadd` de falha de push.
