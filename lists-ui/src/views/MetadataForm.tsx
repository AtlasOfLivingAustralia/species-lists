import {Alert, Button, Checkbox, Group, MultiSelect, Select, Space, Textarea, TextInput} from "@mantine/core";
import { useForm } from "@mantine/form";
import {FormattedMessage} from "react-intl";
import {MetadataFormProps} from "../api/sources/props.ts";
import {useContext, useEffect, useState} from "react";
import {ListsUser, SpeciesList} from "../api/sources/model.ts";
import UserContext from "../helpers/UserContext.ts";
import {IconInfoCircle} from "@tabler/icons-react";
import {validateListType} from "../helpers/validation.tsx";
import tags from '../tags.json';

interface Constraint {
    value: string;
    label: string;
}

interface ConstraintsResponse {
    lists: Constraint[];
    countries: Constraint[];
    licenses: Constraint[];
}

export function MetadataForm({ speciesList,
                               submitFormFcn,
                               formButtons,
                               suppliedFields,
                               edit,
                               resetUpload
                             }: MetadataFormProps) {

    const [isPrivate] = useState(speciesList?.isPrivate);
    const [isAuthoritative] = useState(speciesList?.isAuthoritative);
    const [isSDS] = useState(speciesList?.isSDS);
    const [isBIE] = useState(speciesList?.isBIE);
    const [valueConstraints, setValueConstraints] = useState<ConstraintsResponse | null>(null);
    const currentUser = useContext(UserContext) as ListsUser;

    const form = useForm({
        initialValues: {
            id: speciesList?.id,
            title: speciesList?.title,
            description: speciesList?.description,
            listType: speciesList?.listType,
            licence: speciesList?.licence,
            authority: speciesList?.authority,
            region: speciesList?.region,
            isPrivate: speciesList?.isPrivate,
            isAuthoritative: speciesList?.isAuthoritative,
            isInvasive: speciesList?.isInvasive,
            isThreatened: speciesList?.isThreatened,
            isSDS: speciesList?.isSDS,
            isBIE: speciesList?.isBIE,
            wkt: speciesList?.wkt,
            tags: speciesList?.tags
        },
        validate: {
            title: (value) => (!value ? "Please supply a title" : null),
            description: (value) => (!value ? "Please supply a description" : null),
            listType: (value) => (!value ? "Please select a list type" : null),
            licence: (value) => (!value ? "Please select a licence" : null),
            region: (value) => (!value ? "Please supply a region" : null),
        },
    });

    const listTypeValidation:string| null = validateListType(form.values.listType, suppliedFields);

    // useEffect hook to fetch list type constraints
    useEffect(() => {
        async function getListTypeConstraints() {
            try {
                const resp = await fetch(`${import.meta.env.VITE_API_URL}/constraints`);
                setValueConstraints(await resp.json());
            } catch (error) {
                console.error(error);
            }
        }

        getListTypeConstraints();
    }, []);

    return (
        <>
            <form onSubmit={form.onSubmit((values) => submitFormFcn(values as SpeciesList) )}>
                <TextInput
                    name="id"
                    style={{ display: "none" }}
                    disabled={!edit}
                    {...form.getInputProps("id")}
                />
                <TextInput
                    label="Species list name"
                    placeholder="My species list"
                    disabled={!edit}
                    {...form.getInputProps("title")}
                />
                <Space h="md" />
                <TextInput
                    label="Description"
                    placeholder="Description"
                    disabled={!edit}
                    {...form.getInputProps("description")}
                />
                <Space h="md" />
                <Select
                    label="List type"
                    placeholder="Pick one"
                    disabled={!edit}
                    data={valueConstraints?.lists || []}
                    {...form.getInputProps("listType")}
                />

                { listTypeValidation &&
                    <>
                        <Space h="md" />
                        <Alert variant="light" color="orange" title="Required fields missing for this list type" icon={<IconInfoCircle />}>
                            The uploaded file has the following validation errors.
                            <ul>
                                <li><FormattedMessage id={listTypeValidation} defaultMessage={listTypeValidation}/></li>
                            </ul>
                        </Alert>
                        <Space h="md" />
                    </>
                }

                <Space h="md" />
                <Select
                    label="Licence"
                    placeholder="Pick one"
                    disabled={!edit}
                    data={valueConstraints?.licenses || []}
                    {...form.getInputProps("licence")}
                />
                <Space h="lg" />
                <Group>
                    <Checkbox size="md"
                            label="Is private"
                            checked={isPrivate}
                            {...form.getInputProps('isPrivate', { type: 'checkbox' })}
                    />
                    {currentUser?.isAdmin && <>
                        <Checkbox size="md"
                                  label="Is Authoritative"
                                  checked={isAuthoritative}
                                  {...form.getInputProps('isAuthoritative', { type: 'checkbox' })}
                        />
                        <Checkbox size="md"
                                  label="Used in Sensitive data service"
                                  checked={isSDS}
                                  {...form.getInputProps('isSDS', { type: 'checkbox' })}
                        />
                        <Checkbox size="md"
                                  label="Display on species pages"
                                  checked={isBIE}
                                  {...form.getInputProps('isBIE', { type: 'checkbox' })}
                        />
                    </>}

                </Group>
                <Space h="md" />
                <TextInput mt="md" label="Authority"
                           disabled={!edit}
                           placeholder=""
                           {...form.getInputProps("authority")}
                />

                <Space h="md" />
                <Select
                    label="Region"
                    placeholder="Pick one"
                    disabled={!edit}
                    data={valueConstraints?.countries || []}
                    {...form.getInputProps("region")}
                />

                <MultiSelect
                    mt="md"
                    data={tags}
                    label="Tags"
                    placeholder="Select tags"
                    {...form.getInputProps("tags")}
                />

                <Space h="md" />
                <Textarea h={100} label={<FormattedMessage id='wkt'/>} disabled={!edit} placeholder="" {...form.getInputProps("wkt")} />

                { listTypeValidation &&
                    <>
                        <Space h="md" />
                        <Group position="center" mt="xl">
                            <Button variant="outline" onClick={resetUpload}>Try again</Button>
                        </Group>
                    </>
                }

                { !listTypeValidation && formButtons }
            </form>
        </>
    );
}
