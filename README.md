# Super Cool Additions (sccadd)

Addon para **SCC – Super Cool Calculator** (NeoForge 1.21.1).

Duas frentes:

1. **Performance da Calculadora de Quebra** — o breaking deixa de ser pesado no servidor.
2. **Integração profunda com o AE2** — receitas cruzadas nos dois sentidos.

- Minecraft **1.21.1** / NeoForge **21.1.251**
- Requer **SCC 1.4.0** (`scc-1.4.0.jar`)
- AE2 **opcional** (sem ele, o addon só aplica o fix de performance)

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

### 2.3 Itens entre AE2 e SCC (já automático no SCC 1.4.0)

- **Conector de Armazenamento encostado em qualquer bloco AE2** (cabo, controlador, drive, ME Interface): a rede ME inteira vira armazenamento do SCC — ler, inserir e extrair. A detecção é automática.
- **Antena de Rede encostada na rede AE2**: o estoque do SCC aparece no ME (terminais AE2 veem os itens) e o modo híbrido de fabricação é ativado.
- O addon não mexe nisso — está documentado aqui para você usar.

---

## 3. Build

```bash
./gradlew build          # (ou o gradle do sistema) — o jar sai em build/libs/sccadd-<versão>.jar
```

O projeto compila contra os jars da instância de teste em `libs/`:

- `libs/scc-1.4.0.jar`
- `libs/appliedenergistics2-19.2.18.jar` (+ `guideme`)

Instalação: copie `build/libs/sccadd-*.jar` para `mods/` junto com o SCC e o AE2.

## 4. Teste de fumaça (dev)

```bash
gradle runServer    # sobe um servidor dedicado com SCC + AE2 + o addon
```

`run/mods` recebe automaticamente os jars de `libs/`.

## 5. O que ainda está em aberto

- Bugs de UI e de autocrafting relatados pelo usuário — aguardando os casos específicos.
- Medição real do ganho de performance no mundo do jogador (`/scc profile`).
