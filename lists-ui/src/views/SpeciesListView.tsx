import {useContext, useState} from 'react';
import {
    Drawer,
    Grid,
    Pagination,
    ScrollArea,
    Space,
    Table,
    TextInput,
    Title,
    Group,
    Text,
    Skeleton, Button, List, ThemeIcon, Select, Divider, CloseButton, Switch, HoverCard, Card
} from '@mantine/core';
import {useQuery} from '@apollo/client';
import {useLocation, useParams} from 'react-router-dom';
import SpeciesListSideBar from './SpeciesListSideBar';
import {FormattedMessage, FormattedNumber} from 'react-intl';
import {
    IconAdjustmentsHorizontal,
    IconSearch,
    IconSquareRoundedXFilled,
    IconSelect,
    IconArrowLeftSquare, IconArrowRightSquare, IconDownload
} from "@tabler/icons-react";
import {
    SpeciesListItem,
    Facet, FilteredSpeciesList, SpeciesList, Classification, ListsUser
} from "../api/sources/model";
import {useDisclosure} from "@mantine/hooks";
import { SpeciesListProps} from "../api/sources/props.ts";
import {SpeciesListItemEditor} from "./SpeciesListItemEditor.tsx";
import {GET_LIST} from "../api/sources/graphql.ts";
import {SpeciesListItemView} from "./SpeciesListItemView.tsx";
import UserContext from "../helpers/UserContext.ts";
import {SelectedFacet} from "../components/SelectedFacet.tsx";
import {FacetList} from "../components/FacetList.tsx";
import {TaxonImage} from "./TaxonImage.tsx";


function SpeciesListView({ setSpeciesList, resetSpeciesList }: SpeciesListProps) {

    const location = useLocation();
    let previousQuery = location.state?.searchQuery ? location.state.searchQuery : '';
    const { speciesListID } = useParams<{ speciesListID: string }>();
    const [activePage, setPage] = useState<number>(1);
    const [pageSize, setPageSize] = useState<any>(12);
    const [searchQuery, setSearchQuery] = useState<string | null>(previousQuery);

    const [speciesSelected, speciesHandlers] = useDisclosure(false);
    const [filtersSelected, filtersHandlers] = useDisclosure(false);

    const [selectedIndex, setSelectedIndex] = useState<number | null>(null);
    const [selectedFacets, setSelectedFacets] = useState<any[]>([]);

    const [isEditing, setisEditing] = useState(false);

    const currentUser = useContext(UserContext) as ListsUser;

    const selectRow = (index:number) => {
        setSelectedIndex(index);
        speciesHandlers.open();
    };

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

    function download() {
        fetch(import.meta.env.VITE_DOWNLOAD_URL + "/" + speciesListID, {
            method: 'GET',
            headers: {
                'Content-Type': 'application/zip',
                'Authorization': 'Bearer ' + currentUser?.user.access_token
            }
        })
        .then( res => res.blob() )
        .then( blob => {
            var url = window.URL.createObjectURL(blob);
            var a = document.createElement('a');
            a.href = url;
            a.download = "species-list-" + speciesListID + ".csv";
            document.body.appendChild(a); // we need to append the element to the dom -> otherwise it will not work in firefox
            a.click();
            a.remove();
        });
    }

    if (error) return <>`Error! ${error.message}`</>;

    const classificationFields: string[] = ['family', 'kingdom', 'vernacularName'];
    const customFields: string[] | undefined = data?.getSpeciesListMetadata?.fieldList;
    const facets: Facet[] | undefined = data?.facetSpeciesList;

    const totalPages  = data?.filterSpeciesList?.totalPages || 0;
    const totalElements = data?.filterSpeciesList?.totalElements || 0;
    const results = data?.filterSpeciesList?.content || [];
    const speciesList = data?.getSpeciesListMetadata;

    setSpeciesList(speciesList);

    let selectedItem = selectedIndex != null ? results[selectedIndex] : null;

    const selectNextRow = () => {
        if (selectedIndex || selectedIndex == 0) {
            selectedItem = results[selectedIndex + 1];
            setSelectedIndex(selectedIndex + 1);
        }
    };

    const selectPreviousRow = () => {
        if (selectedIndex) {
            selectedItem = results[selectedIndex - 1];
            setSelectedIndex(selectedIndex - 1);
        }
    };

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
                size="md">
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
                onClose={() => {
                    setisEditing(false);
                    speciesHandlers.close();
                }}
                position="right"
                title={
                    <>
                        <Group noWrap={true}>
                            <Title order={2} style={{ fontStyle: 'italic'}}>
                                <Text>{selectedItem?.scientificName}</Text>
                            </Title>
                            <Switch
                                onLabel="EDIT"
                                offLabel="EDIT"
                                checked={isEditing}
                                size="lg"
                                disabled={
                                    currentUser?.user?.access_token === undefined
                                    || (!currentUser.isAdmin && currentUser.userId != speciesList?.owner)
                                }
                                onChange={(event) => setisEditing(event.currentTarget.checked)}
                            />
                        </Group>
                    </>
                }
                padding="xl"
                size="xl">

                { !selectedItem && isEditing &&
                    <SpeciesListItemEditor
                        speciesListID={speciesListID}
                        selectedItem={ {} as SpeciesListItem}
                        currentFilters={selectedFacets}
                        customFields={customFields}
                        loading={loading}
                        setIsEditing={setisEditing}
                        resetSelectedIndex={() => {
                            setSelectedIndex(null);
                            speciesHandlers.close();
                        }}
                    />
                }

                { selectedItem && !isEditing && <>
                    <Group>
                        <Button size="xs" variant="outline" disabled={selectedIndex == 0} onClick={selectPreviousRow}>
                            <IconArrowLeftSquare/>
                            <Text>Previous</Text>
                        </Button>
                        <Button size="xs" variant="outline" disabled={selectedIndex === undefined || selectedIndex == null || selectedIndex >= results.length -1} onClick={selectNextRow}>
                            <Text style={{ paddingRight: '8px'}}>Next</Text>
                            <IconArrowRightSquare/>
                        </Button>
                    </Group>
                   <Space h="xl" />
                   <SpeciesListItemView selectedItem={selectedItem} customFields={customFields} loading={loading} />
                </>}

                { selectedItem && isEditing &&
                    <SpeciesListItemEditor
                        speciesListID={speciesListID}
                        selectedItem={selectedItem}
                        currentFilters={selectedFacets}
                        customFields={customFields}
                        loading={loading}
                        setIsEditing={setisEditing}
                        resetSelectedIndex={() => {
                            setSelectedIndex(null);
                            speciesHandlers.close();
                        }}
                    />}

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
                        <Grid.Col xs={8} sm={12}>
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
                                    sx={{ flexBasis: '45%' }}
                                    placeholder="Search within list..."
                                    icon={<IconSearch size={16} />}
                                    value={searchQuery ? searchQuery : ''}
                                    rightSection={
                                        <CloseButton
                                            aria-label="Clear input"
                                            onClick={() => setSearchQuery('')}
                                            style={{ display: searchQuery ? undefined : 'none' }}
                                        />
                                    }
                                    onChange={(e) => setSearchQuery(e.currentTarget.value)}
                                />
                                <Group style={{paddingRight:"40px"}}>
                                    {loading && <><Skeleton height={30} width={50} /></>}
                                    {!loading && <><Text><FormattedNumber value={totalElements} /> taxa</Text></>}
                                </Group>
                                <Button variant="outline" onClick={download}><IconDownload />Download</Button>
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
                                    {[...Array(8)].map((_,) => (
                                    <th key={`skHdr`}>
                                        <Skeleton height={14} mt={6} radius="xl" />
                                    </th>
                                    ))}
                                </tr>
                                </thead>
                                <tbody>
                                {[...Array(12)].map((_, rowlIndex) => (
                                    <tr key={`skHdrRow${rowlIndex}`}>
                                        {[...Array(8)].map((_, colIndex) => (
                                        <td>
                                            <Group noWrap={true}>
                                                {colIndex == 0 && <IconSelect />}
                                                <Skeleton height={14} mt={6}  />
                                            </Group>
                                        </td>
                                        ))}
                                    </tr>
                                ))}
                                </tbody>
                            </Table>
                        )}
                        {!loading && (
                            <Table striped highlightOnHover withColumnBorders>
                                <thead>
                                <tr key={`table-hdr`}>
                                    <th style={{
                                            left: 0,
                                            whiteSpace: 'nowrap',
                                        }}>
                                        Supplied name
                                    </th>
                                    <th>
                                        Scientific name (matched)
                                    </th>
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
                                <tbody style={{ verticalAlign :'top'}}>
                                {!loading && (!results || results.length == 0) && <tr><td colSpan={8}>No results found</td></tr>}
                                {!loading && results &&
                                    <SearchTable
                                        results={results}
                                        classificationFields={classificationFields}
                                        customFields={data?.getSpeciesListMetadata?.fieldList}
                                        selectRow={selectRow}
                                        totalPages={totalPages}
                                        totalElements={totalElements}
                                    />
                                }
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
    selectRow: (index:number) => void;
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
                    <tr key={speciesListItem.id} onClick={() => selectRow(index)} style={{ cursor: 'pointer' }}>
                        <td style={{
                                left: 0,
                                whiteSpace: 'nowrap',
                            }}>
                            <Group noWrap={true}>
                                <IconSelect />
                                <Text>{speciesListItem?.scientificName}</Text>
                            </Group>
                        </td>
                        <td style={{
                            left: 0,
                            whiteSpace: 'nowrap',
                        }}>
                            <HoverCard shadow="md"  openDelay={1000} position={`right-end`}>
                                <HoverCard.Target>
                                  <Text><i>{speciesListItem?.classification?.scientificName}</i></Text>
                                </HoverCard.Target>
                                <HoverCard.Dropdown styles={{ padding: '0', margin: '0'}}>
                                    <Card radius="md" >
                                        <Card.Section>
                                            <TaxonImage taxonID={speciesListItem?.classification?.taxonConceptID} />
                                        </Card.Section>
                                        <Group>
                                            <Text size="sm">
                                                <i>{speciesListItem?.classification?.scientificName}</i>
                                            </Text>
                                            <Text size="sm">
                                                {speciesListItem?.classification?.scientificNameAuthorship}
                                            </Text>
                                        </Group>
                                        <Text size="sm">
                                            {speciesListItem?.classification?.vernacularName}
                                        </Text>
                                        {/*<Text size="sm" style={{ maxWidth: "250px", wordWrap: 'break-word'}}>*/}
                                        {/*    <ul>*/}
                                        {/*        <li>{speciesListItem?.classification?.kingdom}</li>*/}
                                        {/*        <li>{speciesListItem?.classification?.phylum}</li>*/}
                                        {/*        <li>{speciesListItem?.classification?.classs}</li>*/}
                                        {/*        <li>{speciesListItem?.classification?.order}</li>*/}
                                        {/*        <li>{speciesListItem?.classification?.family}</li>*/}
                                        {/*        <li>{speciesListItem?.classification?.genus}</li>*/}
                                        {/*    </ul>*/}
                                        {/*</Text>*/}
                                    </Card>
                                </HoverCard.Dropdown>
                            </HoverCard>
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


export default SpeciesListView;
