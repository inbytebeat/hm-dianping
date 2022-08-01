package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshTokenInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

/**
 * @author XTY~
 * @version 1.0
 * @Date: 2022-07-25 10:17
 * @Description: SpringMvc的拦截器
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer
{
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    //注册我们定义的拦截器 使我们自定义的拦截器生效
    @Override
    public void addInterceptors(InterceptorRegistry registry)
    {
        //配置免拦截的路径(登录拦截器)
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(
                        "/user/code",
                        "/user/login",
                        "/shop/**",
                        "/voucher/**",
                        "/shop-type/**",
                        "/upload/**",
                        "/blog/hot"
                ).order(1);
        // 拦截器默认以添加顺序进行拦截 也可以设置order的大小进行设置 order越小优先级越高
        // 设置token刷新的拦截器
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate)).excludePathPatterns(
                "/user/code",
                "/user/login").addPathPatterns("/**").order(0);
    }

}