package cc.uncarbon.module.api.auth;


import cc.uncarbon.framework.core.constant.HelioConstant;
import cc.uncarbon.framework.core.context.UserContext;
import cc.uncarbon.framework.core.context.UserContextHolder;
import cc.uncarbon.framework.web.model.response.ApiResult;
import cc.uncarbon.module.sys.enums.UserTypeEnum;
import cc.uncarbon.module.sys.facade.SysUserFacade;
import cc.uncarbon.module.sys.model.request.SysUserLoginDTO;
import cc.uncarbon.module.sys.model.response.SysUserLoginBO;
import cc.uncarbon.module.util.AdminStpUtil;
import cn.dev33.satoken.annotation.SaCheckLogin;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;


/**
 * @author Uncarbon
 */
@Slf4j
@Api(value = "SAAS鉴权接口", tags = {"SAAS鉴权接口"})
@RequestMapping(HelioConstant.Version.SAAS_API_VERSION_V1 + "/auth")
@RestController
public class AdminAuthController {

    @DubboReference(version = HelioConstant.Version.DUBBO_VERSION_V1, validation = HelioConstant.Dubbo.ENABLE_VALIDATION)
    private SysUserFacade sysUserFacade;


    @ApiOperation(value = "登录", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PostMapping(value = "/login")
    public ApiResult<?> login(@RequestBody @Valid SysUserLoginDTO dto) {
        // RPC调用, 失败抛异常, 成功返回用户信息
        SysUserLoginBO userInfo = sysUserFacade.adminLogin(dto);

        // 构造用户上下文
        UserContext userContext = UserContext.builder()
                .userId(userInfo.getId())
                .userName(userInfo.getUsername())
                .mobile(userInfo.getMobile())
                .userType(UserTypeEnum.ADMIN_USER)
                .extraData(null)
                .roles(userInfo.getRoles())
                .permissions(userInfo.getPermissions())
                .relationalTenant(userInfo.getRelationalTenant())
                .build();

        // 注册到SA-Token
        AdminStpUtil.login(userInfo.getId(), dto.getRememberMe());
        AdminStpUtil.getSession().set("userContext", userContext);


        // 返回登录token
        Map<String, Object> tokenInfo = new HashMap<>(16);
        tokenInfo.put("tokenName", AdminStpUtil.getTokenName());
        tokenInfo.put("tokenValue", AdminStpUtil.getTokenValue());
        tokenInfo.put("roles", userInfo.getRoles());
        tokenInfo.put("permissions", userInfo.getPermissions());

        return ApiResult.data("登录成功", tokenInfo);
    }

    @SaCheckLogin(type = AdminStpUtil.TYPE)
    @ApiOperation(value = "登出", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PostMapping(value = "/logout")
    public ApiResult<?> logout() {
        AdminStpUtil.logout();
        UserContextHolder.setUserContext(null);

        return ApiResult.success();
    }

}
