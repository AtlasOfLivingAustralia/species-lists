import {
    Button, Group, Text
} from "@mantine/core";
import { useNavigate, useParams } from "react-router-dom";
import {useAuth} from "../helpers/useAuth.ts";
import {IconAlertHexagon} from "@tabler/icons-react";

function DangerZone() {
    const navigate = useNavigate();
    const { speciesListID } = useParams();
    const { currentUser } = useAuth();

    function deleteList() {
        fetch( import.meta.env.VITE_DELETE_URL + "/" + speciesListID, {
            method: "DELETE",
            headers: {
                'Authorization': `Bearer ${currentUser?.user?.access_token}`
            },
        }).then((res) => {
            console.log(res);
            navigate(`/`);
        });
    }

    return (
        <>
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
        </>
    );
}

export default DangerZone;