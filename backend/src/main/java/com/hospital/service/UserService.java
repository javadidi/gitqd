package com.hospital.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.common.ErrorCode;
import com.hospital.dto.UserProfileResponse;
import com.hospital.dto.WechatLoginResponse;
import com.hospital.entity.User;
import com.hospital.exception.BizException;
import com.hospital.mapper.UserMapper;
import com.hospital.util.JwtUtil;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * 小程序端用户：微信授权登录 + 个人中心（T07）。
 *
 * <p>红线：小程序端的 userId 一律由 controller 从 token 取出后传进来，
 * 本类不接受任何"请求里带的 userId"，避免横向越权（附录 B 第 806 条）。
 */
@Service
public class UserService {

    private final UserMapper userMapper;
    private final WechatService wechatService;
    private final SmsCodeService smsCodeService;
    private final CryptoService cryptoService;
    private final JwtUtil jwtUtil;

    public UserService(UserMapper userMapper,
                       WechatService wechatService,
                       SmsCodeService smsCodeService,
                       CryptoService cryptoService,
                       JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.wechatService = wechatService;
        this.smsCodeService = smsCodeService;
        this.cryptoService = cryptoService;
        this.jwtUtil = jwtUtil;
    }

    /**
     * code → openid → user（查不到就建）→ 小程序 token。
     *
     * <p>刻意不加 @Transactional：这里只有一次 insert，没有跨表原子性需求；
     * 而一旦包在事务里，下面 catch 住的 DuplicateKeyException 会把事务标成 rollback-only，
     * 重查出来的用户到提交时照样回滚，反而制造一个查不出原因的 500。
     */
    public WechatLoginResponse loginByWechat(String code) {
        String openid = wechatService.code2openid(code);

        User user = selectByOpenid(openid);
        boolean newUser = false;
        if (user == null) {
            user = new User();
            user.setWechatOpenid(openid);
            try {
                userMapper.insert(user);
            } catch (DuplicateKeyException e) {
                // 并发首次登录：两个请求都没查到就都去插，uk_openid 只放一个过。
                // 输的那个重查即可，绝不能因此建出第二个 user（J15 要验的就是这条）。
                user = selectByOpenid(openid);
                if (user == null) {
                    throw new BizException(ErrorCode.WECHAT_LOGIN_FAILED);
                }
            }
            newUser = true;
        }

        WechatLoginResponse response = new WechatLoginResponse();
        response.setToken(jwtUtil.generateUserToken(user.getId(), openid));
        response.setUserId(user.getId());
        response.setNickname(user.getNickname());
        response.setAvatarUrl(user.getAvatarUrl());
        response.setHasPhone(cryptoService.decrypt(user.getPhone()) != null);
        response.setNewUser(newUser);
        return response;
    }

    public UserProfileResponse getProfile(Long userId) {
        return toProfile(requireUser(userId));
    }

    public void updateNickname(Long userId, String nickname) {
        User user = requireUser(userId);
        user.setNickname(nickname.trim());
        userMapper.updateById(user);
    }

    /** 绑定手机号第一步：发码。要求已登录，未登录的人不该消耗短信额度 */
    public void sendBindPhoneCode(Long userId, String phone) {
        requireUser(userId);
        smsCodeService.send(phone);
    }

    /** 绑定手机号第二步：验码 + 加密落库（J16） */
    public UserProfileResponse bindPhone(Long userId, String phone, String code) {
        User user = requireUser(userId);
        if (!smsCodeService.verifyAndConsume(phone, code)) {
            throw new BizException(ErrorCode.SMS_CODE_INVALID);
        }
        user.setPhone(cryptoService.encrypt(phone));
        userMapper.updateById(user);
        return toProfile(user);
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    private User selectByOpenid(String openid) {
        return userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getWechatOpenid, openid));
    }

    private UserProfileResponse toProfile(User user) {
        String phone = cryptoService.decrypt(user.getPhone());
        UserProfileResponse response = new UserProfileResponse();
        response.setUserId(user.getId());
        response.setNickname(user.getNickname());
        response.setAvatarUrl(user.getAvatarUrl());
        response.setHasPhone(phone != null);
        response.setPhone(maskPhone(phone));
        return response;
    }

    /** 手机号只回打码值：个人中心只需要展示，前端没有理由拿到完整号码 */
    private static String maskPhone(String phone) {
        if (phone == null || phone.length() != 11) {
            return null;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
