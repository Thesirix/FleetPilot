"""Logs in to the FleetPilot Keycloak realm like a browser (authorization code + PKCE) and prints the access token.

    python tools/scripts/oidc_login.py claire.morel                 # staff account from the LDAP directory
    python tools/scripts/oidc_login.py m.roux@neolia-energies.fr    # client manager
    python tools/scripts/oidc_login.py camille.martin@neolia-energies.fr --claims

Used by the smoke tests and handy to call the APIs from a terminal:

    TOKEN=$(python tools/scripts/oidc_login.py claire.morel)
    curl -H "Authorization: Bearer $TOKEN" http://localhost:8081/api/v1/fleet/summary
"""
import base64
import hashlib
import html
import json
import re
import secrets
import sys
import urllib.error
import urllib.parse
import urllib.request

ISSUER = "http://localhost:8180/realms/fleetpilot"
CLIENT_ID = "fleetpilot-swagger"
REDIRECT_URI = "http://localhost:8081/swagger-ui/oauth2-redirect.html"
DEMO_PASSWORD = "FleetPilot2026!"


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class _Browser:
    """Keeps the Keycloak session cookies by hand: the standard cookie jar ignores host-only cookies on localhost."""

    def __init__(self):
        self.cookies = {}
        self.opener = urllib.request.build_opener(_NoRedirect)

    def open(self, url, data=None):
        request = urllib.request.Request(url, data)
        if self.cookies:
            request.add_header("Cookie", "; ".join(f"{k}={v}" for k, v in self.cookies.items()))
        try:
            response = self.opener.open(request)
        except urllib.error.HTTPError as error:
            self._keep(error.headers)
            raise
        self._keep(response.headers)
        return response

    def _keep(self, headers):
        for header in headers.get_all("Set-Cookie") or []:
            name, _, value = header.split(";", 1)[0].partition("=")
            self.cookies[name.strip()] = value.strip()


def login(username: str, password: str = DEMO_PASSWORD) -> dict:
    verifier = secrets.token_urlsafe(48)
    challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).rstrip(b"=").decode()
    browser = _Browser()

    query = urllib.parse.urlencode({
        "client_id": CLIENT_ID, "response_type": "code", "scope": "openid", "redirect_uri": REDIRECT_URI,
        "state": secrets.token_hex(8), "code_challenge": challenge, "code_challenge_method": "S256",
    })
    page = browser.open(f"{ISSUER}/protocol/openid-connect/auth?{query}").read().decode()
    action = re.search(r'<form[^>]*id="kc-form-login"[^>]*action="([^"]+)"', page)
    if not action:
        raise RuntimeError("login form not found")
    form = urllib.parse.urlencode({"username": username, "password": password, "credentialId": ""}).encode()
    try:
        browser.open(html.unescape(action.group(1)), form)
        raise RuntimeError("invalid credentials (no redirect after login)")
    except urllib.error.HTTPError as redirect:
        location = redirect.headers.get("Location", "")
    code = urllib.parse.parse_qs(urllib.parse.urlparse(location).query).get("code")
    if not code:
        raise RuntimeError(f"no authorization code in redirect: {location}")

    token_request = urllib.parse.urlencode({
        "grant_type": "authorization_code", "client_id": CLIENT_ID, "code": code[0],
        "redirect_uri": REDIRECT_URI, "code_verifier": verifier,
    }).encode()
    return json.load(urllib.request.urlopen(f"{ISSUER}/protocol/openid-connect/token", token_request))


def claims(access_token: str) -> dict:
    payload = access_token.split(".")[1]
    return json.loads(base64.urlsafe_b64decode(payload + "=" * (-len(payload) % 4)))


if __name__ == "__main__":
    tokens = login(sys.argv[1])
    if "--claims" in sys.argv:
        print(json.dumps(claims(tokens["access_token"]), indent=2, ensure_ascii=False))
    else:
        print(tokens["access_token"])
