package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author XTY~
 * @version 1.0
 * @Date: 2022-07-25 10:06
 * @Description: 该拦截器仅用于判断用户是否存在
 */
public class LoginInterceptor implements HandlerInterceptor
{
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception
    {
        // 首先判断ThreadLocal中是否存有用户数据
        if(UserHolder.getUser() == null)
        {
            // 如果没有 则进行拦截 并且设置状态码
            response.setStatus(401);
            return false;
        }
        return true;
    }
}