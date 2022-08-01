package com.hmdp;

import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.service.impl.ShopTypeServiceImpl;
import com.hmdp.service.impl.UserServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.session.StandardSession;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.servlet.http.HttpSession;

@SpringBootTest
@RunWith(SpringRunner.class)
@Slf4j
class HmDianPingApplicationTests {

    @Autowired
    private UserServiceImpl service;

    @Autowired
    private ShopTypeServiceImpl service1;

    @Test
    public void login()
    {
        Result result = service1.queryList();
        System.out.println(result);
    }


}
