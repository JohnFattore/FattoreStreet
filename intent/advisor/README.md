# Advisor

## Why

The core audience is a long-term, index-minded investor, and many people who'd benefit from that
approach don't know where to start: which account to open, which broad fund to buy. A conversation
lowers that barrier more than a page of text. The advisor answers questions from a passive-investing
point of view, so a newcomer gets the Boglehead basics instead of the stock tips most free tools push.
(inferred)

It runs on Google Gemini through the main API (Django), because a hosted model costs nothing at
this volume and needs no hardware, which fits *Cheap to run* in [principles](../principles.md).
Conversations belong to a signed-in user. (inferred)

This branch has two parts:

- [Persona](persona.md): the outlook the advisor speaks from, and the line it doesn't cross.
- [Conversation history](conversation-history.md): what's remembered, and who can see it.

## Non-goals

- **Investment advice.** The advisor informs; it doesn't recommend specific trades. See the root's
  *Not investment advice*.
- **Answering from the user's holdings.** It doesn't read a user's portfolio. (inferred)
- **A self-hosted model in production.** Local models are for the author's experiments. (inferred)

## Open questions

- **Signed-out visitors.** Only signed-in users can talk to the advisor. Is that to protect the
  model's cost and rate limit, or should newcomers be able to try it without registering?
