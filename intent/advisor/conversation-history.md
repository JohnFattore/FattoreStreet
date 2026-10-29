# Conversation History

## Why

A conversation where the [advisor](README.md) forgets the last question is frustrating and gives
worse answers. But questions about money are personal, so whatever is remembered must stay with
the user who asked it.

One endless thread doesn't work either. Unrelated topics pile up, an old question colours the
answer to a new one, and a returning user can't find what they asked last month. So a user's
history is split into separate conversations, the way people already expect from a chat product.

## Requirements

### A user's history is a set of separate conversations
A user can start a new conversation at any time, and switch back to an earlier one to read it or
carry on. Without this, every topic lands in one thread and old context leaks into new questions.

### The advisor remembers only the current conversation
Follow-up questions only make sense with context, so each answer takes the earlier exchanges of the
same conversation into account. Nothing from the user's other conversations is used: a new
conversation starts fresh, which is what "new" promises.

### A user can see their past conversations when they return
Coming back to a blank page loses whatever the user learned last time. Their conversations are
listed most recently active first, and opening one shows its questions and answers oldest first.

### Each conversation has a title the user can change
A list of conversations is useless if they can't be told apart. A conversation is titled from the
start of its first question, at no extra model cost, and the user can rename it.

### Conversations are private to the user who had them
Applies [data ownership](../accounts/data-ownership.md). No other user, and no signed-out visitor,
can read, rename or delete them.

### Conversations expire, and a user can delete theirs sooner
Money questions are personal, and a record kept forever is a liability with no benefit once the
conversation is over. A conversation is deleted automatically once 90 days have passed since its
last message, so a conversation still in use never loses its beginning. A user can delete any of
their conversations at any time. Today nothing expires automatically, which has to change.

## Non-goals

- **A notice about the model provider.** Questions are sent to a hosted model, and users aren't told
  so beyond the page saying the advisor is automated.
- **Keeping history from before conversations existed.** The single thread each user had was
  dropped when conversations arrived, rather than carried over.
