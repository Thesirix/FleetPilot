package com.fleetpilot.driver.data.api;

import java.util.List;

import com.fleetpilot.driver.data.api.ApiModels.DeclareIncident;
import com.fleetpilot.driver.data.api.ApiModels.DriverNotification;
import com.fleetpilot.driver.data.api.ApiModels.DriverSpace;
import com.fleetpilot.driver.data.api.ApiModels.FuelEntry;
import com.fleetpilot.driver.data.api.ApiModels.Incident;
import com.fleetpilot.driver.data.api.ApiModels.Inspection;
import com.fleetpilot.driver.data.api.ApiModels.MileageReport;
import com.fleetpilot.driver.data.api.ApiModels.NearbyGarage;
import com.fleetpilot.driver.data.api.ApiModels.Photo;
import com.fleetpilot.driver.data.api.ApiModels.RecordFuel;
import com.fleetpilot.driver.data.api.ApiModels.RecordInspection;
import com.fleetpilot.driver.data.api.ApiModels.Vehicle;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * The REST API of FleetPilot used by the driver app, behind the Nginx entry point. The driver is identified by the
 * {@code driver_id} claim of the token, never by the URL. Records carry an id generated on the phone: a PUT sent twice
 * (an answer lost on a bad mobile network) returns the record already stored.
 */
public interface FleetPilotApi {

    @GET("api/v1/me")
    Call<DriverSpace> me();

    @POST("api/v1/me/mileage")
    Call<Vehicle> reportMileage(@Body MileageReport report);

    @GET("api/v1/me/fuel-entries")
    Call<List<FuelEntry>> fuelEntries();

    @PUT("api/v1/me/fuel-entries/{id}")
    Call<FuelEntry> recordFuel(@Path("id") String id, @Body RecordFuel entry);

    @GET("api/v1/me/inspections")
    Call<List<Inspection>> inspections();

    @PUT("api/v1/me/inspections/{id}")
    Call<Inspection> recordInspection(@Path("id") String id, @Body RecordInspection inspection);

    @PUT("api/v1/me/incidents/{id}")
    Call<Incident> declareIncident(@Path("id") String id, @Body DeclareIncident declaration);

    /** owner is "incidents" or "inspections"; the body is the JPEG itself. */
    @PUT("api/v1/me/{owner}/{id}/photos/{photoId}")
    Call<Photo> addPhoto(@Path("owner") String owner, @Path("id") String ownerId, @Path("photoId") String photoId,
            @Body RequestBody image);

    @GET("api/v1/me/notifications")
    Call<List<DriverNotification>> notifications();

    @GET("api/v1/garages/nearby")
    Call<List<NearbyGarage>> nearbyGarages(@Query("latitude") double latitude, @Query("longitude") double longitude,
            @Query("radiusKm") int radiusKm, @Query("limit") int limit);
}
