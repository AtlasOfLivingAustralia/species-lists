import {
    Pagination,
    Space,
    Table,
    Grid,
    TextInput,
    Group,
    Text, Skeleton, CloseButton
} from "@mantine/core";
import { useQuery } from "@apollo/client";
import {useContext, useState} from "react";
import {
    IconEye,
    IconList, IconLock,
    IconSearch
} from '@tabler/icons-react';
import {useNavigate} from "react-router-dom";
import { FormattedMessage } from "react-intl";
import { Dispatch, SetStateAction } from "react";
import {ListsUser, SpeciesList} from "../api/sources/model.ts";
import {SpeciesListsSideBar} from "./SpeciesListsSideBar.tsx";
import UserContext from "../helpers/UserContext.ts";
import {GET_MY_LISTS} from "../api/sources/graphql.ts";

function MyLists() {

    const navigate = useNavigate();
    const [activePage, setPage] = useState<number>(1);
    const [pageSize] = useState<number>(15);
    const [searchQuery, setSearchQuery] = useState<string>("");

    function selectSpeciesList(speciesList: SpeciesList) {
        navigate(`/list/` + speciesList.id + '?q=' + searchQuery);
    }

    return (
        <>
            <Grid mb="md">
                <Grid.Col xs={1} sm={2}>
                    <SpeciesListsSideBar resetSpeciesList={() => console.log('reset list')} selectedView="my-lists" />
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
                                rightSection={
                                    <CloseButton
                                        aria-label="Clear input"
                                        onClick={() => setSearchQuery('')}
                                        style={{ display: searchQuery ? undefined : 'none' }}
                                    />
                                }
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

    const currentUser = useContext(UserContext) as ListsUser;
    const { loading, error, data} = useQuery(GET_MY_LISTS, {
        fetchPolicy: "no-cache",
        context: {
            headers: {
                "Authorization": "Bearer " + currentUser?.user?.access_token
            }
        },
        variables: {
            searchQuery: searchQuery,
            page: activePage - 1,
            size: pageSize,
            userId: currentUser?.userId
        },
    });

    if (loading) return <>
        <Table verticalSpacing="xs" fontSize="md">
            <thead>
            <tr>
                <th>Species list name</th>
                <th>Category</th>
                <th>Visibility</th>
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
                    <td>
                        <Skeleton height={30} width={Math.floor(Math.random() * (50 - 30 + 1) + 100)}/>
                    </td>
                    <td>
                        <Skeleton height={30} width={Math.floor(Math.random() * (50 - 30 + 1) + 100)}/>
                    </td>
                </tr>
            ))}
            </tbody>
        </Table>
    </>;

    if (error) return null;

    if (!data?.lists?.content) return <>No matching lists</>;

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
            <td onClick={() => selectSpeciesList(dataset)}>{dataset.rowCount}</td>
            <td onClick={() => selectSpeciesList(dataset)}>
                {dataset.isPrivate ? <IconLock /> : <IconEye/>}
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
                    <th>Visibility</th>
                </tr>
                </thead>
                <tbody>
                    {rows}
                    {(!rows || rows.length ==0) && <tr><td colSpan={4}>No lists found</td></tr>}
                </tbody>
            </Table>
            <Space h="md" />
            <Pagination value={activePage} onChange={setPage} total={data?.lists?.totalPages || 0} color="gray" />
        </>
    );
}

export default MyLists;
