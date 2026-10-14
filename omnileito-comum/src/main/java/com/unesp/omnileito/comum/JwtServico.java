package com.unesp.omnileito.comum;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

public class JwtServico {

	private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
	private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

	private final byte[] segredo;

	public JwtServico(String segredo) {
		this.segredo = segredo.getBytes(StandardCharsets.UTF_8);
	}

	public String emitir(String usuario, long segundos) {
		String header = ENCODER.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
		long exp = Instant.now().plusSeconds(segundos).getEpochSecond();
		String payload = ENCODER.encodeToString(("{\"sub\":\"" + usuario + "\",\"exp\":" + exp + "}").getBytes(StandardCharsets.UTF_8));
		return header + "." + payload + "." + assinar(header + "." + payload);
	}

	public String validar(String token) {
		if (token == null || token.isBlank()) {
			return null;
		}
		String[] partes = token.split("\\.");
		if (partes.length != 3 || !assinar(partes[0] + "." + partes[1]).equals(partes[2])) {
			return null;
		}
		String json = new String(DECODER.decode(partes[1]), StandardCharsets.UTF_8);
		long exp = Long.parseLong(extrair(json, "exp"));
		if (Instant.now().getEpochSecond() >= exp) {
			return null;
		}
		return extrair(json, "sub");
	}

	private String extrair(String json, String campo) {
		String chave = "\"" + campo + "\":";
		int inicio = json.indexOf(chave);
		if (inicio < 0) {
			return "";
		}
		inicio += chave.length();
		if (json.charAt(inicio) == '"') {
			int fim = json.indexOf('"', inicio + 1);
			return json.substring(inicio + 1, fim);
		}
		int fim = inicio;
		while (fim < json.length() && Character.isDigit(json.charAt(fim))) {
			fim++;
		}
		return json.substring(inicio, fim);
	}

	private String assinar(String conteudo) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(segredo, "HmacSHA256"));
			return ENCODER.encodeToString(mac.doFinal(conteudo.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception erro) {
			throw new IllegalStateException("Falha ao assinar o token.", erro);
		}
	}
}
