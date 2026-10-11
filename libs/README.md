# libs/ — jars de terceiros (não versionados)

O build compila contra os jars reais do mod, que **não são redistribuídos** neste
repositório. Coloque aqui:

| Arquivo | Onde obter |
|---|---|
| `scc-1.4.0.jar` | CurseForge — SCC - Super Cool Calculator 1.4.0 (NeoForge 1.21.1) |
| `appliedenergistics2-19.2.18.jar` | CurseForge — Applied Energistics 2 (1.21.1) |
| `guideme-21.1.19.jar` | CurseForge — GuideME (dependência do AE2) |

Opcionalmente, para os dev runs espelharem a instância de teste, pode-se copiar
outros mods do pack para cá (Sodium, JEI, Jade, Observable...). O build não
depende deles; só o `runClient`/`runServer`.

Depois de colocar os jars:

```bash
./gradlew build
```

O jar do addon sai em `build/libs/sccadd-<versão>.jar`.
