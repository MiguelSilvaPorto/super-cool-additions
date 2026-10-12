# Pendências — Super Cool Additions (sccadd)

Estado em 12/10/2026. 101 arquivos, 16 opções de config, ~36 commits.

## ✅ Entregue e validado em jogo

1. **Performance da Calculadora de Quebra**
   - Orçamento por tick (por máquina e global), loot params cacheados, sync adiado,
     `BlockDropsEvent` direto/opcional, marcas no `/scc profile`.
   - **Modo lote** com "tabelas de dados" (amostras reais → sorteios), cache de loot
     determinístico, buffer escondido por tier, ímã.
   - Medido: 15.001 blocos/10s com apenas 1.326 quebras reais (~11x menos).

2. **Integração AE2 (dois sentidos)**
   - Padrões AE2 com ingredientes no calculador SCC; receitas do SCC como padrões
     reais no AE2 (com confirmação no plano); transferência em chunks (sem teto).
   - **Receptor como ponte**: monta o estoque SCC no ME e **lê o ME para o SCC**
     (conector virtual injetado no scan).
   - **Fluidos nos dois sentidos** (receitas com fluido inclusas).
   - Filtro de oferta pelo estoque real (reusa `CraftSim.reach` do SCC) e pelo botão
     "Only with ingredients" do próprio SCC.
   - Planejador inteligente (2+ jogadores), watchdog da `BridgeGuard`.

3. **Cache de análise resiliente** — versões de mods/configs viram "soft" (usa o
   cache na hora + refresh em background). Validado no ATM10 (cache em 904 ms).

4. **UI / terminal**
   - Filtro de variantes ("Clean variants") + **Filtro Avançado** (estoque, traços,
     mod, nome; `config/sccadd-filter.json`).
   - Editor de barra por arrastar; "ghost veil" para fabricáveis sem estoque.
   - Pocket terminal + regras por slot (exportar/importar, fluidos, craft more).
   - Interface: tanques/templates de fluido; templates recusam inserções de fora.

5. **Requester / autocrafting**
   - **Cadeias de produção** (caixas arrastáveis, receita própria, setas, round-robin
     no servidor, edição durante o job, sem limite de caixas).
   - **Think**: planejamento em background para pedidos grandes.
   - Campos tipados (stock/pedido/tempo/passo) e dígitos no diálogo de pedido.

6. **Extras**: Calculadora de Processamento pode **forçar qual máquina ela é**
   (clique direito no ícone) — automação externa com baú na frente.

## ⏳ Falta fazer

### Alto valor (escopo original)
- [ ] **Análise de receitas incremental + prioridade por busca** — mostrar receitas
      já analisadas enquanto o resto roda, priorizar o que a busca pede.
      (Maior mudança; mexe no motor do SCC.)
- [ ] **Cache de análise no servidor** — cliente envia o `analysis.dat`; quem entra
      depois baixa e carrega instantâneo.
- [ ] **Medição final da performance** com `/scc profile on` (antes/depois do lote)
      + stress do buffer cheio (drops que não cabem).

### Acabamento de release
- [ ] `debugOffer` está **ligado por padrão** (logs a cada 5-10s) → virar `false`.
- [ ] Subir a versão (0.1.0 → 1.0.0) + tag/release no GitHub + atualizar a descrição
      do `neoforge.mods.toml` (hoje só fala do fix e do AE2).
- [ ] **README/TESTING/NOTAS desatualizados** — não citam chains, pocket, filtro
      avançado, machine force, modo lote etc.
- [ ] Decidir visibilidade do repositório (privado hoje).
- [ ] (Opcional) teste de fumaça automático no CI (subir servidor + mixins).

### Bugs/riscos conhecidos
- [ ] **Jade**: `Missing config translation: config.jade.plugin_scc.*` aparece em
      todo log (é do SCC com o Jade; mitigação limitada no addon).
- [ ] **Dupla oferta AE2** se Antena **e** Receptor estiverem no mesmo grid — os dois
      provedores oferecem; documentar "use um" ou deduplicar.
- [ ] Revisitar defaults do orçamento do breaking (`budgetMs` 2 / `totalBudgetMs` 6)
      agora que o lote é barato (talvez 1 / 4).
- [ ] Documentar o trade-off do modo lote (aproximação estatística auto-corrigida).

### Testes pendentes das features novas
- [ ] Cadeias (round-robin, edição durante job, ingredientes faltando).
- [ ] Think (pedido grande, retomar).
- [ ] Pocket terminal + regras por slot.
- [ ] Machine force (recém-commitado).
- [ ] Interface: tanques/templates.
- [ ] Ímã.
- [ ] Buffer cheio do breaking (para por falta de espaço).
