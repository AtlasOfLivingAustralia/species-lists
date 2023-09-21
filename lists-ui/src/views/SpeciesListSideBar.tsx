import {
    Group,
    Skeleton,
    Text,
    Accordion
} from '@mantine/core';
import { gql, useQuery } from '@apollo/client';
import { Link, useParams } from 'react-router-dom';
import {
    IconAlertCircle,
    IconClipboard,
    IconList,
    IconReload,
    IconTable,
    IconUpload,
    IconVersions,
} from '@tabler/icons-react';
import { SpeciesList} from "../api/sources/model";
import {SpeciesListSideBarProps} from "../api/sources/props.ts";
import {useAuth} from "../helpers/useAuth.ts";

function SpeciesListSideBar({ selectedView,
                              resetSpeciesList,
                            }: SpeciesListSideBarProps) {

    const { currentUser } = useAuth();
    const { speciesListID } = useParams();

    const GET_LIST = gql`
        query loadList(
            $speciesListID: String!
        ) {
            getSpeciesListMetadata(speciesListID: $speciesListID) {
                id
                title
                rowCount
                fieldList
                facetList
            }
        }
    `;
    const { loading, error, data } = useQuery(GET_LIST, {
        variables: {
            speciesListID: speciesListID
        },
    });

    if (!speciesListID) {
        return null;
    }

    if (loading) {
        return (
            <Accordion>
                <AllLists resetSpeciesList={resetSpeciesList} />
                <UploadListOption resetSpeciesList={resetSpeciesList} />
                <Accordion.Item value="the-list">
                    <Accordion.Control icon={<IconTable />}>
                        <Skeleton height={20}/>
                    </Accordion.Control>
                </Accordion.Item>
            </Accordion>
        );
    }

    if (error) return <>`Error! ${error.message}`</>;

    const metadata: SpeciesList = data.getSpeciesListMetadata;

    return (
        <>
            <Accordion defaultValue="selected-list" styles={{ chevron: { display: 'none' } }}>
                <AllLists resetSpeciesList={resetSpeciesList} />
                <UploadListOption resetSpeciesList={resetSpeciesList} />
                <Accordion.Item value="the-list">
                    <Accordion.Control icon={<IconTable />}>
                        <Text weight="bold"> {metadata.title}</Text>
                    </Accordion.Control>
                </Accordion.Item>
            </Accordion>
            <Accordion
                defaultValue={selectedView}
                styles={{ chevron: { display: 'none' } }}
                style={{ marginLeft: '15px', backgroundColor: '#F4F4F4'}}>
                <Accordion.Item value="view-list">
                    <Link to={'/list/' + speciesListID}>
                        <Accordion.Control>
                            <Group>
                                <IconTable size={18} />
                                <Text fz="sm">Species list</Text>
                            </Group>
                        </Accordion.Control>
                    </Link>
                </Accordion.Item>
                <Accordion.Item value="metadata">
                    <Link to={`/metadata/${speciesListID}`}>
                        <Accordion.Control>
                            <Group>
                                <IconClipboard size={18} />
                                <Text fz="sm">Metadata</Text>
                            </Group>
                        </Accordion.Control>
                    </Link>
                </Accordion.Item>
                <Accordion.Item value="releases">
                    <Link to={`/releases/${speciesListID}`}>
                        <Accordion.Control>
                            <Group>
                                <IconVersions size={18} />
                                <Text fz="sm">Versions</Text>
                            </Group>
                        </Accordion.Control>
                    </Link>
                </Accordion.Item>

                {currentUser && currentUser.isAdmin &&
                    <Accordion.Item value="reload">
                        <Link to={`/reload/${speciesListID}`}>
                            <Accordion.Control>
                                <Group>
                                    <IconReload size={18} />
                                    <Text fz="sm">Reload list</Text>
                                </Group>
                            </Accordion.Control>
                        </Link>
                    </Accordion.Item>
                }
                {currentUser && currentUser.isAdmin &&
                    <Accordion.Item value="reload">
                        <Link to={`/dangerzone/${speciesListID}`}>
                            <Accordion.Control>
                                <Group>
                                    <IconAlertCircle size={18} />
                                    <Text fz="sm">Danger zone</Text>
                                </Group>
                            </Accordion.Control>
                        </Link>
                    </Accordion.Item>
                }
            </Accordion>
        </>
    );
}

export function AllLists({ resetSpeciesList }: { resetSpeciesList: () => void }) {
    return (
        <Accordion.Item value="species-lists" onClick={resetSpeciesList}>
            <Link to={`/`}>
                <Accordion.Control icon={<IconList />}>All species lists</Accordion.Control>
            </Link>
        </Accordion.Item>
    );
}

export function UploadListOption({ resetSpeciesList }: { resetSpeciesList: () => void }) {
    return (
        <Accordion.Item value="upload-new" onClick={resetSpeciesList}>
            <Link to={`/upload`}>
                <Accordion.Control icon={<IconUpload />}>Upload new list</Accordion.Control>
            </Link>
        </Accordion.Item>
    );
}

export default SpeciesListSideBar;
