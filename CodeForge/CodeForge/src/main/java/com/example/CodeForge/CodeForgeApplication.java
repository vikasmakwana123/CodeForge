package com.example.CodeForge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class CodeForgeApplication {

	public static void main(String[] args) {
		SpringApplication.run(CodeForgeApplication.class, args);
	}

}
