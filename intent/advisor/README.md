# Advisor

## Why

The core audience is a long-term, index-minded investor, and many people who'd benefit from that
approach don't know where to start: which account to open, which broad fund to buy. A conversation
lowers that barrier more than a page of text. The advisor answers questions from a passive-investing
point of view, so a newcomer gets the Boglehead basics instead of the stock tips most free tools push.

It runs on Google Gemini through the main API (Django), because a hosted model costs nothing at
this volume and needs no hardware, which fits *Cheap to run* in [principles](../principles.md).
Gemini is the current choice, not the final one: the advisor is meant to move to a self-hosted
open-source model eventually, so it stops depending on a third party's free tier. Cost decides
when.
Conversations belong to a signed-in user.

This branch has two parts:

- [Persona](persona.md): the outlook the advisor speaks from, and the line it doesn't cross.
- [Conversation history](conversation-history.md): what's remembered, and who can see it.

## Requirements

### Only signed-in users can use the advisor
Every question costs a hosted-model call, and an open chat box on a public site invites abuse that
could exhaust the free tier or run up a bill. Requiring an account caps that exposure.

## Non-goals

- **Investment advice.** The advisor informs; it doesn't recommend specific trades. See the root's
  *Not investment advice*.
- **Answering from the user's holdings, for now.** It doesn't read a user's portfolio today.
  Answering with their holdings in view is wanted eventually; deferred, not ruled out.
