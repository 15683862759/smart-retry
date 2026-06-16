package com.smart.retry.test;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.pool.DruidPooledConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.SQLException;

/**
 * @Author xiaoqiang
 * @Version DataSource.java, v 0.1 2025年02月19日 19:02 xiaoqiang
 * @Description: TODO
 */
@Configuration
public class DataSourceConfiguration {

    @Bean
    public DataSource dataSource() throws SQLException {
        DruidDataSource dataSource = new DruidDataSource();
        // mysql-connector-java 8.x 驱动类
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUsername("root");
        dataSource.setPassword("root");
        dataSource.setUrl("jdbc:mysql://127.0.0.1:3306/charging?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false");
        DruidPooledConnection connection = dataSource.getConnection();
        return dataSource;
    }

}
