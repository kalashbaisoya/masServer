package com.mas.masServer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MasServerApplication {

	public static void main(String[] args) {
		System.out.println( "Hello World!" );
		SpringApplication.run(MasServerApplication.class, args);
	}

}
