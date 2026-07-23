package com.lemon.music.musicbackservice;

import com.lemon.music.musicbackservice.auth.AuthProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(AuthProperties.class)
@EnableScheduling
@MapperScan({"com.lemon.music.musicbackservice.user.mapper", "com.lemon.music.musicbackservice.membership.mapper"})
public class MusicBackServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MusicBackServiceApplication.class, args);
    }

}
