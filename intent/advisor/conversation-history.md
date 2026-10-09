# Conversation History

## Why

A conversation where the [advisor](README.md) forgets the last question is frustrating and gives
worse answers. But questions about money are personal, so whatever is remembered must stay with
the user who asked it.

## Requirements

### The advisor remembers a user's recent conversation
Follow-up questions only make sense with context. Each answer takes the user's most recent
exchanges into account. (inferred)

### A user can see their past conversation when they return
Coming back to a blank page loses whatever the user learned last time. Their earlier questions and
answers are shown again, oldest first. (inferred)

### Conversations are private to the user who had them
Applies [data ownership](../accounts/data-ownership.md). No other user, and no signed-out visitor,
can read them. (inferred)

## Open questions

- **Retention.** Conversations are kept forever. Should they expire, or be deletable by the user?
- **What the model provider keeps.** Every question is sent to the hosted model. Should users be
  told that, and does the provider's data policy fit a public site?
