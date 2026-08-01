package com.mamoki.ieojuda;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class IeojudaApplication {

	public static void main(String[] args) {
		SpringApplication.run(IeojudaApplication.class, args);
	}

}
