package com.unesp.omnileito.frota;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class CacheOcupacao {

	static final String FROTA = "frota:disponiveis";

	private final StringRedisTemplate redis;

	public CacheOcupacao(StringRedisTemplate redis) {
		this.redis = redis;
	}

	public Optional<String> ler(String chave) {
		try {
			return Optional.ofNullable(redis.opsForValue().get(chave));
		} catch (RuntimeException ignorado) {
			return Optional.empty();
		}
	}

	public void gravar(String chave, String valor) {
		try {
			redis.opsForValue().set(chave, valor);
		} catch (RuntimeException ignorado) {
			// A consulta segue pelo MySQL quando o cache não responde.
		}
	}

	public void apagar(String chave) {
		try {
			redis.delete(chave);
		} catch (RuntimeException ignorado) {
			// A próxima consulta reconstrói a lista a partir do MySQL.
		}
	}
}
