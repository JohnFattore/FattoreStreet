from rest_framework.request import Request
from rest_framework_simplejwt.authentication import JWTAuthentication


class TrackingJWTAuthentication(JWTAuthentication):
    """JWTAuthentication that records the verified user on the underlying HttpRequest.

    DRF attaches JWT-authenticated users only to its wrapped Request object, leaving
    Django's HttpRequest.user as AnonymousUser for outer middleware. Recording the
    verified user here lets RequestActivityLoggingMiddleware attribute API calls
    without re-decoding the token or trusting unverified headers.
    """

    def authenticate(self, request: Request):
        result = super().authenticate(request)
        if result is not None:
            user, _token = result
            raw_request = getattr(request, "_request", request)
            raw_request._monitoring_user = user
        return result
