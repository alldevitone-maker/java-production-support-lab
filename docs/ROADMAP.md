# Roadmap · v0.2.0

## Implementado no código

- [x] React responsivo com PT/EN e temas claro/escuro
- [x] Java 17 + Spring Boot + validação JSON
- [x] Cinco instituições e conectores populados por SQL
- [x] Flyway, H2 e configuração PostgreSQL
- [x] Identificadores fictícios txid e endToEndId
- [x] Eventos, tentativas, busca e filtros
- [x] Idempotência com unicidade no SQL e teste concorrente
- [x] Sessões anônimas com históricos separados
- [x] Doze fluxos, incluindo o caso normal e indisponibilidade
- [x] Docker e Blueprint de hospedagem
- [x] Modo de navegador explicitamente identificado

## Publicação e validação

- [ ] Serviço Spring Boot público ativo no provedor do proprietário
- [ ] PostgreSQL persistente publicado (opcional; H2 já é executável)
- [ ] Auditoria visual manual em desktop e dispositivos móveis

Os testes e builds são verificados a cada alteração no GitHub Actions. A existência do Blueprint não comprova que o serviço Java já foi publicado.

## Próximos passos

- [ ] Métricas e tracing com OpenTelemetry
- [ ] Processamento assíncrono de confirmação e reconciliação
- [ ] Exercícios de circuit breaker e backoff
- [ ] Catálogo configurável por migrações incrementais
- [ ] Hardening de acesso para qualquer uso além de dados fictícios
