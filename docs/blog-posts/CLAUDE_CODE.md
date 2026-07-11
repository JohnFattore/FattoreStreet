What is Claude Code?
Claude Code is a command-line tool from Anthropic that puts its models to work on software development tasks. Rather than being just a chat window, it's an orchestration layer on top of the model: it pulls in relevant context from your project, gives the model tools (reading/writing files, running shell commands, searching your codebase, using git), and can spin up autonomous subagents to work on sub-tasks in isolation. In practice, it can read your codebase for context, write and edit code directly, run tests or builds, and iterate on the results in a loop until the task is done. It and tools like it are a massive breakthrough that are rapidly changing software development. Many organizations rely on these tools to automate away manually writing code. 

Compared to other tools (Cursor, Codex, Windsurf)
I prefer a CLI-based tool over an IDE with AI baked in. IDEs that fuse "text editor" and "AI agent" into one product mix concerns. 
I'd rather have:
An editor that's just good at editing (vanilla VS Code, kept up to date with whatever plugins are current)
A separate tool that's good at driving an AI coding agent (Claude Code, in a terminal alongside)
This also avoids the "forked IDE" problem. Cursor and Windsurf are forks of VS Code, which means they lag upstream and I have to trust their fork to stay current and secure. With vanilla VS Code + Claude Code, I get the real thing plus whatever agent I want, decoupled.d

Context
Context is the whole game. Like any chatbot conversation, the more irrelevant stuff sitting in the context window, the worse and more expensive the responses get. A bloated context raises token costs and degrade the overall experience. Keep sessions focused, and clear/reset context often rather than letting one session sprawl across unrelated work. A rule of thumb, one session per task and air on the side of clearing context. A practical example is to clear session context before reviewing a new feature, so the same agent isn't just confirming its own creations.

CLAUDE.md
The CLAUDE.md file is loaded into context automatically, every session. This is your always-on project memory and can include things such as build commands, directory structure, conventions, team norms. Because it's paid for on every single task whether it's relevant or not, the guidance is to keep it short (rule of thumb: under 200 lines), treat it like reviewed code, and use it for facts, not procedures.

Rules
Effectively an extension of CLAUDE.md, but scoped. A rule can be tied to specific file paths (e.g., "migrations are append-only" only applies when touching migration files). This lets you keep hard constraints out of context except when they're actually relevant, rather than paying for them globally. Keep these short too.

Skills
Skills are packaged, reusable know-how. They are structures as folders containing a SKILL.md file (name + description + instructions, plus optional scripts/templates/reference docs). They can be shared across a team, iterated on, and version-controlled, and they help Claude solve recurring problems the same way every time instead of reinventing an approach each session. The modular design makes them my favorite context injector to work on. Already there are loads of fantastic community build skills you can download that are applicable to all sorts of applications. 

Skills are lighter weight than rules; only the skill's name and description load into context at session start (cheap — roughly a hundred tokens). The full body of instructions only loads when the skill is actually invoked; either explicitly (a slash command) or automatically, when Claude matches your request against the description. This makes skills the better home for conditional context: procedures, checklists, and workflows that are only useful sometimes, as opposed to CLAUDE.md, which is for things that should always be present.

One practical note from how people actually use skills successfully: keep each one narrow (one job per skill) and make the description read like a trigger condition ("use when the user asks to X") rather than vague documentation. The description is literally what Claude pattern-matches against to decide whether to load it. A popular example is a PR review skill.