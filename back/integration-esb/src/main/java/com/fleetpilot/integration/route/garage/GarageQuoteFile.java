package com.fleetpilot.integration.route.garage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlValue;
import jakarta.xml.bind.annotation.adapters.XmlAdapter;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

/**
 * A quote file of a garage (format "devis garage" version 1, schema {@code schemas/garage-quote.xsd}), as JAXB reads
 * it. The element names are French: they are those of the garages' management software.
 */
@XmlRootElement(name = "devis", namespace = GarageQuoteFile.NAMESPACE)
@XmlAccessorType(XmlAccessType.FIELD)
public class GarageQuoteFile {

    public static final String NAMESPACE = "urn:fleetpilot:devis-garage:1";

    @XmlElement(name = "emetteur", namespace = NAMESPACE)
    Issuer issuer;
    @XmlElement(name = "reference", namespace = NAMESPACE)
    String reference;
    @XmlElement(name = "dateEmission", namespace = NAMESPACE)
    @XmlJavaTypeAdapter(DateAdapter.class)
    LocalDate issuedOn;
    @XmlElement(name = "vehicule", namespace = NAMESPACE)
    Vehicle vehicle;
    @XmlElement(name = "objet", namespace = NAMESPACE)
    String subject;
    @XmlElementWrapper(name = "lignes", namespace = NAMESPACE)
    @XmlElement(name = "ligne", namespace = NAMESPACE)
    List<Line> lines = new ArrayList<>();
    @XmlElement(name = "totalHT", namespace = NAMESPACE)
    BigDecimal totalExcludingTax;

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Issuer {
        @XmlAttribute(name = "siret")
        String siret;
        @XmlValue
        String name;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Vehicle {
        @XmlAttribute(name = "immatriculation")
        String registration;
        @XmlAttribute(name = "kilometrage")
        Integer mileage;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Line {
        @XmlAttribute(name = "nature")
        String nature;
        @XmlAttribute(name = "code")
        String code;
        @XmlElement(name = "libelle", namespace = NAMESPACE)
        String label;
        @XmlElement(name = "quantite", namespace = NAMESPACE)
        BigDecimal quantity;
        @XmlElement(name = "prixUnitaireHT", namespace = NAMESPACE)
        BigDecimal unitPrice;
    }

    /** ISO dates ({@code xs:date}) to {@link LocalDate}. */
    public static class DateAdapter extends XmlAdapter<String, LocalDate> {

        @Override
        public LocalDate unmarshal(String value) {
            return value == null ? null : LocalDate.parse(value.trim());
        }

        @Override
        public String marshal(LocalDate value) {
            return value == null ? null : value.toString();
        }
    }
}
