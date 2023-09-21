import {
    Table,
    Grid,
    Text, Button,
} from "@mantine/core";
import {useAuth} from "../helpers/useAuth.ts";
import {SpeciesListsSideBar} from "./SpeciesListsSideBar.tsx";

function Admin() {

    const {  currentUser } = useAuth();

    function reindex() {
        if (window.confirm('Are you sure you want to reindex all lists?')) {
            fetch(import.meta.env.VITE_REINDEX_URL, {
                method: 'GET',
                headers: {
                    'Authorization': 'Bearer ' + currentUser?.user.access_token,
                }
            }).then(response => {
                if (response.ok) {
                    alert('Reindexing started.');
                } else {
                    alert('Reindexing failed.');
                }
            });
        }
    }

    return (
        <>
            <Grid mb="md">
                <Grid.Col xs={1} sm={2}>
                    <SpeciesListsSideBar resetSpeciesList={() => console.log('reset list')} />
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
                                <td><Button variant="outline"  onClick={() => alert('Not implemented! ')}>Rematch lists</Button></td>
                                <td><Text>Rematch the taxonomy for all lists</Text></td>
                            </tr>
                            <tr>
                                <td><Button variant="outline"  onClick={() => alert('Not implemented! ')}>Migrate</Button></td>
                                <td><Text>Migrate data from existing list tool</Text></td>
                            </tr>
                            <tr>
                                <td><Button variant="outline"  onClick={() => alert('Not implemented! ')}>Manage licences</Button></td>
                                <td><Text>Manage the CC licences available to assign to lists</Text></td>
                            </tr>
                            <tr>
                                <td><Button variant="outline"  onClick={() => alert('Not implemented! ')}>Manage list types</Button></td>
                                <td><Text>Manage the list type vocabulary</Text></td>
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
