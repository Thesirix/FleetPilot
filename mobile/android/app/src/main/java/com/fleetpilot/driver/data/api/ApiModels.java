package com.fleetpilot.driver.data.api;

import java.util.List;

/**
 * The JSON of the FleetPilot REST API, as Gson reads it. Only the fields the app shows are declared; dates stay ISO 8601
 * strings and are parsed where they are displayed.
 */
public final class ApiModels {

    private ApiModels() {
    }

    // ---------------------------------------------------------------- driver space (GET /api/v1/me)

    public static final class DriverSpace {
        public DriverDetail driver;
        public Vehicle vehicle;
    }

    public static final class DriverDetail {
        public DriverSummary driver;
        public String licenseNumber;
        public String licenseIssuedOn;
        public List<Incident> incidents;
        public int drivenKilometres;
    }

    public static final class DriverSummary {
        public String id;
        public String employeeNumber;
        public String firstName;
        public String lastName;
        public String fullName;
        public String email;
        public String phone;
        public SiteRef site;
        public List<String> licenseCategories;
        public String licenseExpiresOn;
    }

    public static final class SiteRef {
        public String id;
        public String name;
        public String city;
    }

    public static final class CompanyRef {
        public String id;
        public String name;
    }

    public static final class Vehicle {
        public String id;
        public CompanyRef company;
        public String registration;
        public String vin;
        public String category;
        public String make;
        public String model;
        public String trimLevel;
        public String energy;
        public int critAir;
        public int co2GramsPerKm;
        public String firstRegistrationDate;
        public String acquisition;
        public int mileage;
        public String mileageReadAt;
        public String status;
        public SiteRef site;
        public Contract activeContract;
        public List<Deadline> openDeadlines;
        public List<MileagePoint> mileageHistory;
    }

    /** A reading of the odometer of the vehicle. */
    public static final class MileagePoint {
        public int mileage;
        public String readAt;
    }

    public static final class Contract {
        public String lessor;
        public String contractNumber;
        public String startDate;
        public String endDate;
        public int contractualMileage;
        public int startMileage;
        public boolean maintenanceIncluded;
        public boolean tyresIncluded;
        public boolean replacementVehicle;
    }

    public static final class Deadline {
        public String id;
        public String type;
        public String dueDate;
        public Integer dueMileage;
        public String urgency;
        public Long daysRemaining;
        public Integer kilometresRemaining;
    }

    public static final class Incident {
        public String id;
        public String reference;
        public String type;
        public String occurredAt;
        public String location;
        public String status;
    }

    // ---------------------------------------------------------------- what the driver sends

    public static final class MileageReport {
        public final int mileage;

        public MileageReport(int mileage) {
            this.mileage = mileage;
        }
    }

    public static final class RecordFuel {
        public String filledAt;
        public String kind;
        public double quantity;
        public double amount;
        public int mileage;
        public String station;
        public String payment;
    }

    public static final class RecordInspection {
        public String kind;
        public String performedAt;
        public int mileage;
        public int fuelLevel;
        public String remarks;
        public List<CheckAnswer> checks;
    }

    public static final class CheckAnswer {
        public String item;
        public String condition;
        public String comment;

        public CheckAnswer(String item, String condition, String comment) {
            this.item = item;
            this.condition = condition;
            this.comment = comment;
        }
    }

    public static final class DeclareIncident {
        public String type;
        public String occurredAt;
        public String location;
        public Double latitude;
        public Double longitude;
        public String description;
        public boolean thirdPartyInvolved;
        public boolean injuries;
    }

    // ---------------------------------------------------------------- what the driver reads

    public static final class FuelEntry {
        public String id;
        public String filledAt;
        public String kind;
        public double quantity;
        public double amount;
        public double unitPrice;
        public int mileage;
        public String station;
        public String payment;
    }

    public static final class Inspection {
        public String id;
        public String kind;
        public String performedAt;
        public int mileage;
        public int fuelLevel;
        public String remarks;
        public List<CheckAnswer> checks;
        public int defects;
        public List<Photo> photos;
    }

    public static final class Photo {
        public String id;
        public String contentType;
        public int sizeBytes;
    }

    public static final class DriverNotification {
        public String id;
        public String kind;
        public String level;
        public String code;
        public String detail;
        public String dueDate;
        public Integer dueMileage;
        public String at;
    }

    public static final class NearbyGarage {
        public String id;
        public String name;
        public String network;
        public String brand;
        public String address;
        public String postalCode;
        public String city;
        public String phone;
        public Double hourlyRate;
        public Double rating;
        public Double latitude;
        public Double longitude;
        public double distanceKm;
    }

    /** RFC 9457 problem of the services, with the stable business code. */
    public static final class Problem {
        public String code;
        public String title;
        public String detail;
        public Integer status;
    }
}
