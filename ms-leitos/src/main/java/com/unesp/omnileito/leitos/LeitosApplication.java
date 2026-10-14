package com.unesp.omnileito.leitos;

import com.unesp.omnileito.comum.ErrosAdvice;
import com.unesp.omnileito.comum.MensageriaConfig;
import com.unesp.omnileito.comum.SegurancaConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@Import({SegurancaConfig.class, ErrosAdvice.class, MensageriaConfig.class})
public class LeitosApplication {

	public static void main(String[] args) {
		SpringApplication.run(LeitosApplication.class, args);
	}
}
