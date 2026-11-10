# Advisor

## Why

The core audience is a long-term, index-minded investor, and many people who'd benefit from that
approach don't know where to start: which account to open, which broad fund to buy. A conversation
lowers that barrier more than a page of text. The advisor answers questions from a passive-investing
point of view, so a newcomer gets the Boglehead basics instead of the stock tips most free tools push.

It runs on an open-weight model served by AWS Bedrock, called from the main API (Django). It
moved there from Google Gemini's free tier, which carried rate limits, a third-party dependency and
terms that let prompts be used for training. Bedrock bills per token and nothing runs between
questions, so at this volume it costs a few dollars a month and fits *Cheap to run* in
[principles](../principles.md); running a GPU of our own would cost hundreds a month, and a CPU
model on the existing host is too weak to answer well. Bedrock authenticates with the host's own
AWS identity, so there's no API key to store or leak, and its spend shows up in the same AWS bill.
It doesn't use prompts for training, which matters once the advisor sees a user's holdings. One API
serves many models, so the model can be changed, or several compared on the same questions, without
changing code.
Conversations belong to a signed-in user.

This branch has two parts:

- [Persona](persona.md): the outlook the advisor speaks from, and the line it doesn't cross.
- [Conversation history](conversation-history.md): what's remembered, and who can see it.

## Requirements

### Only signed-in users can use the advisor
Every question costs a hosted-model call, and an open chat box on a public site invites abuse that
could run up a per-token bill. Requiring an account caps that exposure.

### Spend has a hard ceiling
Accounts are free to create, so sign-in alone doesn't bound the bill. Each user gets a daily
question limit, and once the month's tokens across all users reach a set cap, the advisor stops
calling the model and says it's back next month. An AWS Budgets action that cuts off the host's
model access is the backstop for anything the app doesn't count. Running out early is the
accepted trade: a paused advisor costs nothing, a runaway one could cost hundreds a day.

## Non-goals

- **Investment advice.** The advisor informs; it doesn't recommend specific trades. See the root's
  *Not investment advice*.
- **Answering from the user's holdings, for now.** It doesn't read a user's portfolio today.
  Answering with their holdings in view is wanted eventually; deferred, not ruled out.

## Open questions

- **Which open-weight model?** Llama 3.3 70B is the starting choice, not yet compared against
  others (Qwen, Mistral, gpt-oss) on a fixed set of advisor questions: persona, refusing specific
  trades, account basics and the reference's fund examples.
- **Stream answers?** Streaming would show the reply as it's written and free the server sooner,
  but the page would have to render a partial answer. Today the whole answer arrives at once.
