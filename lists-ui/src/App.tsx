import {
    AppShell,
    Container, Grid,
    Header,
    Space,
    Image,
    Text,
    Breadcrumbs, Button, Anchor
} from '@mantine/core';
import {Route, Routes} from "react-router-dom";
import SpeciesLists from "./views/SpeciesLists";
import SpeciesListView from "./views/SpeciesListView";
import {Breadcrumb, ListsUser, SpeciesList} from "./api/sources/model";
import {Metadata} from "./views/Metadata";
import SpeciesListSideBar from "./views/SpeciesListSideBar";
import UploadList from "./views/UploadList";
import ReloadList from "./views/ReloadList";
import {useContext, useEffect, useState} from "react";
import {Releases} from "./views/Releases";
import Admin from "./views/Admin.tsx";
import {User, UserManager} from "oidc-client-ts";
import AuthContext from "./helpers/AuthContext.ts";
import DangerZone from "./views/DangerZone.tsx";
import {SpeciesListsSideBar} from "./views/SpeciesListsSideBar.tsx";
import MyLists from "./views/MyLists.tsx";
import UserContext from "./helpers/UserContext.ts";

export default function App() {

    const [speciesList, setSpeciesList] = useState<SpeciesList | null | undefined>(null);
    const userManager: UserManager = useContext(AuthContext) as UserManager;
    const [currentUser, setCurrentUser] = useState<ListsUser | null>(null);
    const { search } = window.location;

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

    const signIn = () => {
        // @ts-ignore
        let redirectUri = {
            redirect_uri: window.location.href,
            expiry: new Date().getTime() + 300000
        };
        console.log(redirectUri);
        localStorage.setItem("redirectUri", JSON.stringify(redirectUri))
        userManager.signinRedirect(redirectUri);
    };
    const signOut = () => {
        userManager.signoutRedirect({"post_logout_redirect_uri": window.location.href})
    };

    const myProfile = () => {
        window.location.href = import.meta.env.VITE_PROFILE_URL
    };

    useEffect(() => {
        let result = userManager.getUser();
        result.then((user:User | null) => {

            if (user) {

                if (!user.expired) {
                    const roles = (user?.profile?.role || []) as string[];
                    const userId = user?.profile?.userid as string || '';
                    setCurrentUser({
                        user: user,
                        userId: userId,
                        isAdmin: roles.includes('ROLE_ADMIN'),
                        roles: roles
                    });
                    localStorage.setItem('access_token', user.access_token);
                    console.log('Setting current user');
                } else {

                    // TODO use refresh token
                    userManager.removeUser();
                    setCurrentUser(null);
                    localStorage.removeItem('access_token');
                    console.log('Setting current user to null');
                }
            } else if (search.includes('code=') && search.includes('state=')) {
                const params = new URLSearchParams(window.location.search);

                (async () => {
                    // Attempt to retrieve an access token
                    try {
                        const user = await userManager.signinRedirectCallback();
                        await userManager.storeUser(user);
                        const roles = (user?.profile?.role || []) as string[];
                        const userId = user?.profile?.userid as string || '';
                        setCurrentUser({
                            user: user,
                            userId: userId,
                            isAdmin: roles.includes('ROLE_ADMIN'),
                            roles: roles
                        });
                        localStorage.setItem('access_token', user.access_token);
                        console.log('Setting current user');
                    } catch (error) {
                        console.log("Problem retrieving access token: " + error);
                    }

                    // Replace the URL to one without the auth query params
                    params.delete('code');
                    params.delete('state');
                    params.delete('client_id');

                    let paramStr = (params.toString() ? "?" : "") + params.toString()

                    window.history.replaceState(
                        null,
                        '',
                        `${window.location.origin}${window.location.pathname}${paramStr}`
                    );
                })();
            } else {
                userManager.removeUser();
                localStorage.removeItem('access_token');
                setCurrentUser(null);
                console.log('Setting current user to null');
            }
        });
    }, []);

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
                        <Header height={{ base: 140, md: 140, padding: 0, margin: 0 }}>
                            <Container
                                style={{
                                    backgroundColor: '#212121',
                                    width: '100%',
                                    paddingTop: '20px',
                                    paddingLeft: 0,
                                    paddingRight: 0
                                }}
                                size="100%"
                            >
                                    <Image
                                        radius="md"
                                        src={import.meta.env.VITE_LOGO_URL}
                                        alt="ALA logo"
                                        width={'335px'}
                                        style={{ marginTop: '10px', marginLeft: '30px' }}
                                    />

                                    <div className={`loginButtons`} style={{ position: 'absolute', right: '20px', top: '20px', color: 'white'}}>
                                        { currentUser ? (
                                            <>
                                                <Button onClick={myProfile} variant="outline" size="md" style={{ marginRight: '10px'}} compact>
                                                    Profile - {currentUser?.user?.profile?.name} {currentUser.isAdmin ? '(ADMIN)' : ''}
                                                </Button>
                                                <Button onClick={signOut} variant="outline" size="md" compact>Logout</Button>
                                            </>
                                        ) : (
                                            <Button onClick={signIn} variant="outline" color="gray" size="md" compact>Sign In</Button>
                                        )}
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
                                    size="100%"
                                >
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
                                <Grid.Col xs={1} sm={2} style={{ backgroundColor: '#F6F6F6' }}>
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
                                <Grid.Col xs={1} sm={2} style={{ backgroundColor: '#F6F6F6' }}>
                                    <SpeciesListSideBar selectedView="metadata" resetSpeciesList={resetSpeciesList} />
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
                                <Grid.Col xs={1} sm={2} style={{ backgroundColor: '#F6F6F6' }}>
                                    <SpeciesListSideBar selectedView="metadata" resetSpeciesList={resetSpeciesList} />
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

