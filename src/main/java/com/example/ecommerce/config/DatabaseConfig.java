package com.example.ecommerce.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import javax.sql.DataSource;

@Configuration
public class DatabaseConfig {

    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://ep-dry-snow-a2z6wx87-pooler.eu-central-1.aws.neon.tech:5432/verceldb?sslmode=require");
        config.setUsername("default");
        config.setPassword("FE8afJtkv0PR");
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.addDataSourceProperty("ssl", "true");
        config.addDataSourceProperty("sslmode", "require");

        return new HikariDataSource(config);
    }
}