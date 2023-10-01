import {
    Button, Container, Group, Loader, Modal, Space, Table, Text, TextInput
} from "@mantine/core";
import { useNavigate, useParams } from "react-router-dom";
import {IconAlertHexagon, IconCheck, IconEdit, IconRowRemove, IconX} from "@tabler/icons-react";
import {useContext, useState} from "react";
import UserContext from "../helpers/UserContext.ts";
import {ListsUser, SpeciesList} from "../api/sources/model.ts";
import {useMutation, useQuery} from "@apollo/client";
import {ADD_FIELD, GET_LIST_METADATA, REMOVE_FIELD, RENAME_FIELD} from "../api/sources/graphql.ts";

import { notifications } from '@mantine/notifications';

function DangerZone() {
    const navigate = useNavigate();
    const { speciesListID } = useParams();
    const [newFieldName, setNewFieldName] = useState('');
    const [newFieldValue, setNewFieldValue] = useState('');
    const currentUser = useContext(UserContext) as ListsUser;
    const [isUpdating, setIsUpdating] = useState(false);
    const [isDeleting, setIsDeleting] = useState(false);
    const [isReindexing, setIsReindexing] = useState(false);
    const [isRematching, setIsRematching] = useState(false);

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
            notifications.show({
                icon: <IconCheck />,
                title: 'The field added to list',
                message: 'The field '+ newFieldName.trim() + ' has been added to the list ' + speciesList.title
            })
        }).catch((err) => {
            console.log(err);
            setIsUpdating(false);
            notifications.show({
                icon: <IconX color={`red`}/>,
                title: 'The field was not added',
                message: 'The adding of the field has failed due to a system problem',
            });
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
            notifications.show({
                icon: <IconCheck />,
                title: 'List ' + speciesList.title + ' has been deleted',
                message: 'The dataset was removed from the system',
            })

            navigate(`/`);
        }).catch((err) => {
            console.log(err);
            setIsDeleting(false);
            notifications.show({
                icon: <IconX color={`red`}/>,
                title: 'List has not been deleted',
                message: 'The dataset was not removed from the system due to a system problem',
            });
        });
    }

    function rematchList() {
        setIsRematching(true);
        fetch( import.meta.env.VITE_REMATCH_URL + "/" + speciesListID, {
            method: "GET",
            headers: {
                'Authorization': `Bearer ${currentUser?.user?.access_token}`
            },
        }).then((res) => {
            console.log(res);
            setIsRematching(false);
            setIsReindexing(true);
            fetch( import.meta.env.VITE_REINDEX_URL + "/" + speciesListID, {
                method: "GET",
                headers: {
                    'Authorization': `Bearer ${currentUser?.user?.access_token}`
                },
            }).then((res) => {
                console.log(res);
                setIsReindexing(false);
                notifications.show({
                    icon: <IconCheck />,
                    title: 'List rematched',
                    message: 'The taxonomy of the list ' + speciesList.title + ' has been rematched'
                })
            }).catch((err) => {
                console.log(err);
                setIsDeleting(false);
                notifications.show({
                    icon: <IconX color={`red`} />,
                    title: 'Rematching the list ' + speciesList.title + ' has failed',
                    message: 'The dataset was not rematched from the system due to a system problem',
                });
            });
        }).catch((err) => {
            console.log(err);
            setIsRematching(false);
            setIsReindexing(false);
            notifications.show({
                icon: <IconX color={`red`} />,
                title: 'Rematching the list ' + speciesList.title + ' has failed',
                message: 'The dataset was not rematched from the system due to a system problem',
            });
        });
    }

    return (
        <>
            <Modal opened={isDeleting} onClose={close} title="Deleting list">
                <Group>
                    <Loader color="orange" />
                    <Text>Deleting this list. Please wait...</Text>
                </Group>
            </Modal>

            <Modal opened={isUpdating} onClose={close} title="Updating list">
                <Group>
                    <Loader color="orange" />
                    <Text>Adding the new field to this list. Please wait...</Text>
                </Group>
            </Modal>

            <Modal opened={isReindexing} onClose={close} title="Reindexing list">
                <Group>
                    <Loader color="orange" />
                    <Text>Please wait while reindexing...</Text>
                </Group>
            </Modal>

            <Modal opened={isRematching} onClose={close} title="Rematching list">
                <Group>
                    <Loader color="orange" />
                    <Text>Please wait while rematching runs...</Text>
                </Group>
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

                {currentUser && currentUser.isAdmin && <>
                    <Space h="lg" />
                    <h3>Processing</h3>
                    <Group>
                        <Button variant="outline" onClick={rematchList}>
                            <IconAlertHexagon />
                            Re-match this list
                        </Button>
                        <Text>
                            Rematching this list will update the taxonomy for this list.
                        </Text>
                    </Group>
                    <Space h="lg" />
                </>}

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
            notifications.show({
                icon: <IconCheck />,
                title: 'The field removed from list',
                className: 'success-notification',
                message: 'The field '+ fieldName.trim + ' has been removed from the list'
            })
        }).catch((err) => {
            console.log(err);
            setIsUpdating(false);
            notifications.show({
                icon: <IconX color={`red`}/>,
                className: 'fail-notification',
                title: 'The field was not removed',
                message: 'The removal of the field has failed due to a system problem',
            });
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
            notifications.show({
                icon: <IconCheck />,
                title: 'The field renamed',
                className: 'success-notification',
                message: 'The field '+ originalName.trim() + ' has been renamed to ' + fieldName +' in the list '
            })
        }).catch((err) => {
            console.log(err);
            setIsUpdating(false);
            notifications.show({
                icon: <IconX color={`red`}/>,
                className: 'fail-notification',
                title: 'The field was not renamed',
                message: 'The rename of the field has failed due to a system problem',
            });
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
            <Group>
                <Loader color="orange" />
                <Text>Updating the list with the changes to fields....</Text>
            </Group>
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