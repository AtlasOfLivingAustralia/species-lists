import {useContext, useState} from 'react';
import {
    Space,
    Table,
    TextInput,
    Group,
     Button,
} from '@mantine/core';
import {useMutation} from '@apollo/client';
import {
    ListsUser, InputSpeciesList
} from "../api/sources/model";
import {SpeciesListItemProps} from "../api/sources/props.ts";
import {useForm} from "@mantine/form";
import UserContext from "../helpers/UserContext.ts";
import {DELETE_SPECIES_LIST_ITEM, GET_LIST, UPDATE_SPECIES_LIST_ITEM} from "../api/sources/graphql.ts";
import {IconCheck, IconRowRemove} from "@tabler/icons-react";
import {notifications} from "@mantine/notifications";
export function SpeciesListItemEditor({ speciesListID, selectedItem, customFields,  currentFilters, setIsEditing, resetSelectedIndex }: SpeciesListItemProps) {

    const currentUser = useContext(UserContext) as ListsUser;
    const taxonFields = ['scientificName', 'taxonID', 'vernacularName', 'genus', 'family', 'order', 'classs', 'phylum', 'kingdom'];
    const [isUpdating, setIsUpdating] = useState(false);

    const [updateSpeciesListItem] = useMutation(UPDATE_SPECIES_LIST_ITEM, {
        context: {
            headers: {
                "Authorization": "Bearer " + currentUser?.user?.access_token,
            }
        },
        refetchQueries: [
            {
                query: GET_LIST,
                variables: {
                    speciesListID: speciesListID,
                    filters: currentFilters
                }
            }
        ]
    });

    const [deleteSpeciesListItem] = useMutation(DELETE_SPECIES_LIST_ITEM, {
        context: {
            headers: {
                "Authorization": "Bearer " + currentUser?.user?.access_token,
            }
        },
        refetchQueries: [
            {
                query: GET_LIST,
                variables: {
                    speciesListID: speciesListID,
                    filters: currentFilters
                }
            }
        ]
    });

    function deleteSpeciesListItemForm() {
        if (window.confirm('Are you sure you want to delete this item')) {
            setIsUpdating(true);
            deleteSpeciesListItem({
                variables: {
                    id: selectedItem.id,
                }
            }).then( () => {
                setIsUpdating(false);
                if (setIsEditing) {
                    setIsEditing(false);
                }

                if (resetSelectedIndex) {
                    resetSelectedIndex();
                }

                notifications.show({
                    icon: <IconCheck />,
                    title: 'Record removed from list',
                    message: 'The record for ' + selectedItem.scientificName + ' has been removed from this list'
                })
            });
        }
    }

    function updateSpeciesListItemForm(values: InputSpeciesList) {
        setIsUpdating(true);
        updateSpeciesListItem({
            variables: {
                inputSpeciesListItem: {
                    id: values.id,
                    speciesListID: speciesListID,
                    scientificName: values.scientificName,
                    vernacularName: values.vernacularName,
                    genus: values.genus,
                    family: values.family,
                    classs: values.classs,
                    order: values.order,
                    phylum: values.phylum,
                    kingdom: values.kingdom,
                    properties: values.properties.map((property) => {
                        return { key: property.key, value: property.value };
                    })
                }
            }
        }).then( () => {
            setIsUpdating(false);
            if (setIsEditing) {
                setIsEditing(false);
            }
        });
    }

    const customValues = (customFields || []).map((customField) => {
        const currentValue = selectedItem.properties.find((element) => element.key === customField);
        if (currentValue){
            return { key: currentValue.key, value: currentValue.value, __typename: ''};
        }
        return { key: customField, value: '', __typename: ''};
    });

    const form = useForm({
        initialValues: {
            id: selectedItem?.id,
            scientificName: selectedItem?.scientificName,
            vernacularName: selectedItem?.vernacularName,
            genus: selectedItem?.genus,
            family: selectedItem?.family,
            classs: selectedItem?.classs,
            order: selectedItem?.order,
            phylum: selectedItem?.phylum,
            kingdom: selectedItem?.kingdom,
            properties: customValues
        }

    });

    return <>
        <form onSubmit={form.onSubmit((values) => updateSpeciesListItemForm(values)) }>

            <Button style={{ float: 'right'}} onClick={deleteSpeciesListItemForm} disabled={isUpdating}><IconRowRemove/> Remove taxon from list</Button>
            <h2>Taxonomy</h2>
            <Table striped highlightOnHover withBorder>
                <tbody>
                {taxonFields?.map((taxonField) => (
                    <tr key={taxonField}>
                        <td>{taxonField}</td>
                        <td>
                            <TextInput
                                disabled={isUpdating}
                                withAsterisk
                                placeholder={taxonField}
                                {...form.getInputProps(taxonField)}
                            />
                        </td>
                    </tr>
                ))}
                </tbody>
            </Table>

            <Space h={30}/>

            <h2>Properties</h2>
            <Table striped highlightOnHover withBorder>
                <tbody>
                {customFields?.map((customField, index) => (
                    <tr key={customField}>
                        <td>{customField}</td>
                        <td>
                            <TextInput
                                disabled={isUpdating}
                                {...form.getInputProps(`properties.${index}.value`)}
                            />
                        </td>
                    </tr>
                ))}
                </tbody>
            </Table>

            <Group mt="md">
                {setIsEditing &&
                <Button variant="default" onClick={() => setIsEditing(false)} disabled={isUpdating}>Cancel</Button>
                }
                <Button type="submit" disabled={isUpdating}>Update</Button>
            </Group>
        </form>
    </>;
}
