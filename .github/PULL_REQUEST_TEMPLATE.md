## Summary

<!-- What does this PR do? 1-3 bullet points. -->

-
-

## Type of change

- [ ] Bug fix
- [ ] New feature / endpoint
- [ ] Database schema change (Flyway migration included?)
- [ ] Infrastructure / CI change
- [ ] Refactoring (no behaviour change)

## Testing

- [ ] Unit tests added / updated (`pnpm test` passes)
- [ ] Tested locally with `docker compose -f infra/local/docker-compose.yml up -d`
- [ ] `pnpm lint` and `pnpm typecheck` pass
- [ ] DB migration runs cleanly: `pnpm db:migrate`

## Checklist

- [ ] No `any` types introduced
- [ ] All new DTOs have class-validator decorators
- [ ] All new controller methods have `@ApiOperation` summary
- [ ] No secrets or PII in the diff
- [ ] ARCHITECTURE.md or INFRASTRUCTURE.md updated if this changes a contract
