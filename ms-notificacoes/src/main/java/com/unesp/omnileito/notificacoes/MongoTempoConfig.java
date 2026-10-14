package com.unesp.omnileito.notificacoes;

import com.mongodb.MongoClientSettings;
import org.bson.codecs.configuration.CodecRegistries;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

import java.time.OffsetDateTime;

@Configuration
public class MongoTempoConfig {

	@Bean
	MongoCustomConversions conversoesDeTempo() {
		return MongoCustomConversions.create(config -> {
			config.registerConverter(new Converter<OffsetDateTime, String>() {
				@Override
				public String convert(OffsetDateTime origem) {
					return origem.toString();
				}
			});
			config.registerConverter(new Converter<String, OffsetDateTime>() {
				@Override
				public OffsetDateTime convert(String origem) {
					return OffsetDateTime.parse(origem);
				}
			});
		});
	}

	@Bean
	@Order(Ordered.LOWEST_PRECEDENCE)
	MongoClientSettingsBuilderCustomizer offsetDateTime() {
		return builder -> builder.codecRegistry(CodecRegistries.fromRegistries(
				CodecRegistries.fromCodecs(new OffsetDateTimeCodec()),
				MongoClientSettings.getDefaultCodecRegistry()));
	}
}
