package com.fleetpilot.quote.acceptance;

import com.fleetpilot.quote.integration.IntegrationTestBase;
import io.cucumber.spring.CucumberContextConfiguration;

/** The scenarios run against the same application context as the integration tests (PostgreSQL, demo data, fixed clock). */
@CucumberContextConfiguration
public class CucumberSpringConfiguration extends IntegrationTestBase {
}
