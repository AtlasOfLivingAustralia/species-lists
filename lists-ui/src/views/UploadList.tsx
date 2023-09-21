import {useContext, useState} from "react";
import {
    Group,
    Text,
    Button,
    Notification,
} from "@mantine/core";
import { IconUpload, IconX, IconFile } from "@tabler/icons-react";
import { Dropzone } from "@mantine/dropzone";
import { useNavigate } from "react-router-dom";
import { MetadataForm } from "./MetadataForm";
import {ListsUser, SpeciesList} from "../api/sources/model";
import UserContext from "../helpers/UserContext.ts";

const ACCEPTED_TYPES: string[] = ["text/csv", "application/zip"];

function UploadList() {
    const navigate = useNavigate();
    const [uploading, setUploading] = useState<boolean>(false);
    const [uploaded, setUploaded] = useState<any>(null); // Replace 'any' with actual uploaded data type
    const [ingesting, setIngesting] = useState<boolean | null>(null);
    const [rejectedMessage, setRejectedMessage] = useState<string | null>(null);
    const currentUser = useContext(UserContext) as ListsUser;

    let speciesList:SpeciesList = {} as SpeciesList;

    function resetUpload() {
        setUploaded(null);
        setUploading(false);
        setIngesting(false);
    }

    function ingest(values: SpeciesList) {

        if (!currentUser?.user?.access_token){
            alert('Access token empty');
            return;
        }

        if (currentUser && currentUser?.user?.access_token) {
            setIngesting(true);
            const formData = new FormData();
            formData.append("title", values["title"]);
            formData.append("description", values["description"]);
            formData.append("listType", values["listType"]);
            formData.append("region", values["region"]);
            formData.append("authority", values["authority"]);
            formData.append("licence", values["licence"]);
            formData.append("file", uploaded.localFile);

            fetch(import.meta.env.VITE_INGEST_URL, {
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
                            setIngesting(false);
                            if (data?.id){
                                navigate("/list/" + data.id);
                            } else {
                                console.log(data);
                            }
                        },
                        function (error) {
                            console.log(error);
                            setIngesting(false);
                        }
                    );
                    return respJson;
                })
                .catch((err) => {
                    console.log("File upload error", err);
                });
        }
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
                    <Notification loading title="Uploading data to the server"  withCloseButton={false}>
                        Please wait until data is uploaded....
                    </Notification>
                </Text>
            </div>
        );
    }

    if (uploaded) {
        return (
            <>
                <Dropzone disabled onDrop={() => console.log('disabled')}>
                    <div>
                        <Text size="xl" inline>
                            <IconFile /> File Uploaded: {uploaded.localFile}
                        </Text>
                        <Text size="sm" color="dimmed" inline mt={7}>
                            Line count: {uploaded.rowCount}, Fields:{uploaded.fieldList?.length || 0}
                        </Text>
                    </div>
                </Dropzone>
                <br />
                <div>
                    <MetadataForm
                        speciesList={speciesList}
                        submitFormFcn={ingest}
                        edit={true}
                        formButtons={
                            <Group position="center" mt="xl">
                                <Button variant="outline" onClick={resetUpload}>Reset</Button>
                                {currentUser &&
                                    <Button  variant="outline" type="submit">Upload list</Button>
                                }
                                {!currentUser &&
                                    <Button variant="outline" onClick={() => navigate("/login")}>Login to upload list</Button>
                                }
                            </Group>
                        }
                    />
                </div>
            </>
        );
    }

    return (
        <>
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
                                },
                                function (error) {
                                    console.log(error);
                                    setUploading(false);
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
                        <IconUpload />
                    </Dropzone.Accept>
                    <Dropzone.Reject>
                        <Text h="md">{rejectedMessage}</Text>
                        <IconX />
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

export default UploadList;
