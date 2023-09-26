import {
    Button, Container, Group, Modal, Space, Table, Text, TextInput
} from "@mantine/core";
import { useNavigate, useParams } from "react-router-dom";
import {IconAlertHexagon, IconEdit, IconRowRemove} from "@tabler/icons-react";
import {useContext, useState} from "react";
import UserContext from "../helpers/UserContext.ts";
import {ListsUser, SpeciesList} from "../api/sources/model.ts";
import {gql, useMutation, useQuery} from "@apollo/client";
import {GET_LIST_METADATA, REMOVE_FIELD, RENAME_FIELD} from "../api/sources/graphql.ts";


function DangerZone() {
    const navigate = useNavigate();
    const { speciesListID } = useParams();
    const [newFieldName, setNewFieldName] = useState('');
    const [newFieldValue, setNewFieldValue] = useState('');
    const currentUser = useContext(UserContext) as ListsUser;
    const [isUpdating, setIsUpdating] = useState(false);
    const [isDeleting, setIsDeleting] = useState(false);

    const ADD_FIELD = gql`
        mutation addField(
            $id: String!
            $fieldName: String!,
            $fieldValue: String
        ) {
            addField(
                id: $id
                fieldName: $fieldName
                fieldValue: $fieldValue
            ) {
                id
                title
                description
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
                isSDS
                isBIE
                dateCreated
                lastUpdated
                lastUploaded
                owner
                editors
                wkt
            }
        }
    `;

    const [addField] = useMutation(ADD_FIELD, {
        context: {
            headers: {
                "Authorization": "Bearer " + currentUser?.user?.access_token,
            }
        },
        refetchQueries: [
            {
                query: GET_LIST_METADATA,
            },
        ],
    });

    function addFieldToList() {
        setIsUpdating(true);
        addField({
            variables: {
                id: speciesListID,
                fieldName: newFieldName.trim(),
                fieldValue: newFieldValue?.trim()
            }
        }).then(() => {
            setIsUpdating(false);
        });
    }

    const { data} = useQuery<{ getSpeciesListMetadata: SpeciesList }>(GET_LIST_METADATA, {
        variables: {
            speciesListID: speciesListID,
        },
        errorPolicy: "all", // Add errorPolicy if needed
    });

    const speciesList = data?.getSpeciesListMetadata ? data?.getSpeciesListMetadata as SpeciesList : {} as SpeciesList;

    function deleteList() {
        setIsDeleting(true);
        fetch( import.meta.env.VITE_DELETE_URL + "/" + speciesListID, {
            method: "DELETE",
            headers: {
                'Authorization': `Bearer ${currentUser?.user?.access_token}`
            },
        }).then((res) => {
            console.log(res);
            setIsDeleting(false);
            navigate(`/`);
        });
    }

    return (
        <>
            <Modal opened={isDeleting} onClose={close} title="Deleting list">
                Please wait...
            </Modal>
            <Modal opened={isUpdating} onClose={close} title="Updating list">
                Please wait...
            </Modal>
            <Container>
            <h2>Danger zone</h2>
            <Group>
                <Button variant="outline" onClick={deleteList}>
                    <IconAlertHexagon />
                    Delete this list
                </Button>
                <Text>Click here to delete this list. This action cannot be undone.
                    <br/>
                    <span style={{ color: 'red'}}>Please be aware if this list is in use by any downstream applications
                        before proceeding.</span>
                </Text>
            </Group>

            {speciesList && <>
                <Space h="lg" />
                <h3>Manage fields</h3>
                <p>
                    Here you can change the structure of the list by removing or adding fields.
                </p>
                <Table verticalSpacing="md" horizontalSpacing="lg" withBorder>
                    <thead>
                    <tr>
                        <th>Field name</th>
                        <th></th>
                        <th>Actions</th>
                    </tr>
                    </thead>
                    <tbody>
                        {speciesList?.fieldList?.map((field) => <ExistingField speciesListID={speciesList.id} originalName={field} />)}
                        <tr>
                            <td>
                                <label>New field name</label>
                                <TextInput onChange={evt => setNewFieldName(evt.currentTarget.value)} placeholder="Add field" />
                            </td>
                            <td>
                                <label>Default value (Optional)</label>
                                <TextInput onChange={evt => setNewFieldValue(evt.currentTarget.value)} placeholder="default value" />
                            </td>
                            <td>
                                <Button size={`sm`} variant="outline" style={{ marginTop: '20px' }} onClick={addFieldToList}>Add new field</Button>
                            </td>
                        </tr>
                    </tbody>
                </Table>
            </>}
            </Container>
        </>
    );
}

export function ExistingField({speciesListID, originalName}: {speciesListID: string, originalName: string}) {

    const [fieldName, setFieldName] = useState(originalName);
    const currentUser = useContext(UserContext) as ListsUser;
    const [isUpdating, setIsUpdating] = useState(false);

    function removeFieldFromList() {
        setIsUpdating(true);
        removeField({
            variables: {
                id: speciesListID,
                fieldName: fieldName.trim()
            }
        }).then(() => {
            setIsUpdating(false);
        });
    }

    function renameFieldFromList() {
        setIsUpdating(true);
        renameField({
            variables: {
                id: speciesListID,
                oldName: originalName,
                newName: fieldName.trim()
            }
        }).then(() => {
            setIsUpdating(false);
        });
    }

    const [removeField] = useMutation(REMOVE_FIELD, {
        context: {
            headers: {
                "Authorization": "Bearer " + currentUser?.user?.access_token,
            }
        },
        refetchQueries: [
            {
                query: GET_LIST_METADATA,
            },
        ],
    });

    const [renameField] = useMutation(RENAME_FIELD, {
        context: {
            headers: {
                "Authorization": "Bearer " + currentUser?.user?.access_token,
            }
        },
        refetchQueries: [
            {
                query: GET_LIST_METADATA,
            },
        ],
    });

    return <>
        <Modal opened={isUpdating} onClose={close} title="Updating list">
            <p>Please wait...</p>
        </Modal>
        <tr>
        <td>
            <TextInput defaultValue={fieldName} onChange={evt => setFieldName(evt.currentTarget.value)}  />
        </td>
        <td></td>
        <td>
            <Group>
                <Button size="sm" variant="outline" onClick={renameFieldFromList} disabled={originalName.trim() === fieldName.trim()}><IconEdit /> Rename</Button>
                <Button size={`sm`} variant="outline" onClick={removeFieldFromList}><IconRowRemove /> Delete</Button>
            </Group>
        </td>
    </tr>
    </>
}

export default DangerZone;