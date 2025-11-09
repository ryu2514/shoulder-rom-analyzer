# Repository Guidelines

## Project Structure & Module Organization
- Source lives in `src/`; tests in `tests/`; scripts in `scripts/`; assets in `assets/`.
- Keep modules small and single‑purpose. Group by feature (e.g., `src/rom/`, `src/core/`).
- Public APIs go in `src/<area>/__init__.*` or index files; internal utilities live under `src/<area>/internal/`.

## Build, Test, and Development Commands
- Bootstrap: `make setup` or `./scripts/setup.sh` (install deps). If no Makefile, use the script.
- Run locally: `make run` or `./scripts/run.sh` (starts the app/CLI).
- Tests: `make test` or `./scripts/test.sh` (unit tests + coverage).
- Lint/format: `make lint` / `make fmt` or `./scripts/lint.sh` / `./scripts/fmt.sh`.

Examples:
- `make test` — executes the full test suite with coverage.
- `./scripts/run.sh --help` — shows CLI options if present.

## Coding Style & Naming Conventions
- Indentation: 2 spaces (JS/TS), 4 spaces (Python), tabs not allowed.
- Names: `camelCase` for variables/functions, `PascalCase` for classes/types, `kebab-case` for filenames, `UPPER_SNAKE_CASE` for constants.
- Prefer pure, side‑effect‑free functions. Keep functions ≤ 50 lines when practical.
- Use formatters/linters where configured (e.g., Prettier, Black, ESLint). Do not mix unrelated formatting with feature changes.

## Testing Guidelines
- Put tests in `tests/` mirroring source paths (e.g., `src/rom/parser.ts` → `tests/rom/parser.test.ts`).
- Test naming: `*.test.*` or `test_*.py` as appropriate.
- Aim for ≥ 80% line coverage on changed code; include edge cases and negative paths.
- Fast unit tests > slow integration; mark slow tests with a clear tag/marker.

## Commit & Pull Request Guidelines
- Commit messages: Conventional Commits (`feat:`, `fix:`, `chore:`, `docs:`, `refactor:`, `test:`). Imperative, present tense.
- One logical change per PR. Include:
  - Clear description and rationale
  - Linked issue (e.g., `Closes #123`)
  - Testing evidence (commands run, screenshots if UI)
- Keep diffs minimal; include docs/README updates when behavior changes.

## Security & Configuration
- Never commit secrets. Use `.env.local` for overrides; document required vars in `.env.example`.
- Prefer read‑only operations by default; guard destructive actions behind explicit flags.

## Agent‑Specific Notes
- This AGENTS.md applies repo‑wide. If a nested `AGENTS.md` exists, it takes precedence within its directory.
