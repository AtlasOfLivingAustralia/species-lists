import {
    Pagination,
    Space,
    Table,
    Grid,
    TextInput,
    Group,
    Text, Skeleton
} from "@mantine/core";
import { gql, useQuery } from "@apollo/client";
import {useState} from "react";
import {
    IconList,
    IconSearch
} from '@tabler/icons-react';
import {useNavigate} from "react-router-dom";
import { FormattedMessage } from "react-intl";
import { Dispatch, SetStateAction } from "react";
import {SpeciesList} from "../api/sources/model.ts";
import {SpeciesListsSideBar} from "./SpeciesListsSideBar.tsx";


function SpeciesLists() {

    const navigate = useNavigate();
    const [activePage, setPage] = useState<number>(1);
    const [pageSize] = useState<number>(15);
    const [searchQuery, setSearchQuery] = useState<string>("");

    function selectSpeciesList(speciesList: SpeciesList) {
        navigate(`/list/` + speciesList.id, {state: {searchQuery: searchQuery}} );
    }

    return (
        <>
            <Grid mb="md">
                <Grid.Col xs={1} sm={2}>
                    <SpeciesListsSideBar resetSpeciesList={() => console.log('reset list')} />
                </Grid.Col>
                <Grid.Col xs={12} sm={10}>
                    <Grid align="center" mb="md">
                        <Grid.Col xs={8} sm={9}>
                            <TextInput
                                h={"lg"}
                                sx={{ flexBasis: "60%" }}
                                placeholder="Search across lists..."
                                icon={<IconSearch size={16} />}
                                value={searchQuery}
                                onChange={(e) => setSearchQuery(e.currentTarget.value)}
                            />
                        </Grid.Col>
                        <Grid.Col xs={4} sm={3}>

                        </Grid.Col>
                    </Grid>
                    <SearchTable
                        searchQuery={searchQuery}
                        activePage={activePage}
                        pageSize={pageSize}
                        setPage={setPage}
                        selectSpeciesList={selectSpeciesList}
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
    setPage: Dispatch<SetStateAction<number>>;
    selectSpeciesList: (speciesList: SpeciesList) => void;
}

export function SearchTable({
                                searchQuery,
                                activePage,
                                pageSize,
                                setPage,
                                selectSpeciesList
                            }: SearchTableProps) {

    const GET_LISTS = gql`
        query findList($searchQuery: String, $page: Int, $size: Int) {
            lists(searchQuery: $searchQuery, page: $page, size: $size) {
                content {
                    id
                    title
                    rowCount
                    listType
                }
                totalPages
                totalElements
            }
        }
    `;

    const { loading, error, data} = useQuery(GET_LISTS, {
        variables: {
            searchQuery: searchQuery,
            page: activePage - 1,
            size: pageSize
        },
    });

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
                            <IconList /> <Skeleton height={30} width={300} />
                        </Group>
                    </td>
                    <td>
                         <Skeleton height={30} />
                    </td>
                    <td >
                        <Skeleton height={30} />
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
            style={{ cursor: "pointer" }}
        >
            <td onClick={() => selectSpeciesList(dataset)}>
                <Group>
                    <IconList />
                    <Text>{dataset.title}</Text>
                </Group>
            </td>
            <td onClick={() => selectSpeciesList(dataset)}>
                <FormattedMessage id={dataset.listType} />
            </td>
            <td onClick={() => selectSpeciesList(dataset)}>{dataset.rowCount}</td>
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
                <tbody>{rows}</tbody>
            </Table>
            <Space h="md" />
            <Pagination value={activePage} onChange={setPage} total={data?.lists?.totalPages || 0} color="gray" />
        </>
    );
}

export default SpeciesLists;
