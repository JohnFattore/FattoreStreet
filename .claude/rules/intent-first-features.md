# Intent First for New Features

When the user requests a new feature, or a change to what users see or what the app must do, invoke the `intent` skill before writing any code. Do this automatically as part of your normal workflow -- do NOT ask the user for permission to run it.

The intent tree in `intent/` is the source of truth and the code is derived from it, so a new feature changes intent first and the code follows.

## When to Invoke

| Request | Intent mode |
|---|---|
| New feature, page, endpoint, data source, or scheduled job | Sync (intent changed): edit or add the requirement first, then implement |
| Feature in an area with no matching node | Grow the node, then Sync |
| Removing a feature or changing user-visible behavior | Sync: update or remove the affected requirement first |
| Change that would contradict an existing requirement or principle | Stop and tell the user which requirement it breaks before proceeding |

## How to Apply

- Call the `intent` skill via the Skill tool, naming the feature and the area it touches
- Write the intent change before the code change, in the same branch, so both land together
- Mark anything you had to guess as `(inferred)` or record it as an open question; don't invent the owner's reasons
- Run the skill's validator before moving on to the implementation

## Skip When

- The change is a bug fix that restores behavior a requirement already describes (the requirement stands; fix the code)
- The change is a pure refactor, test, styling, dependency, or tooling change with no effect on behavior
- The change is documentation-only, or the user is already working in the `intent` skill
- The user explicitly says to skip intent for this change
