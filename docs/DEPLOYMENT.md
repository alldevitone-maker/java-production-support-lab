# Hospedagem

## Estado das publicações

- Sites: React com simulador explícito no navegador. Não hospeda JVM.
- GitHub: código completo, testes, Dockerfile e Blueprint.
- Spring Boot público: requer um serviço Java/Docker conectado à conta do proprietário, como Render.

Não aponte o Sites para uma URL inventada nem marque o modo Java como operacional antes de verificar `/actuator/health` e `/api/catalog` no serviço publicado.

## Render

O arquivo `render.yaml` solicita apenas um web service Docker no plano `free`. O Dockerfile compila o React e o Java e publica tudo na mesma origem. O Render deve definir `PORT`; Spring respeita essa variável.

A opção inicial usa H2 em memória. Ao reiniciar a instância, os pagamentos são perdidos e o catálogo fictício é recriado pelo Flyway. Recursos gratuitos podem ter suspensão por inatividade e outras limitações do provedor. Confira as condições no painel antes de criar recursos.

Para PostgreSQL, provisione um banco e configure no serviço:

| Variável | Conteúdo |
|---|---|
| DATABASE_URL | JDBC: `jdbc:postgresql://HOST:PORT/DATABASE` |
| DATABASE_USERNAME | Usuário do banco |
| DATABASE_PASSWORD | Senha, armazenada como segredo no provedor |

O Blueprint não cria PostgreSQL pago nem um banco gratuito com expiração. Escolher banco/plano é uma decisão posterior. Não converta uma URL de conexão pública em texto de documentação com senha.

## Verificação após deploy

1. `/actuator/health` retorna `UP`.
2. `/api/catalog` mostra `SPRING_BOOT_SQL` e cinco instituições.
3. A raiz abre o React, sem conta de visitante.
4. Crie uma operação, consulte os identificadores e confirme o reenvio idempotente.
5. Abra outra sessão de navegador e confira isolamento dos históricos.

## Publicar só a demonstração

```bash
VITE_LAB_MODE=demo npm run build --prefix frontend
```

Publique `frontend/dist`. Essa build é autossuficiente e identifica o modo no cabeçalho. Nunca apresente essa versão como execução Java.

## Referências oficiais

- [Render web services](https://render.com/docs/web-services)
- [Render Blueprint](https://render.com/docs/blueprint-spec)
- [Requisitos Spring Boot 3.5](https://docs.spring.io/spring-boot/3.5/system-requirements.html)
- [API Pix do Banco Central](https://github.com/bacen/pix-api)
