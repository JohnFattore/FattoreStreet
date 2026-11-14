# react-markdown's AST pipeline vs. dangerouslySetInnerHTML, in BlogPost and ChatbotOutput

_FattoreStreet @ [`978d9049`](https://github.com/JohnFattore/FattoreStreet/tree/978d9049ec5892b06e417df4edaec38e922fb32a) — 2026-11-11_

_Source: [#357](https://github.com/JohnFattore/FattoreStreet/issues/357)_

Rendering untrusted-ish text as Markdown safely means avoiding the obvious shortcut: convert it to an HTML string and hand that string to `dangerouslySetInnerHTML`. That shortcut works, but it means any stray `<script>` or `<img onerror=...>` that ends up in the Markdown source gets injected into the DOM verbatim — the browser never distinguishes "HTML the app wrote" from "HTML that arrived in the data." The safer approach parses Markdown into a syntax tree and lets the rendering framework turn that tree into real elements through its own escaping rules, never through raw HTML injection. React's ecosystem does this with `react-markdown`, built on the `unified`/`remark` toolchain: text in, an AST (abstract syntax tree) in the middle, React elements out. No string of HTML ever exists to inject.

FattoreStreet uses exactly this pipeline in two unrelated places, with the same three packages and nearly the same plugin list.

**Blog posts** (`react-app/src/pages/BlogPost.tsx:3-5, 48-59`) render `body_markdown`, a field that ships as plain Markdown files in `django/blog/journal/` and `django/blog/learning-topics/` and is synced verbatim into Postgres by `sync_blog_posts` (see topic #181) — so what reaches the browser is exactly what's committed to the repo, with no server-side HTML transformation in between:

```tsx
<Markdown
  remarkPlugins={[remarkGfm, remarkBreaks]}
  components={{
    a: ({ href, title, children }) => (
      <a href={href} title={title} target="_blank" rel="noreferrer">
        {children}
      </a>
    ),
  }}
>
  {data.body_markdown || ""}
</Markdown>
```

**Chatbot answers** (`react-app/src/components/ChatbotOutput.tsx:9-11, 57-60`) render the Boglehead advisor's model-generated replies the same way, swapping only which messages get the Markdown treatment (`msg.role === "model"`) versus plain text (the user's own messages, left as a bare `<div>`):

```tsx
<Markdown remarkPlugins={[remarkGfm, remarkBreaks]}>
  {msg.text}
</Markdown>
```

Here the safety property matters more directly than for the blog: the chatbot's input ultimately comes from a hosted LLM (AWS Bedrock, via the Converse API), and nothing guarantees that an LLM's free-text output never contains something that looks like a tag. Because `react-markdown` never builds an HTML string, there's no injection point to worry about — the worst a stray `<script>foo</script>` in a reply can do is render as the literal text `<script>foo</script>`.

Both call sites pass the same two remark plugins, each tweaking one step of the `unified` pipeline before it reaches the AST-to-React stage:

- `remark-gfm` adds GitHub-Flavored Markdown syntax — tables, strikethrough, autolinked URLs, and task-list checkboxes — none of which plain CommonMark parses.
- `remark-breaks` turns a single newline into a hard line break (`<br>`) instead of requiring a blank line between paragraphs, which matters for chat replies and journal-style posts where authors (human or model) write short lines without blank-line separators.

The blog's version layers one more thing on top: a `components` override that intercepts every rendered `<a>` tag to force `target="_blank" rel="noreferrer"`, so external links in post bodies open in a new tab without the new page getting a `window.opener` reference back — a small, standard mitigation for reverse-tabnabbing that's easy to add because `react-markdown` renders real React elements you can intercept, not an opaque HTML blob.

**Takeaway**: when a page needs to display Markdown — whether it's trusted repo content or free text from an external model — parsing it to a syntax tree and letting the framework render that tree is a strictly safer default than building an HTML string and injecting it, and it costs nothing extra once the pipeline is in place.

Docs: https://github.com/remarkjs/react-markdown

