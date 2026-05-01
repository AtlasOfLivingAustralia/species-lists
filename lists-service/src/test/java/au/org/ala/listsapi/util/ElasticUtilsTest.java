package au.org.ala.listsapi.util;

import static org.junit.jupiter.api.Assertions.*;

import au.org.ala.listsapi.model.Classification;
import au.org.ala.listsapi.model.Filter;
import au.org.ala.listsapi.model.KeyValue;
import au.org.ala.listsapi.model.SpeciesListIndex;
import au.org.ala.listsapi.model.SpeciesListItem;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

class ElasticUtilsTest {

  @Test
  void testConvert() {
    SpeciesListIndex index = new SpeciesListIndex();
    index.setId(new ObjectId().toString());
    index.setSpeciesListID("list123");
    index.setSuppliedName("Macropus giganteus");
    index.setScientificName("Macropus giganteus");
    index.setVernacularName("Eastern Grey Kangaroo");
    index.setTaxonID("taxon1");
    index.setKingdom("Animalia");
    index.setFamily("Macropodidae");

    List<KeyValue> properties = new ArrayList<>();
    properties.add(new KeyValue("habitat", "terrestrial"));
    index.setProperties(properties);

    Classification classification = new Classification();
    classification.setTaxonConceptID("tc1");
    index.setClassification(classification);

    index.setDateCreated("2023-01-01T10:00:00Z");

    SpeciesListItem item = ElasticUtils.convert(index);

    assertNotNull(item);
    assertEquals(index.getId(), item.getId().toString());
    assertEquals("list123", item.getSpeciesListID());
    assertEquals("Macropus giganteus", item.getSuppliedName());
    assertEquals("Macropus giganteus", item.getScientificName());
    assertEquals("Eastern Grey Kangaroo", item.getVernacularName());
    assertEquals("taxon1", item.getTaxonID());
    assertEquals("Animalia", item.getKingdom());
    assertEquals("Macropodidae", item.getFamily());
    assertEquals("habitat", item.getProperties().get(0).getKey());
    assertEquals("terrestrial", item.getProperties().get(0).getValue());
    assertEquals("tc1", item.getClassification().getTaxonConceptID());
    assertNotNull(item.getDateCreated());
  }

  @Test
  void testConvertList() {
    SpeciesListIndex i1 = new SpeciesListIndex();
    i1.setId(new ObjectId().toString());
    SpeciesListIndex i2 = new SpeciesListIndex();
    i2.setId(new ObjectId().toString());

    List<SpeciesListItem> items = ElasticUtils.convertList(Arrays.asList(i1, i2));

    assertEquals(2, items.size());
    assertEquals(i1.getId(), items.get(0).getId().toString());
    assertEquals(i2.getId(), items.get(1).getId().toString());
  }

  @Test
  void testAddOrUpdateFilter() {
    List<Filter> filters = new ArrayList<>();

    Filter f1 = new Filter();
    f1.setKey("kingdom");
    f1.setValue("Animalia");

    filters = ElasticUtils.addOrUpdateFilter(filters, f1);
    assertEquals(1, filters.size());
    assertEquals("Animalia", filters.get(0).getValue());

    // Update existing filter
    Filter f2 = new Filter();
    f2.setKey("kingdom");
    f2.setValue("Plantae");

    filters = ElasticUtils.addOrUpdateFilter(filters, f2);
    assertEquals(1, filters.size());
    assertEquals("Plantae", filters.get(0).getValue());

    // Add new filter
    Filter f3 = new Filter();
    f3.setKey("family");
    f3.setValue("Rosaceae");

    filters = ElasticUtils.addOrUpdateFilter(filters, f3);
    assertEquals(2, filters.size());
  }

  @Test
  void testAddOrUpdateFilterWithNullList() {
    Filter f1 = new Filter();
    f1.setKey("kingdom");
    f1.setValue("Animalia");

    List<Filter> filters = ElasticUtils.addOrUpdateFilter(null, f1);
    assertNotNull(filters);
    assertEquals(1, filters.size());
    assertEquals("Animalia", filters.get(0).getValue());
  }
}
