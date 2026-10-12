# Checklist de teste — sccadd 1.0.0

Instância: `scc fix` (NeoForge 21.1.251, SCC 1.4.0, AE2 19.2.18, Observable).
O jar já está em `mods/sccadd-1.0.0.jar`.

## 1. O jogo abre

- [ ] Cliente chega ao menu principal sem crash.
- [ ] Lista de mods mostra **Super Cool Additions 1.0.0**.
- [ ] Aba **SCC Additions** no inventário criativo, logo depois da aba do SCC: ícone da Calculadora de Quebra, itens do addon (Interface Compacta, e a Câmara de Vila com Easy Villagers) e as máquinas do SCC que o addon mexe.
- [ ] `config/sccadd-common.toml` foi criado (seção `[breaking]` e `[ae2]`).

## 2. Performance — Calculadora de Quebra

Prepare uma calculadora (de preferência **Singularidade**) com filtros, ferramenta e
um contêiner em cima para a saída.

- [ ] `/scc profile on`, deixe rodando alguns minutos, `/scc profile`.
      Compare o tempo do BE da calculadora e as linhas `sccadd breaking work`,
      `sccadd loot roll`, `sccadd drop event`.
- [ ] `/sccadd breaking`: mostra taxa, crédito, keys, buffer e os contadores (lotes, blocos,
      quebras reais, esperas). Com `speedup = 1.0` a taxa mostrada deve bater com o tier
      (Singularidade sem Overdrive = 32/s; no passo 4 do Overdrive = 128/s).
- [ ] `/sccadd breaking stress 4096` num lote: nenhum "long call" no `logs/scc.log` acima de
      ~8 ms e o crédito volta a acumular depois.
- [ ] `/sccadd breaking tables` lista as tabelas prontas; `reset` faz as máquinas aprenderem de novo.
- [ ] Os drops continuam corretos (pedra → pedregulho, minérios, etc.).
- [ ] Com Overdrive ligado, a taxa não passa do orçamento configurado
      (`totalBudgetMs`, padrão 6 ms/tick somando todas as calculadoras).
- [ ] Teste `fireDropsEvent = false` no config (o pack não tem mods de drop por
      encantamento): deve ficar ainda mais leve.
- [ ] Teste uma calculadora Básico/Anã com ferramenta: a durabilidade ainda
      desce e o cliente vê a barra atualizar (1× por tick agora).
- [ ] Compare com o Observable (antes/depois) num cenário de várias calculadoras.

## 3. AE2 — receitas do AE2 no calculador do SCC

- [ ] Coloque um **Conector de Armazenamento** encostado em qualquer bloco da rede AE2
      (cabo serve).
- [ ] No terminal do SCC, um item que o AE2 fabrica deve ter a marca AE2.
- [ ] Abra a árvore/calculador para esse item: agora devem aparecer **os
      ingredientes do padrão AE2** como opção de receita (não só "AE2 constrói").
- [ ] Pedir o item continua indo para o AE2 (job list do AE2 pega a tarefa).
- [ ] Itens sem padrão AE2 continuam como antes.

## 4. AE2 — receitas do SCC como padrões AE2

- [ ] Coloque uma **Antena de Rede** encostada na rede AE2.
- [ ] No terminal do AE2, um item que **só o SCC fabrica** deve aparecer como
      fabricável (agora com árvore de ingredientes no cálculo do AE2).
- [ ] Inicie um job no AE2 que precise desse item: o AE2 deve empurrar os
      ingredientes para o SCC e o SCC iniciar o job; o resultado volta para o ME.
- [ ] `logs/scc.log` mostra as mensagens `ae2`/`sccadd` do fluxo.
- [ ] Sem terminal/planejador, nada trava: o push falha e os itens ficam na rede SCC.

## 5. Requester e cadeias

- [ ] Item novo numa linha entra **desligado**; Shift+clique liga.
- [ ] Campos: `64*2`, `1k` e tempo `1m`/`3h` são aceitos; sem teto de 9999.
- [ ] Editar quantidade/tempo com um job rodando **não** inicia um segundo job.
- [ ] Clique no slot (cursor vazio): aparecem **Item** e **Chain**.
- [ ] Editor: + Box, escolher item (seletor) e receita (seletor do SCC), ligar OUT→IN, ★ start, cortar link.
- [ ] A linha mostra o item da caixa start e "Chain: N boxes".
- [ ] Ciclo de duas caixas (ex.: madeira ↔ pedra) fecha com "crafts por vez" pequeno.
- [ ] Pensar: pedido grande vira cartão roxo e retoma com um clique.

## 6. Terminal, bolso, Interface, máquina

- [ ] Filtro avançado e Clean variants; editor de barra por arrastar.
- [ ] Terminal de bolso: painel à direita só no sem fio; regras por slot.
- [ ] Interface: slot com template recusa inserção de cano; balde num template cria tanque de fluido.
- [ ] Processing Calculator: clique direito no ícone da máquina → escolher máquina; "Auto" volta.
- [ ] Inscriber: `@ae2 proce` mostra os processadores como craftáveis; prensa dentro da máquina não gera pedido de outra; prensa errada volta para a rede.
- [ ] Ímã puxa itens soltos até 16 blocos.

## 7. Relatar

Para qualquer problema, mande:

- `logs/latest.log` (ou o crash report);
- `logs/scc.log`;
- print da tela + o que você fez / esperava / aconteceu.
