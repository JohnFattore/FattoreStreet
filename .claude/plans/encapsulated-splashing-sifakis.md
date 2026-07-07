# Migrate legacy createAsyncThunk/axios code to RTK Query

## Context

Half the React app uses the modern RTK Query setup (`src/functions/api/`), the other half still uses `createAsyncThunk` thunks in `src/functions/axiosFunctions.tsx` backed by per-entity slices in `src/reducers/` (auth, restaurants, reviews, chatbot, quote). This migration moves all remaining API calls onto RTK Query, deletes the thunk file and the data-fetching slices, and leaves only genuine client-state slices (`user`, `location`, `watchList`, `adminSuccessBar`). The repo convention (`.cursor/rules/react-typescript.mdc`) already mandates RTK Query for all API calls; `Admin.tsx`'s raw axios remains the one intentional exception.

Per user decision: the currently-dead restaurant-recommendation feature (`RestaurantRecommend*`, `LocationForm`, commented out in `Restaurants.tsx`) is **migrated too but stays dormant** — zero legacy code remains, feature stays commented out.

## Key design decisions

1. **Auth**: `login`, `refreshLogin`, `postUser` become `djangoApi` mutations (`withAuth: false`). The persisted `user` slice stays but is slimmed to `{username, access, refresh, darkMode}`; it captures tokens via `extraReducers` **matchers** (`djangoApi.endpoints.login.matchFulfilled` → set tokens+username from `action.meta.arg.originalArgs`; `refreshLogin.matchFulfilled` → set access; `refreshLogin.matchRejected` → clear access/refresh). `loading`/`error`/`clearErrors`/`setUserError` are removed — forms use mutation-hook `isLoading`/`error`. Import chain `userReducer → djangoApi → baseQuery` has no cycle (baseQuery imports `RootState` type-only).
2. **401 refresh**: keep the single global axios interceptor in `App.tsx:43-71` as the *only* reauth mechanism (it already covers RTK Query calls — baseQuery uses the global axios instance — plus Admin.tsx raw axios). Swap `dispatch(refreshLogin()).unwrap()` for `dispatch(djangoApi.endpoints.refreshLogin.initiate(refresh))` + `.unwrap()`, calling `.reset()` on the subscription in a `finally` to avoid mutation-cache leaks. Keep the `_retry` guard and the `isTokenError → logout()` branch. Do **not** add a `baseQueryWithReauth` wrapper — two mechanisms would double-refresh.
3. **Sort state**: delete the sorted-array-in-store pattern (`setReviewSort`/`setRestaurantSort`/`setRestaurantRecommendSort`). Tables move to `src/components/SortableTable.tsx` (repo rule mandates it; it owns sort locally and wraps StateHandler).
4. **Chatbot optimistic update**: `postChatbot` mutation does **not** invalidate; `onQueryStarted` pushes the user message into the `getChatbot` cache via `updateQueryData` (undo on failure), then pushes the model reply on success — preserving today's optimistic behavior.
5. **`getQuote` already exists** on djangoApi (`djangoApi.ts:335`) — WatchListForm just switches to `useLazyGetQuoteQuery`; the plain axios fn is deleted.

## New djangoApi endpoints

Add tagTypes `"Restaurants" | "RestaurantRecommendations" | "Reviews" | "Chatbot"` alongside existing `["Assets","Accounts"]`. Local `IRaw*` interfaces in `djangoApi.ts` (existing idiom); camelCase-output interfaces `IReview`/`IRestaurant`/`IChatMessage` stay in `interfaces.ts` as-is.

| Endpoint | Kind | Args | Notes |
|---|---|---|---|
| `login` | mutation | `{username, password}` | POST `users/api/token/`, `withAuth: false` |
| `refreshLogin` | mutation | `string` (refresh token) | POST `users/api/token/refresh/`, `withAuth: false` |
| `postUser` | mutation | `{username, email, password}` | POST `users/api/users/`, `withAuth: false` |
| `getRestaurants` | query | `{state, city}` | GET `restaurants/api/restaurant-list-create/`, `withAuth: false` (public page; avoids expired-token 401 tripping refresh), `params`, transformResponse raw→`IRestaurant[]`, providesTags Restaurants/LIST. Callers pass location from `useSelector(state.location)` — endpoints can't read state. |
| `getRestaurantRecommendations` | query | void | Bearer, providesTags RestaurantRecommendations/LIST; consumed lazily |
| `getReviews` | query | void | Bearer, transformResponse flattens `restaurant_detail` (with `Number()` coercions incl. latitude/longitude), providesTags Reviews/LIST |
| `postReview` | mutation | `{restaurant, rating, comment}` | keeps `user: 1` on the wire (backend contract; TODO comment), invalidates Reviews/LIST — replaces manual `reviews.push` |
| `deleteReview` | mutation | `number` | DELETE `review/{id}/`, invalidates Reviews/LIST |
| `patchReview` | mutation | `{id, rating}` | PATCH `review-update/{id}/`, body `{rating}`, invalidates Reviews/LIST |
| `getChatbot` | query | void | Bearer, transformResponse flattens interactions→`IChatMessage[]`, providesTags Chatbot |
| `postChatbot` | mutation | `string` | optimistic `onQueryStarted` per decision 4; transformResponse → `{role:"model", text}` |

Export all hooks from `djangoApi.ts` and re-export from the barrel `src/functions/api/index.ts`.

## Implementation steps (app compiles/tests green after each)

1. **MSW handlers (additive)** — `__tests__/mocks/handlers.ts`: add `POST users/api/token/refresh/`, `GET/POST chatbot/api/chatbot/`, `POST restaurants/api/review-create/`, `DELETE restaurants/api/review/:id/`, `PATCH restaurants/api/review-update/:id/`, `GET restaurants/api/restaurant-recommend/`.
2. **Endpoints (additive)** — all endpoints above in `djangoApi.ts` + barrel exports.
3. **Auth (atomic)** —
   - `src/reducers/userReducer.tsx`: slim slice + matchers (decision 1).
   - `src/components/LoginForm.tsx`: `useLoginMutation`; hook `isLoading`/`error` (via `getApiErrorMessages`); drop `getReviews`/`clearReviewErrors` dispatches (ReviewTable's `skip: !access` auto-fires when access lands).
   - `src/components/RegisterForm.tsx`: `usePostUserMutation` + chained `useLoginMutation`; replace `setUserError("Passwords must match")` with yup `oneOf([yup.ref("password")])` or local state.
   - `src/App.tsx`: interceptor swap (decision 2); mount effect → `if (refresh) dispatch(djangoApi.endpoints.refreshLogin.initiate(refresh))`; delete `clearErrors` effect.
   - Remove auth thunks from `axiosFunctions.tsx`.
4. **Reviews** — `ReviewTable.tsx` → `useGetReviewsQuery(undefined, {skip: !access})` + `useDeleteReviewMutation`/`usePatchReviewMutation` + SortableTable (render-cells for rating/delete; keep "no reviews" empty state); `ReviewForm.tsx` → `usePostReviewMutation` (hook `isSuccess`/`error`); `ReviewMap.tsx` reviews half → `useGetReviewsQuery`; `Restaurants.tsx` drop `dispatch(getReviews())`. Delete `reviewReducer.tsx` + rootReducer entry + review thunks.
5. **Restaurants** — `RestaurantTable.tsx` → `useGetRestaurantsQuery({state, city})` (location via selector) + SortableTable (inline RestaurantRow's "create review" cell as a column `render`; keep search bar as pre-filter); `Restaurants.tsx` drop `dispatch(getRestaurants())`; `LocationForm.tsx` drop `getRestaurants` dispatch (arg change auto-refetches). Delete `restaurantReducer.tsx` + entry + thunk. Delete `RestaurantRow.tsx` if unused after this.
6. **Recommendations (migrate, keep dormant)** — `RestaurantRecommend.tsx` → `useLazyGetRestaurantRecommendationsQuery`; `RestaurantRecommendTable.tsx` → presentational SortableTable fed by props; `ReviewMap.tsx` recommendations half → `djangoApi.endpoints.getRestaurantRecommendations.useQueryState()` (reads cache without fetching — preserves "empty until button clicked"). Commented-out block in `Restaurants.tsx` stays commented (update the commented code to match new component APIs so it's re-enable-ready). Delete `restaurantRecommendReducer.tsx` + entry + thunk.
7. **Chatbot** — `ChatbotOutput.tsx` → `useGetChatbotQuery(undefined, {skip: !access})`; `ChatbotForm.tsx` → `usePostChatbotMutation`; `Chatbot.tsx` drop dispatch effect. (`updateQueryData` no-ops if the getChatbot cache entry doesn't exist — fine, ChatbotForm only renders alongside ChatbotOutput.) Delete `chatbotReducer.tsx` + entry + thunks.
8. **Quote** — `WatchListForm.tsx` → `useLazyGetQuoteQuery` with `.unwrap()` preserving the null-price check; delete `getQuote` fn and then the now-empty `axiosFunctions.tsx`.
9. **Sweep** — `grep -rn axiosFunctions src __tests__` returns nothing; prune `rootReducer.tsx` to `user/location/watchList/adminSuccessBar` + api reducers; clean stale legacy-error comment in `helperFunctions.tsx` (~line 160); bump `persistConfig` `version` in `store.ts` with a `migrate` that strips stale `loading`/`error` keys from persisted `user` state.
10. **Tests + docs** — rewrite `ChatbotIntegration.test.tsx` (drive via MSW handler instead of preloading the removed `chatbot` slice); verify `render.test.tsx`/`Pages.test.tsx`/`Forms.test.tsx` still pass; add new tests per `.cursor/rules/auto-update-tests.mdc`: LoginForm success/failure, ReviewTable render+delete, ReviewForm submit, RestaurantTable render+sort+search, Chatbot optimistic flow, WatchListForm lazy quote. Update `CLAUDE.md` root "React State & API" section (legacy-thunk paragraph is now obsolete) per auto-update-docs rule.

## Deleted / kept

- **Deleted**: `src/functions/axiosFunctions.tsx`, `src/reducers/{reviewReducer,restaurantReducer,restaurantRecommendReducer,chatbotReducer}.tsx`, likely `src/components/restaurants/RestaurantRow.tsx`.
- **Kept**: `userReducer` (slimmed), `locationReducer`, `watchListReducer`, `adminSuccessBarReducer`; persist whitelist `['user','location','adminSuccessBar']` unchanged.

## Risks / edge cases

- **Double refresh**: avoided by keeping exactly one reauth path (interceptor). No `baseQueryWithReauth`.
- **Persist rehydration**: old localStorage `user` payloads contain `loading`/`error`; handled by the persist `version` bump + migrate in step 9.
- **Accepted behavior changes**: cached data served on remount (60s `keepUnusedDataFor`) instead of refetch-every-mount; auth errors become per-form instead of global; `postReview` refetches via invalidation instead of hand-pushing the response.
- **TS ripple**: `RootState` loses four keys — `npm run build` (tsc) surfaces any missed selector; treat compile errors as the checklist.

## Verification

1. `cd react-app && npx vitest --run` — full suite green.
2. `npm run lint` (`--max-warnings=0`) and `npm run build` (tsc + vite) — no dangling imports/state keys.
3. Manual flows against local Django (`npm run dev`): login/logout/register; force token expiry → single `token/refresh/` call in network tab + retried request; restaurants anonymous vs logged-in (sort/search/create/delete review); chatbot (instant user bubble, model reply appended, history persists on revisit); watchlist add valid/invalid ticker; hard reload with pre-migration localStorage.
4. Optionally drive Restaurants + Chatbot flows with the `webapp-testing` skill for a browser-level pass.
