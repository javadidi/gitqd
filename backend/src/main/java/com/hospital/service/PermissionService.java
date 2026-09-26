package com.hospital.service;

import com.hospital.enums.Capability;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PermissionService {

    public static final String MODULE_DASHBOARD = "dashboard";
    public static final String MODULE_SCHEDULE = "schedule";
    public static final String MODULE_APPOINTMENT = "appointment";
    public static final String MODULE_FINANCE = "finance";
    public static final String MODULE_REPORT = "report";
    public static final String MODULE_PHYSICAL = "physical";
    public static final String MODULE_SETTINGS = "settings";
    public static final String MODULE_SYSTEM = "system";

    public static final List<String> ALL_MODULES = Collections.unmodifiableList(Arrays.asList(
            MODULE_DASHBOARD, MODULE_SCHEDULE, MODULE_APPOINTMENT, MODULE_FINANCE,
            MODULE_REPORT, MODULE_PHYSICAL, MODULE_SETTINGS, MODULE_SYSTEM
    ));

    private static final Map<String, List<String>> ROLE_MODULES = new HashMap<>();
    private static final Map<String, List<Capability>> ROLE_CAPS = new HashMap<>();

    static {
        ROLE_MODULES.put("system", ALL_MODULES);
        ROLE_MODULES.put("admin", ALL_MODULES);
        ROLE_MODULES.put("doctor", Arrays.asList(MODULE_DASHBOARD, MODULE_SCHEDULE, MODULE_APPOINTMENT, MODULE_REPORT));
        ROLE_MODULES.put("nurse", Arrays.asList(MODULE_DASHBOARD, MODULE_SCHEDULE, MODULE_APPOINTMENT,
                MODULE_REPORT, MODULE_PHYSICAL, MODULE_SYSTEM));

        ROLE_CAPS.put("system", Arrays.asList(Capability.values()));
        ROLE_CAPS.put("admin", Arrays.asList(Capability.values()));
        ROLE_CAPS.put("doctor", Collections.emptyList());
        ROLE_CAPS.put("nurse", Collections.emptyList());
    }

    public List<String> getModules(String roleName) {
        return ROLE_MODULES.getOrDefault(roleName, Collections.emptyList());
    }

    public List<Capability> getCaps(String roleName) {
        return ROLE_CAPS.getOrDefault(roleName, Collections.emptyList());
    }

    public boolean hasCap(String roleName, Capability cap) {
        return getCaps(roleName).contains(cap);
    }

    public boolean hasModule(String roleName, String module) {
        return getModules(roleName).contains(module);
    }
}
