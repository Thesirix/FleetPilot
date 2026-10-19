package com.fleetpilot.driver.auth;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Base64;
import androidx.annotation.Nullable;
import com.fleetpilot.driver.BuildConfig;
import net.openid.appauth.AuthState;
import net.openid.appauth.AuthorizationException;
import net.openid.appauth.AuthorizationRequest;
import net.openid.appauth.AuthorizationResponse;
import net.openid.appauth.AuthorizationService;
import net.openid.appauth.AuthorizationServiceConfiguration;
import net.openid.appauth.EndSessionRequest;
import net.openid.appauth.ResponseTypeValues;
import net.openid.appauth.connectivity.ConnectionBuilder;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Sign-in of the driver with the Keycloak of FleetPilot: authorization code with PKCE in a Custom Tab (AppAuth), the
 * same account as on the web portal. The tokens are kept in the private preferences of the app and refreshed when they
 * expire, so the driver signs in once and the background sync keeps working.
 */
public final class AuthManager {

    private static final String PREFS = "fleetpilot-auth";
    private static final String KEY_STATE = "state";

    private final SharedPreferences prefs;
    private final AuthorizationService service;
    private AuthState state;

    public AuthManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.service = new AuthorizationService(context, new net.openid.appauth.AppAuthConfiguration.Builder()
                .setConnectionBuilder(LocalConnectionBuilder.INSTANCE)
                // The development Keycloak is served over plain HTTP: only a debug build accepts its ID tokens
                .setSkipIssuerHttpsCheck(BuildConfig.DEBUG && BuildConfig.AUTH_ISSUER.startsWith("http://localhost"))
                .build());
        this.state = read();
    }

    public synchronized boolean isSignedIn() {
        return state.isAuthorized();
    }

    /** Reads the configuration of the realm, then gives the intent that opens the Keycloak login page. */
    public void loginIntent(Callback<Intent> callback) {
        AuthorizationServiceConfiguration.fetchFromIssuer(Uri.parse(BuildConfig.AUTH_ISSUER), (config, error) -> {
            if (config == null) {
                callback.done(null, error);
                return;
            }
            synchronized (this) {
                state = new AuthState(config);
                write();
            }
            AuthorizationRequest request = new AuthorizationRequest.Builder(config, BuildConfig.AUTH_CLIENT_ID,
                    ResponseTypeValues.CODE, Uri.parse(BuildConfig.AUTH_REDIRECT_URI))
                    .setScope("openid profile email")
                    .build();
            callback.done(service.getAuthorizationRequestIntent(request), null);
        }, LocalConnectionBuilder.INSTANCE);
    }

    /** Exchanges the code returned by Keycloak for the tokens. */
    public void handleLoginResult(Intent data, Callback<Boolean> callback) {
        AuthorizationResponse response = AuthorizationResponse.fromIntent(data);
        AuthorizationException error = AuthorizationException.fromIntent(data);
        synchronized (this) {
            state.update(response, error);
            write();
        }
        if (response == null) {
            callback.done(false, error);
            return;
        }
        service.performTokenRequest(response.createTokenExchangeRequest(), (tokens, tokenError) -> {
            synchronized (this) {
                state.update(tokens, tokenError);
                write();
            }
            callback.done(tokens != null, tokenError);
        });
    }

    /**
     * A valid access token, refreshed first when it has expired. Blocking: called by the HTTP client on a background
     * thread. Null when the driver is signed out or Keycloak cannot be reached.
     */
    @Nullable
    public String freshAccessToken() {
        if (!isSignedIn()) {
            return null;
        }
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> token = new AtomicReference<>();
        synchronized (this) {
            state.performActionWithFreshTokens(service, (accessToken, idToken, error) -> {
                token.set(accessToken);
                synchronized (AuthManager.this) {
                    write();
                }
                latch.countDown();
            });
        }
        try {
            latch.await(20, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return token.get();
    }

    /** First name of the driver, from the identity token. */
    public String firstName() {
        JSONObject claims = idTokenClaims();
        return claims == null ? "" : claims.optString("given_name", claims.optString("preferred_username", ""));
    }

    public String fullName() {
        JSONObject claims = idTokenClaims();
        return claims == null ? "" : claims.optString("name", "");
    }

    /** Forgets the tokens and gives the intent that closes the Keycloak session too, when there is one. */
    @Nullable
    public synchronized Intent logout() {
        AuthorizationServiceConfiguration config = state.getAuthorizationServiceConfiguration();
        String idToken = state.getIdToken();
        state = new AuthState();
        write();
        if (config == null || config.endSessionEndpoint == null || idToken == null) {
            return null;
        }
        EndSessionRequest request = new EndSessionRequest.Builder(config)
                .setIdTokenHint(idToken)
                .setPostLogoutRedirectUri(Uri.parse(BuildConfig.AUTH_REDIRECT_URI))
                .build();
        return service.getEndSessionRequestIntent(request);
    }

    public void dispose() {
        service.dispose();
    }

    @Nullable
    private synchronized JSONObject idTokenClaims() {
        String idToken = state.getIdToken();
        if (idToken == null) {
            return null;
        }
        String[] parts = idToken.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        try {
            byte[] payload = Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            return new JSONObject(new String(payload, java.nio.charset.StandardCharsets.UTF_8));
        } catch (IllegalArgumentException | JSONException e) {
            return null;
        }
    }

    private AuthState read() {
        String json = prefs.getString(KEY_STATE, null);
        if (json == null) {
            return new AuthState();
        }
        try {
            return AuthState.jsonDeserialize(json);
        } catch (JSONException e) {
            return new AuthState();
        }
    }

    private void write() {
        prefs.edit().putString(KEY_STATE, state.jsonSerializeString()).apply();
    }

    /** Result of an asynchronous step of the sign-in. */
    public interface Callback<T> {
        void done(@Nullable T result, @Nullable Exception error);
    }

    /**
     * AppAuth refuses plain HTTP by default. The development platform serves Keycloak on http://localhost (reached from
     * the emulator through adb reverse): HTTP is accepted for that host only, HTTPS everywhere else.
     */
    private enum LocalConnectionBuilder implements ConnectionBuilder {
        INSTANCE;

        @Override
        public java.net.HttpURLConnection openConnection(Uri uri) throws java.io.IOException {
            boolean local = "localhost".equals(uri.getHost()) || "10.0.2.2".equals(uri.getHost());
            if (!"https".equals(uri.getScheme()) && !local) {
                throw new IllegalArgumentException("Only HTTPS is accepted outside the development platform");
            }
            java.net.HttpURLConnection connection =
                    (java.net.HttpURLConnection) new java.net.URL(uri.toString()).openConnection();
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(15_000);
            connection.setInstanceFollowRedirects(false);
            return connection;
        }
    }
}
