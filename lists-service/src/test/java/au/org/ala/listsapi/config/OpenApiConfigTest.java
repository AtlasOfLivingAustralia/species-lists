package au.org.ala.listsapi.config;

import org.junit.jupiter.api.Test;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.media.Schema;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class OpenApiConfigTest {
    @Test
    public void testClonePathItemManually() {
        PathItem original = new PathItem();
        Operation op = new Operation();
        op.setOperationId("testId");
        
        Parameter p = new Parameter().name("id").in("path");
        p.setSchema(new Schema().type("string"));
        op.addParametersItem(p);
        
        original.setGet(op);
        
        // Manual clone
        PathItem cloned = new PathItem();
        cloned.setSummary(original.getSummary());
        cloned.setDescription(original.getDescription());
        cloned.setServers(original.getServers());
        cloned.setParameters(original.getParameters());
        cloned.set$ref(original.get$ref());
        cloned.setExtensions(original.getExtensions());
        
        if (original.getGet() != null) cloned.setGet(cloneOperation(original.getGet()));
        if (original.getPut() != null) cloned.setPut(cloneOperation(original.getPut()));
        if (original.getPost() != null) cloned.setPost(cloneOperation(original.getPost()));
        if (original.getDelete() != null) cloned.setDelete(cloneOperation(original.getDelete()));
        if (original.getOptions() != null) cloned.setOptions(cloneOperation(original.getOptions()));
        if (original.getHead() != null) cloned.setHead(cloneOperation(original.getHead()));
        if (original.getPatch() != null) cloned.setPatch(cloneOperation(original.getPatch()));
        if (original.getTrace() != null) cloned.setTrace(cloneOperation(original.getTrace()));

        assertNotSame(original, cloned);
        assertNotSame(original.getGet(), cloned.getGet());
        assertEquals("testId", cloned.getGet().getOperationId());
    }

    private Operation cloneOperation(Operation op) {
        Operation clone = new Operation();
        clone.setTags(op.getTags());
        clone.setSummary(op.getSummary());
        clone.setDescription(op.getDescription());
        clone.setExternalDocs(op.getExternalDocs());
        clone.setOperationId(op.getOperationId());
        clone.setParameters(op.getParameters());
        clone.setRequestBody(op.getRequestBody());
        clone.setResponses(op.getResponses());
        clone.setCallbacks(op.getCallbacks());
        clone.setDeprecated(op.getDeprecated());
        clone.setSecurity(op.getSecurity());
        clone.setServers(op.getServers());
        clone.setExtensions(op.getExtensions());
        return clone;
    }
}
