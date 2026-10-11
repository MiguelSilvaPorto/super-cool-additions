# Checklist de teste — sccadd 0.1.0

Instância: `scc fix` (NeoForge 21.1.251, SCC 1.4.0, AE2 19.2.18, Observable).
O jar já está em `mods/sccadd-0.1.0.jar`.

## 1. O jogo abre

- [ ] Cliente chega ao menu principal sem crash.
- [ ] Lista de mods mostra **Super Cool Additions 0.1.0**.
- [ ] `config/sccadd-common.toml` foi criado (seção `[breaking]` e `[ae2]`).

## 2. Performance — Calculadora de Quebra

Prepare uma calculadora (de preferência **Singularidade**) com filtros, ferramenta e
um contêiner em cima para a saída.

- [ ] `/scc profile on`, deixe rodando alguns minutos, `/scc profile`.
      Compare o tempo do BE da calculadora e as linhas `sccadd breaking work`,
      `sccadd loot roll`, `sccadd drop event`.
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

## 5. Relatar

Para qualquer problema, mande:

- `logs/latest.log` (ou o crash report);
- `logs/scc.log`;
- print da tela + o que você fez / esperava / aconteceu.
