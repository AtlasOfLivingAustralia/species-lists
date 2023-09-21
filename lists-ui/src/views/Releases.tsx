import {
    Button,
    Space,
    Table,
    Title,
} from "@mantine/core";
import { useParams } from "react-router-dom";
import { gql, useQuery } from "@apollo/client";
import {ListsUser, ReleasesData} from "../api/sources/model.ts";
import { IconDownload } from "@tabler/icons-react";
import {useContext} from "react";
import UserContext from "../helpers/UserContext.ts";

export function Releases(){
    const { speciesListID } = useParams<{ speciesListID: string }>();
    const currentUser = useContext(UserContext) as ListsUser;

    const GET_LIST = gql`
        query loadList($speciesListID: String!) {
            getSpeciesListMetadata(speciesListID: $speciesListID) {
                id
                title
                licence
                rowCount
                fieldList
                listType
                doi
                authority
                region
                isAuthoritative
                isPrivate
                isInvasive
                isThreatened
            }
            listReleases(speciesListID: $speciesListID) {
                releasedVersion
                metadata {
                    fieldList
                    rowCount
                }
                createdDate
            }
        }
    `;

    const { loading , data } = useQuery<ReleasesData>(GET_LIST, {
        variables: {
            speciesListID: speciesListID,
        },
    });

    if (loading) return <></>; // Replace with your loading indicator.

    const speciesList = data?.getSpeciesListMetadata;
    const releases = data?.listReleases;

    // @ts-ignore
    return (
        <>
            <Title order={3}>Version history for {speciesList?.title}</Title>
            <Space h="md" />
            <Table striped highlightOnHover withColumnBorders>
                <thead>
                <tr>
                    <th>version</th>
                    <th>createdDate</th>
                    <th>fieldList</th>
                    <th>rowCount</th>
                    <th></th>
                    <th></th>
                </tr>
                </thead>
                <tbody>
                {releases?.map((release) => (
                    <tr key={release.releasedVersion}>
                        <td>{release.releasedVersion}</td>
                        <td>{release.createdDate}</td>
                        <td>{release.metadata.fieldList.join(", ")}</td>
                        <td>{release.metadata.rowCount}</td>
                        <td>
                            <Button size="sm" variant="outline" onClick={() => alert("Not implemented yet!")}>
                                <IconDownload />
                                Download
                            </Button>
                        </td>
                        <td>
                            <Button
                                variant="outline"
                                size="xs"
                                color="red"
                                onClick={() => alert("Not implemented yet!")}
                                disabled={currentUser?.isAdmin || currentUser?.userId == speciesList?.owner}>
                                Restore this version
                            </Button>
                        </td>
                    </tr>
                ))}
                </tbody>
            </Table>
        </>
    );
}
