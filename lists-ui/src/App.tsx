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
import {Breadcrumb, SpeciesList} from "./api/sources/model";
import {Metadata} from "./views/Metadata";
import SpeciesListSideBar from "./views/SpeciesListSideBar";
import UploadList from "./views/UploadList";
import ReloadList from "./views/ReloadList";
import {useContext, useState} from "react";
import {Releases} from "./views/Releases";
import Admin from "./views/Admin.tsx";
import {useAuth} from "./helpers/useAuth.ts";
import {UserManager} from "oidc-client-ts";
import AuthContext from "./helpers/AuthContext.ts";
import DangerZone from "./views/DangerZone.tsx";
import {SpeciesListsSideBar} from "./views/SpeciesListsSideBar.tsx";
import MyLists from "./views/MyLists.tsx";

export default function App() {

    const [speciesList, setSpeciesList] = useState<SpeciesList | null | undefined>(null);
    const {currentUser} = useAuth();
    const userManager:UserManager = useContext(AuthContext) as UserManager;

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

    return (
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
                                    {currentUser ? (
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
                        <SpeciesLists />
                    }
                />
                <Route
                    path="/my-lists"
                    element={
                        <MyLists />
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
                                <SpeciesListsSideBar  resetSpeciesList={resetSpeciesList} />
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
    );
}

