package com.unesp.omnileito.comum;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ErrosAdvice {

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ErroApi> api(ApiException erro) {
		return ResponseEntity.status(erro.getStatus()).body(new ErroApi(erro.getCodigo(), erro.getMessage()));
	}

	@ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
	public ResponseEntity<ErroApi> corpoInvalido() {
		return ResponseEntity.badRequest().body(new ErroApi("CAMPO_INVALIDO", "Campo ausente ou valor fora da lista fechada."));
	}
}
