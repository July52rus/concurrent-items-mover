package com.iulii.records_mover;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
public class RecordsMoverApplication {

	public static void main(String[] args) {
		SpringApplication.run(RecordsMoverApplication.class, args);
	}

}
