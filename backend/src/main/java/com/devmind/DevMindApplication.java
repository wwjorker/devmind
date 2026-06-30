package com.devmind;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan(basePackages = "com.devmind.module", markerInterface = BaseMapper.class)
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class DevMindApplication {

    public static void main(String[] args) {
        SpringApplication.run(DevMindApplication.class, args);
    }
}
