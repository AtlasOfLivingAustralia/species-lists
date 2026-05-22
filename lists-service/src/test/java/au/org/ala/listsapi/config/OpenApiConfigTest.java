package au.org.ala.listsapi.config;

import org.junit.jupiter.api.Test;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.media.Schema;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class OpenApiConfigTest {
    @Test
    public void testWsAliasCustomiserCreatesDistinctWsOperationWithUniqueOperationId() {
        OpenAPI openAPI = new OpenAPI();
        PathItem original = new PathItem();
        Operation op = new Operation();
        op.setOperationId("testId");

        Parameter p = new Parameter().name("id").in("path");
        p.setSchema(new Schema().type("string"));
        op.addParametersItem(p);

        original.setGet(op);

        Paths paths = new Paths();
        paths.addPathItem("/lists/{id}", original);
        openAPI.setPaths(paths);

        new OpenApiConfig().wsAliasCustomiser().customise(openAPI);

        PathItem wsAliased = openAPI.getPaths().get("/ws/lists/{id}");
        assertNotNull(wsAliased);
        assertNotSame(original, wsAliased);
        assertNotNull(wsAliased.getGet());
        assertNotSame(original.getGet(), wsAliased.getGet());
        assertEquals("testId", original.getGet().getOperationId());
        assertEquals("testId_ws", wsAliased.getGet().getOperationId());
        assertNotEquals(original.getGet().getOperationId(), wsAliased.getGet().getOperationId());
    }
}
