package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.UserInfo;
import com.hmdp.mapper.UserInfoMapper;
import com.hmdp.service.IUserInfoService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RegexPatterns;
import com.hmdp.utils.RegexUtils;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-24
 */
@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements IUserInfoService {

    @Override
    public Result sendCode(String phone, HttpSession session)
    {
        //1.验证手机号
        if (RegexUtils.isPhoneInvalid(phone))
        {
            //手机号不符合，返回错误信息
            return Result.fail("手机格式有误");
        }
        //2.手机号符合，生成验证码
        String code = RandomUtil.randomNumbers(6);
        //3.保存验证码到session中
        session.setAttribute("code",code);
        //4.发送验证码

        return Result.ok();
    }
}
