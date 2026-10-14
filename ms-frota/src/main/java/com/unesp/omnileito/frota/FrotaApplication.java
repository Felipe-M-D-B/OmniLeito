package com.unesp.omnileito.frota;

import com.unesp.omnileito.comum.ErrosAdvice;
import com.unesp.omnileito.comum.MensageriaConfig;
import com.unesp.omnileito.comum.SegurancaConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import({SegurancaConfig.class, ErrosAdvice.class, MensageriaConfig.class})
public class FrotaApplication {

	public static void main(String[] args) {
		SpringApplication.run(FrotaApplication.class, args);
	}
}
