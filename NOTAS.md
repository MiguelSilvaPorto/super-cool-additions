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
   - **Energia ME → SCC pelo Quartz Fiber** (novo): o fiber encostado num bloco da
     encanação (`cable`, `cable_bus`, `scc_storage`, `storage_connector`, `controller`,
     `interface`) puxa AE do ME e entrega FE à rede SCC (2 FE/AE), só repondo o
     consumo; e o **Stellar Distributor** deixa de mirar blocos do AE2 que aceitam FE
     (`Energy Acceptor`/FE P2P), que era o laço SCC → distributor → ME → fiber → SCC.
     Diagnóstico: `/sccadd fibertest`. *Verificado num servidor dev com a bancada do
     `fibertest` (guarda do distributor, só-fiber, e 1 M FE chegando na storage);
     falta testar no mundo do jogador.*

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
- [x] `debugOffer` agora é **`false`** por padrão.
- [x] Versão **1.0.0** e descrição do `neoforge.mods.toml` atualizadas.
- [ ] Tag/release no GitHub (decisão do dono).
- [x] README/TESTING atualizados (cadeias, bolso, filtro avançado, máquina forçada, modo lote, prensas).
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
- [ ] **Energia ME → SCC (Quartz Fiber)** e a guarda do Distributor: `/sccadd fibertest`
      (bancada + 3 perguntas) e, no mundo real, fiber encostado num cabo SCC com
      `"power": true`; conferir a fonte "AE2" no painel do controller e o não-dreno do ME.
      *Bancada do `fibertest` já passou no servidor dev; o mundo do jogador ainda não.*
