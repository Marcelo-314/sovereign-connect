package com.sovereign.eib.ref;

import com.sovereign.eib.northbound.dto.NorthboundTemporalActViewDto;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

public final class EibEffectiveRefCodec {

    private final byte[] secret;

    public EibEffectiveRefCodec(String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String generateRef(String type, String habitatId, String canonicalId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] hash = mac.doFinal((habitatId + ":" + type + ":" + canonicalId).getBytes(StandardCharsets.UTF_8));
            return type + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(hash).substring(0, 22);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("EIB ref codec failure", e);
        }
    }

    public Optional<String> resolveTemporalRef(
            String suppliedRef,
            String habitatId,
            List<NorthboundTemporalActViewDto> visible
    ) {
        return visible.stream()
                .filter(act -> generateRef("eib.temporal", habitatId, act.temporalActId()).equals(suppliedRef))
                .map(NorthboundTemporalActViewDto::temporalActId)
                .findFirst();
    }
}
