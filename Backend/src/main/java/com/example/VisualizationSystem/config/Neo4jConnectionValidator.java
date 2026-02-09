package com.example.VisualizationSystem.config;

import org.neo4j.driver.Driver;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Neo4jConnectionValidator {

    @Bean
    CommandLineRunner validateNeo4j(Driver driver) {
        return args -> {
            try (var session = driver.session()) {
                session.run("RETURN 1").consume();
                System.out.println("✅ Neo4j is reachable");
            }
        };
    }
}
