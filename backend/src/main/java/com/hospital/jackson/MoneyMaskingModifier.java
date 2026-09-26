package com.hospital.jackson;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;

public class MoneyMaskingModifier extends BeanSerializerModifier {

    private static final Pattern MONEY_NAME = Pattern.compile(
            "amount|price|fen|gross|receivable|received|outstanding|discount|diff",
            Pattern.CASE_INSENSITIVE);

    private static final JsonSerializer<Object> MASKING = new MoneyMaskingSerializer();

    @Override
    public List<BeanPropertyWriter> changeProperties(SerializationConfig config,
                                                     BeanDescription beanDesc,
                                                     List<BeanPropertyWriter> beanProperties) {
        for (BeanPropertyWriter writer : beanProperties) {
            if (isNumericType(writer) && MONEY_NAME.matcher(writer.getName()).find()) {
                writer.assignSerializer(MASKING);
            }
        }
        return beanProperties;
    }

    private boolean isNumericType(BeanPropertyWriter writer) {
        Class<?> raw = writer.getType().getRawClass();
        return Number.class.isAssignableFrom(raw) || raw == long.class || raw == int.class;
    }
}
