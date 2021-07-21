package cc.uncarbon.module.interceptor;

import cc.uncarbon.framework.core.props.HelioProperties;
import cn.dev33.satoken.interceptor.SaRouteInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.web.servlet.ModelAndView;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Collections;

/**
 * 路由拦截器，自定义验证规则
 * http://sa-token.dev33.cn/doc/index.html#/use/route-check
 * @author Uncarbon
 */
public class SaTokenRouteInterceptor extends SaRouteInterceptor {

    @Resource
    private HelioProperties helioProperties;


    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) {
        // 登录验证
        SaRouter.match(
                Collections.singletonList("/**")
                , helioProperties.getSecurity().getExcludeRoutes()
                , StpUtil::checkLogin
        );
    }
}
