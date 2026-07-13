package com.lemon.music.musicbackservice;

import com.lemon.music.musicbackservice.auth.AuthProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AuthProperties.class)
@MapperScan("com.lemon.music.musicbackservice.user.mapper")
public class MusicBackServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MusicBackServiceApplication.class, args);
    }

}
