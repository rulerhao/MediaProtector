---
name: commit
description: Create a well-formatted git commit with conventional commit style
disable-model-invocation: true
allowed-tools:
  - Bash
---

Create a git commit for MediaProtector changes.

## Commit Message Format

```
<type>: <short description>

<optional body - bullet points of changes>

🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
```

## Types

- `feat`: New feature
- `fix`: Bug fix
- `chore`: Maintenance (deps, config, cleanup)
- `refactor`: Code restructuring
- `docs`: Documentation only
- `style`: Formatting, no code change
- `perf`: Performance improvement

## Instructions

1. Run `git status` to see changes
2. Run `git diff --stat` to summarize changes
3. Run `git log --oneline -3` to see recent commit style
4. Stage relevant files with `git add`
5. Create commit with HEREDOC format:

```bash
git commit -m "$(cat <<'EOF'
type: short description

- Change 1
- Change 2

🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

## Rules

- Do NOT commit `.claude/settings.local.json`
- Do NOT push unless explicitly asked
- Keep description under 72 characters
