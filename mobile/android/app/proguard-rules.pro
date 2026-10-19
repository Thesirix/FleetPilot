# Gson reads the API answers into these classes by reflection: keep their fields.
-keep class com.fleetpilot.driver.data.api.** { *; }
-keep class com.fleetpilot.driver.domain.** { *; }
-keepattributes Signature, *Annotation*
