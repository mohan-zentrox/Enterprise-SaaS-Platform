package com.zentrox.forge.sso;

/**
 * FRD-13.1.
 *
 * SAML is modelled and storable but not yet implemented at the protocol level - see
 * SsoConnectionService, which rejects enabling it rather than pretending. Having the value here
 * means the schema and the API do not need to change when it lands.
 */
public enum SsoProtocol {
    OIDC,
    SAML
}
