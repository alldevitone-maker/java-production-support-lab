# Arquitetura / Architecture

[Português](../README.md) | [English](../README.en.md)

## Componentes

| Caminho | Responsabilidade |
|---|---|
| `src/main/java/lab/payments/PaymentSupportLab.java` | Servidor HTTP, domínio simulado, roteamento e injeção de falhas |
| `ui/index.html` | Console responsivo, filtros, criação de simulações e timeline |
| `sql/` | Modelo e consultas PostgreSQL para estudo |
| `scripts/` | Verificação de fluxos e cenários |
| `.github/workflows/java.yml` | Compilação e validação no GitHub Actions |

## Fluxo

O console envia JSON para a API na mesma origem. A API cria pagamentos e eventos em memória e registra logs JSON no terminal. Cada cenário é selecionado pelo cabeçalho `X-Lab-Bug`. Não há chamada externa de pagamento.

A thread pool possui quatro workers. Os mapas são concorrentes, mas a sequência consultar/criar da idempotência não é atômica. Não use essa implementação como garantia financeira.

## Design boundaries

The service is a compact teaching implementation, not a production reference architecture. Fault injection belongs to the local lab. The SQL model is independent from runtime storage. Future persistence, JSON validation and atomic idempotency must preserve the educational scenarios as explicitly selected fixtures.
