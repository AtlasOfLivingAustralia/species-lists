import {
    Table,
    Grid,
    Text,
    Button,
} from "@mantine/core";
import {SpeciesListsSideBar} from "./SpeciesListsSideBar.tsx";
import UserContext from "../helpers/UserContext.ts";
import {useContext} from "react";
import {ListsUser} from "../api/sources/model.ts";
import {notifications} from "@mantine/notifications";
import {IconCheck, IconX} from "@tabler/icons-react";

function Admin() {

    const currentUser = useContext(UserContext) as ListsUser;

    function reindex() {
        if (window.confirm('Are you sure you want to reindex all lists?')) {
            fetch(import.meta.env.VITE_REINDEX_URL, {
                method: 'GET',
                headers: {
                    'Authorization': 'Bearer ' + currentUser?.user.access_token,
                }
            }).then(response => {
                if (response.ok) {
                    notifications.show({
                        icon: <IconCheck />,
                        className: 'success-notification',
                        title: 'Reindexing started',
                        message: 'This will will take a while to finish.'
                    })

                } else {
                    if (response.status == 409){
                        notifications.show({
                            icon: <IconX />,
                            className: 'success-notification',
                            title: 'Reindexing already running!',
                            message: 'Reindexing already running.'
                        });
                    } else {
                        notifications.show({
                            icon: <IconX/>,
                            className: 'fail-notification',
                            title: 'Reindexing was not started due to an error',
                            message: 'Unable to start reindexing. Please try again later'
                        })
                    }
                }
            });
        }
    }

    function rematch() {
        if (window.confirm('Are you sure you want to rematch all lists?')) {
            fetch(import.meta.env.VITE_REMATCH_URL, {
                method: 'GET',
                headers: {
                    'Authorization': 'Bearer ' + currentUser?.user.access_token,
                }
            }).then(response => {
                if (response.ok) {
                    notifications.show({
                        icon: <IconCheck />,
                        className: 'success-notification',
                        title: 'Rematching started',
                        message: 'This will  take a while to finish.'
                    })
                } else {
                    if (response.status == 409){
                        notifications.show({
                            icon: <IconX />,
                            className: 'success-notification',
                            title: 'Rematching already running!',
                            message: 'Rematching already running.'
                        });
                    } else {
                        notifications.show({
                            icon: <IconX/>,
                            className: 'fail-notification',
                            title: 'Rematching failed to start',
                            message: 'Unable to start rematching. Please try again later'
                        })
                    }
                }
            });
        }
    }

    function migrate() {
        if (window.confirm('Are you sure you want to migrate all lists?')) {
            fetch(import.meta.env.VITE_MIGRATE_URL, {
                method: 'GET',
                headers: {
                    'Authorization': 'Bearer ' + currentUser?.user.access_token,
                }
            }).then(response => {
                if (response.ok) {
                    notifications.show({
                        icon: <IconCheck/>,
                        className: 'success-notification',
                        title: 'Migration started',
                        message: 'This will will take a while to finish.'
                    })
                } else {
                    if (response.status == 409){
                        notifications.show({
                            icon: <IconX />,
                            className: 'success-notification',
                            title: 'Migration already running!',
                            message: 'Migration already running.'
                        });
                    } else {
                        notifications.show({
                            icon: <IconX />,
                            className: 'fail-notification',
                            title: 'Migration failed!',
                            message: 'Migration failed to start. Please try again later'
                        });
                    }
                }
            });
        }
    }

    return (
        <>
            <Grid mb="md">
                <Grid.Col xs={1} sm={2}>
                    <SpeciesListsSideBar
                        resetSpeciesList={() => console.log('reset list')}
                        selectedView="admin"
                    />
                </Grid.Col>
                <Grid.Col xs={12} sm={10}>
                    <>
                        <h2>Admin tools</h2>
                        {!currentUser?.isAdmin &&
                            <p>User {currentUser?.user?.profile?.name} is not authorised to access these tools.</p>
                        }
                        {currentUser?.isAdmin &&
                        <Table verticalSpacing="lg" width={50}>
                            <tbody>
                            <tr >
                                <td><Button variant="outline" onClick={reindex}>Reindex lists</Button></td>
                                <td><Text>Regenerate the elastic search index for all lists</Text></td>
                            </tr>
                            <tr>
                                <td><Button variant="outline"  onClick={rematch}>Rematch lists</Button></td>
                                <td><Text>Rematch the taxonomy for all lists.
                                    <br/>Note: this does not re-index the data</Text>
                                </td>
                            </tr>
                            <tr>
                                <td><Button variant="outline"  onClick={migrate}>Migrate</Button></td>
                                <td><Text>Migrate data from existing list tool.
                                    <br/>Note: this does not perform taxon matching and re-indexing the data.
                                </Text></td>
                            </tr>
                            </tbody>
                        </Table>
                        }
                    </>
                </Grid.Col>
            </Grid>
        </>
    );
}

export default Admin;
