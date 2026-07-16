# FattoreStreet Blog Voice Guide

Distilled from the published posts in `docs/blog-posts/` (PILOT, INDEX_FUNDS_101, ETFS_VS_MUTUAL_FUNDS, JSON_WEB_TOKEN, DJANGO_MTV, REDUX). When calibration beyond this guide is needed, read one or two of those posts directly — `ETFS_VS_MUTUAL_FUNDS.md` and `JSON_WEB_TOKEN.md` are the strongest exemplars.

## Structure

- One `#` title, then `##` section headers. Posts run roughly 300–700 words.
- Each section is a single tight paragraph (3–8 sentences). Prose over bullets; numbered lists only for enumerating criteria (e.g., "What makes a good index?").
- Openings are personal and situational: what was built or worked on lately, often with a bare link to the live feature ("Check out an example of the financial data from this server here: https://fattorestreet.com/asset/AAPL").
- Question-style headers are common and welcome: "What is a JWT?", "Which should you choose", "Why mutual funds distribute capital gains".
- Posts often close with a practical takeaway or a punchy one-liner ("The best choice is whichever one keeps you invested.").
- Educational arc: define the thing → explain the mechanics → practical usage/verdict.

## Sentence-level voice

- First person singular, active voice, declarative. Confident verdicts stated flat out: "The Dow is not a good index."
- The author prefers slightly shorter sentences. When a sentence carries two ideas joined by a semicolon or a trailing "since"/"which" clause, split it in two. Err on the side of splitting.
- Terms are defined inline, immediately after first use, in plain language: "…exactly net asset value (NAV). Net asset value is the underlying value of all the assets the fund owns…"
- Acronyms expanded with a parenthetical on first use: "exchange traded fund (ETF)", "authorized participants (APs)", "Object-Relational Mapper (ORM)".
- Abstract claims grounded with concrete numbers and named examples: Nvidia's ~$4.47T market cap vs Netflix, Tesla's five-month S&P 500 delay, $10,000/year on a $1M portfolio at 1%.
- Mechanics explained as a plain walkthrough: "The mechanic works like this: an AP delivers a large cash injection…, the fund then hands back its most appreciated stocks…, and the AP exits the position."
- About one colloquial flourish per post — keep them, they are the voice, not errors: "just buy the whole haystack", "champagne popping", "splitting hairs", "Low cost is king", "incredibly dangerous tools for rapid development".
- No emoji. No exclamation marks. No rhetorical hype ("game-changing", "revolutionary") — enthusiasm shows through concrete wins instead ("a massive technical win").
- The author never uses em dashes (—). Where an em dash might be tempting, use a comma, parentheses, or a new sentence instead.
- The author avoids mid-sentence colons. Constructions like "The rule of thumb: ..." or "there's nothing to configure: ..." are not his style. Write it as a period and a new sentence, or restructure the sentence so no joint is needed. The only acceptable colon is one introducing a code block or a numbered list.
- Code appears sparingly and only when it teaches (the Django post's three short snippets); financial posts use none.

## What editing should NOT do

- Never add an em dash (—). If the draft contains one, replace it. This is a hard rule.
- Never add a mid-sentence colon. This is a hard rule, same tier as the em dash rule. If the draft contains one, rewrite it as two sentences or restructure. Colons are only allowed before a code block or numbered list.
- Do not bulletize prose paragraphs or add tables.
- Do not add corporate polish, hedging ("it's worth noting", "arguably"), or filler transitions ("Moreover", "Furthermore").
- Do not remove casual phrasing or personality; fix only genuine typos, grammar errors, double spaces, and trailing whitespace.
- Do not change factual claims, numbers, or dates. If one looks wrong, leave it and flag it to the author instead.
- Do not pad. If a section says what it needs to in four sentences, leave it at four sentences.
