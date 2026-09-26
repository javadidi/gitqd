package com.hospital.config;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.hospital.jackson.MoneyMaskingModifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    @Bean
    public Module moneyMaskingModule() {
        SimpleModule module = new SimpleModule("moneyMasking");
        module.setSerializerModifier(new MoneyMaskingModifier());
        return module;
    }
}
