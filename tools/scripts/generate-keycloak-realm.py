"""Generates infra/keycloak/realms/fleetpilot-realm.json, the Keycloak realm imported at startup.

    python tools/scripts/generate-keycloak-realm.py [output file]

Company and driver identifiers are the deterministic UUIDs of the demo data set (see DemoDataGenerator).
"""
import hashlib
import json
import sys
import uuid

OUT = sys.argv[1] if len(sys.argv) > 1 else "infra/keycloak/realms/fleetpilot-realm.json"
PASSWORD = "FleetPilot2026!"
NEO = "474a1f7d-c59a-3321-a0a2-729ec42501d5"
MAP = "29d53fba-827a-3e2b-bec1-e74f22fc6de9"
TCL = "849d0af0-f20c-3127-96a3-7d8e0c5a30f3"


def demo_uuid(kind, key):
    """Same as Java's UUID.nameUUIDFromBytes("fleetpilot-demo:<kind>:<key>"), used by the demo data generators."""
    digest = bytearray(hashlib.md5(f"fleetpilot-demo:{kind}:{key}".encode("utf-8")).digest())
    digest[6] = (digest[6] & 0x0F) | 0x30
    digest[8] = (digest[8] & 0x3F) | 0x80
    return str(uuid.UUID(bytes=bytes(digest)))


PRADO = demo_uuid("garage", "MRS-01")
VIEUX_PORT = demo_uuid("garage", "MRS-02")

ROLES = [
    ("platform-admin", "Administrateur de la plateforme : paramètres, entreprises clientes, utilisateurs"),
    ("fleet-manager", "Gestionnaire de flotte : gère les flottes de toutes les entreprises clientes de son portefeuille"),
    ("client-manager", "Gestionnaire de flotte côté client : gère la flotte de sa propre entreprise"),
    ("driver", "Conducteur : son véhicule, ses échéances, ses déclarations de sinistre"),
    ("garage", "Garage agréé : dépose ses devis et ses factures"),
    ("api-supplier", "Fournisseur connecté par API : loueurs, assureurs, cartes carburant"),
]

def attr_mapper(name, attribute):
    return {
        "name": name, "protocol": "openid-connect", "protocolMapper": "oidc-usermodel-attribute-mapper",
        "consentRequired": False,
        "config": {"user.attribute": attribute, "claim.name": attribute, "jsonType.label": "String",
                   "id.token.claim": "true", "access.token.claim": "true", "userinfo.token.claim": "true",
                   "introspection.token.claim": "true", "multivalued": "false"},
    }

client_scope = {
    "name": "fleetpilot",
    "description": "FleetPilot claims: client company, driver and garage of the user, API audience",
    "protocol": "openid-connect",
    "attributes": {"include.in.token.scope": "false", "display.on.consent.screen": "false"},
    "protocolMappers": [
        attr_mapper("company id", "company_id"),
        attr_mapper("driver id", "driver_id"),
        attr_mapper("garage id", "garage_id"),
        attr_mapper("lessor", "lessor"),
        {"name": "api audience", "protocol": "openid-connect", "protocolMapper": "oidc-audience-mapper",
         "consentRequired": False,
         "config": {"included.custom.audience": "fleetpilot-api", "access.token.claim": "true",
                    "id.token.claim": "false", "introspection.token.claim": "true"}},
    ],
}

# Every client gets the FleetPilot claims directly (no custom client scope: the realm keeps Keycloak's default scopes).
MAPPERS = client_scope["protocolMappers"]

def public_client(client_id, name, description, redirects, origins):
    return {
        "clientId": client_id, "name": name, "description": description, "enabled": True,
        "publicClient": True, "standardFlowEnabled": True, "directAccessGrantsEnabled": False,
        "implicitFlowEnabled": False, "serviceAccountsEnabled": False, "protocol": "openid-connect",
        "redirectUris": redirects, "webOrigins": origins, "rootUrl": "", "frontchannelLogout": True,
        "attributes": {"pkce.code.challenge.method": "S256", "post.logout.redirect.uris": "+"},
        "protocolMappers": MAPPERS,
    }

def web_client(client_id, name, description, secret, redirects):
    """Confidential web application: authorization code with PKCE, tokens received by its server."""
    return {
        "clientId": client_id, "name": name, "description": description, "enabled": True,
        "publicClient": False, "clientAuthenticatorType": "client-secret", "secret": secret,
        "standardFlowEnabled": True, "directAccessGrantsEnabled": False, "implicitFlowEnabled": False,
        "serviceAccountsEnabled": False, "protocol": "openid-connect", "redirectUris": redirects, "webOrigins": [],
        "rootUrl": "", "frontchannelLogout": True,
        "attributes": {"pkce.code.challenge.method": "S256", "post.logout.redirect.uris": "+"},
        "protocolMappers": MAPPERS,
    }

def service_client(client_id, name, description, secret):
    return {
        "clientId": client_id, "name": name, "description": description, "enabled": True,
        "publicClient": False, "clientAuthenticatorType": "client-secret", "secret": secret,
        "standardFlowEnabled": False, "directAccessGrantsEnabled": False, "serviceAccountsEnabled": True,
        "protocol": "openid-connect", "protocolMappers": MAPPERS,
    }

swagger_ports = range(8081, 8090)
clients = [
    public_client("fleetpilot-portal", "Portail FleetPilot", "Portail web Nuxt des gestionnaires et de l'espace garage",
                  ["http://localhost:3000/*", "http://localhost/*"], ["http://localhost:3000", "http://localhost"]),
    public_client("fleetpilot-mobile", "Application conducteur", "Application Android des conducteurs (AppAuth, PKCE)",
                  ["com.fleetpilot.driver:/oauth2redirect"], []),
    public_client("fleetpilot-swagger", "Documentation des API", "Swagger UI des services, pour essayer les API connecté",
                  [f"http://localhost:{p}/*" for p in swagger_ports] + ["http://localhost/*"],
                  [f"http://localhost:{p}" for p in swagger_ports] + ["http://localhost"]),
    # Development-only secrets: a real deployment injects them from a vault.
    web_client("fleetpilot-legacy-portal", "Extranet loueurs (v1)",
               "Portail historique des loueurs : WAR sur Tomcat derrière Apache httpd",
               "dev-legacy-portal-secret", ["http://localhost:8095/extranet/*"]),
    service_client("supplier-arval", "Arval (loueur)", "Connecteur loueur : relevés kilométriques et contrats",
                   "dev-supplier-arval-secret"),
    service_client("fleetpilot-services", "Services FleetPilot", "Appels de service à service (ESB, passerelle)",
                   "dev-fleetpilot-services-secret"),
    service_client("fleetpilot-admin-api", "Administration des comptes",
                   "Écrans d'administration de FleetPilot : gestion des comptes des clients, conducteurs, garages et loueurs",
                   "dev-fleetpilot-admin-secret"),
]

def user(username, first, last, email, roles, attributes=None):
    return {
        "username": username, "email": email, "firstName": first, "lastName": last, "enabled": True,
        "emailVerified": True, "realmRoles": roles, "attributes": attributes or {},
        "credentials": [{"type": "password", "value": PASSWORD, "temporary": False}],
    }

users = [
    user("m.roux@neolia-energies.fr", "Mathilde", "Roux", "m.roux@neolia-energies.fr", ["client-manager"],
         {"company_id": [NEO]}),
    user("p.bianchi@azur-provence-metropole.fr", "Pascal", "Bianchi", "p.bianchi@azur-provence-metropole.fr",
         ["client-manager"], {"company_id": [MAP]}),
    user("n.haddad@calanques-logistique.fr", "Nadia", "Haddad", "n.haddad@calanques-logistique.fr", ["client-manager"],
         {"company_id": [TCL]}),
    user("camille.martin@neolia-energies.fr", "Camille", "Martin", "camille.martin@neolia-energies.fr", ["driver"],
         {"company_id": [NEO], "driver_id": ["87162d7c-e396-30cd-b37c-a6278869bd78"]}),
    user("jean.dupont@neolia-energies.fr", "Jean", "Dupont", "jean.dupont@neolia-energies.fr", ["driver"],
         {"company_id": [NEO], "driver_id": ["23f33fb7-f4c2-371c-93f9-e1f915b8bd15"]}),
    user("atelier@garage-du-prado.fr", "Marc", "Giraud", "atelier@garage-du-prado.fr", ["garage"],
         {"garage_id": [PRADO]}),
    user("devis@carrosserie-vieux-port.fr", "Leïla", "Mansour", "devis@carrosserie-vieux-port.fr", ["garage"],
         {"garage_id": [VIEUX_PORT]}),
    user("extranet@arval.fr", "Sophie", "Lemoine", "extranet@arval.fr", ["api-supplier"], {"lessor": ["Arval"]}),
    user("extranet@ayvens.fr", "Julien", "Perrot", "extranet@ayvens.fr", ["api-supplier"], {"lessor": ["Ayvens"]}),
    {"username": "service-account-supplier-arval", "enabled": True, "serviceAccountClientId": "supplier-arval",
     "realmRoles": ["api-supplier"]},
    {"username": "service-account-fleetpilot-services", "enabled": True, "serviceAccountClientId": "fleetpilot-services",
     "realmRoles": ["platform-admin"]},
    # Only what the administration screens need: read and manage the users of this realm
    {"username": "service-account-fleetpilot-admin-api", "enabled": True, "serviceAccountClientId": "fleetpilot-admin-api",
     "clientRoles": {"realm-management": ["view-users", "query-users", "manage-users", "view-realm"]}},
]

def admin_attribute(name, display):
    return {"name": name, "displayName": display, "permissions": {"view": ["admin"], "edit": ["admin"]},
            "multivalued": False, "validations": {"length": {"max": 64}}}

user_profile = {
    "unmanagedAttributePolicy": "ADMIN_VIEW",
    "attributes": [
        {"name": "username", "displayName": "${username}",
         "validations": {"length": {"min": 3, "max": 255}, "username-prohibited-characters": {},
                         "up-username-not-idn-homograph": {}},
         "permissions": {"view": ["admin", "user"], "edit": ["admin", "user"]}, "multivalued": False},
        {"name": "email", "displayName": "${email}", "validations": {"email": {}, "length": {"max": 255}},
         "required": {"roles": ["user"]}, "permissions": {"view": ["admin", "user"], "edit": ["admin", "user"]},
         "multivalued": False},
        {"name": "firstName", "displayName": "${firstName}",
         "validations": {"length": {"max": 255}, "person-name-prohibited-characters": {}},
         "required": {"roles": ["user"]}, "permissions": {"view": ["admin", "user"], "edit": ["admin", "user"]},
         "multivalued": False},
        {"name": "lastName", "displayName": "${lastName}",
         "validations": {"length": {"max": 255}, "person-name-prohibited-characters": {}},
         "required": {"roles": ["user"]}, "permissions": {"view": ["admin", "user"], "edit": ["admin", "user"]},
         "multivalued": False},
        admin_attribute("company_id", "Entreprise cliente (identifiant)"),
        admin_attribute("driver_id", "Conducteur (identifiant)"),
        admin_attribute("garage_id", "Garage (identifiant)"),
        admin_attribute("lessor", "Loueur"),
    ],
    "groups": [{"name": "user-metadata", "displayHeader": "User metadata",
                "displayDescription": "Attributes, which refer to user metadata"}],
}

def ldap_mapper(name, provider, config):
    return {"name": name, "providerId": provider, "subComponents": {}, "config": {k: [v] for k, v in config.items()}}

attribute_mapper = "user-attribute-ldap-mapper"
ldap = {
    "name": "fleetpilot-directory",
    "providerId": "ldap",
    "subComponents": {"org.keycloak.storage.ldap.mappers.LDAPStorageMapper": [
        ldap_mapper("username", attribute_mapper, {"ldap.attribute": "uid", "user.model.attribute": "username",
            "read.only": "true", "always.read.value.from.ldap": "false", "is.mandatory.in.ldap": "true"}),
        ldap_mapper("first name", attribute_mapper, {"ldap.attribute": "givenName", "user.model.attribute": "firstName",
            "read.only": "true", "always.read.value.from.ldap": "true", "is.mandatory.in.ldap": "true"}),
        ldap_mapper("last name", attribute_mapper, {"ldap.attribute": "sn", "user.model.attribute": "lastName",
            "read.only": "true", "always.read.value.from.ldap": "true", "is.mandatory.in.ldap": "true"}),
        ldap_mapper("email", attribute_mapper, {"ldap.attribute": "mail", "user.model.attribute": "email",
            "read.only": "true", "always.read.value.from.ldap": "false", "is.mandatory.in.ldap": "false"}),
        ldap_mapper("roles", "role-ldap-mapper", {"roles.dn": "ou=roles,dc=fleetpilot,dc=dev",
            "role.name.ldap.attribute": "cn", "role.object.classes": "groupOfNames",
            "membership.ldap.attribute": "member", "membership.attribute.type": "DN",
            "membership.user.ldap.attribute": "uid", "mode": "READ_ONLY",
            "user.roles.retrieve.strategy": "LOAD_ROLES_BY_MEMBER_ATTRIBUTE", "use.realm.roles.mapping": "true",
            "memberof.ldap.attribute": "memberOf", "roles.ldap.filter": ""}),
    ]},
    "config": {k: [v] for k, v in {
        "enabled": "true", "priority": "0", "vendor": "other", "editMode": "READ_ONLY", "importEnabled": "true",
        "syncRegistrations": "false", "connectionUrl": "ldap://openldap:389", "authType": "simple",
        "bindDn": "cn=admin,dc=fleetpilot,dc=dev", "bindCredential": "${LDAP_ADMIN_PASSWORD:fleetpilot-ldap}",
        "usersDn": "ou=people,dc=fleetpilot,dc=dev", "usernameLDAPAttribute": "uid", "rdnLDAPAttribute": "uid",
        "uuidLDAPAttribute": "entryUUID", "userObjectClasses": "inetOrgPerson, organizationalPerson",
        "searchScope": "1", "pagination": "false", "trustEmail": "true", "useTruststoreSpi": "never",
        "connectionPooling": "true", "fullSyncPeriod": "-1", "changedSyncPeriod": "-1", "cachePolicy": "DEFAULT",
        "batchSizeForSync": "1000", "validatePasswordPolicy": "false", "allowKerberosAuthentication": "false",
        "useKerberosForPasswordAuthentication": "false",
    }.items()},
}

realm = {
    "realm": "fleetpilot",
    "displayName": "FleetPilot",
    "enabled": True,
    "sslRequired": "external",
    "registrationAllowed": False,
    "loginWithEmailAllowed": True,
    "duplicateEmailsAllowed": False,
    "resetPasswordAllowed": True,
    # "Forgotten password" e-mails go to Mailpit locally (http://localhost:8025)
    "smtpServer": {"host": "mailpit", "port": "1025", "from": "no-reply@fleetpilot.dev", "fromDisplayName": "FleetPilot",
                   "replyTo": "", "ssl": "false", "starttls": "false", "auth": "false"},
    "rememberMe": True,
    "bruteForceProtected": True,
    "failureFactor": 5,
    "accessTokenLifespan": 600,
    "ssoSessionIdleTimeout": 1800,
    "ssoSessionMaxLifespan": 36000,
    "internationalizationEnabled": True,
    "supportedLocales": ["fr", "en"],
    "defaultLocale": "fr",
    "loginTheme": "fleetpilot",
    "passwordPolicy": "length(12) and notUsername",
    "roles": {"realm": [{"name": n, "description": d, "composite": False} for n, d in ROLES]},
    "clients": clients,
    "users": users,
    "components": {
        "org.keycloak.storage.UserStorageProvider": [ldap],
        "org.keycloak.userprofile.UserProfileProvider": [{
            "providerId": "declarative-user-profile", "subComponents": {},
            "config": {"kc.user.profile.config": [json.dumps(user_profile, ensure_ascii=False)]},
        }],
    },
}

with open(OUT, "w", encoding="utf-8", newline="\n") as f:
    json.dump(realm, f, indent=2, ensure_ascii=False)
    f.write("\n")
print("written", OUT)
