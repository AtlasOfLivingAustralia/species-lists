import {
    AppShell,
    Container, Grid,
    Header,
    Space,
    Image,
    Text,
    Breadcrumbs, Button, Anchor, Group, Loader, Modal, Divider, Box
} from '@mantine/core';
import {Route, Routes} from "react-router-dom";
import SpeciesLists from "./views/SpeciesLists";
import SpeciesListView from "./views/SpeciesListView";
import {Breadcrumb, ListsUser, SpeciesList} from "./api/sources/model";
import {Metadata} from "./views/Metadata";
import SpeciesListSideBar from "./views/SpeciesListSideBar";
import UploadList from "./views/UploadList";
import ReloadList from "./views/ReloadList";
import {useState} from "react";
import {Releases} from "./views/Releases";
import Admin from "./views/Admin.tsx";
import DangerZone from "./views/DangerZone.tsx";
import {SpeciesListsSideBar} from "./views/SpeciesListsSideBar.tsx";
import MyLists from "./views/MyLists.tsx";
import UserContext from "./helpers/UserContext.ts";
import {useAuth} from "react-oidc-context";
import {IconLogin, IconLogout} from "@tabler/icons-react";

export default function App() {

    const [speciesList, setSpeciesList] = useState<SpeciesList | null | undefined>(null);
    const [currentUser, setCurrentUser] = useState<ListsUser | null>(null);
    const auth = useAuth();

    const resetSpeciesList = () => {
        setSpeciesList(null);
    };

    const breadcrumbs: Breadcrumb[] = [
        { title: 'Home', href: import.meta.env.VITE_HOME_URL },
        { title: 'Species lists and traits', href: '/' },
    ];

    if (speciesList) {
        breadcrumbs.push({ title: speciesList.title, href: location.href });
    }

    // const breadcrumbItems = breadcrumbMap.map(item => item.title);
    const breadcrumbItems = breadcrumbs.map( (breadcrumb: Breadcrumb) => <Anchor href={breadcrumb.href ? breadcrumb.href : '#'  }>{breadcrumb.title}</Anchor> );

    if (auth.error) {
        return <div>Configuration error... {auth.error.message}</div>;
    }

    const myProfile = () => {
        window.location.href = import.meta.env.VITE_OIDC_AUTH_PROFILE
    };

    const signUp = () => {
        window.location.href = import.meta.env.VITE_SIGNUP_URL
    };

    const logout = () => {
        auth.removeUser();
        setCurrentUser(null);
        window.location.href = import.meta.env.VITE_OIDC_REDIRECT_URL
    };

    if (auth.isAuthenticated && auth.user && !currentUser) {
        // set the current user
        const user = auth.user;
        const roles = (user?.profile[import.meta.env.VITE_PROFILE_ROLES] || []) as string[];
        const userId = (user?.profile[import.meta.env.VITE_PROFILE_USERID]) as string || '';
        setCurrentUser({
            user: auth.user,
            userId: userId,
            isAdmin: roles.includes(import.meta.env.VITE_ADMIN_ROLE),
            roles: roles
        });
    }

    return (
        <UserContext.Provider value={currentUser}>
            <AppShell
                navbarOffsetBreakpoint="sm"
                asideOffsetBreakpoint="sm"
                style={{
                    backgroundColor: '#FFF',
                }}
                header={
                    <>
                        <Modal title="About" opened={auth.isLoading} onClose={() => console.log("auth checked")} >
                            <Group>
                                <Loader color="orange" />
                                <Text>Logging in...</Text>
                            </Group>
                        </Modal>
                        <Header height={{ base: 140, md: 140, padding: 0, margin: 0 }}>
                            <Container
                                style={{
                                    backgroundColor: '#212121',
                                    width: '100%',
                                    paddingTop: '20px',
                                    paddingLeft: 0,
                                    paddingRight: 0
                                }}
                                size="100%">
                                    <Image
                                        className={`logoImage`}
                                        radius="md"
                                        src={import.meta.env.VITE_LOGO_URL}
                                        alt="ALA logo"
                                        width={'335px'}
                                        fit="contain"
                                        style={{ marginTop: '10px', marginLeft: '30px' }}
                                    />

                                    <Image
                                        className={`logoImageSmall`}
                                        radius="md"
                                        src={import.meta.env.VITE_LOGO_URL}
                                        alt="ALA logo"
                                        width={'250px'}
                                        fit="contain"
                                        style={{ marginTop: '10px', marginLeft: '30px' }}
                                    />

                                    <div className={`loginButtons`} style={{ position: 'absolute', right: '40px', top: '20px', color: 'white'}}>

                                        <Group>
                                            <Anchor className={`contactUsLink`} href="https://www.ala.org.au/contact-us/" target="_blank"
                                                    sx={() => ({
                                                        '@media (max-width: 800px)': {
                                                            display: 'none'
                                                        },
                                                    })}
                                            >
                                                Contact us
                                            </Anchor>

                                            <Divider size="xs" orientation="vertical" style={{ borderLeft: '#808080 1px solid'}} />

                                            { currentUser ? (
                                                <>
                                                    <Button radius="xs" onClick={myProfile} variant="outline" size="md" compact sx={() => ({
                                                        '@media (max-width: 800px)': {
                                                            display: 'none'
                                                        },
                                                    })}>
                                                        <Text>
                                                            Profile - {currentUser?.user?.profile?.name || (currentUser?.user?.profile?.given_name + ' ' + currentUser?.user?.profile?.family_name)} {currentUser?.isAdmin ? '(ADMIN)' : ''}
                                                        </Text>
                                                    </Button>
                                                    <Button radius="xs" onClick={logout} variant="outline" size="md" compact sx={() => ({
                                                        '@media (max-width: 800px)': {
                                                            display: 'none'
                                                        },
                                                    })}>
                                                        <Text >Logout</Text>
                                                    </Button>
                                                    <Box  sx={() => ({
                                                        '@media (min-width: 800px)': {
                                                            display: 'none'
                                                        },
                                                    })}>
                                                        <IconLogout onClick={logout}/>
                                                    </Box>
                                                </>
                                            ) : (
                                                <>
                                                    <Group>
                                                        <Button radius="xs" onClick={signUp} size="md" compact sx={() => ({
                                                            '@media (max-width: 800px)': {
                                                                display: 'none'
                                                            },
                                                        })}>Sign up</Button>
                                                        <Button className={`loginButton`} radius="xs" onClick={() => void auth.signinRedirect()}
                                                                color="orange" size="md" compact sx={() => ({
                                                            '@media (max-width: 800px)': {
                                                                display: 'none'
                                                            },
                                                        })}>Login</Button>

                                                        <Box  sx={() => ({
                                                            '@media (min-width: 800px)': {
                                                                display: 'none'
                                                            },
                                                        })}>
                                                           <IconLogin onClick={() => void auth.signinRedirect()} />
                                                        </Box>

                                                    </Group>
                                                </>
                                            )}
                                        </Group>
                                    </div>

                                <Container
                                    style={{
                                        backgroundColor: '#E7E7E7',
                                        width: '100%',
                                        marginLeft: 0,
                                        marginRight: 0,
                                        paddingRight: 0,
                                        marginTop: '20px',
                                    }}
                                    size="100%">
                                    <Text style={{ padding: '16px 12px 16px 22px', color: '#000', paddingLeft: '22px' }}>
                                        <Breadcrumbs id={`breadcrumbs`} separator="&#x276F;" style={{ fontFamily: '"Roboto",sans-serif', fontSize: '14px' }}>
                                            {breadcrumbItems}
                                        </Breadcrumbs>
                                    </Text>
                                </Container>
                            </Container>
                        </Header>
                    </>
                }>

                <Space h="md" />
                <Routes>
                    <Route
                        path="/"
                        element={
                            <SpeciesLists isPrivate={false}  />
                        }
                    />
                    <Route
                        path="/my-lists"
                        element={
                            <MyLists />
                        }
                    />
                    <Route
                        path="/private-lists"
                        element={
                            <SpeciesLists isPrivate={true}  />
                        }
                    />
                    <Route
                        path="/list/:speciesListID"
                        element={
                            <SpeciesListView
                                setSpeciesList={(speciesList:SpeciesList | null | undefined) => setSpeciesList(speciesList)}
                                resetSpeciesList={resetSpeciesList}
                            />
                        }
                    />
                    <Route
                        path="/metadata/:speciesListID"
                        element={
                            <Metadata setSpeciesList={(list : SpeciesList) => setSpeciesList(list)} />
                        }
                    />
                    <Route
                        path="/releases/:speciesListID"
                        element={
                            <Grid mb="md" align="flex-start">
                                <Grid.Col xs={1} sm={2} className={`sideBarColumn`}>
                                    <SpeciesListSideBar selectedView="releases" resetSpeciesList={resetSpeciesList} />
                                </Grid.Col>
                                <Grid.Col xs={12} sm={10}>
                                    <Releases />
                                </Grid.Col>
                            </Grid>
                        }
                    />
                    <Route
                        path="/reload/:speciesListID"
                        element={
                            <Grid mb="md" align="flex-start">
                                <Grid.Col xs={1} sm={2} className={`sideBarColumn`}>
                                    <SpeciesListSideBar selectedView="reload" resetSpeciesList={resetSpeciesList} />
                                </Grid.Col>
                                <Grid.Col xs={12} sm={10}>
                                    <ReloadList/>
                                </Grid.Col>
                            </Grid>
                        }
                    />
                    <Route
                        path="/dangerzone/:speciesListID"
                        element={
                            <Grid mb="md" align="flex-start">
                                <Grid.Col xs={1} sm={2} className={`sideBarColumn`}>
                                    <SpeciesListSideBar selectedView="dangerzone" resetSpeciesList={resetSpeciesList} />
                                </Grid.Col>
                                <Grid.Col xs={12} sm={10}>
                                    <DangerZone />
                                </Grid.Col>
                            </Grid>
                        }
                    />
                    <Route
                        path="/upload"
                        element={
                            <Grid mb="md" align="flex-start">
                                <Grid.Col xs={1} sm={2}>
                                    <SpeciesListsSideBar
                                        resetSpeciesList={resetSpeciesList}
                                        selectedView="upload-new"
                                    />
                                </Grid.Col>
                                <Grid.Col xs={12} sm={10}>
                                    <UploadList />
                                </Grid.Col>
                            </Grid>
                        }
                    />
                    <Route
                        path="/admin"
                        element={
                            <Admin />
                        }
                    />
                </Routes>
            </AppShell>
        </UserContext.Provider>
    );
}

