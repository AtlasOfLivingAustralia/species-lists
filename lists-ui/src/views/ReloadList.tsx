import { useState} from "react";
import {
    Button,
    Group,
    Space,
    Text,
    Notification,
    Title,
} from "@mantine/core";
import { useNavigate, useParams } from "react-router-dom";
import { gql, useQuery } from "@apollo/client";
import { Dropzone } from "@mantine/dropzone";
import { IconFile, IconUpload, IconX } from "@tabler/icons-react";
import {useAuth} from "../helpers/useAuth.ts";

const ACCEPTED_TYPES: string[] = ["text/csv", "application/zip"];

function ReloadList() {
    const navigate = useNavigate();
    const { speciesListID } = useParams();
    const [uploading, setUploading] = useState<boolean>(false);
    const [uploaded, setUploaded] = useState<any>(null); // Replace 'any' with actual uploaded data type
    const [ingesting, setIngesting] = useState<boolean | null>(null);
    const [rejectedMessage, setRejectedMessage] = useState<string | null>(null);
    const { currentUser } = useAuth();

    const GET_LIST = gql`
        query loadList( $speciesListID:String!) {
            getSpeciesListMetadata(speciesListID: $speciesListID){
                id
                title
                licence
                rowCount
            }
        }`;

    const { loading, data } = useQuery(GET_LIST, {
        variables: {
            speciesListID: speciesListID,
        },
    });

    if (loading) return <></>;

    const speciesList = data.getSpeciesListMetadata;

    function resetUpload() {
        setUploaded(null);
        setUploading(false);
        setIngesting(false);
    }

    function ingest() {

        setIngesting(true);
        const formData = new FormData();
        formData.append("file", uploaded.localFile);

        fetch(import.meta.env.VITE_INGEST_URL + "/" + speciesList.id, {
            method: "POST",
            headers: {
                'Authorization': `Bearer ${currentUser?.user?.access_token}`
            },
            body: formData,
        })
            .then((res) => {
                const respJson = res.json();
                console.log(respJson);

                respJson.then(
                    function (data) {
                        console.log(data);
                        setIngesting(false);
                        navigate("/list/" + speciesList.id);
                    },
                    function (error) {
                        console.error(error);
                        setIngesting(false);
                    }
                );
                return respJson;
            })
            .catch((err) => {
                console.log("File upload error", err);
            });
    }

    if (ingesting) {
        return (
            <div>
                <Text size="xl" inline>
                    <Notification loading title="Loading into ALA, matching taxonomy" withCloseButton={false}>
                        Please wait until data is uploaded....
                    </Notification>
                </Text>
            </div>
        );
    }

    if (uploading) {
        return (
            <div>
                <Text size="xl" inline>
                    <Notification loading title="Uploading data to the server" withCloseButton={false}>
                        Please wait until data is uploaded....
                    </Notification>
                </Text>
            </div>
        );
    }

    if (uploaded) {
        return (
            <>
                <Title order={3}>Reload for {speciesList.title}</Title>
                <Space h="md" />
                <Dropzone disabled={true} onDrop={() => console.log('disabled')}>
                    <div>
                        <Text size="xl" inline>
                            <IconFile /> File Uploaded: {uploaded.localFile}
                        </Text>
                        <Text size="sm" color="dimmed" inline mt={7}>
                            Line count: {uploaded.rowCount}, Fields:{uploaded.fieldList?.length || 0}
                        </Text>
                    </div>
                </Dropzone>
                <Space h="md" />
                <div>
                    <Group position="center" mt="xl">
                        <Button variant="outline" onClick={resetUpload}>Reset</Button>
                        <Button variant="outline" onClick={ingest}>Re-Upload list</Button>
                    </Group>
                </div>
            </>
        );
    }

    return (
        <>
            <Title order={3}>Reload for {speciesList.title}</Title>
            <Space h="md" />
            <Dropzone
                onDrop={(files) => {
                    setUploading(true);
                    console.log("accepted files", files);

                    const formData = new FormData();
                    formData.append("file", files[0]);
                    fetch(import.meta.env.VITE_UPLOAD_URL, {
                        method: "POST",
                        body: formData,
                    })
                        .then((res) => {
                            const respJson = res.json();
                            console.log(respJson);

                            respJson.then(
                                function (data) {
                                    setUploaded(data);
                                    setUploading(false);
                                    setIngesting(false);
                                },
                                function (error) {
                                    console.error(error);
                                    setUploading(false);
                                    setIngesting(false);
                                }
                            );
                            return respJson;
                        })
                        .catch((err) => {
                            // setUploading(false);
                            console.log("File upload error", err);
                        });
                }}
                onReject={(files) => {
                    console.log("rejected files", files);
                    setRejectedMessage(files[0].errors[0].message);
                }}
                maxSize={5 * 1024 ** 2}
                accept={ACCEPTED_TYPES}
            >
                <Group position="center" spacing="xl" style={{ minHeight: 220, pointerEvents: "none" }}>
                    <Dropzone.Accept>
                        <IconUpload size={24}
                        />
                    </Dropzone.Accept>
                    <Dropzone.Reject>
                        <Text h="md">{rejectedMessage}</Text>
                        <IconX
                        />
                    </Dropzone.Reject>
                    <Dropzone.Idle>
                        <IconFile size={64} />
                    </Dropzone.Idle>

                    <div>
                        <Text size="xl" inline>
                            Drag a CSV file or zipped CSV file here or click to select file
                        </Text>
                        <Text size="sm" color="dimmed" inline mt={7}>
                            Files should not exceed 5mb
                        </Text>
                        <Text h="md" weight="bold">
                            {rejectedMessage}
                        </Text>
                    </div>
                </Group>
            </Dropzone>
        </>
    );
}

export default ReloadList;