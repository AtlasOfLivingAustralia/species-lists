import {
    Pagination,
    Space,
    Table,
    Grid,
    TextInput,
    Group,
    Text, Skeleton, CloseButton, Select, HoverCard
} from "@mantine/core";
import { useQuery } from "@apollo/client";
import {useContext, useState} from "react";
import {
    IconEye,
    IconList, IconLock,
    IconSearch
} from '@tabler/icons-react';
import {useNavigate} from "react-router-dom";
import {FormattedMessage, FormattedNumber} from "react-intl";
import { Dispatch, SetStateAction } from "react";
import {ListsUser, SpeciesList} from "../api/sources/model.ts";
import {SpeciesListsSideBar} from "./SpeciesListsSideBar.tsx";
import UserContext from "../helpers/UserContext.ts";
import {GET_MY_LISTS} from "../api/sources/graphql.ts";

function MyLists() {

    const navigate = useNavigate();
    const [activePage, setPage] = useState<number>(1);
    const [pageSize, setPageSize] = useState<number>(12);
    const [searchQuery, setSearchQuery] = useState<string>("");
    const currentUser = useContext(UserContext) as ListsUser;

    function selectSpeciesList(speciesList: SpeciesList) {
        navigate(`/list/` + speciesList.id + '?q=' + searchQuery);
    }

    const { loading, error, data} = useQuery(GET_MY_LISTS, {
        fetchPolicy: "no-cache",
        variables: {
            searchQuery: searchQuery,
            page: activePage - 1,
            size: pageSize,
            userId: currentUser?.userId
        },
    });

    return (
        <>
            <Grid mb="md">
                <Grid.Col xs={1} sm={2}>
                    <SpeciesListsSideBar resetSpeciesList={() => console.log('reset list')} selectedView="my-lists" />
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
                                    {!loading && <><Text>{data.lists.totalElements} lists</Text></>}
                                </Group>
                            </Group>
                        </Grid.Col>
                    </Grid>
                    <SearchTable
                        searchQuery={searchQuery}
                        activePage={activePage}
                        pageSize={pageSize}
                        setPage={setPage}
                        selectSpeciesList={selectSpeciesList}
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
                <Group noWrap={true}>
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
            <td onClick={() => selectSpeciesList(dataset)}>
                <HoverCard width={280} shadow="md">
                    <HoverCard.Target>
                        {dataset.isPrivate ? <IconLock /> : <IconEye/>}
                    </HoverCard.Target>
                    <HoverCard.Dropdown>
                        <Text size="sm">
                            {dataset.isPrivate ? <FormattedMessage id="visiblility.private"/> : <FormattedMessage id="visiblility.public"/>}
                        </Text>
                    </HoverCard.Dropdown>
                </HoverCard>
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
