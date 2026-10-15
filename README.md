# Java Production Support Lab

[Português](README.md) | [English](README.en.md)

![Java smoke checks](https://github.com/alldevitone-maker/java-production-support-lab/actions/workflows/java.yml/badge.svg)

[Executar](#executar) · [Cenários](#cenários-disponíveis) · [Runbook](#runbook-de-triagem-l2l3) · [Arquitetura](docs/ARCHITECTURE.md) · [Contribuir](CONTRIBUTING.md)

Laboratório didático de suporte L2/L3 para uma API fictícia de pagamentos. A aplicação roda com Java 17 sem bibliotecas externas e permite reproduzir incidentes de forma controlada, correlacionar logs e seguir runbooks. Os dados são simulados e ficam só na memória.

> Os cenários são falhas intencionais para treinamento. Rode apenas localmente; não use com dados, credenciais ou sistemas reais.

## Executar

```bash
./run.sh
```

Ou compile manualmente:

```bash
mkdir -p out
javac -d out src/main/java/lab/payments/*.java
java -cp out lab.payments.PaymentSupportLab
```

A API escuta em `http://localhost:8080`.

Abra `http://localhost:8080` no navegador para usar o console visual. A tela permite simular Pix, crédito ou débito, selecionar um cenário de falha, filtrar operações e inspecionar correlação e timeline.

## Fluxo rápido

```bash
curl -i -X POST http://localhost:8080/payments \
  -H 'Content-Type: application/json' \
  -H 'X-Correlation-Id: demo-001' \
  -d '{"customerId":"cust-42","amount":19.99,"currency":"BRL"}'

curl -i http://localhost:8080/payments/PAYMENT_ID
curl -s http://localhost:8080/admin/scenarios
```

Reenvio com a mesma chave, sem bug injetado, devolve a mesma transação:

```bash
curl -i -X POST http://localhost:8080/payments \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: order-1001' \
  -d '{"customerId":"cust-42","amount":19.99,"currency":"BRL"}'
```

Para reproduzir captura duplicada, repita o pedido com a mesma chave e `X-Lab-Bug: duplicate-charge`.

Para injetar uma falha, use `X-Lab-Bug` na criação. Exemplo:

```bash
curl -i -X POST http://localhost:8080/payments \
  -H 'Content-Type: application/json' -H 'X-Lab-Bug: duplicate-charge' \
  -d '{"customerId":"cust-42","amount":19.99,"currency":"BRL"}'
```

## Cenários disponíveis

| Identificador | Sintoma para investigar | Pista de diagnóstico |
|---|---|---|
| `duplicate-charge` | Uma operação Pix é liquidada duas vezes ou um cartão tem duas capturas simuladas | Compare `paymentId`, `idempotencyKey` e os eventos associados |
| `timeout-after-accept` | A API responde timeout, mas a transação foi aceita | Consulte pelo `X-Correlation-Id` antes de repetir a operação |
| `stale-status` | Estado permanece `PENDING` apesar de evento de autorização | Compare horário/eventos e estado projetado |
| `rounding-error` | Valor em centavos difere do esperado | Compare conversão decimal e valor persistido; não use `double` para dinheiro |
| `missing-correlation` | Log sai sem identificador de correlação | Verifique propagação do cabeçalho entre camadas |
| `wrong-http-status` | Erro de validação retorna status HTTP incorreto | Compare status, corpo e classificação de erro |
| `null-response` | Integração retorna resposta vazia | Verifique validação de resposta e tratamento de nulos |
| `retry-storm` | Um único pedido dispara várias tentativas | Inspecione política de retry, backoff e limite de tentativas |
| `duplicate-webhook` | O mesmo evento de webhook é processado mais de uma vez | Procure deduplicação por `eventId` |
| `out-of-order-events` | Evento mais antigo sobrescreve estado mais novo | Ordene por timestamp/versão antes de atualizar o estado |

Envie os valores na forma kebab-case acima em `X-Lab-Bug`. Os efeitos ficam restritos à instância local e são reiniciados quando o processo é encerrado.

## Endpoints

- `GET /health`: liveness simples.
- `GET /`: console de operações local.
- `GET /payments`: lista pagamentos criados na sessão.
- `POST /payments`: cria pagamento fictício; aceita Pix ou cartão e os cabeçalhos `X-Correlation-Id`, `Idempotency-Key`, `X-Lab-Bug`.
- `GET /payments/{id}`: consulta pagamento e eventos simulados.
- `GET /admin/scenarios`: lista os bugs injetáveis.
- `POST /admin/reset`: apaga dados em memória para reiniciar um exercício.

## Runbook de triagem L2/L3

1. Registre horário, endpoint, status HTTP, `paymentId` e `correlationId`.
2. Consulte `GET /payments/{id}` antes de reenviar uma operação; timeout não prova que nada foi processado.
3. Compare a linha do tempo dos eventos com o estado final e procure repetição ou inversão de ordem.
4. Separe falha do cliente, da aplicação e da integração; preserve evidências sem expor dados sensíveis.
5. Defina impacto e escopo: transações afetadas, janela, clientes e taxa de erro.
6. Escalone com request/response sanitizados, timestamps UTC, correlation ID, passos para reproduzir e mitigação tentada.
7. Após resolver, documente causa, correção, prevenção e item de acompanhamento.

## SQL de investigação

`sql/schema.sql` e `sql/investigation_queries.sql` mostram um modelo PostgreSQL equivalente ao domínio simulado: Pix, adquirência, pagamentos, tentativas e webhooks. São exemplos de estudo; esta versão em memória não conecta a um banco. As consultas ajudam a praticar investigação de duplicidade, transações pendentes, eventos repetidos e valores divergentes.

As fontes oficiais do Banco Central, Cielo e Visa ficam na própria interface. Elas servem de referência conceitual para os fluxos reais; o laboratório não se conecta a esses serviços.

## Limites e segurança

- Não há gateway real, cobrança, autenticação ou persistência.
- A aplicação é intencionalmente simples e não é adequada para produção.
- Os endpoints `/admin/*` são apenas para localhost/laboratório.
- Não registre tokens, dados de cartão, senhas ou dados pessoais nos exemplos.

## Limitações conhecidas

- Parser JSON simplificado por expressão regular, sem validação completa do contrato.
- Idempotência sequencial: requisições concorrentes com a mesma chave podem criar duplicatas.
- Os exemplos SQL não são executados pela API.
- Interface atual em português; a documentação está disponível em português e inglês.

## Próximas evoluções

1. Persistência real em PostgreSQL com migrações e testes de integração.
2. Testes automatizados para cada cenário e contrato HTTP.
3. Métricas, tracing e painel de observabilidade.
4. Containerização; o pipeline CI de compilação e smoke checks já está incluído.

## Licença

MIT. Veja [LICENSE](LICENSE).
