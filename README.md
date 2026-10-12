# Super Cool Additions (sccadd)

Addon para **SCC – Super Cool Calculator** (NeoForge 1.21.1).

O que o addon traz:

1. **Performance da Calculadora de Quebra** — orçamento por tick e modo lote.
2. **Integração profunda com o AE2** — receitas, itens e fluidos nos dois sentidos, Inscriber/Charger e prensas reaproveitáveis.
3. **Requester** — campos digitados, cadeias de produção e o botão Pensar.
4. **Terminal e ferramentas** — filtro avançado, editor de barra, terminal de bolso com regras por slot, ímã, tanques de fluido no Interface e máquina escolhida na Processing Calculator.
5. **Aba criativa própria** — a aba **SCC Additions** no inventário criativo, logo depois da aba do SCC, com os itens do addon e as máquinas do SCC que ele melhora.

- Minecraft **1.21.1** / NeoForge **21.1.251**
- Requer **SCC 1.4.0** (`scc-1.4.0.jar`)
- AE2 **opcional** (sem ele, valem só as partes que não dependem do AE2)

---

## 1. Fix de performance — Calculadora de Quebra

O SCC 1.4.0 já tenta limitar cada operação a 2 ms por tick, mas esse limite:

- só passa a valer **depois do primeiro bloco** quebrado;
- não conta o tempo do **plano**, do **snapshot** nem do **settle**;
- é **por máquina** — dez calculadoras Singularidade podem empilhar 10 × 2 ms no mesmo tick.

### O que o addon faz

| Mudança | Efeito |
|---|---|
| **Orçamento global por tick** | Todos os Breaking Calculators juntos gastam no máximo `totalBudgetMs` (padrão **6 ms**) por tick do servidor. O trabalho restante vai para o próximo tick via `carry`. |
| **Orçamento por máquina configurável** | `budgetMs` (padrão **2 ms**), checado **antes de cada bloco**, inclusive o primeiro. |
| **Loot params cacheados** | Origem, ferramenta, jogador e posição são idênticos no tick inteiro; os `LootParams` agora são montados **uma vez por estado de bloco por tick**, não por bloco. |
| **`BlockDropsEvent` direto** | O evento passa a ser construído direto (sem reflexão do SCC). Pode ser desligado em `fireDropsEvent = false` para packs sem encantamentos que alteram drops (ex.: Boon of the Earth, Apotheosis). |
| **Sync da ferramenta adiado** | Nos tiers abaixo de Singularidade, o desgaste da ferramenta gerava `setChanged()+sync()` **por bloco** (pacote por bloco!). Agora sincroniza 1× por tick. |
| **Marcas de profiler** | Com `/scc profile on`, aparecem as linhas `sccadd breaking work`, `sccadd loot roll` e `sccadd drop event` no relatório, para medir exatamente onde o tempo vai. |

### Modo lote

Com `[breaking] batch = true` (padrão), o addon aprende a distribuição real dos drops de cada minério ("tabelas de dados") e passa a creditar **vários blocos de uma vez** em vez de simular um por um, ainda gastando a durabilidade. Cada nível tem um buffer escondido (`bufferBase`, dobra a cada tier) e `accumulate` junta blocos em lotes grandes. É uma **aproximação estatística** que se corrige sozinha com as quebras reais; `[breaking] batch = false` volta ao ritmo por tick.

### Como medir

```
/scc profile on
# jogue alguns minutos com as calculadoras trabalhando
/scc profile
```

Compare as linhas `sccadd …` e o total do BE da calculadora. O log `logs/scc.log` também mostra "long call" acima de 8 ms.

### Config

`config/sccadd-common.toml`, seção `[breaking]`:

| Opção | Padrão | Descrição |
|---|---|---|
| `budgetMs` | `2.0` | ms por calculadora por tick |
| `totalBudgetMs` | `6.0` | ms somando todas as calculadoras por tick |
| `fireDropsEvent` | `true` | disparar `BlockDropsEvent` nas quebras simuladas |
| `deferToolSync` | `true` | sincronizar desgaste 1× por tick |
| `cacheLootParams` | `true` | reaproveitar parâmetros de loot no tick |
| `profile` | `true` | marcas extras no `/scc profile` |

Valores são lidos a cada uso: pode editar e recarregar o mundo sem reiniciar (ou usar `/scc reloadconfig` para o lado do SCC).

---

## 2. Integração AE2

### 2.1 Receitas do AE2 no calculador do SCC (padrões com ingredientes)

Antes: um item que o AE2 sabe fabricar aparecia no SCC como **um passo único** ("AE2 constrói"), sem ingredientes.

Agora: os padrões do AE2 são lidos do grid (via Conector de Armazenamento), sincronizados para o cliente e mostrados como **receitas de verdade** no calculador — com todos os ingredientes do padrão, aparecendo como opção na árvore e na escolha de receita. Ao pedir, o trabalho continua indo para o AE2 (que usa o próprio estoque), como já era.

- Config: `[ae2] patternsInScc` (padrão `true`).
- Limites de segurança: até 400 padrões / 96 KB por terminal.
- Padrões com fluidos ou saídas com chance não são mostrados (melhor não mostrar do que mostrar errado); nesses casos o passo "AE2 constrói" antigo continua valendo.

### 2.2 Receitas do SCC como padrões AE2 reais

Antes: a Antena de Rede oferecia ao AE2 só uma **lista de itens** ("SCC sabe fazer"), e o AE2 planejava como folha sem ingredientes.

Agora: junto com a lista, o cliente envia as **receitas** dos itens oferecidos. O servidor as transforma em `IPatternDetails` reais:

- o **AE2 enxerga a árvore** (ingredientes) dos itens que o SCC fabrica;
- CPUs do AE2 podem **empurrar os ingredientes exatos** para o SCC (`pushPattern`): os itens entram na rede SCC e o SCC inicia o job; o resultado é entregue de volta ao ME pela Antena.

- Config: `[ae2] sccPatternsInAe2` (padrão `true`).
- Até 400 receitas por antena, escolhendo primeiro as mais usadas como ingrediente (materiais base).
- Sem terminal/planejador disponível, o push falha de forma segura (nada é perdido; fica na rede SCC).

### 2.3b Inscriber e Charger no SCC

O recipe viewer não entrega as receitas de máquina do AE2, então o addon lê as do **Inscriber** e do **Charger** direto do jogo e as ensina ao SCC (uma Processing Calculator na frente da máquina recebe os jobs). Presses nos slots de cima/baixo não são consumidas. Receitas que copiam uma prensa só são usadas quando já existe uma.

Itens que o AE2 já sabe fabricar continuam craftáveis: o passo "AE2 constrói" fica como última opção quando as receitas detalhadas do padrão não se resolvem.

### 2.3 Itens entre AE2 e SCC (já automático no SCC 1.4.0)

- **Conector de Armazenamento encostado em qualquer bloco AE2** (cabo, controlador, drive, ME Interface): a rede ME inteira vira armazenamento do SCC — ler, inserir e extrair. A detecção é automática.
- **Antena de Rede encostada na rede AE2**: o estoque do SCC aparece no ME (terminais AE2 veem os itens) e o modo híbrido de fabricação é ativado.
- O addon não mexe nisso — está documentado aqui para você usar.

### 2.4 Receptor Sem Fio como ponte AE2 (novo no addon)

Sem antena: o par sem fio vira a ponte.

1. **Transmissor Sem Fio** na rede SCC (base).
2. **Receptor Sem Fio** encostado na rede AE2 (cabo/controlador/drive).
3. Pronto: o receptor vira um nó do AE2 e faz o mesmo papel da antena —
   - expõe a **rede SCC como armazenamento ME** (terminais e Storage Buses do AE2 veem os itens, ida e volta);
   - oferece as **receitas do SCC como padrões reais** (com ingredientes): o AE2 mostra o plano de fabricação e pede sua **confirmação** antes de rodar;
   - aprovado → o AE2 empurra os ingredientes → **o job SCC roda** → o resultado volta para o ME.

Config: `[ae2] bridgeOnReceiver` (padrão `true`). Com dois pares transmissor/receptor você escolhe exatamente qual rede SCC conversa com qual rede AE2 — pensado para servidores com várias redes.

O receptor também lê a rede ME para dentro do SCC: os itens do AE2 contam como estoque SCC, sem Conector de Armazenamento. Config: `[ae2] receiverReadsMe` (padrão `true`).

> A Antena de Rede continua funcionando como sempre; quem preferir o fluxo antigo é só desligar esta opção.

---

## 3. Requester

Tudo em `config/sccadd-common.toml`, seções `[requester]` e `[think]`.

- **Campos digitados** em cada linha, da esquerda para a direita: estoque a manter, quantidade por pedido e tempo. Aceitam fórmulas (`64*2/4`) e sufixos `k`, `m`, `g`, `t`; o tempo aceita `20s`, `1m`, `3h`, `7d`. Sem o teto de 9999.
- **Linhas novas nascem desligadas.** Ligue com Shift+clique depois de ajustar os números.
- **Editar com um job rodando** (quantidade, passo ou tempo) mantém o agendamento: o job continua e a mudança vale no próximo pedido, em vez de iniciar um segundo job.
- **Botão Pensar** (4º botão do diálogo de pedido): planeja pedidos grandes em segundo plano; aparece como cartão roxo na faixa de Autocraft e retoma com um clique. `[think] maxOrderAmount`, `msPerTick`, `maxTasks`, `maxRestarts`.

### Cadeias de produção

Clique no slot de uma linha com o cursor vazio: aparecem **1 Item** e **2 Chain**. A cadeia é um editor de caixas ligadas por setas:

- cada caixa mantém **um item** em estoque com **uma receita** que você escolhe (o seletor de receitas do próprio SCC) e, opcionalmente, um máximo de crafts por vez;
- a plate **OUT** (direita) liga na caixa de destino; a **IN** (esquerda) recebe; pontos animados mostram o sentido; clique direito numa plate ou no x do link corta;
- a **★ start** é onde a cadeia começa e seu item é o que aparece no botão da linha; cada caixa mostra o estoque atual (`have`);
- o servidor roda as caixas em rodízio, **um job por vez**, fazendo quantos crafts o estoque dos ingredientes permitir, e **não depende de jogador online** nem do planejador. É o jeito de descrever laços de receitas que se alimentam.

Limites: uma seta de saída por caixa; receitas com químicos (Mekanism) ainda não.

---

## 4. Terminal, bolso e ferramentas

- **Clean variants** (`[terminal] cleanVariants`): esconde variantes só fabricáveis com encantamentos/atributos/nome/lore; o que a rede tem continua visível.
- **Filtro avançado** (botão na barra do terminal, `config/sccadd-filter.json`): estoque, traços, mod e nome.
- **Editor de barra**: engrenagem no começo da barra lateral; arraste para reordenar e use a bandeja dos botões fora da barra.
- **Terminal de bolso**: painel à direita (ímã, restock, pegar, controlador, exportar ↓, importar ↑) só no terminal sem fio, e regras por slot (item/fluido, quantidade, "fabricar mais" quando esvazia). O painel fica fora da área do JEI/EMI.
- **Ímã** (`[magnet] range`, padrão 16 blocos): puxa itens soltos para a rede.
- **Interface**: slot com template **recusa inserções de fora** (só a rede o abastece); clique com um balde num template cria um **template de fluido**, com um tanque de 16 baldes por slot que entrega ao vizinho e se reabastece da rede.
- **Processing Calculator**: clique direito no ícone da máquina para escolher **qual máquina ela é**, mesmo com um baú na frente — a automação externa assume dali. "Auto" volta a detectar.
- **Prensas reaproveitáveis** (`[processing] reuseCatalysts`, `reusableCatalysts`): as prensas do AE2 dentro da máquina contam como estoque, a prensa errada volta para a rede antes da certa, e uma prensa não é puxada como "resultado" enquanto outra coisa ainda está na máquina.

---

## 5. Build

```bash
./gradlew build          # (ou o gradle do sistema) — o jar sai em build/libs/sccadd-<versão>.jar
```

O projeto compila contra os jars da instância de teste em `libs/`:

- `libs/scc-1.4.0.jar`
- `libs/appliedenergistics2-19.2.18.jar` (+ `guideme`)

Instalação: copie `build/libs/sccadd-*.jar` para `mods/` junto com o SCC e o AE2.

## 6. Teste de fumaça (dev)

```bash
gradle runServer    # sobe um servidor dedicado com SCC + AE2 + o addon
```

`run/mods` recebe automaticamente os jars de `libs/`.

## 7. O que ainda está em aberto

- Bugs de UI e de autocrafting relatados pelo usuário — aguardando os casos específicos.
- Medição real do ganho de performance no mundo do jogador (`/scc profile`).
