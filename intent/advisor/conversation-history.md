# Conversation History

## Why

A conversation where the [advisor](README.md) forgets the last question is frustrating and gives
worse answers. But questions about money are personal, so whatever is remembered must stay with
the user who asked it.

## Requirements

### The advisor remembers a user's recent conversation
Follow-up questions only make sense with context. Each answer takes the user's most recent
exchanges into account.

### A user can see their past conversation when they return
Coming back to a blank page loses whatever the user learned last time. Their earlier questions and
answers are shown again, oldest first.

### Conversations are private to the user who had them
Applies [data ownership](../accounts/data-ownership.md). No other user, and no signed-out visitor,
can read them.

### Conversations expire, and a user can delete theirs sooner
Money questions are personal, and a record kept forever is a liability with no benefit once the
conversation is over. Conversations are deleted automatically 90 days after they happen, and a user
can clear their own history at any time. Today they're kept forever, which has to change.

## Non-goals

- **A notice about the model provider.** Questions are sent to a hosted model, and users aren't told
  so beyond the page saying the advisor is automated.
