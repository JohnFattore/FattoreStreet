# What is Claude Code?

Claude Code is a command-line tool from Anthropic that puts its models to work on software development tasks. Rather than being just a chat window, it's an orchestration layer on top of the model. It pulls relevant context from your project, gives the model tools (reading and writing files, running shell commands, searching the codebase, using git), and can spin up autonomous subagents to work on sub-tasks in isolation. In practice, it reads your codebase for context, writes and edits code directly, runs tests or builds, and iterates on the results in a loop until the task is done. The closest comparison is OpenAI's Codex, which follows the same terminal-based agent pattern with their models. Tools like this are genuinely changing how software gets built. Many organizations now lean on coding agents for a large share of the code they ship, shifting the developer's job from writing every line by hand to directing and reviewing the work.

## Compared to other tools (Cursor, Windsurf)

I prefer a CLI-based tool over an integrated development environment (IDE) with AI baked in. IDEs that fuse "text editor" and "AI agent" into one product mix concerns. I'd rather have an editor that's just good at editing (vanilla VS Code, kept up to date with whatever plugins are current) and a separate tool that's good at driving an AI coding agent (Claude Code, in a terminal alongside). This also avoids the "forked IDE" problem. Cursor and Windsurf are forks of VS Code, which means they lag upstream and I have to trust their fork to stay current and secure. With vanilla VS Code plus Claude Code, I get the real thing plus whatever agent I want, decoupled.

## Context

Context is the whole game. Like any chatbot conversation, the more irrelevant stuff sitting in the context window, the worse and more expensive the responses get. Keep sessions focused and clear context often rather than letting one session sprawl across unrelated work. A good rule of thumb is one session per task, erring on the side of clearing. Idle sessions cost you too; coming back after an hour forces the whole context to be reloaded at full token price, so it is usually better to start fresh unless the current context is truly important. Clear session context before reviewing a new feature, so the same agent isn't just confirming its own creations.

## CLAUDE.md

The CLAUDE.md file is loaded into context automatically, every session. This is your always-on project memory: build commands, directory structure, conventions, team norms. Because it's paid for on every single task whether it's relevant or not, keep it short (rule of thumb: under 200 lines), treat it like reviewed code, and use it for facts, not procedures. You don't have to write it by hand either; the /init slash command has Claude explore the codebase and generate one for you, which makes a solid starting point to trim and refine. It also needs to stay up to date. A stale CLAUDE.md actively misleads the model on every task, so update it whenever commands, structure, or conventions change.

## Rules

Rules are effectively an extension of CLAUDE.md, but scoped. A rule can be tied to specific file paths, so a constraint like "migrations are append-only" only loads when migration files are being touched. This keeps hard constraints out of context except when they're actually relevant, rather than paying for them globally. Keep these short too.

## Skills

Skills are packaged, reusable know-how. A skill is a folder containing a SKILL.md file (name, description, and instructions, plus optional scripts, templates, and reference docs). Skills can be shared across a team, iterated on, and version-controlled, and they help Claude solve recurring problems the same way every time instead of reinventing an approach each session. The modular design makes skills my favorite way to feed Claude context, and there's a growing ecosystem of community-built skills you can drop into any project.

Skills load lazily; only the name and description load into context at session start, costing roughly a hundred tokens. The full body of instructions loads only when the skill is actually invoked, either explicitly as a slash command or automatically when Claude matches your request against the description. Where rules trigger on file paths, skills trigger on intent, which makes them the better home for procedures, checklists, and workflows that are only useful sometimes. Because the description is literally what Claude pattern-matches against, keep each skill narrow (one job per skill) and make the description read like a trigger condition ("use when the user asks to X") rather than vague documentation. A popular example is a PR review skill.

## Putting it together

The three mechanisms form a hierarchy of context cost. CLAUDE.md is always loaded, so it holds only what every session needs. Rules load when matching files are touched, so they hold path-specific constraints. Skills load on demand, so they hold procedures and workflows. Specificity follows the same hierarchy. CLAUDE.md needs to be ruthlessly specific because every line taxes every task, rules get a little more room since they only load when relevant, and skills get the most, free to spell out full procedures that cost nothing until invoked. Put each piece of guidance at the cheapest level that still gets it in front of the model when it matters.
