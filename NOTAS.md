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
- [ ] Reclassificar no fingerprint: mover **versões de mods** e os **campos de
      config que afetam a análise** do hard para o soft (hard = só ids de mods +
      "v62" + source), fazendo o cache ser **usado na hora** e atualizado em
      background em vez de descartado.
- [ ] Ganho esperado: fim dos 8-12 min "mortos" a cada update de pack/ajuste.

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

## 3. Bugs de UI (usuário relatou; detalhes pendentes)


- [ ] Jade: `Missing config translation: config.jade.plugin_scc.*` — as opções do
      SCC na tela de config do Jade aparecem como chaves cruas.
      (pré-existente do SCC; dá para mitigar com traduções do addon?)
- [ ] Aguardando a lista completa do usuário (prints ajudam).

## 3. Problemas com tarefas / autocrafting (usuário relatou; detalhes pendentes)

- [ ] Aguardando os casos específicos (jobs travados? itens somem? máquina não
      encontrada?).

## 4. Performance — acompanhamento

- [ ] Usuário medir `/scc profile` com a Calculadora de Quebra Singularidade e
      mandar as linhas `sccadd breaking work`, `sccadd loot roll`,
      `sccadd drop event`.
- [ ] Avaliar depois: `fits` + inserção dupla (simular + real) na saída;
      cache de plano/tag filters; fairness do orçamento global entre máquinas.

## 5. Integração AE2 — validação em jogo

- [ ] Padrões AE2 com ingredientes no calculador SCC (config `[ae2] patternsInScc`).
- [ ] Receitas SCC como padrões AE2 reais + push de ingredientes
      (config `[ae2] sccPatternsInAe2`).
- [ ] Conferir `logs/scc.log` para mensagens `sccadd` de falha de push.
