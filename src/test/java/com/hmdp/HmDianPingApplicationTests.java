package com.hmdp;

import com.hmdp.dto.LoginFormDTO;
import com.hmdp.entity.User;
import com.hmdp.service.impl.UserServiceImpl;
import org.apache.catalina.session.StandardSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.servlet.http.HttpSession;

@SpringBootTest
class HmDianPingApplicationTests {

    @Autowired
    private UserServiceImpl service;

    @Test
    private void login()
    {

    }


}
