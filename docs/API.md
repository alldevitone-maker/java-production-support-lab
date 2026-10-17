# Contrato HTTP

Base: `/api`. JSON. Todos os recursos são de laboratório, com dados fictícios.

| Método | Caminho | Cabeçalhos | Resultado |
|---|---|---|---|
| GET | `/catalog` | Nenhum | 5 instituições, conectores, cenários e runtime |
| GET | `/payments` | X-Lab-Session | Até 100 operações da sessão |
| GET | `/payments/{id}` | X-Lab-Session | Detalhe, eventos e tentativas |
| POST | `/payments` | X-Lab-Session, Idempotency-Key, X-Correlation-Id opcional | Pagamento e metadados do resultado |
| DELETE | `/payments` | X-Lab-Session | Limpa somente a sessão informada |
| GET | `/actuator/health` | Nenhum; fora da base `/api` | Saúde para a hospedagem |

Exemplo (substitua o UUID da sessão por um novo UUID ao iniciar outro exercício):

```bash
curl -i http://localhost:8080/api/payments \
  -H 'Content-Type: application/json' \
  -H 'X-Lab-Session: 40cc990d-9528-4c6f-b929-30189333a12a' \
  -H 'Idempotency-Key: demo-order-001' \
  -H 'X-Correlation-Id: demo-corr-001' \
  -d '{"sourceId":"aurora","destinationId":"vertice","amount":49.90,"currency":"BRL","scenario":"normal"}'
```

Um sucesso retorna HTTP 201 e `{payment: {...}, replayed: false, simulated: true}`. O reenvio idêntico retorna HTTP 200, o mesmo pagamento e `replayed: true`. Mesmo cabeçalho de chave com corpo diferente retorna 409. Valores com mais de duas casas decimais são rejeitados antes da gravação.

Os cenários 502, 503 e 504 retornam o pagamento persistido no corpo, junto de `error`, para permitir investigação. Um reenvio idempotente desses resultados retorna HTTP 200 com o estado já registrado. No exercício de HTTP incorreto, o corpo indica erro e pagamento REJECTED apesar do status HTTP 200.

`rounding-error` injeta um centavo de diferença de forma explícita; `missing-correlation` preserva o identificador no registro e o omite somente no log do conector.

Os identificadores têm formato didático: `txid` começa com `SIMTX`; `endToEndId` usa `E00000000`, data UTC e sufixo aleatório. Não são identificadores utilizáveis na rede Pix.
