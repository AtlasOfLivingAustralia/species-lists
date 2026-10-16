package au.org.ala.listsapi.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.http.converter.autoconfigure.ServerHttpMessageConvertersCustomizer;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;

import au.org.ala.listsapi.model.SpeciesList;
import tools.jackson.databind.json.JsonMapper;

class JacksonDateFormatTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withPropertyValues("spring.jackson.datatype.datetime.write-dates-as-timestamps=true")
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class,
                    HttpMessageConvertersAutoConfiguration.class));

    @Test
    void dateFieldsAreSerializedAsNumericTimestamps() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(JsonMapper.class);
            JsonMapper mapper = context.getBean(JsonMapper.class);

            Date now = new Date(1779062778909L);
            SpeciesList list = SpeciesList.builder()
                    .id("test-list-1")
                    .dateCreated(now)
                    .lastUpdated(now)
                    .build();

            String json = mapper.writeValueAsString(list);

            // Ensure date fields are serialized as epoch numbers, not ISO-8601 strings
            assertTrue(json.contains("\"dateCreated\":1779062778909"));
            assertTrue(json.contains("\"lastUpdated\":1779062778909"));
            assertFalse(json.contains("\"dateCreated\":\""));
        });
    }

    @Test
    void serverHttpMessageConvertersConfiguredWithNumericTimestamps() {
        contextRunner.run(context -> {
            assertThat(context).hasBean("jacksonJsonHttpMessageConvertersCustomizer");
            ServerHttpMessageConvertersCustomizer customizer =
                    context.getBean("jacksonJsonHttpMessageConvertersCustomizer", ServerHttpMessageConvertersCustomizer.class);

            HttpMessageConverters.ServerBuilder builder = HttpMessageConverters.forServer();
            customizer.customize(builder);
            HttpMessageConverters converters = builder.build();

            JacksonJsonHttpMessageConverter jsonConverter = StreamSupport.stream(converters.spliterator(), false)
                    .filter(JacksonJsonHttpMessageConverter.class::isInstance)
                    .map(JacksonJsonHttpMessageConverter.class::cast)
                    .findFirst()
                    .orElseThrow();

            Date now = new Date(1779062778909L);
            SpeciesList list = SpeciesList.builder()
                    .id("test-list-2")
                    .dateCreated(now)
                    .build();

            String json = jsonConverter.getMapper().writeValueAsString(list);
            assertTrue(json.contains("\"dateCreated\":1779062778909"));
            assertFalse(json.contains("\"dateCreated\":\""));
        });
    }
}
