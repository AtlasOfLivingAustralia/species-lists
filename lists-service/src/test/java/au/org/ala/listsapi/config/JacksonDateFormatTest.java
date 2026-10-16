package au.org.ala.listsapi.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import au.org.ala.listsapi.model.SpeciesList;
import tools.jackson.databind.json.JsonMapper;

class JacksonDateFormatTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withPropertyValues("spring.jackson.datatype.datetime.write-dates-as-timestamps=true")
            .withUserConfiguration(WebConfig.class)
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class,
                    HttpMessageConvertersAutoConfiguration.class,
                    WebMvcAutoConfiguration.class));

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
    void webMvcHttpMessageConvertersSerializeDatesAsNumericTimestamps() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(RequestMappingHandlerAdapter.class);
            RequestMappingHandlerAdapter adapter = context.getBean(RequestMappingHandlerAdapter.class);
            List<HttpMessageConverter<?>> converters = adapter.getMessageConverters();

            JacksonJsonHttpMessageConverter jsonConverter = converters.stream()
                    .filter(JacksonJsonHttpMessageConverter.class::isInstance)
                    .map(JacksonJsonHttpMessageConverter.class::cast)
                    .findFirst()
                    .orElseThrow();

            Date now = new Date(1779062778909L);
            SpeciesList list = SpeciesList.builder()
                    .id("test-list-2")
                    .dateCreated(now)
                    .lastUpdated(now)
                    .build();

            String json = jsonConverter.getMapper().writeValueAsString(list);
            assertTrue(json.contains("\"dateCreated\":1779062778909"));
            assertTrue(json.contains("\"lastUpdated\":1779062778909"));
            assertFalse(json.contains("\"dateCreated\":\""));
        });
    }
}
