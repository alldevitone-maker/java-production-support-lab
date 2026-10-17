# PayOps Lab

[Português](README.md) | [English](README.en.md)

![PayOps checks](https://github.com/alldevitone-maker/java-production-support-lab/actions/workflows/java.yml/badge.svg)

Laboratório de suporte L2/L3 com **React, Java 17, Spring Boot e SQL**. Simule transferências Pix entre cinco instituições fictícias, injete incidentes e investigue identificadores, estados, tentativas e eventos.

[Interface online](https://payops-java-support-lab.giovane-pereir914826.chatgpt.site) · [Arquitetura](docs/ARCHITECTURE.md) · [Deploy](docs/DEPLOYMENT.md) · [Contrato da API](docs/API.md) · [Roadmap](docs/ROADMAP.md)

> A publicação Sites usa o modo de demonstração no navegador. O backend Spring Boot é uma aplicação separada, pronta para deploy com o Dockerfile e o Blueprint. A interface identifica o motor em uso e nunca troca silenciosamente Java por simulação local.

## O que você pode investigar

- Cinco instituições e cinco conectores populados por migrações SQL.
- Origem e destino selecionáveis, com proibição de transferir para a mesma instituição.
- `txid`, `endToEndId`, `paymentId` e `correlationId` fictícios por operação.
- Idempotência por sessão, com unicidade no banco e rejeição de chave reutilizada com outro conteúdo.
- Histórico de eventos e tentativas de integração, consulta e filtros.
- Diferença entre valor solicitado e valor liquidado no exercício de divergência.
- Interface adaptada a celular e desktop, PT/EN e temas claro/escuro.
- Sessão anônima no navegador, sem conta. Cada visitante consulta e limpa somente suas próprias simulações.

Todas as instituições, conexões e transações são fictícias. Não há integração com bancos, envio de Pix, consulta ao SPI ou movimentação financeira. Os tempos dos conectores são valores configurados, não medições reais.

## Executar a aplicação completa

Requisitos: JDK 17+, Maven 3.6.3+, Node 22.12+ e npm.

```bash
git clone https://github.com/alldevitone-maker/java-production-support-lab.git
cd java-production-support-lab
sh run.sh
```

Abra **http://localhost:8080**. O build coloca o React dentro do JAR; o Spring serve a página e a API no mesmo endereço.

O padrão usa H2 em memória com SQL compatível com PostgreSQL. As tabelas e os cinco bancos fictícios são criados automaticamente pelo Flyway. Ao reiniciar, os pagamentos são apagados e o catálogo é recriado.

### Desenvolvimento

```bash
# Terminal 1
mvn -f backend/pom.xml spring-boot:run

# Terminal 2
npm ci --prefix frontend
npm run dev --prefix frontend
```

O Vite informa o endereço local e encaminha `/api` ao Spring em `localhost:8080`.

### PostgreSQL com Docker

```bash
cp .env.example .env
# Defina POSTGRES_PASSWORD no arquivo .env antes de continuar.
docker compose up --build
```

Abra http://localhost:8080. O volume `payops-data` mantém os dados entre reinícios. O banco não é exposto por uma porta pública no Compose.

Sem Docker, configure `DATABASE_URL=jdbc:postgresql://HOST:5432/DB`, `DATABASE_USERNAME` e `DATABASE_PASSWORD`. Nunca inclua credenciais no repositório.

## Instituições e conectores

| Instituição fictícia | Perfil | Latência base configurada |
|---|---|---|
| Banco Aurora | FAST | 120 ms |
| Vértice Pay | STANDARD | 260 ms |
| Banco Horizonte | SLOW | 1.200 ms |
| Nimbus Money | STANDARD | 380 ms |
| Órbita Bank | FAST | 180 ms |

O catálogo compartilhado está em `catalog/institutions.json`. O script `python3 scripts/generate-seed.py` gera a migração inicial correspondente. O backend consulta o SQL; os conectores são selecionados pelo perfil salvo em banco, sem condicionais por nome de instituição. O simulador de navegador usa o mesmo catálogo como fixture e dispensa banco remoto.

**Após publicar uma migração aplicada em banco persistente, não a reescreva:** crie `V3`, `V4` etc. para futuras alterações. O gerador da `V2` é para preparar/verificar este catálogo inicial.

## Exercícios

| Cenário | Evidência principal |
|---|---|
| `normal` | HTTP 201 e SETTLED |
| `duplicate-charge` | Dois eventos PIX_SETTLED para o mesmo pagamento |
| `timeout-after-accept` | HTTP 504, mas operação persistida |
| `stale-status` | Evento de liquidação com estado PENDING |
| `rounding-error` | Divergência injetada de um centavo; estado REVIEW |
| `missing-correlation` | Log do conector sem correlationId |
| `wrong-http-status` | Rejeição com HTTP 200 |
| `null-response` | HTTP 502 e UNKNOWN |
| `retry-storm` | Quatro tentativas sem backoff |
| `duplicate-webhook` | Entregas repetidas com o mesmo eventId |
| `out-of-order-events` | Evento antigo aplicado após liquidação |
| `connector-unavailable` | HTTP 503 e FAILED |

O cenário `rounding-error` desta versão injeta uma diferença fixa para investigação. Não é uma reprodução do algoritmo de arredondamento de uma instituição real. O retorno 504 é simulado imediatamente; a aplicação não bloqueia uma thread por um timeout real.

## Verificação

```bash
python3 scripts/generate-seed.py --check
npm ci --prefix frontend
npm run test --prefix frontend
npm run build --prefix frontend
mvn -B -f backend/pom.xml verify
```

O CI repete os testes do backend em H2 **e PostgreSQL**, incluindo migrações, todos os cenários, isolamento de sessão, valores inválidos, reenvio e concorrência com a mesma chave. O CI também testa o simulador, a interface React e gera o JAR executável.

## Limites

- Sessão aleatória mantida em `sessionStorage`. Não representa autenticação nem uma conta bancária.
- No máximo 100 pagamentos por sessão; limite global de 10.000 registros, com descarte programado de itens com mais de 24 horas.
- O limite global é uma proteção simples para demonstração, não um mecanismo de quotas distribuídas.
- Somente dados fictícios. Não envie nomes, documentos, chaves Pix ou dados de clientes reais.
- Não há garantia de conformidade ou interoperabilidade com o Pix. `E00000000` marca um participante fictício no identificador didático.
- Não há endpoint que execute SQL enviado pelo visitante. O guia SQL mostra consultas para estudo.
- O motor de simulação representa um fluxo reduzido, não a implementação de um PSP ou adquirente.

## Histórico e licença

A versão Java sem frameworks foi preservada em [legacy](legacy/). Para executá-la, entre em `legacy/` e use `sh run.sh`.

[MIT](LICENSE). Projeto de Giovane. Veja [CHANGELOG](CHANGELOG.md) e [CONTRIBUTING](CONTRIBUTING.md).
