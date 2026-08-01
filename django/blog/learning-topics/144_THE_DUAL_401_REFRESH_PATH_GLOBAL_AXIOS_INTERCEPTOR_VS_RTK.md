# The dual 401-refresh path — global axios interceptor vs. RTK Query baseQuery

_FattoreStreet @ [`00a2ff95`](https://github.com/JohnFattore/FattoreStreet/tree/00a2ff95654de93e01af01de3308dc6528ce3611) — 2026-07-26_

_Source: [#144](https://github.com/JohnFattore/FattoreStreet/issues/144)_

## Overview

FattoreStreet's frontend auth model issues short-lived SimpleJWT access tokens plus a longer-lived refresh token (`django/CLAUDE.md` / root `CLAUDE.md`), and the interesting engineering problem is *where* the "access token expired mid-request, refresh it and retry" logic lives. Nearly all API calls in this app go through RTK Query, but RTK Query's custom `axiosBaseQuery` (`react-app/src/functions/api/baseQuery.ts`) is a thin wrapper that just calls the global `axios` instance and turns any thrown error into an RTK Query `{ error }` result — it does **not** itself handle token refresh. Instead, a single `axios.interceptors.response.use` registered once in `App.tsx` intercepts every 401 response — whether it originated from an RTK Query hook or the raw axios calls in `Admin.tsx` — dispatches the `refreshLogin` RTK Query mutation to get a new access token, patches the original request's `Authorization` header, and replays it with plain `axios(originalRequest)`. This is a subtle but important pattern: it centralizes retry-on-401 in exactly one place instead of duplicating it into every RTK Query endpoint or every raw axios caller, at the cost of RTK Query and vanilla axios needing to agree on how errors surface.

## Files to read

- `react-app/src/App.tsx:38-73` — the interceptor itself: registered in a `useEffect` keyed on `[refresh, dispatch]` (so it's re-registered whenever the refresh token changes), ejected on cleanup via `axios.interceptors.response.eject(interceptorId)`
  - Line 46: the `isTokenError` regex (`/token.*(invalid|expired)/i`) matched against SimpleJWT's `detail` field — this is what distinguishes "access token merely expired, refresh and retry" from "refresh token itself is invalid, give up and log out"
  - Line 49: `originalRequest._retry` — the one-shot guard that stops an infinite retry loop if the replayed request also comes back 401
  - Lines 53-64: the actual refresh-and-replay — dispatching `djangoApi.endpoints.refreshLogin.initiate(refresh)`, awaiting `.unwrap()`, then calling plain `axios(originalRequest)` (not a Redux/RTK Query call) to replay
- `react-app/src/functions/api/baseQuery.ts` — the whole file (61 lines): notice it reads `access` straight from `api.getState()` on *every* call (line 34) rather than caching it, and that it has no retry/refresh logic of its own — it just forwards axios errors as `{ error: { status, data } }`
- `react-app/src/functions/api/djangoApi.ts:169-196` — the `login` and `refreshLogin` endpoint definitions; note `withAuth: false` on both (they can't send a bearer token when the whole point is to obtain one) and `login`'s `transformResponse` folding `arg.username` back into the response payload
- `react-app/src/reducers/userReducer.tsx` — the whole file (56 lines): `extraReducers` uses `addMatcher(djangoApi.endpoints.login.matchFulfilled | matchRejected, ...)` and the same for `refreshLogin` to update `access`/`refresh` in Redux state purely by pattern-matching on RTK Query's dispatched actions, with no explicit action creators for "set tokens" — `refreshLogin.matchRejected` clears both `access` and `refresh`, forcing a full re-login
- root `CLAUDE.md` "Authentication" section — the one-paragraph summary this issue expands on

## Questions to answer while reading

1. Why does the interceptor call plain `axios(originalRequest)` to replay the failed request instead of somehow re-triggering the original RTK Query hook/mutation that failed?
2. What would go wrong if the interceptor used `dispatch(djangoApi.endpoints.refreshLogin.initiate(refresh))` directly instead of also calling `.unwrap()` and manually setting `originalRequest.headers["Authorization"]` — i.e., why isn't updating Redux state via the matcher in `userReducer.tsx` enough on its own to fix the original request?
3. Two nearly-simultaneous requests both get a 401 at the same moment (e.g., a page that fires several RTK Query hooks in parallel). Walk through what happens to each — does the app end up calling `refreshLogin` more than once, and does that matter?
4. Why guard on `!isTokenError` (line 51) before attempting a refresh at all — what class of 401 is being deliberately excluded from the refresh-and-retry path, and where does that path in the code end up sending the user?
5. The `useEffect` re-runs whenever `refresh` changes (dependency array `[refresh, dispatch]`). Trace why the interceptor needs `refresh` in its closure at all, given it's dispatching an RTK Query action rather than reading `refresh` from Redux state directly inside the handler.

## Primer: interceptor-based token refresh vs. baseQuery-based token refresh

Two common patterns solve "refresh an expired access token and retry the failed request" in apps built on RTK Query. The first — used in many RTK Query tutorials — wraps the baseQuery itself in a function that inspects the result, and on a 401 dispatches a refresh action and retries, all within the query layer (see the RTK Query docs' "Automatic re-authorization" recipe). The second — used here — hooks into the HTTP client's interceptor layer beneath RTK Query, so a single retry mechanism covers *any* caller of that HTTP client, RTK Query included, without every RTK Query endpoint needing to opt in. The tradeoff: an interceptor-based approach is harder to unit test in isolation (it's wired to a global axios instance and depends on React lifecycle timing via `useEffect`) but avoids re-implementing the same logic if any code ever calls axios directly outside RTK Query (as `Admin.tsx` intentionally does, per root `CLAUDE.md`'s React State & API section).

## External references

- RTK Query docs, "Automatic Re-authorization by extending fetchBaseQuery": https://redux-toolkit.js.org/rtk-query/usage/customizing-queries#automatic-re-authorization-by-extending-fetchbasequery
- Axios docs, Interceptors: https://axios-http.com/docs/interceptors
- SimpleJWT docs on access/refresh token lifetimes and rotation: https://django-rest-framework-simplejwt.readthedocs.io/en/latest/rotate_refresh_tokens.html

## Exercise (optional)

In a local dev run, log in, then in the browser's Redux DevTools (or a quick console script) manually shrink the access token's expiry by editing `ACCESS_TOKEN_LIFETIME` in Django settings to a few seconds, hit an authenticated page, and watch the network tab: confirm you see the original request 401, a `token/refresh/` call, then the same request replayed with a new bearer token — all without the user seeing an error or being logged out.
