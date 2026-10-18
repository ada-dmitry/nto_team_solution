package ru.nto.team

import com.fasterxml.jackson.databind.cfg.CoercionAction
import com.fasterxml.jackson.databind.cfg.CoercionInputShape
import com.fasterxml.jackson.databind.type.LogicalType
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.format.DateTimeFormatter

@Configuration
class JsonConfiguration {
    @Bean fun jsonCustomizer() = Jackson2ObjectMapperBuilderCustomizer { builder ->
        builder.serializers(LocalDateTimeSerializer(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")))
        builder.postConfigurer { mapper ->
            for (shape in listOf(CoercionInputShape.Integer, CoercionInputShape.Float,
                CoercionInputShape.Boolean, CoercionInputShape.Array, CoercionInputShape.Object)) {
                mapper.coercionConfigFor(LogicalType.Textual).setCoercion(shape, CoercionAction.Fail)
            }
        }
    }
}
