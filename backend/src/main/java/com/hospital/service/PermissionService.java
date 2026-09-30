package com.hospital.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.enums.Capability;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    private final ObjectMapper objectMapper;

    public PermissionService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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

    /**
     * V2__init_admin.sql:7-10 建立的四行角色。
     *
     * <p>它们的<b>名字</b>就是上面 {@code ROLE_CAPS} 这张表的键：写权限不落库
     * （原因见 {@code Capability} 的类注释：V2 的 role 表没有 caps 列），
     * 所以把 'admin' 改名成 'ADMIN2' 不会报错，只会让那个角色静默失去全部写权限。
     * T28 的「角色管理：CRUD + 权限配置」（卡片 763 行）因此对这四行只开读：
     * 改名会抽掉权限，删除会让 V2 的四个演示账号变成无主角色，
     * 而规格里没有任何一句写"误改了怎么恢复"。自定义角色走完整的增删改。
     */
    public static final Set<String> CORE_ROLES = Set.of("system", "admin", "doctor", "nurse");

    public static boolean isCoreRole(String roleName) {
        return CORE_ROLES.contains(roleName);
    }

    /**
     * V2:7 给 system 角色的 permissions 写的是 {@code ["*"]}，读作"全部模块"。
     * 这里是它唯一的特殊值，别的字符串一律按字面的模块键处理。
     */
    public static final String MODULE_WILDCARD = "*";

    /**
     * 登录时模块列表的真值来源（T28 卡片 763 行「权限配置」的落点）。
     *
     * <p><b>为什么改成读库</b>：{@code role.permissions}（V1:392 JSON NOT NULL）从 V1 起就是
     * PRD 596 行「角色 | 角色ID、名称、<b>权限列表</b>」那一行的落点，V2 也照着它写了四行，
     * 但 T03 之后登录一直只读上面那张静态表——于是后台"配置权限"这个动作没有任何去处，
     * 新建的角色既不在静态表里也不在别处，模块列表为空、侧边栏空白。
     *
     * <p>改完对四个内置角色<b>零行为变化</b>：逐行核对过，V2 的 JSON 与静态表内容一致
     * （admin 是全 8 个、doctor 是那 4 个、nurse 是 6 个、system 走通配），
     * 所以 J5/J6 那批 T03 的断言原样成立。差别只出现在自定义角色上：它从此按页面上勾的走。
     *
     * <p>解析不出合法模块键时退回静态表（不返回空列表）：一个被手改坏的 JSON
     * 应该让那个人回到他原本该有的入口，而不是把他关在门外——关在门外会让人以为
     * "权限配置"这个功能在吞权限。未知键直接丢弃：不发明模块，也不因为多写了一个键就报错。
     */
    public List<String> resolveModules(String roleName, String permissionsJson) {
        List<String> fromJson = parseModules(permissionsJson);
        return fromJson.isEmpty() ? getModules(roleName) : fromJson;
    }

    /** 把 permissions JSON 解析成模块键列表；不是数组、解析失败、全是未知键都返回空列表。 */
    public List<String> parseModules(String permissionsJson) {
        if (permissionsJson == null || permissionsJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<String> raw = objectMapper.readValue(permissionsJson, new TypeReference<List<String>>() {
            });
            if (raw.contains(MODULE_WILDCARD)) {
                return ALL_MODULES;
            }
            List<String> known = new ArrayList<>();
            for (String item : raw) {
                if (item != null && ALL_MODULES.contains(item) && !known.contains(item)) {
                    known.add(item);
                }
            }
            return known;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
