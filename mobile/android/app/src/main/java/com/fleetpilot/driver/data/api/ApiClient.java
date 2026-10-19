package com.fleetpilot.driver.data.api;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import com.fleetpilot.driver.BuildConfig;
import com.fleetpilot.driver.auth.AuthManager;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/** Retrofit on OkHttp, with the access token of the driver on every request. */
public final class ApiClient {

    private final FleetPilotApi api;
    private final Gson gson = new Gson();

    public ApiClient(AuthManager auth) {
        this(auth, BuildConfig.API_BASE_URL);
    }

    public ApiClient(AuthManager auth, String baseUrl) {
        OkHttpClient http = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .addInterceptor(chain -> {
                    String token = auth.freshAccessToken();
                    Request.Builder request = chain.request().newBuilder().header("Accept-Language", "fr-FR");
                    if (token != null) {
                        request.header("Authorization", "Bearer " + token);
                    }
                    return chain.proceed(request.build());
                })
                .build();
        this.api = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(http)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()
                .create(FleetPilotApi.class);
    }

    public FleetPilotApi api() {
        return api;
    }

    /**
     * Runs a call on the current (background) thread and returns its body, or throws an {@link ApiException} saying
     * whether trying again later may work.
     */
    public <T> T execute(Call<T> call) throws ApiException {
        Response<T> response;
        try {
            response = call.execute();
        } catch (IOException e) {
            throw ApiException.network(e);
        }
        if (response.isSuccessful()) {
            return response.body();
        }
        ApiModels.Problem problem = null;
        try (ResponseBody body = response.errorBody()) {
            if (body != null) {
                problem = gson.fromJson(body.charStream(), ApiModels.Problem.class);
            }
        } catch (JsonSyntaxException | com.google.gson.JsonIOException e) {
            problem = null;
        }
        throw ApiException.http(response.code(), problem);
    }
}
