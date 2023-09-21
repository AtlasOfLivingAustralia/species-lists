import {Alert, Button, Checkbox, Group, Select, Space, Textarea, TextInput} from "@mantine/core";
import { useForm } from "@mantine/form";
import {FormattedMessage, useIntl} from "react-intl";

import {MetadataFormProps} from "../api/sources/props.ts";
import {useContext, useState} from "react";
import {ListsUser, SpeciesList} from "../api/sources/model.ts";
import UserContext from "../helpers/UserContext.ts";
import {IconInfoCircle} from "@tabler/icons-react";

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
            isBIE: speciesList?.isBIE
        },
        validate: {
            title: (value) => (!value ? "Please supply a title" : null),
            description: (value) => (!value ? "Please supply a description" : null),
            listType: (value) => (!value ? "Please select a list type" : null),
            licence: (value) => (!value ? "Please select a licence" : null),
            region: (value) => (!value ? "Please supply a region" : null),
        },
    });

    function validateListType() {
        if (form.values.listType === "SENSITIVE_LIST" && !(suppliedFields.includes('generalisation'))){
            return "SENSITIVE_LIST_VALIDATION_FAILED";
        }
        if (form.values.listType === "CONSERVATION_LIST" && !(suppliedFields.includes('status'))){
            return "CONSERVATION_LIST_VALIDATION_FAILED";
        }
        if (form.values.listType === "INVASIVE" && !(suppliedFields.includes('status'))){
            return "INVASIVE_LIST_VALIDATION_FAILED";
        }
        return null
    }

    const listTypeValidation = validateListType();

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
                    data={[
                        { value: "CONSERVATION_LIST", label: useIntl().formatMessage({ id: "CONSERVATION_LIST" }) },
                        { value: "SENSITIVE_LIST", label: useIntl().formatMessage({ id: "SENSITIVE_LIST" }) },
                        { value: "INVASIVE", label: useIntl().formatMessage({ id: "INVASIVE" }) },
                        { value: "COMMON_TRAIT", label: useIntl().formatMessage({ id: "COMMON_TRAIT" }) },
                        { value: "LOCAL_LIST", label: useIntl().formatMessage({ id: "LOCAL_LIST" }) },
                        { value: "SPECIES_CHARACTERS", label: useIntl().formatMessage({ id: "SPECIES_CHARACTERS" }) },
                    ]}
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
                    data={[
                        { value: "CC0", label: "Creative Commons Zero" },
                        { value: "CC-BY", label: "Creative Commons By Attribution" },
                        { value: "CC-BY-NC", label: "Creative Commons Attribution-Noncommercial" },
                    ]}
                    {...form.getInputProps("licence")}
                />
                <Space h="md" />
                <Group>
                    <Checkbox size="lg"
                            label="Is private"
                            checked={isPrivate}
                            {...form.getInputProps('isPrivate', { type: 'checkbox' })}
                    />
                    {currentUser?.isAdmin && <>
                        <Checkbox size="lg"
                                  label="Is Authoritative"
                                  checked={isAuthoritative}
                                  {...form.getInputProps('isAuthoritative', { type: 'checkbox' })}
                        />
                        <Checkbox size="lg"
                                  label="Use in Sensitive data service"
                                  checked={isSDS}
                                  {...form.getInputProps('isSDS', { type: 'checkbox' })}
                        />
                        <Checkbox size="lg"
                                  label="Display on species pages"
                                  checked={isBIE}
                                  {...form.getInputProps('isBIE', { type: 'checkbox' })}
                        />
                    </>}

                </Group>
                <TextInput mt="md" label="Authority" disabled={!edit} placeholder="" {...form.getInputProps("authority")} />
                <Space h="md" />
                <Select
                    label="Region"
                    placeholder="Pick one"
                    disabled={!edit}
                    data={[
                        { value : "AUS", label: "Australia"},
                        { value : "NZ",  label: "New Zealand"},
                        { value : "NOTAUS", label: "Outside Australia"},
                        { value : "ACT", label: "Australian Capital Territory"},
                        { value : "NSW", label: "New South Wales"},
                        { value : "NT",  label: "Northern Territory"},
                        { value : "QLD", label: "Queensland"},
                        { value : "SA",  label: "South Australia"},
                        { value : "TAS", label: "Tasmania"},
                        { value : "VIC", label: "Victoria"},
                        { value : "WA",  label: "Western Australia"},
                        { value : "CC",  label: "Cocos (Keeling) Islands"},
                        { value : "CX",  label: "Christmas Island"},
                        { value : "AC",  label: "Ashmore and Cartier Islands"},
                        { value : "CS",  label: "Coral Sea Islands"},
                        { value : "NF",  label: "Norfolk Island"},
                        { value : "HM",  label: "Heard and McDonald Islands"},
                        { value : "AQ",  label: "Australian Antarctic Territory"}
                    ]}
                    {...form.getInputProps("region")}
                />
                <Space h="md" />
                <Textarea  label="Well known text" disabled={!edit} placeholder="" {...form.getInputProps("wkt")} />

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
