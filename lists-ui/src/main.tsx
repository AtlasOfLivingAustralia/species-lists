import React from 'react';
import ReactDOM from 'react-dom/client';
import './index.css';
import App from './App';
import {ApolloClient, InMemoryCache, ApolloProvider, createHttpLink} from "@apollo/client";
import {HashRouter as Router} from 'react-router-dom';
import {Global, MantineProvider} from "@mantine/core";
import messages_en from "./translations/en.json";
import {IntlProvider} from "react-intl";
import {setContext} from "@apollo/client/link/context";
import {Notifications} from "@mantine/notifications";
import { AuthProvider } from "react-oidc-context";
import {User, WebStorageStateStore} from "oidc-client-ts";

const httpLink = createHttpLink({
    uri: import.meta.env.VITE_GRAPHQL_URL,
});

const authLink = setContext((_, { headers }) => {

    // get the authentication token from local storage if it exists
    const oidcStorage = localStorage.getItem(`oidc.user:${import.meta.env.VITE_OIDC_AUTH_SERVER}:${import.meta.env.VITE_OIDC_CLIENT_ID}`);
    if (oidcStorage) {
        const user = User.fromStorageString(oidcStorage);
        if (!user.expired){
            const token = user?.access_token;
            return {
                headers: {
                    ...headers,
                    authorization: token ? `Bearer ${token}` : "",
                }
            }
        } else {
            // TODO look into refresh token
            localStorage.removeItem(`oidc.user:${import.meta.env.VITE_OIDC_AUTH_SERVER}:${import.meta.env.VITE_OIDC_CLIENT_ID}`);
        }
    }

    return {
        headers: {
            ...headers,
        }
    }
});

const client = new ApolloClient({
    link: authLink.concat(httpLink),
    cache: new InMemoryCache()
});

const root = ReactDOM.createRoot(
    document.getElementById('root') as HTMLElement
);

const oidcConfig = {
    authority: import.meta.env.VITE_OIDC_AUTH_SERVER,
    client_id:  import.meta.env.VITE_OIDC_CLIENT_ID,
    redirect_uri: import.meta.env.VITE_OIDC_REDIRECT_URL,
    scope: import.meta.env.VITE_OIDC_SCOPE,
    post_logout_redirect_uri: import.meta.env.VITE_OIDC_REDIRECT_URL,
    userStore: new WebStorageStateStore({ store: window.localStorage }),
    onSigninCallback: () => {
        const { search } = window.location;
        if (search.includes('code=') && search.includes('state=')) {
            const params = new URLSearchParams(window.location.search);
            params.delete('code');
            params.delete('state');
            params.delete('client_id');
            let paramStr = (params.toString() ? "?" : "") + params.toString()
            window.history.replaceState(
                null,
                '',
                `${window.location.origin}${window.location.pathname}${paramStr}`
            );
        }
    },
    onSignoutCallback: () => {
        console.log("onSignoutCallback");
    }

};

root.render(
    <MantineProvider
        theme={{
            colorScheme: 'light',
            fontFamily: 'Roboto, sans-serif',
            headings: {
                fontFamily: 'Lato, sans-serif',
            },
            primaryColor:'rust',
            colors: {
                rust: [
                    '#000000',
                    '#000000',
                    '#FDEBE7',
                    '#FAC7BC',
                    '#F7A392',
                    '#F47F67',
                    '#c44d34',
                    '#BE2C0E',
                    '#8F210A',
                ],
            },
        }}
        withGlobalStyles withNormalizeCSS
    >
        <Global
            styles={(theme) => ({
                body: {
                    backgroundColor:
                        theme.colorScheme === 'dark'
                            ? theme.colors.dark[7]
                            : theme.colors.gray[5],
                },
            })}
        />
        <React.StrictMode>
            <Router>
                <AuthProvider {...oidcConfig}>
                    <ApolloProvider client={client}>
                        <IntlProvider messages={messages_en} locale="en" defaultLocale="en" onError={() => {}}>
                            <Notifications position="top-center" />
                            <App />
                        </IntlProvider>
                    </ApolloProvider>
                </AuthProvider>
            </Router>
        </React.StrictMode>
    </MantineProvider>
);