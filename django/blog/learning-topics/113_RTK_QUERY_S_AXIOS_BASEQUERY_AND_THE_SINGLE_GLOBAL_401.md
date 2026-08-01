# RTK Query's axios baseQuery and the single global 401-refresh interceptor

_FattoreStreet @ [`f337c3fe`](https://github.com/JohnFattore/FattoreStreet/tree/f337c3fef734f52b9a5a4a696997a3960944a332) — 2026-07-19_

_Source: [#113](https://github.com/JohnFattore/FattoreStreet/issues/113)_

## Overview

FattoreStreet's frontend doesn't use RTK Query's built-in `fetchBaseQuery` — it wraps global `axios` in a custom `BaseQueryFn` (`axiosBaseQuery`) that pulls the JWT out of Redux state on every call. Token *expiry* handling, though, doesn't live inside that base query at all: it's a single `axios.interceptors.response.use` registered once at the top of `App.tsx`, which fires for *any* failed axios call in the app — RTK Query requests (since RTK Query's base query calls the same global `axios` instance) and the raw axios calls in `Admin.tsx` alike. This is a distinctive design choice: instead of duplicating 401-refresh logic in two places (a custom RTK Query `baseQuery` wrapper for RTK Query, and something separate for Admin.tsx's manual axios calls), the app centralizes it once at the transport layer that both paths share. It's worth understanding closely because "share one interceptor across two different data-fetching mechanisms" trades some subtlety (a mutable `_retry` flag on the axios request config, an RTK Query mutation `dispatch`ed *from outside* a component, careful error-detail sniffing to avoid infinite refresh loops) for avoiding real duplication — a pattern worth having in your toolkit whenever an app mixes libraries around one HTTP client.

## Files to read

- `react-app/src/functions/api/baseQuery.ts` — the whole file (56 lines). Focus on:
  - Lines 21-32: `axiosBaseQuery` reads `state.user.access` fresh from Redux on *every* call via `api.getState()` — not captured once at setup — so a token refreshed by the interceptor is picked up on the next RTK Query call automatically
  - Lines 34-41: the raw `axios(...)` call — no special headers or retry logic here; this base query does not itself know how to refresh
  - Lines 44-52: on failure it returns `{ error: { status, data } }` rather than throwing, which is how RTK Query expects `BaseQueryFn` implementations to report failures
- `react-app/src/App.tsx` lines 38-73 — the interceptor, registered in a `useEffect` keyed on `[refresh, dispatch]`:
  - Line 42-46: reads `error.config` as `originalRequest` and regex-tests `error.response?.data?.detail` against `/token.*(invalid|expired)/i` to distinguish "access token merely expired, refresh it" from "refresh token itself is dead, log out" — SimpleJWT returns similar-looking 401s for both cases, so this string-matching is the only signal available
  - Lines 47-64: the refresh branch — guarded by `!originalRequest._retry` (a one-shot flag mutated onto the axios config to prevent infinite retry loops), `refresh` (must have a refresh token in Redux), and `!isTokenError` (this must not be the "refresh token is bad" case). It dispatches `djangoApi.endpoints.refreshLogin.initiate(refresh)` directly (not via a React hook, since this runs outside a component), `unwrap()`s it, patches the original request's `Authorization` header, and replays it with `axios(originalRequest)`
  - Line 62: `refreshRequest.reset()` in a `finally` — cleans up the RTK Query cache entry for this manually-dispatched mutation so it doesn't linger
  - Lines 65-67: the logout branch — a confirmed-dead refresh token dispatches `logout()` unconditionally
  - Line 72: `axios.interceptors.response.eject(interceptorId)` cleanup — re-registered whenever `refresh` changes, so the interceptor closure always closes over a current token, not a stale one from first mount
- `react-app/src/reducers/userReducer.tsx` — the whole file (53 lines):
  - Lines 31-47: `extraReducers` uses `addMatcher` against `djangoApi.endpoints.login.matchFulfilled/matchRejected` and `refreshLogin.matchFulfilled/matchRejected` — Redux state is updated by pattern-matching on RTK Query action types, not by the interceptor or a component dispatching a plain action
  - Line 44-46: `refreshLogin.matchRejected` clears *both* `access` and `refresh` — this is what actually forces a re-login if the refresh call itself 401s outside the interceptor's own retry path (e.g., if something else triggers a refresh)
- `react-app/src/functions/api/djangoApi.ts` lines 160-187 — `login` and `refreshLogin` mutation definitions: note `withAuth: false` on both (line 168, 185), since neither should send a maybe-stale `Authorization` header
- `.claude/rules/react-typescript.md` "State & Data Fetching" section and CLAUDE.md's "React State & API" bullet on the 401 interceptor, for the one-line summary this issue expands on

## Questions to work through while reading

1. `axiosBaseQuery` and the `App.tsx` interceptor are two separate pieces of code that both touch auth, but neither directly calls the other. Trace exactly how they cooperate: when an RTK Query hook triggers a request with an expired access token, which function's `axios(...)` call actually throws, where does that error surface, and how does the *retried* request end up using the new token given that `axiosBaseQuery` re-reads `state.user.access` from Redux each time?
2. Why is `originalRequest._retry` needed at all? Construct the failure scenario: what would happen on a repeatedly-401ing endpoint (e.g. a genuinely revoked access token that a refresh keeps "fixing" only for the retried call to fail again) if that flag were removed?
3. The interceptor is registered in a `useEffect` with dependency array `[refresh, dispatch]`, meaning it's torn down and re-registered every time `refresh` changes (e.g., right after a successful refresh sets a new access token but leaves `refresh` unchanged — versus after a fresh `login`, which changes both). Is there a window between the old interceptor being ejected and the new one registering where an in-flight request could fail without being retried? Does it matter given axios interceptors are synchronous registrations inside a synchronous `useEffect` body?
4. `refreshRequest.reset()` runs in a `finally` block after `unwrap()`, whether the refresh succeeded or threw. What RTK Query cache state is `reset()` cleaning up, and what would you observe (e.g., in Redux DevTools or repeated calls) if that line were deleted — would refreshes still work, just with some side effect?
5. `Admin.tsx` is called out in CLAUDE.md as the one place using raw axios instead of RTK Query, yet its calls still go through the *same* global 401-refresh interceptor as RTK Query traffic. What specifically makes that possible (hint: look at what object `axios.interceptors.response.use` is attached to, versus what `axiosBaseQuery` and Admin.tsx's calls both use to make requests) — and what would break if Admin.tsx instead created its own `axios.create()` instance?

## Primer: axios interceptors vs. RTK Query base queries, and where 401-refresh logic belongs

RTK Query's `baseQuery` is a per-request adapter: it's the function RTK Query calls for every query/mutation to actually perform the HTTP request and normalize the result into `{ data }` or `{ error }`. It has no built-in concept of "retry this specific failure type" — that logic is usually added either (a) by wrapping the base query itself (RTK Query's docs show a `baseQueryWithReauth` pattern that catches a 401, dispatches a refresh, and retries — the "obvious" place to put this), or (b) by intercepting at the underlying HTTP client, if the base query is a thin wrapper over one (as `axiosBaseQuery` is over `axios`). This app chose (b): a *global axios response interceptor*, registered once, sees every failed response regardless of whether it originated from RTK Query's base query or a hand-written axios call elsewhere in the app (like `Admin.tsx`). The key mechanical fact that makes this work is that axios interceptors are a property of an axios *instance* (here, the default global instance, since neither `axiosBaseQuery` nor `Admin.tsx` calls `axios.create()`) — anyone using that same instance automatically inherits every interceptor registered on it, without needing to know the interceptor exists. The tradeoff versus option (a): a single shared interceptor avoids duplicating retry logic across every consumer, but it also means auth-refresh behavior isn't visible from a request's call site — you have to know to go look at `App.tsx` to find it, and it fires for *all* HTTP traffic in the app, whether that's desired for every future caller or not.

## External references

- Axios interceptors: https://axios-http.com/docs/interceptors
- RTK Query "Customizing Queries" — `baseQuery` and the re-authentication pattern this app deliberately did *not* use, useful as a point of comparison: https://redux-toolkit.js.org/rtk-query/usage/customizing-queries#automatic-re-authorization-by-extending-fetchbasequery

## Exercise (optional)

In a local dev run, log in, then use browser devtools to manually edit the Redux `user.access` token (via Redux DevTools' state editor, or by dispatching an action) to an invalid string, leaving `refresh` valid. Trigger any RTK Query-backed page load and watch the network tab: confirm you see the original request 401, then a `POST .../token/refresh/`, then the original request replayed with a new `Authorization` header — all without a page reload or the user noticing. Then repeat with `refresh` also cleared/invalidated and confirm you get bounced to a logged-out state instead.
