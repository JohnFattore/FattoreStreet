export const ACCESS_TOKEN_REFRESH_SKEW_MS = 30_000;

function decodeBase64Url(segment: string): string | null {
  try {
    const normalized = segment.replace(/-/g, "+").replace(/_/g, "/");
    const padLength = (4 - (normalized.length % 4)) % 4;
    return atob(normalized + "=".repeat(padLength));
  } catch {
    return null;
  }
}

/**
 * Extracts the `exp` claim from a JWT as epoch milliseconds, or returns `null`
 * if the string is not a valid 3-part JWT with a numeric `exp` claim.
 */
export function decodeJwtExpMs(token: string): number | null {
  if (!token) return null;
  const parts = token.split(".");
  if (parts.length !== 3) return null;

  const decoded = decodeBase64Url(parts[1]);
  if (!decoded) return null;

  try {
    const payload = JSON.parse(decoded) as { exp?: unknown };
    if (typeof payload.exp === "number" && Number.isFinite(payload.exp)) {
      return payload.exp * 1000;
    }
    return null;
  } catch {
    return null;
  }
}

/**
 * Returns true if the token is empty or has a decodable `exp` claim that has
 * passed (accounting for optional clock/in-flight skew). Non-JWT opaque strings
 * return false so the server remains the authority.
 */
export function isJwtExpired(
  token: string,
  skewMs = 0,
  nowMs = Date.now(),
): boolean {
  if (!token) return true;
  const expMs = decodeJwtExpMs(token);
  if (expMs === null) return false;
  return nowMs + skewMs >= expMs;
}

/**
 * Returns whether the stored tokens represent a session that is still usable
 * (either directly or via refresh). A dead refresh token ends the session even
 * if a stale access string is still persisted.
 */
export function hasActiveSession(
  { access, refresh }: { access: string; refresh: string },
  nowMs = Date.now(),
): boolean {
  if (refresh) {
    return !isJwtExpired(refresh, 0, nowMs);
  }
  if (access) {
    return !isJwtExpired(access, 0, nowMs);
  }
  return false;
}

/**
 * Returns the epoch-ms timestamp when the session can no longer be renewed
 * (`refresh` exp if present, otherwise `access` exp), or `null` if unknown.
 */
export function getSessionExpiryMs({
  access,
  refresh,
}: {
  access: string;
  refresh: string;
}): number | null {
  if (refresh) {
    return decodeJwtExpMs(refresh);
  }
  if (access) {
    return decodeJwtExpMs(access);
  }
  return null;
}
