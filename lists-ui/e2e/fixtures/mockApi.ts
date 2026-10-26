import { Page } from '@playwright/test';
import {
  mockConstraints,
  mockFacets,
  mockItemFacets,
  sampleList1,
  sampleList2,
  sampleList3Private,
  sampleListItems,
} from './mockData';

export interface MockApiOptions {
  lists?: any[];
  listItems?: any[];
  constraints?: any;
}

export async function setupMockApi(page: Page, options: MockApiOptions = {}) {
  const currentLists = options.lists ? [...options.lists] : [sampleList1, sampleList2, sampleList3Private];
  const currentItems = options.listItems ? [...options.listItems] : [...sampleListItems];
  const constraints = options.constraints || mockConstraints;

  // Intercept banner messages
  await page.route('**/v2/banner**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([]),
    });
  });

  // Intercept Admin migrate progress
  await page.route('**/admin/migrate/progress', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(null),
    });
  });

  // Intercept Admin indexes
  await page.route('**/admin/indexes', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([]),
    });
  });

  // Intercept CSRF
  await page.route('**/csrf', async (route) => {
    await route.fulfill({
      status: 200,
      headers: {
        'Set-Cookie': 'XSRF-TOKEN=mock-csrf-token; Path=/',
      },
      contentType: 'application/json',
      body: JSON.stringify({ token: 'mock-csrf-token' }),
    });
  });

  // Intercept Constraints
  await page.route('**/v2/constraints', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(constraints),
    });
  });

  // Intercept Upload
  await page.route('**/v2/upload', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        localFile: 'tmp/Acacia_characters-testing.csv',
        facetList: [],
        fieldList: ['habit', 'flowerColor'],
        originalFieldNames: ['scientificName', 'vernacularName', 'habit', 'flowerColor'],
        rowCount: 3,
        validationErrors: null,
      }),
    });
  });

  // Intercept Ingest Progress
  await page.route('**/v2/ingest/*/progress', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 'ingest-1',
        speciesListId: 'list-acacia-1',
        rowCount: 3,
        elasticTotal: 3,
        mongoTotal: 3,
        started: Date.now(),
        completed: true,
      }),
    });
  });

  // Intercept Ingest & Reingest (/v2/ingest and /v2/ingest/:id)
  await page.route(/\/v2\/ingest(?:\/[^/]+)?$/, async (route) => {
    if (route.request().method() === 'POST') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: 'list-acacia-1',
          title: 'Acacia characters testing',
        }),
      });
      return;
    }
    await route.continue();
  });

  // Intercept Download
  await page.route('**/v2/download/*', async (route) => {
    const csvContent =
      'scientificName,vernacularName,habit,flowerColor\n"Acacia dealbata","Silver Wattle","Tree","Yellow"\n';
    await route.fulfill({
      status: 200,
      contentType: 'text/csv',
      headers: {
        'Content-Disposition': 'attachment; filename="Acacia_characters-testing.csv"',
      },
      body: csvContent,
    });
  });

  // Intercept Delete
  await page.route('**/v2/delete/*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ success: true }),
    });
  });

  // Intercept Rematch
  await page.route('**/v2/rematch/*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ status: 'COMPLETE' }),
    });
  });

  // Intercept GraphQL
  await page.route('**/graphql', async (route) => {
    const request = route.request();
    if (request.method() !== 'POST') {
      await route.continue();
      return;
    }

    let body: any = {};
    try {
      body = request.postDataJSON() || {};
    } catch {
      await route.continue();
      return;
    }

    const { operationName, query, variables = {} } = body;

    // 1. QUERY_LISTS_SEARCH (findList)
    if (operationName === 'findList' || (query && query.includes('lists('))) {
      let filtered = [...currentLists];

      // Check if user ID filter is provided (My Lists page)
      if (variables.userId) {
        filtered = filtered.filter((l) => l.owner === variables.userId);
      } else if (variables.isPrivate === false) {
        filtered = filtered.filter((l) => !l.isPrivate);
      }

      // Check if searchQuery is provided
      if (variables.searchQuery) {
        const q = variables.searchQuery.toLowerCase();
        filtered = filtered.filter(
          (l) =>
            l.title.toLowerCase().includes(q) ||
            (l.description && l.description.toLowerCase().includes(q))
        );
      }

      // Check facet filters
      if (Array.isArray(variables.filters) && variables.filters.length > 0) {
        for (const f of variables.filters) {
          if (f.key === 'listType') {
            filtered = filtered.filter((l) => l.listType === f.value);
          }
          if (f.key === 'licence') {
            filtered = filtered.filter((l) => l.licence === f.value);
          }
          if (f.key === 'isAuthoritative') {
            filtered = filtered.filter((l) => String(l.isAuthoritative) === f.value);
          }
          if (f.key === 'isSDS') {
            filtered = filtered.filter((l) => String(l.isSDS) === f.value);
          }
        }
      }

      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            lists: {
              content: filtered,
              totalPages: 1,
              totalElements: filtered.length,
              __typename: 'SpeciesListPage',
            },
            facets: mockFacets,
            baseFacets: mockFacets,
          },
        }),
      });
      return;
    }

    // 2. QUERY_LISTS_GET (loadList)
    if (operationName === 'loadList' || (query && query.includes('getSpeciesListMetadata'))) {
      const listId = variables.speciesListID || 'list-acacia-1';
      const meta = currentLists.find((l) => l.id === listId) || currentLists[0] || sampleList1;

      let items = [...currentItems];
      if (variables.searchQuery) {
        const q = variables.searchQuery.toLowerCase();
        items = items.filter(
          (i) =>
            i.scientificName.toLowerCase().includes(q) ||
            (i.vernacularName && i.vernacularName.toLowerCase().includes(q))
        );
      }

      // Filter by facet
      if (Array.isArray(variables.filters) && variables.filters.length > 0) {
        for (const f of variables.filters) {
          if (f.key === 'order') {
            items = items.filter((i) => i.order === f.value);
          }
        }
      }

      // Sort
      if (variables.sort) {
        const dir = variables.dir === 'desc' ? -1 : 1;
        const getNestedVal = (obj: any, path: string): string => {
          const val = path.split('.').reduce((acc, part) => (acc != null ? acc[part] : undefined), obj);
          return val != null ? String(val) : '';
        };
        items.sort((a: any, b: any) => {
          const valA = getNestedVal(a, variables.sort);
          const valB = getNestedVal(b, variables.sort);
          return valA.localeCompare(valB) * dir;
        });
      }

      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            meta,
            list: {
              content: items,
              totalPages: 1,
              totalElements: items.length,
            },
            facets: mockItemFacets,
            baseFacets: mockItemFacets,
          },
        }),
      });
      return;
    }

    // 3. MUTATION_LIST_UPDATE (updateMetadata)
    if (
      (query && query.includes('updateMetadata')) ||
      (operationName === 'update' && variables.id && !variables.editItem)
    ) {
      const targetIndex = currentLists.findIndex((l) => l.id === variables.id);
      if (targetIndex >= 0) {
        currentLists[targetIndex] = {
          ...currentLists[targetIndex],
          ...variables,
        };
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            updateMetadata: { id: variables.id },
          },
        }),
      });
      return;
    }

    // 4. MUTATION_LIST_ITEM_CREATE (create)
    if (operationName === 'create' || (query && query.includes('addSpeciesListItem'))) {
      const newItem = {
        id: `item-${Date.now()}`,
        scientificName: variables.createItem?.scientificName || 'Acacia dealbata',
        vernacularName: 'Silver Wattle',
        genus: 'Acacia',
        family: 'Fabaceae',
        order: 'Fabales',
        classs: 'Magnoliopsida',
        phylum: 'Tracheophyta',
        kingdom: 'Plantae',
        properties: [],
        classification: {
          scientificName: variables.createItem?.scientificName || 'Acacia dealbata',
          vernacularName: 'Silver Wattle',
          family: 'Fabaceae',
          order: 'Fabales',
          rank: 'species',
          matchType: 'exactMatch',
        },
        dateCreated: new Date().toISOString(),
        lastUpdated: new Date().toISOString(),
      };
      currentItems.push(newItem as any);

      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            newItem,
          },
        }),
      });
      return;
    }

    // 5. MUTATION_LIST_ITEM_UPDATE (updateSpeciesListItem)
    if (
      (query && query.includes('updateSpeciesListItem')) ||
      (operationName === 'update' && variables.editItem)
    ) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            newItem: currentItems[0],
          },
        }),
      });
      return;
    }

    // 6. MUTATION_LIST_ITEM_DELETE (delete / removeSpeciesListItem)
    if (operationName === 'delete' || (query && query.includes('removeSpeciesListItem'))) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            removeSpeciesListItem: { id: variables.id },
          },
        }),
      });
      return;
    }

    // 7. Field mutations: addField, removeField, renameField
    if (
      operationName === 'addField' ||
      operationName === 'removeField' ||
      operationName === 'renameField' ||
      (query && (query.includes('addField') || query.includes('removeField') || query.includes('renameField')))
    ) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            [operationName || 'field']: { id: variables.id || 'list-acacia-1' },
          },
        }),
      });
      return;
    }

    // 8. QUERY_IMAGE_GET
    if (operationName === 'loadImage' || (query && query.includes('getTaxonImage'))) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            image: { url: 'https://images.ala.org.au/image/proxyImageThumbnailLarge?imageId=1' },
          },
        }),
      });
      return;
    }

    await route.continue();
  });
}
