package com.hzgc.collect;

import com.hzgc.collect.service.FTP;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.util.ClassUtils;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@EnableEurekaClient
public class FtpApplication {
    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(FtpApplication.class, args);
        context.getBean(FTP.class).startFtpServer();
    }
}