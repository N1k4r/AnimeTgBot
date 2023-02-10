package io.proj3ct.TelegramBot;

import Testing.test;
import io.proj3ct.TelegramBot.service.Schedule;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Schedules;

@SpringBootApplication
@EnableScheduling
public class TelegramBotApplication {

	public static void main(String[] args)
	{
		SpringApplication.run(TelegramBotApplication.class, args);
	}

}
