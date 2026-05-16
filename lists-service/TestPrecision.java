import co.elastic.clients.elasticsearch._types.aggregations.CardinalityAggregation;

public class TestPrecision {
    public static void main(String[] args) {
        CardinalityAggregation.Builder builder = new CardinalityAggregation.Builder();
        builder.field("foo").precisionThreshold(40000);
    }
}
