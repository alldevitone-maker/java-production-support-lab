# Arquitetura

[Português](../README.md) | [English](../README.en.md)

## Aplicação completa

React chama `/api` na mesma origem. Spring Boot serve tanto os assets compilados quanto a API. Não há cadastro. O servidor usa o identificador aleatório de sessão para consultar, criar e limpar somente as simulações do visitante.

| Componente | Responsabilidade |
|---|---|
| `frontend/` | Console React, idiomas, temas, filtros e detalhe dos eventos |
| `ApiController` | Contrato HTTP e validação dos cabeçalhos |
| `PaymentService` | Identificadores, idempotência, regras e cenários |
| `ConnectorRegistry` | Estratégias de simulação selecionadas pelo perfil SQL |
| `PaymentRepository` | Consultas parametrizadas, eventos e tentativas |
| Flyway | Schema e catálogo inicial reproduzíveis |
| PostgreSQL / H2 | Persistência real / SQL efêmero para demonstração |

A transação SQL grava pagamento, eventos e tentativas de forma atômica. Uma restrição de unicidade resolve reenvios concorrentes; a impressão SHA-256 normalizada do pedido rejeita uso da chave com outro conteúdo.

## Modelo SQL

| Tabela | Relação |
|---|---|
| institutions | Catálogo de cinco instituições fictícias |
| connectors | Um conector por instituição, com perfil e latência base |
| payments | Origem, destino, valores, identificadores e sessão |
| payment_events | Muitos eventos por pagamento; duplicata didática pode compartilhar eventId |
| connector_attempts | Muitas tentativas por pagamento e conector |

Os eventos possuem horário e sequência de recebimento separados. No cenário fora de ordem, uma mensagem mais antiga chega depois. As latências são valores sintéticos e não são usadas para dormir ou bloquear threads.

## Publicação no Sites

O Sites hospeda somente a build com `VITE_LAB_MODE=demo`. Essa build usa `demo.js`, dados em memória da aba e o catálogo compartilhado. Mostra BROWSER DEMO e uma nota explícita. Não usa nem afirma usar SQL ou Spring Boot. A build padrão usa o backend; erro de rede continua sendo erro de rede.

## Limites técnicos

Não há integração real, compliance bancário, autenticação, consulta DICT/SPI, cobrança ou execução remota de SQL. A sessão é isolamento didático por capacidade aleatória, não uma identidade verificada. O controle global de capacidade não garante quotas entre múltiplas réplicas. Para dados sensíveis ou uso comercial, redesenhe identidade, autorização e controles operacionais.
