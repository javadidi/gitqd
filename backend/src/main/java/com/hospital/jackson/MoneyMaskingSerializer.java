package com.hospital.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.hospital.security.LoginUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.math.BigDecimal;

public class MoneyMaskingSerializer extends JsonSerializer<Object> {

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        if (currentRoleIsNurse()) {
            gen.writeNull();
            return;
        }
        if (value instanceof BigDecimal) {
            gen.writeNumber((BigDecimal) value);
        } else {
            gen.writeNumber(((Number) value).longValue());
        }
    }

    private boolean currentRoleIsNurse() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        Object principal = auth.getPrincipal();
        return principal instanceof LoginUser && "nurse".equals(((LoginUser) principal).getRoleName());
    }
}
