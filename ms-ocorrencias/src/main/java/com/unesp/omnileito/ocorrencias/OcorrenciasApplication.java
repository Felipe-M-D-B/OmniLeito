package com.unesp.omnileito.ocorrencias;

import com.unesp.omnileito.comum.ErrosAdvice;
import com.unesp.omnileito.comum.MensageriaConfig;
import com.unesp.omnileito.comum.SegurancaConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import({SegurancaConfig.class, ErrosAdvice.class, MensageriaConfig.class})
public class OcorrenciasApplication {

	public static void main(String[] args) {
		SpringApplication.run(OcorrenciasApplication.class, args);
	}
}
