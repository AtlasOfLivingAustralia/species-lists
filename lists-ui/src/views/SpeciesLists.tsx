import {
    Pagination,
    Space,
    Table,
    Grid,
    TextInput,
    Group,
    Text, Skeleton, CloseButton, Select, Drawer, Title, ThemeIcon, Button, Divider, List
} from "@mantine/core";
import { useQuery } from "@apollo/client";
import {useState} from "react";
import {
    IconAdjustmentsHorizontal,
    IconList,
    IconSearch, IconSquareRoundedXFilled
} from '@tabler/icons-react';
import {useNavigate} from "react-router-dom";
import {FormattedMessage, FormattedNumber} from "react-intl";
import { Dispatch, SetStateAction } from "react";
import {Facet, SpeciesList, SpeciesListPage} from "../api/sources/model.ts";
import {SpeciesListsSideBar} from "./SpeciesListsSideBar.tsx";
import {SEARCH_SPECIES_LISTS} from "../api/sources/graphql.ts";
import {useDisclosure} from "@mantine/hooks";
import {FacetList} from "../components/FacetList.tsx";
import {SelectedFacet} from "../components/SelectedFacet.tsx";


function SpeciesLists({isPrivate}: {isPrivate: boolean})  {

    const navigate = useNavigate();
    const [activePage, setPage] = useState<number>(1);
    const [searchQuery, setSearchQuery] = useState<string>("");
    const [pageSize, setPageSize] = useState<number>(12);
    const [filtersSelected, filtersHandlers] = useDisclosure(false);
    const [selectedFacets, setSelectedFacets] = useState<any[]>([]);


    const { loading, error, data, refetch} = useQuery<{
        lists: SpeciesListPage;
        facetSpeciesLists: Facet[];
    }>(SEARCH_SPECIES_LISTS, {
        fetchPolicy: "no-cache",
        variables: {
            searchQuery: searchQuery,
            page: activePage - 1,
            size: pageSize,
            filters: [],
            isPrivate: isPrivate,
        },
    });

    refetch({
        searchQuery: searchQuery,
        page: activePage - 1,
        size: pageSize,
        filters: selectedFacets,
        isPrivate: isPrivate,
    });

    const facets = data?.facetSpeciesLists;

    function selectSpeciesList(speciesList: SpeciesList) {
        navigate(`/list/` + speciesList.id, {state: {searchQuery: searchQuery}} );
    }

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
                <>{facets && facets.map((facet) => <FacetList facet={facet} addToQuery={addToQuery} hideCount={true} />)}</>
            </Drawer>
            <Grid mb="md">
                <Grid.Col xs={1} sm={2}>
                    <SpeciesListsSideBar resetSpeciesList={() => console.log('reset list')}
                         selectedView={isPrivate ? "private-lists" : "species-lists"}
                    />
                </Grid.Col>
                <Grid.Col xs={12} sm={10}>
                    <Grid align="center" mb="md">
                        <Grid.Col xs={8} sm={9}>
                            <Group>
                                <Select
                                    value={pageSize.toString()}
                                    onChange={(value:string) => setPageSize(parseInt(value || "12"))}
                                    size="sm"
                                    data={[
                                        { value: "12", label: '12 results' },
                                        { value: "50", label: '50 results' },
                                        { value: "100", label: '100 results' },
                                    ]}
                                />
                                <TextInput
                                    sx={{ flexBasis: "60%" }}
                                    placeholder="Search across lists..."
                                    icon={<IconSearch size={16} />}
                                    value={searchQuery}
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
                                    {!loading && <><Text>{data?.lists?.totalElements} lists</Text></>}
                                </Group>
                            </Group>
                            <Space h={"md"}/>
                            <Group>
                                <Button className={`filterQueryBtn`} variant="outline"  onClick={filtersHandlers.open}>
                                    <IconAdjustmentsHorizontal />
                                    <Text>Filters</Text>
                                </Button>
                                <Divider size="sm" orientation="vertical" />
                                <>
                                    {(!selectedFacets || selectedFacets.length == 0)  && <Text style={{color: 'gray'}}>No filters selected</Text> }
                                    {selectedFacets && selectedFacets.length > 0 &&
                                        selectedFacets.map((facet, idx) => <Button variant="outline" color="gray" size="sm" onClick={() => removeFacet(idx)}><IconSquareRoundedXFilled/><FormattedMessage id={facet.key} />: <FormattedMessage id={facet.value} defaultMessage={facet.value} /></Button> )
                                    }
                                </>
                            </Group>
                        </Grid.Col>
                    </Grid>
                    <SearchTable
                        searchQuery={searchQuery}
                        activePage={activePage}
                        pageSize={pageSize}
                        setPage={setPage}
                        selectSpeciesList={selectSpeciesList}
                        isPrivate={isPrivate}
                        loading={loading}
                        error={error}
                        data={data}
                    />
                </Grid.Col>
            </Grid>
        </>
    );
}

interface SearchTableProps {
    searchQuery: string;
    activePage: number;
    pageSize: number;
    isPrivate: boolean
    loading: boolean;
    error: any;
    data: any;
    setPage: Dispatch<SetStateAction<number>>;
    selectSpeciesList: (speciesList: SpeciesList) => void;
}

export function SearchTable({
                                activePage,
                                setPage,
                                selectSpeciesList,
                                loading,
                                error,
                                data,
                            }: SearchTableProps) {

    if (loading) return <>
        <Table verticalSpacing="xs" fontSize="md">
            <thead>
            <tr>
                <th>Species list name</th>
                <th>Category</th>
                <th>Taxa</th>
                <th></th>
            </tr>
            </thead>
            <tbody>
            {[...Array(10)].map((_, index) => (
                <tr key={index}>
                    <td >
                        <Group>
                            <IconList /> <Skeleton height={30} width={Math.floor(Math.random() * (500 - 200 + 1) + 200)} />
                        </Group>
                    </td>
                    <td>
                         <Skeleton height={30} width={Math.floor(Math.random() * (150 - 100 + 1) + 100)}/>
                    </td>
                    <td >
                        <Skeleton height={30} width={Math.floor(Math.random() * (50 - 30 + 1) + 100)}/>
                    </td>
                </tr>
            ))}
            </tbody>
        </Table>
    </>;

    if (error) return null;

    const rows = data?.lists?.content?.map((dataset: any) => (
        <tr
            id={dataset.id}
            key={dataset.id}
            style={{ cursor: "pointer" }}>
            <td onClick={() => selectSpeciesList(dataset)}>
                <Group>
                    <IconList />
                    <Text>{dataset.title}</Text>
                </Group>
            </td>
            <td onClick={() => selectSpeciesList(dataset)}>
                <FormattedMessage id={dataset.listType} />
            </td>
            <td onClick={() => selectSpeciesList(dataset)}>
                <FormattedNumber value={dataset.rowCount} />
            </td>
        </tr>
    ));

    return (
        <>
            <Table verticalSpacing="xs" fontSize="md">
                <thead>
                <tr>
                    <th>Species list name</th>
                    <th>Category</th>
                    <th>Taxa</th>
                    <th></th>
                </tr>
                </thead>
                <tbody>
                    {rows}
                    {(!rows || rows.length ==0) && <tr><td colSpan={4}>No lists found matching the search</td></tr>}
                </tbody>
            </Table>
            <Space h="md" />
            <Pagination value={activePage} onChange={setPage} total={data?.lists?.totalPages || 0} color="gray" />
        </>
    );
}

export default SpeciesLists;
