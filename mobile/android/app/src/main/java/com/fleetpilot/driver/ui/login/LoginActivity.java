package com.fleetpilot.driver.ui.login;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.fleetpilot.driver.BuildConfig;
import com.fleetpilot.driver.FleetPilotApp;
import com.fleetpilot.driver.R;
import com.fleetpilot.driver.auth.AuthManager;
import com.fleetpilot.driver.databinding.ActivityLoginBinding;
import com.fleetpilot.driver.notifications.NotificationsWorker;
import com.fleetpilot.driver.sync.SyncWorker;
import com.fleetpilot.driver.ui.MainActivity;

/** Entry point: a signed-in driver goes straight to the app, the others sign in with Keycloak in a Custom Tab. */
public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "FleetPilotLogin";

    private ActivityLoginBinding binding;
    private AuthManager auth;

    private final ActivityResultLauncher<Intent> login = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getData() == null) {
                    busy(false);
                    return;
                }
                auth.handleLoginResult(result.getData(), (ok, error) -> runOnUiThread(() -> {
                    if (Boolean.TRUE.equals(ok)) {
                        openApp();
                    } else {
                        Log.w(TAG, "Sign-in refused", error);
                        busy(false);
                        showError();
                    }
                }));
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        auth = ((FleetPilotApp) getApplication()).auth();
        if (auth.isSignedIn()) {
            openApp();
            return;
        }
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Uri issuer = Uri.parse(BuildConfig.AUTH_ISSUER);
        binding.server.setText(getString(R.string.login_server, issuer.getScheme() + "://" + issuer.getAuthority()));
        binding.login.setOnClickListener(v -> start());
    }

    private void start() {
        busy(true);
        binding.error.setVisibility(View.GONE);
        auth.loginIntent((intent, error) -> runOnUiThread(() -> {
            if (intent == null) {
                Log.w(TAG, "Keycloak configuration unreachable", error);
                busy(false);
                showError();
            } else {
                login.launch(intent);
            }
        }));
    }

    private void openApp() {
        SyncWorker.schedule(this);
        NotificationsWorker.schedule(this);
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void busy(boolean busy) {
        binding.login.setEnabled(!busy);
        binding.progress.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    private void showError() {
        binding.error.setText(R.string.login_failed);
        binding.error.setVisibility(View.VISIBLE);
    }
}
