package com.devmind;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.devmind.module")
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class DevMindApplication {

    public static void main(String[] args) {
        SpringApplication.run(DevMindApplication.class, args);
    }
}
