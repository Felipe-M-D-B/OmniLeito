package com.unesp.omnileito.identidade;

import com.unesp.omnileito.comum.ErrosAdvice;
import com.unesp.omnileito.comum.SegurancaConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import({SegurancaConfig.class, ErrosAdvice.class})
public class IdentidadeApplication {

	public static void main(String[] args) {
		SpringApplication.run(IdentidadeApplication.class, args);
	}
}
