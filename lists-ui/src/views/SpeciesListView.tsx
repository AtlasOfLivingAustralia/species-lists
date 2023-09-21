import { useState } from 'react';
import {
    Drawer,
    Grid,
    Pagination,
    ScrollArea,
    Space,
    Switch,
    Table,
    TextInput,
    Title,
    Anchor,
    Group,
    Text,
    Skeleton, Button, List, ThemeIcon, Select, Divider
} from '@mantine/core';
import { gql, useQuery } from '@apollo/client';
import { useParams } from 'react-router-dom';
import SpeciesListSideBar from './SpeciesListSideBar';
import { FormattedMessage } from 'react-intl';
import {
    IconAdjustmentsHorizontal,
    IconSearch,
    IconSquareRoundedXFilled,
    IconSelect,
    IconArrowLeftSquare, IconArrowRightSquare
} from "@tabler/icons-react";
import {
    SpeciesListItem,
    Facet, FacetCount, FilteredSpeciesList, SpeciesList, Classification
} from "../api/sources/model";
import {TaxonImage} from "./TaxonImage";
import {useDisclosure} from "@mantine/hooks";
import {FacetProps, SpeciesListItemProps, SpeciesListProps} from "../api/sources/props.ts";

function SpeciesListView({ setSpeciesList, resetSpeciesList }: SpeciesListProps) {

    const url = new URL(window.location.href);
    const params = url.searchParams;
    const { speciesListID } = useParams<{ speciesListID: string }>();
    const [activePage, setPage] = useState<number>(1);
    const [pageSize, setPageSize] = useState<any>(12);
    const [searchQuery, setSearchQuery] = useState<string | null>(params.get('q') ? params.get('q') : '')

    const [speciesSelected, speciesHandlers] = useDisclosure(false);
    const [filtersSelected, filtersHandlers] = useDisclosure(false);

    const [selectedItem, setSelectedItem] = useState<SpeciesListItem | null>(null);
    const [selectedIndex, setSelectedIndex] = useState<number | null>(null);
    const [selectedFacets, setSelectedFacets] = useState<any[]>([]);

    const selectRow = (selectedItem: SpeciesListItem, index:number) => {
        setSelectedItem(selectedItem);
        setSelectedIndex(index);
        speciesHandlers.open();
    };

    const GET_LIST = gql`
        query loadList($speciesListID: String!, $searchQuery:String, $filters:[Filter], $page: Int, $size: Int) {
            getSpeciesListMetadata(speciesListID: $speciesListID) {
                id
                title
                rowCount
                fieldList
            }
            filterSpeciesList(speciesListID: $speciesListID, searchQuery: $searchQuery, page: $page, size: $size, filters: $filters){
                content {
                    id
                    scientificName
                    properties {
                        key
                        value
                    }
                    classification {
                        scientificName
                        vernacularName
                        taxonConceptID
                        kingdom
                        phylum
                        classs
                        order
                        family
                        genus
                        kingdomID
                        phylumID
                        classID
                        orderID
                        familyID
                        genusID
                    }
                }
                totalPages
                totalElements
            }            
            facetSpeciesList(
                speciesListID: $speciesListID
                searchQuery: $searchQuery
                filters: $filters
                facetFields: []
            ) {
                key
                counts {
                    value
                    count
                }
            }
        }
    `;

    function addToQuery(facetName: string, facetValue: string) {
        const existing = selectedFacets.find((facet) => facet.key === facetName);
        if (existing === undefined) {
            const updatedArray = selectedFacets.concat([
                { key: facetName, value: facetValue },
            ]);
            setSelectedFacets(updatedArray);
        }
    }

    function removeFacet(idx: number) {
        const array3 = selectedFacets.concat([]);
        array3.splice(idx, 1);
        setSelectedFacets(array3);
    }

    const { loading, error, data, refetch } = useQuery<{
        getSpeciesListMetadata: SpeciesList;
        facetSpeciesList: Facet[];
        filterSpeciesList: FilteredSpeciesList;
    }>(GET_LIST, {
        variables: {
            speciesListID: speciesListID,
            filters: [],
            searchQuery: searchQuery,
            page: activePage - 1,
            size: pageSize
        },
    });

    refetch({
        speciesListID: speciesListID,
        filters: selectedFacets
    });

    if (error) return <>`Error! ${error.message}`</>;

    const standardFields: string[] = ['scientificName'];
    const classificationFields: string[] = ['scientificName', 'family', 'kingdom', 'vernacularName'];
    const customFields: string[] | undefined = data?.getSpeciesListMetadata?.fieldList;
    const facets: Facet[] | undefined = data?.facetSpeciesList;

    const totalPages  = data?.filterSpeciesList?.totalPages || 0;
    const totalElements = data?.filterSpeciesList?.totalElements || 0;
    const results = data?.filterSpeciesList?.content || [];

    const selectNextRow = () => {
        if (selectedIndex || selectedIndex == 0) {
            setSelectedItem(results[selectedIndex + 1]);
            setSelectedIndex(selectedIndex + 1);
        }
    };

    const selectPreviousRow = () => {
        if (selectedIndex) {
            setSelectedItem(results[selectedIndex - 1]);
            setSelectedIndex(selectedIndex - 1);
        }
    };

    setSpeciesList(data?.getSpeciesListMetadata);

    return (
        <>
            <Drawer
                opened={filtersSelected}
                onClose={filtersHandlers.close}
                position="left"
                title={
                    <Title order={2}><ThemeIcon color={`gray`}><IconAdjustmentsHorizontal /></ThemeIcon>Query filters</Title>
                }
                padding="xl"
                size="md"
            >
                {selectedFacets && selectedFacets.length > 0 && (
                    <>
                        <Text weight={'bold'}>Selected filters</Text>
                        <List>
                            {selectedFacets.map((facet, idx) => (
                                <SelectedFacet
                                    facet={facet}
                                    idx={idx}
                                    removeFacet={removeFacet}
                                />
                            ))}
                        </List>
                        <Space h="md" />
                    </>
                )}
                <>{facets && facets.map((facet) => <FacetList facet={facet} addToQuery={addToQuery} />)}</>
            </Drawer>
            <Drawer
                opened={speciesSelected}
                onClose={speciesHandlers.close}
                position="right"
                title={
                <>
                    <Title order={2} style={{ fontStyle: 'italic'}}>
                        <Text>{selectedItem?.scientificName}</Text>
                    </Title>
                </>
                }
                padding="xl"
                size="xl"
            >
                <Group style={{ float: 'right'}}>
                    <Button size="xs" variant="outline" disabled={selectedIndex == 0} onClick={selectPreviousRow}><IconArrowLeftSquare/>Previous</Button>
                    <Button size="xs" variant="outline" disabled={selectedIndex === undefined || selectedIndex == null || selectedIndex >= results.length -1} onClick={selectNextRow}><Text style={{ paddingRight: '8px'}}>Next</Text><IconArrowRightSquare/></Button>
                </Group>
                {selectedItem &&
                    <SpeciesListItemView selectedItem={selectedItem} customFields={customFields} loading={loading} />
                }
            </Drawer>
            <Grid mb="md" align="flex-start">
                <Grid.Col xs={1} sm={2} style={{ backgroundColor: '#F6F6F6' }}>
                    <SpeciesListSideBar
                        selectedView="view-list"
                        resetSpeciesList={resetSpeciesList}
                    />
                </Grid.Col>
                <Grid.Col xs={12} sm={10}>
                    <Grid align="center" mb="md">
                        <Grid.Col xs={8} sm={9}>
                            <Group>
                                <Select
                                    value={pageSize}
                                    onChange={setPageSize}
                                    size="sm"
                                    data={[
                                        { value: 12 as any, label: '12 results' },
                                        { value: 50 as any, label: '50 results' },
                                        { value: 100 as any, label: '100 results' },
                                        { value: 1000 as any, label: '1000 results' }
                                    ]}
                                />
                                <TextInput
                                    sx={{ flexBasis: '60%' }}
                                    placeholder="Search within list..."
                                    icon={<IconSearch size={16} />}
                                    value={searchQuery ? searchQuery : ''}
                                    onChange={(e) => setSearchQuery(e.currentTarget.value)}
                                />
                                <Text size="xl">{totalElements} taxa</Text>
                            </Group>
                        </Grid.Col>
                        <Grid.Col xs={8} sm={9}>
                            <Group>
                                <Button className={`filterQueryBtn`} variant="outline"  onClick={filtersHandlers.open}>
                                    <IconAdjustmentsHorizontal />
                                    <Text>Filters</Text>
                                </Button>
                                <Divider size="sm" orientation="vertical" />
                                <>
                                {(!selectedFacets || selectedFacets.length == 0)  && <Text style={{color: 'gray'}}>No filters selected</Text> }
                                {selectedFacets && selectedFacets.length > 0 &&
                                    selectedFacets.map((facet, idx) => <Button variant="outline" color="gray" size="sm" onClick={() => removeFacet(idx)}><IconSquareRoundedXFilled/><FormattedMessage id={facet.key} />: {facet.value}</Button> )
                                }
                                </>
                            </Group>
                        </Grid.Col>
                    </Grid>
                    <ScrollArea scrollbarSize={20}>
                        {loading && (
                            <Table>
                                <thead>
                                <tr key={`skHdr`}>
                                    <th key={`skHdr`}>
                                        <Skeleton height={14} mt={6} radius="xl" />
                                    </th>
                                </tr>
                                </thead>
                                <tbody>
                                <tr key={`skHdrRow1`}>
                                    <td>
                                        <Skeleton height={14} mt={6} radius="xl" />
                                    </td>
                                </tr>
                                <tr key={`skHdrRow2`}>
                                    <td>
                                        <Skeleton height={14} mt={6} radius="xl" />
                                    </td>
                                </tr>
                                <tr key={`skHdrRow3`}>
                                    <td>
                                        <Skeleton height={14} mt={6} radius="xl" />
                                    </td>
                                </tr>
                                </tbody>
                            </Table>
                        )}
                        {!loading && (
                            <Table striped highlightOnHover withColumnBorders>
                                <thead>
                                <tr key={`table-hdr`}>
                                    {standardFields &&
                                        standardFields.map((header) => (
                                            <th style={{
                                                    left: 0,
                                                    whiteSpace: 'nowrap',
                                                }}>
                                                <FormattedMessage id={header} defaultMessage={header} />
                                            </th>
                                        ))}

                                    {customFields &&
                                        customFields.map((header) => (
                                            <th key={header}
                                                style={{
                                                    position: 'sticky',
                                                    whiteSpace: 'nowrap',
                                                }}>
                                                <FormattedMessage id={header} defaultMessage={header} />
                                            </th>
                                        ))}

                                    {classificationFields &&
                                        classificationFields.map((header) => (
                                            <th key={header}
                                                style={{
                                                    position: 'sticky',
                                                    whiteSpace: 'nowrap',
                                                }}>
                                                <FormattedMessage id={`classification.${header}`} defaultMessage={header} />
                                            </th>
                                        ))}
                                </tr>
                                </thead>
                                <tbody>
                                <SearchTable
                                    results={results}
                                    classificationFields={classificationFields}
                                    customFields={data?.getSpeciesListMetadata.fieldList}
                                    selectRow={selectRow}
                                    totalPages={totalPages}
                                    totalElements={totalElements}
                                />
                                </tbody>
                            </Table>
                        )}
                    </ScrollArea>
                    {!loading &&
                        <>
                            <Space h="md" />
                            <Pagination value={activePage} onChange={setPage} total={totalPages} color="gray" />
                        </>
                    }
                </Grid.Col>
            </Grid>
        </>
    );
}

interface SearchTableProps {
    results: SpeciesListItem[];
    classificationFields?: string[];
    customFields?: string[];
    selectRow: (selectedItem: SpeciesListItem, index:number) => void;
    totalPages: number;
    totalElements: number;
}

function SearchTable({ results,
                       classificationFields,
                       customFields,
                       selectRow,
                     }: SearchTableProps) {

    function truncate(input: any | null | undefined): string {
        if (input && input.length > 60) {
            return input.substring(0, 60) + '...';
        }
        return input || '';
    }

    function outputCl(cl:Classification, field:string){
        const propertyName = field as keyof typeof cl;
        if (cl)
            return cl[propertyName];
        return '';
    }

    // @ts-ignore
    return (
        <>
            {results && results.map((speciesListItem:SpeciesListItem, index:number) => (
                    <tr key={speciesListItem.id} onClick={() => selectRow(speciesListItem, index)} style={{ cursor: 'pointer' }}>
                        <td style={{
                                left: 0,
                                whiteSpace: 'nowrap',
                            }}>
                            {/*<HoverCard shadow="xl" >*/}
                            {/*    <HoverCard.Target>*/}
                                    <Group>
                                        <IconSelect />
                                        <Text>{speciesListItem?.scientificName}</Text>
                                    </Group>
                                {/*</HoverCard.Target>*/}
                                {/*<HoverCard.Dropdown>*/}
                                {/*    {speciesListItem?.classification?.scientificName &&*/}
                                {/*        <>*/}
                                {/*            <Text fz="xs">Supplied name: {speciesListItem?.scientificName}</Text>*/}
                                {/*            <Text fz="xs">Matched to: {speciesListItem?.classification?.scientificName}</Text>*/}
                                {/*            <Text fz="xs">Vernacular name: {speciesListItem?.classification?.vernacularName}</Text>*/}
                                {/*        </>*/}
                                {/*    }*/}
                                {/*    {!speciesListItem?.classification && <Text fz="xs">We have not yet matched this name to our classification</Text>}*/}
                                {/*</HoverCard.Dropdown>*/}
                            {/*</HoverCard>*/}
                        </td>
                        {customFields &&
                            customFields.map((customField) => (
                                <td style={{ position: 'sticky' }}>
                                    <Text sx={{ textOverflow: 'ellipsis', overflow: 'hidden' }}>
                                        {truncate(speciesListItem?.properties?.find((element) => element.key === customField)?.value)}
                                    </Text>
                                </td>
                            ))
                        }
                        {speciesListItem && classificationFields &&
                            classificationFields.map((classificationField:string) => (
                                <td style={{ position: 'sticky' }}>
                                    <Text sx={{ textOverflow: 'ellipsis', overflow: 'hidden' }}>
                                        { outputCl(speciesListItem.classification, classificationField) }
                                    </Text>
                                </td>
                            ))
                        }
                    </tr>
                ))}
        </>
    );
}

function SpeciesListItemView({ selectedItem, customFields }: SpeciesListItemProps) {

    const [showInterpretation, setShowInterpretation] = useState<boolean>(false);

    return (
        <>
            <TaxonImage taxonID={selectedItem?.classification?.taxonConceptID || ''} />
            <Title order={3}>Taxonomy</Title>
            <Space h="md" />

            {!showInterpretation && selectedItem?.classification && (
                <Table striped highlightOnHover withBorder>
                    <tbody>
                    <tr>
                        <td>Supplied scientificName</td>
                        <td>{selectedItem.scientificName}</td>
                    </tr>
                    <tr>
                        <td>scientificName</td>
                        <td>
                            <Anchor href={'https://bie.ala.org.au/species/' + selectedItem.classification.taxonConceptID}>
                                {selectedItem.classification?.scientificName}
                            </Anchor>
                        </td>
                    </tr>
                    <tr>
                        <td>vernacularName</td>
                        <td>{selectedItem.classification?.vernacularName}</td>
                    </tr>
                    <tr>
                        <td>family</td>
                        <td>
                            <Anchor href={'https://bie.ala.org.au/species/' + selectedItem.classification.familyID}>
                                {selectedItem.classification?.family}
                            </Anchor>
                        </td>
                    </tr>
                    <tr>
                        <td>kingdom</td>
                        <td>
                            <Anchor href={'https://bie.ala.org.au/species/' + selectedItem.classification.kingdomID}>
                                {selectedItem.classification?.kingdom}
                            </Anchor>
                        </td>
                    </tr>
                    </tbody>
                </Table>
            )}

            {showInterpretation && (
                <Table striped highlightOnHover withBorder>
                    <thead>
                        <tr key="interpretation-header">
                            <th key="no-header"></th>
                            <th key="supplied-header">Supplied</th>
                            <th key="matched-header">Matched</th>
                        </tr>
                    </thead>
                    <tbody>
                    <tr>
                        <td>scientificName</td>
                        <td>{selectedItem?.scientificName}</td>
                        <td>
                            <Anchor href={'https://bie.ala.org.au/species/' + selectedItem?.classification.taxonConceptID}>
                                {selectedItem?.classification?.scientificName}
                            </Anchor>
                        </td>
                    </tr>
                    <tr>
                        <td>vernacularName</td>
                        <td>{selectedItem?.vernacularName}</td>
                        <td>{selectedItem?.classification?.vernacularName}</td>
                    </tr>
                    <tr>
                        <td>genus</td>
                        <td>{selectedItem?.genus}</td>
                        <td>
                            <Anchor href={'https://bie.ala.org.au/species/' + selectedItem.classification.genusID}>
                                {selectedItem?.classification?.genus}
                            </Anchor>
                        </td>
                    </tr>
                    <tr>
                        <td>family</td>
                        <td>{selectedItem?.family}</td>
                        <td>
                            <Anchor href={'https://bie.ala.org.au/species/' + selectedItem.classification.familyID}>
                                {selectedItem?.classification?.family}
                            </Anchor>
                        </td>
                    </tr>
                    <tr>
                        <td>order</td>
                        <td>{selectedItem?.order}</td>
                        <td>
                            <Anchor href={'https://bie.ala.org.au/species/' + selectedItem.classification.orderID}>
                                {selectedItem?.classification?.order}
                            </Anchor>
                        </td>
                    </tr>
                    <tr>
                        <td>class</td>
                        <td>{selectedItem?.classs}</td>
                        <td>
                            <Anchor href={'https://bie.ala.org.au/species/' + selectedItem.classification.classID}>
                                {selectedItem?.classification?.classs}
                            </Anchor>
                        </td>
                    </tr>
                    <tr>
                        <td>phylum</td>
                        <td>{selectedItem?.phylum}</td>
                        <td>
                            <Anchor href={'https://bie.ala.org.au/species/' + selectedItem.classification.phylumID}>
                                {selectedItem?.classification?.phylum}
                            </Anchor>
                        </td>
                    </tr>
                    <tr>
                        <td>kingdom</td>
                        <td>{selectedItem?.kingdom}</td>
                        <td>
                            <Anchor href={'https://bie.ala.org.au/species/' + selectedItem.classification.kingdomID}>
                                {selectedItem?.classification?.kingdom}
                            </Anchor>
                        </td>
                    </tr>
                    </tbody>
                </Table>
            )}

            <Text style={{ paddingTop: '15px', paddingBottom: '15px' }}>
                <FormattedMessage id="Note: Interpreted values are matched against the national checklists. These may differ from the original values supplied." />
            </Text>

            <Switch
                id="interpretation"
                label="Show full taxonomy"
                checked={showInterpretation}
                onChange={() => setShowInterpretation(!showInterpretation)}
            />

            {customFields && customFields.length > 0 && <>
                <Space h="md" />

                <Title order={3}>Properties</Title>
                <Space h="md" />
                <Table striped highlightOnHover>
                    <thead>
                    <tr>
                        <th key={`customPropertyHdr`}>Property</th>
                        <th key={`customPropertyValueHdr`}>Value</th>
                    </tr>
                    </thead>
                    <tbody>
                    {customFields?.map((customField) => (
                        <tr key={customField}>
                            <td>{customField}</td>
                            <td>{selectedItem?.properties.find((element) => element.key === customField)?.value}</td>
                        </tr>
                    ))}
                    </tbody>
                </Table>
                <div style={{ height: '300px' }}></div>
                <Space h="md" />
            </>}
        </>
    );
}

interface SelectedFacetProps {
    facet: { key: string; value: string };
    idx: number;
    removeFacet: (idx: number) => void;
}

export function SelectedFacet({ facet, idx, removeFacet }: SelectedFacetProps) {
    return (
        <Button variant={"outline"} onClick={() => removeFacet(idx)}>
            <IconSquareRoundedXFilled />
            <FormattedMessage id={facet.key} /> : {facet.value || 'Not supplied'}
        </Button>
    );
}

export function FacetList({ facet, addToQuery }: FacetProps) {
    if (facet.counts.length === 0) return null;

    return (
        <>
            <Title order={6}>
                <FormattedMessage id={facet.key} />
            </Title>
            <List>
                {facet.counts.map((facetCount: FacetCount) => (
                    <List.Item onClick={() => addToQuery(facet.key, facetCount.value)}>
                        <Text fz="sm" style={{ color: '#c44d34', cursor: 'pointer' }}>
                            {facetCount.value || 'Not supplied'} ({facetCount.count})
                        </Text>
                    </List.Item>
                ))}
            </List>
        </>
    );
}

export default SpeciesListView;
