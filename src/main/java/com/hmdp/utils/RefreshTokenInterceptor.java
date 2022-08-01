package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author XTY~
 * @version 1.0
 * @Date: 2022-07-25 10:06
 * @Description: 用于刷新token的拦截器
 */
public class RefreshTokenInterceptor implements HandlerInterceptor
{
    /**
     * 使用构造方法注入redis
     */
    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate)
    {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception
    {
        /*
        //1.获取Session
        HttpSession session = request.getSession();
        //2.获取session中的用户
        Object user = session.getAttribute("user");
         */
        // 获取请求头中的token
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token))
        {
            response.setStatus(401);
            return false;
        }
        String key = RedisConstants.LOGIN_USER_TTL + token;
        // 以token为key从redis中获取user的hash数据
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(RedisConstants.LOGIN_USER_KEY + token);
        if (userMap.isEmpty())
        {
            response.setStatus(401);
            return false;
        }
        // 将查询到的Hash数据转化为UserDto对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        //保存用户信息到ThreadLocal中
        UserHolder.saveUser(userDTO);
        // 最后刷新token的有效期，使得用户在发送请求时就会刷新token的有效期使得如果活跃其用户信息的有效生命周期保持在我们设定的30min
        stringRedisTemplate.expire(key,RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception
    {
        //移除用户
        UserHolder.removeUser();
    }
}